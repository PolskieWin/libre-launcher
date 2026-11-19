//! Rust Native Telemetry & Frame Pacing Engine for Libre Launcher
//! Zero heap allocations or mutexes on the render thread.

use std::os::raw::{c_char, c_int, c_void};
use std::sync::atomic::{AtomicBool, AtomicU32, AtomicU64, AtomicUsize, Ordering};
use std::sync::OnceLock;
use std::time::{Duration, Instant};

pub const RING_BUFFER_SIZE: usize = 240;

// Lock-Free Ring Buffer: [AtomicU64; 240] recording nanosecond frame deltas
static FRAME_RING_BUFFER: [AtomicU64; RING_BUFFER_SIZE] = {
    const INIT: AtomicU64 = AtomicU64::new(16_666_667); // Default ~60 FPS
    [INIT; RING_BUFFER_SIZE]
};

static RING_HEAD: AtomicUsize = AtomicUsize::new(0);
static TOTAL_FRAMES: AtomicU64 = AtomicU64::new(0);
static LAST_FRAME_TIMESTAMP_NS: AtomicU64 = AtomicU64::new(0);

// Hibernate Mode state
static IS_HIBERNATE: AtomicBool = AtomicBool::new(false);
static HIBERNATE_BUDGET_NS: AtomicU64 = AtomicU64::new(200_000_000); // 5 FPS (200ms frame budget)
static HIBERNATE_TARGET_FPS: AtomicU32 = AtomicU32::new(5);

// NDK Thermal Cache (polled strictly at most once every 10 seconds)
static CACHED_THERMAL_HEADROOM_X100: AtomicU32 = AtomicU32::new(0);
static LAST_THERMAL_POLL_MS: AtomicU64 = AtomicU64::new(0);

static START_INSTANT: OnceLock<Instant> = OnceLock::new();

#[inline]
pub fn get_monotonic_nanos() -> u64 {
    let start = START_INSTANT.get_or_init(Instant::now);
    Instant::now().duration_since(*start).as_nanos() as u64
}

#[repr(C)]
#[derive(Clone, Copy, Debug, PartialEq)]
pub struct LibreMetrics {
    pub avg_fps: f32,
    pub one_percent_low: f32,
    pub latest_frame_time_ms: f32,
    pub jitter_ms: f32,
    pub ram_used_mb: u32,
    pub thermal_headroom: f32,
    pub is_hibernate: bool,
    pub total_frames: u64,
}

/// Swap Hook called from GL/Vulkan present path (eglSwapBuffers / vkQueuePresentKHR)
/// Lock-free, zero heap allocation, paces thread via std::thread::sleep when Hibernate is active.
#[no_mangle]
pub extern "C" fn libre_on_frame_present() -> u64 {
    let now_ns = get_monotonic_nanos();
    let prev_ns = LAST_FRAME_TIMESTAMP_NS.swap(now_ns, Ordering::AcqRel);

    let delta_ns = if prev_ns > 0 && now_ns > prev_ns {
        let diff = now_ns - prev_ns;
        if diff == 0 { 1 } else { diff }
    } else {
        16_666_667
    };

    let idx = RING_HEAD.fetch_add(1, Ordering::Relaxed) % RING_BUFFER_SIZE;
    FRAME_RING_BUFFER[idx].store(delta_ns, Ordering::Release);
    TOTAL_FRAMES.fetch_add(1, Ordering::Relaxed);

    // If Hibernate Mode is active, pace the render thread
    if IS_HIBERNATE.load(Ordering::Acquire) {
        let budget_ns = HIBERNATE_BUDGET_NS.load(Ordering::Relaxed);
        if delta_ns < budget_ns {
            let sleep_ns = budget_ns - delta_ns;
            std::thread::sleep(Duration::from_nanos(sleep_ns));
            LAST_FRAME_TIMESTAMP_NS.store(get_monotonic_nanos(), Ordering::Release);
        }
    }

    delta_ns
}

/// Configure Hibernate (Low-Power AFK) mode and target frame rate (e.g. 5 FPS = 200ms frame budget)
#[no_mangle]
pub extern "C" fn libre_set_hibernate(enabled: bool, target_fps: u32) {
    let fps = target_fps.clamp(1, 1000);
    let budget_ns = 1_000_000_000u64 / (fps as u64);
    HIBERNATE_BUDGET_NS.store(budget_ns, Ordering::Release);
    HIBERNATE_TARGET_FPS.store(fps, Ordering::Release);
    IS_HIBERNATE.store(enabled, Ordering::Release);
}

#[no_mangle]
pub extern "C" fn libre_is_hibernate() -> bool {
    IS_HIBERNATE.load(Ordering::Acquire)
}

/// Compute metrics every 500ms using a stack-local copy of the ring buffer
#[no_mangle]
pub extern "C" fn libre_compute_metrics() -> LibreMetrics {
    let mut local_buf = [0u64; RING_BUFFER_SIZE];
    let mut valid_count = 0usize;
    let mut sum_ns = 0u64;

    for i in 0..RING_BUFFER_SIZE {
        let val = FRAME_RING_BUFFER[i].load(Ordering::Relaxed);
        if val > 0 {
            local_buf[valid_count] = val;
            sum_ns += val;
            valid_count += 1;
        }
    }

    if valid_count == 0 {
        return LibreMetrics {
            avg_fps: 0.0,
            one_percent_low: 0.0,
            latest_frame_time_ms: 0.0,
            jitter_ms: 0.0,
            ram_used_mb: read_proc_rss_mb(),
            thermal_headroom: read_thermal_headroom(),
            is_hibernate: IS_HIBERNATE.load(Ordering::Relaxed),
            total_frames: TOTAL_FRAMES.load(Ordering::Relaxed),
        };
    }

    let avg_delta_ns = (sum_ns as f64) / (valid_count as f64);
    let avg_fps = if avg_delta_ns > 0.0 {
        (1_000_000_000.0 / avg_delta_ns) as f32
    } else {
        0.0
    };

    // Calculate frame jitter (Mean Absolute Deviation in ms)
    let mut jitter_sum_ms = 0.0f64;
    for i in 0..valid_count {
        let delta_ms = (local_buf[i] as f64) / 1_000_000.0;
        let avg_ms = avg_delta_ns / 1_000_000.0;
        jitter_sum_ms += (delta_ms - avg_ms).abs();
    }
    let jitter_ms = (jitter_sum_ms / (valid_count as f64)) as f32;

    // Latest frame time
    let head = RING_HEAD.load(Ordering::Relaxed);
    let latest_idx = (head + RING_BUFFER_SIZE - 1) % RING_BUFFER_SIZE;
    let latest_delta_ns = FRAME_RING_BUFFER[latest_idx].load(Ordering::Relaxed);
    let latest_frame_time_ms = (latest_delta_ns as f32) / 1_000_000.0;

    // 1% Low FPS: in-place sort on the stack-local array
    for i in 1..valid_count {
        let mut j = i;
        while j > 0 && local_buf[j - 1] > local_buf[j] {
            local_buf.swap(j - 1, j);
            j -= 1;
        }
    }
    let p99_idx = ((valid_count as f32) * 0.99) as usize;
    let p99_idx = p99_idx.min(valid_count.saturating_sub(1));
    let p99_ns = local_buf[p99_idx];
    let one_percent_low = if p99_ns > 0 {
        (1_000_000_000.0 / (p99_ns as f64)) as f32
    } else {
        0.0
    };

    LibreMetrics {
        avg_fps,
        one_percent_low,
        latest_frame_time_ms,
        jitter_ms,
        ram_used_mb: read_proc_rss_mb(),
        thermal_headroom: read_thermal_headroom(),
        is_hibernate: IS_HIBERNATE.load(Ordering::Relaxed),
        total_frames: TOTAL_FRAMES.load(Ordering::Relaxed),
    }
}

/// NDK Thermal Headroom Reader (polled strictly at most once every 10 seconds)
pub fn read_thermal_headroom() -> f32 {
    let now_ms = get_monotonic_nanos() / 1_000_000;
    let last_poll = LAST_THERMAL_POLL_MS.load(Ordering::Relaxed);

    if last_poll > 0 && now_ms.saturating_sub(last_poll) < 10_000 {
        return (CACHED_THERMAL_HEADROOM_X100.load(Ordering::Relaxed) as f32) / 100.0;
    }

    LAST_THERMAL_POLL_MS.store(now_ms, Ordering::Relaxed);

    let headroom = unsafe { poll_ndk_thermal_headroom() };
    let clamped = headroom.clamp(0.0, 2.0);
    CACHED_THERMAL_HEADROOM_X100.store((clamped * 100.0) as u32, Ordering::Relaxed);
    clamped
}

type AThermalAcquireManagerFn = unsafe extern "C" fn() -> *mut c_void;
type AThermalGetThermalHeadroomFn = unsafe extern "C" fn(*mut c_void, c_int) -> f32;

extern "C" {
    fn dlopen(filename: *const c_char, flags: c_int) -> *mut c_void;
    fn dlsym(handle: *mut c_void, symbol: *const c_char) -> *mut c_void;
}

unsafe fn poll_ndk_thermal_headroom() -> f32 {
    let lib_name = b"libandroid.so\0";
    let handle = dlopen(lib_name.as_ptr() as *const c_char, 2 /* RTLD_NOW */);
    if handle.is_null() {
        return 0.0;
    }

    let acquire_name = b"AThermal_acquireManager\0";
    let get_headroom_name = b"AThermal_getThermalHeadroom\0";

    let acquire_sym = dlsym(handle, acquire_name.as_ptr() as *const c_char);
    let get_sym = dlsym(handle, get_headroom_name.as_ptr() as *const c_char);

    if acquire_sym.is_null() || get_sym.is_null() {
        return 0.0;
    }

    let acquire_fn: AThermalAcquireManagerFn = std::mem::transmute(acquire_sym);
    let get_fn: AThermalGetThermalHeadroomFn = std::mem::transmute(get_sym);

    let manager = acquire_fn();
    if manager.is_null() {
        return 0.0;
    }

    let headroom = get_fn(manager, 10);
    if headroom.is_nan() || headroom < 0.0 {
        0.0
    } else {
        headroom
    }
}

/// Live process resident RAM from /proc/self/statm in MB
pub fn read_proc_rss_mb() -> u32 {
    if let Ok(content) = std::fs::read_to_string("/proc/self/statm") {
        let parts: Vec<&str> = content.split_whitespace().collect();
        if parts.len() >= 2 {
            if let Ok(resident_pages) = parts[1].parse::<u64>() {
                return ((resident_pages * 4) / 1024) as u32;
            }
        }
    }
    0
}

/// Panic-safe packed primitive telemetry computation for JNI / C callers
/// Fills 8 elements:
/// 0: (avg_fps * 100)
/// 1: (one_percent_low * 100)
/// 2: (latest_frame_time_ms * 100)
/// 3: (jitter_ms * 100)
/// 4: ram_used_mb
/// 5: (thermal_headroom * 100)
/// 6: is_hibernate (1 or 0)
/// 7: total_frames
#[no_mangle]
pub extern "C" fn libre_compute_metrics_packed(out_buffer: *mut i64, max_len: usize) -> i32 {
    let result = std::panic::catch_unwind(|| {
        if out_buffer.is_null() || max_len < 8 {
            return -1;
        }
        let metrics = libre_compute_metrics();
        unsafe {
            *out_buffer.add(0) = (metrics.avg_fps * 100.0) as i64;
            *out_buffer.add(1) = (metrics.one_percent_low * 100.0) as i64;
            *out_buffer.add(2) = (metrics.latest_frame_time_ms * 100.0) as i64;
            *out_buffer.add(3) = (metrics.jitter_ms * 100.0) as i64;
            *out_buffer.add(4) = metrics.ram_used_mb as i64;
            *out_buffer.add(5) = (metrics.thermal_headroom * 100.0) as i64;
            *out_buffer.add(6) = if metrics.is_hibernate { 1 } else { 0 };
            *out_buffer.add(7) = metrics.total_frames as i64;
        }
        8
    });
    match result {
        Ok(code) => code,
        Err(_) => -1,
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_ring_buffer_and_metrics() {
        // Feed 100 frames of 16.6ms (~60 FPS)
        for _ in 0..90 {
            let idx = RING_HEAD.fetch_add(1, Ordering::Relaxed) % RING_BUFFER_SIZE;
            FRAME_RING_BUFFER[idx].store(16_666_667, Ordering::Release);
        }
        // Feed 10 frames of 33.3ms (~30 FPS)
        for _ in 0..10 {
            let idx = RING_HEAD.fetch_add(1, Ordering::Relaxed) % RING_BUFFER_SIZE;
            FRAME_RING_BUFFER[idx].store(33_333_333, Ordering::Release);
        }

        let metrics = libre_compute_metrics();
        assert!(metrics.avg_fps > 50.0 && metrics.avg_fps < 65.0);
        assert!(metrics.one_percent_low > 25.0 && metrics.one_percent_low < 40.0);
    }

    #[test]
    fn test_hibernate_config() {
        libre_set_hibernate(true, 5);
        assert!(libre_is_hibernate());
        assert_eq!(HIBERNATE_BUDGET_NS.load(Ordering::Relaxed), 200_000_000);

        libre_set_hibernate(false, 60);
        assert!(!libre_is_hibernate());
    }

    #[test]
    fn test_packed_metrics() {
        let mut buf = [0i64; 8];
        let count = libre_compute_metrics_packed(buf.as_mut_ptr(), 8);
        assert_eq!(count, 8);
    }
}
