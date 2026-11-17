//! Libre Controls - High-performance Rust native engine for Libre Launcher
//! Designed for zero heap allocation, low latency, and smooth aiming in Minecraft.

pub mod telemetry;

use std::sync::Mutex;

const MAX_POINTERS: usize = 16;
const ACTION_DOWN: i32 = 0;
const ACTION_UP: i32 = 1;
const ACTION_MOVE: i32 = 2;
const ACTION_CANCEL: i32 = 3;
const ACTION_POINTER_DOWN: i32 = 5;
const ACTION_POINTER_UP: i32 = 6;

// Joystick Direction bitmasks
pub const DIR_NONE: u32 = 0;
pub const DIR_NORTH: u32 = 1 << 0;       // W (Forward)
pub const DIR_NORTH_EAST: u32 = 1 << 1;  // W + D
pub const DIR_EAST: u32 = 1 << 2;        // D (Right)
pub const DIR_SOUTH_EAST: u32 = 1 << 3;  // S + D
pub const DIR_SOUTH: u32 = 1 << 4;       // S (Backward)
pub const DIR_SOUTH_WEST: u32 = 1 << 5;  // S + A
pub const DIR_WEST: u32 = 1 << 6;        // A (Left)
pub const DIR_NORTH_WEST: u32 = 1 << 7;  // W + A

// Aim Acceleration Profiles
pub const AIM_PROFILE_LINEAR: i32 = 0;
pub const AIM_PROFILE_EXPONENTIAL: i32 = 1;
pub const AIM_PROFILE_SIGMOID: i32 = 2;

#[derive(Clone, Copy)]
struct PointerState {
    id: i32,
    active: bool,
    last_x: f32,
    last_y: f32,
    filtered_dx: f32,
    filtered_dy: f32,
}

impl PointerState {
    const fn new() -> Self {
        Self {
            id: -1,
            active: false,
            last_x: 0.0,
            last_y: 0.0,
            filtered_dx: 0.0,
            filtered_dy: 0.0,
        }
    }
}

pub struct ControlsEngine {
    pointers: [PointerState; MAX_POINTERS],
    primary_pointer_id: i32,
    smoothing_factor: f32,
    deadzone_threshold: f32,
}

impl ControlsEngine {
    pub const fn new() -> Self {
        Self {
            pointers: [PointerState::new(); MAX_POINTERS],
            primary_pointer_id: -1,
            smoothing_factor: 0.65,
            deadzone_threshold: 0.15,
        }
    }

    pub fn reset(&mut self) {
        for p in self.pointers.iter_mut() {
            *p = PointerState::new();
        }
        self.primary_pointer_id = -1;
    }

    #[inline]
    pub fn set_smoothing(&mut self, factor: f32) {
        self.smoothing_factor = factor.clamp(0.0, 0.95);
    }

    #[inline]
    pub fn set_deadzone(&mut self, deadzone: f32) {
        self.deadzone_threshold = deadzone.clamp(0.0, 0.5);
    }

    /// Process a motion event with zero heap allocation
    pub fn process_touch(
        &mut self,
        pointer_id: i32,
        action: i32,
        x: f32,
        y: f32,
        sensitivity: f32,
    ) -> (f32, f32) {
        let action_masked = action & 0xff;

        match action_masked {
            ACTION_DOWN | ACTION_POINTER_DOWN => {
                let slot = self.find_or_alloc_slot(pointer_id);
                if let Some(p) = slot {
                    p.id = pointer_id;
                    p.active = true;
                    p.last_x = x;
                    p.last_y = y;
                    p.filtered_dx = 0.0;
                    p.filtered_dy = 0.0;
                    if self.primary_pointer_id == -1 {
                        self.primary_pointer_id = pointer_id;
                    }
                }
                (0.0, 0.0)
            }
            ACTION_MOVE => {
                let smoothing = self.smoothing_factor;
                let alpha = 1.0 - smoothing;
                if let Some(p) = self.find_slot_mut(pointer_id) {
                    if !p.active {
                        p.active = true;
                        p.last_x = x;
                        p.last_y = y;
                        return (0.0, 0.0);
                    }
                    let raw_dx = (x - p.last_x) * sensitivity;
                    let raw_dy = (y - p.last_y) * sensitivity;
                    p.last_x = x;
                    p.last_y = y;

                    // Apply Exponential Moving Average (EMA) to eliminate sensor noise jitter
                    let smooth_dx = p.filtered_dx * smoothing + raw_dx * alpha;
                    let smooth_dy = p.filtered_dy * smoothing + raw_dy * alpha;
                    p.filtered_dx = smooth_dx;
                    p.filtered_dy = smooth_dy;

                    (smooth_dx, smooth_dy)
                } else {
                    (0.0, 0.0)
                }
            }
            ACTION_UP | ACTION_POINTER_UP | ACTION_CANCEL => {
                if let Some(p) = self.find_slot_mut(pointer_id) {
                    p.active = false;
                    p.id = -1;
                    p.filtered_dx = 0.0;
                    p.filtered_dy = 0.0;
                }
                if self.primary_pointer_id == pointer_id {
                    self.primary_pointer_id = -1;
                }
                (0.0, 0.0)
            }
            _ => (0.0, 0.0),
        }
    }

    #[inline]
    fn find_slot_mut(&mut self, pointer_id: i32) -> Option<&mut PointerState> {
        self.pointers.iter_mut().find(|p| p.active && p.id == pointer_id)
    }

    #[inline]
    fn find_or_alloc_slot(&mut self, pointer_id: i32) -> Option<&mut PointerState> {
        if let Some(pos) = self.pointers.iter().position(|p| p.active && p.id == pointer_id) {
            return Some(&mut self.pointers[pos]);
        }
        if let Some(pos) = self.pointers.iter().position(|p| !p.active) {
            return Some(&mut self.pointers[pos]);
        }
        None
    }

    /// Process virtual joystick angle and strength into direction bitmask
    pub fn process_joystick(angle: i32, strength: i32, deadzone: i32) -> u32 {
        if strength <= deadzone {
            return DIR_NONE;
        }

        // Normalize angle to [0, 360)
        let normalized = ((angle % 360) + 360) % 360;

        // Sectors of 45 degrees
        // 0 / 360 = East, 90 = North, 180 = West, 270 = South
        match normalized {
            23..=67 => DIR_NORTH_EAST,
            68..=112 => DIR_NORTH,
            113..=157 => DIR_NORTH_WEST,
            158..=202 => DIR_WEST,
            203..=247 => DIR_SOUTH_WEST,
            248..=292 => DIR_SOUTH,
            293..=337 => DIR_SOUTH_EAST,
            _ => DIR_EAST,
        }
    }

    /// Pro-gaming aiming curve (Linear, Exponential, Sigmoid) with zero allocations
    pub fn process_aim(
        dx: f32,
        dy: f32,
        sensitivity: f32,
        profile: i32,
        accel_exponent: f32,
    ) -> (f32, f32) {
        let raw_dx = dx * sensitivity;
        let raw_dy = dy * sensitivity;
        let magnitude = (raw_dx * raw_dx + raw_dy * raw_dy).sqrt();

        if magnitude < 0.0001 {
            return (0.0, 0.0);
        }

        let multiplier = match profile {
            AIM_PROFILE_EXPONENTIAL => {
                let exp = accel_exponent.clamp(1.0, 3.0);
                (magnitude.powf(exp - 1.0)).clamp(0.2, 5.0)
            }
            AIM_PROFILE_SIGMOID => {
                2.0 / (1.0 + (-magnitude * 0.05).exp())
            }
            _ => 1.0,
        };

        (raw_dx * multiplier, raw_dy * multiplier)
    }
}

// -------------------------------------------------------------
// Spatial Hitbox & Touch Detection
// -------------------------------------------------------------

#[repr(C)]
#[derive(Clone, Copy, Debug, PartialEq)]
pub struct LibreRect {
    pub id: i32,
    pub left: f32,
    pub top: f32,
    pub right: f32,
    pub bottom: f32,
}

#[repr(C)]
#[derive(Clone, Copy, Debug, PartialEq)]
pub struct LibreCircle {
    pub id: i32,
    pub center_x: f32,
    pub center_y: f32,
    pub radius: f32,
}

pub fn hit_test_rects(x: f32, y: f32, rects: &[LibreRect]) -> i32 {
    for r in rects.iter().rev() {
        if x >= r.left && x <= r.right && y >= r.top && y <= r.bottom {
            return r.id;
        }
    }
    -1
}

pub fn hit_test_circles(x: f32, y: f32, circles: &[LibreCircle]) -> i32 {
    for c in circles.iter().rev() {
        let dx = x - c.center_x;
        let dy = y - c.center_y;
        if dx * dx + dy * dy <= c.radius * c.radius {
            return c.id;
        }
    }
    -1
}

// -------------------------------------------------------------
// Fast O(1) KeyCode Mapping Table (Android KeyEvent -> GLFW)
// -------------------------------------------------------------

pub const GLFW_KEY_UNKNOWN: i32 = 0;
pub const GLFW_KEY_SPACE: i32 = 32;
pub const GLFW_KEY_APOSTROPHE: i32 = 39;
pub const GLFW_KEY_COMMA: i32 = 44;
pub const GLFW_KEY_MINUS: i32 = 45;
pub const GLFW_KEY_PERIOD: i32 = 46;
pub const GLFW_KEY_SLASH: i32 = 47;
pub const GLFW_KEY_SEMICOLON: i32 = 59;
pub const GLFW_KEY_EQUAL: i32 = 61;
pub const GLFW_KEY_LEFT_BRACKET: i32 = 91;
pub const GLFW_KEY_BACKSLASH: i32 = 92;
pub const GLFW_KEY_RIGHT_BRACKET: i32 = 93;
pub const GLFW_KEY_GRAVE_ACCENT: i32 = 96;
pub const GLFW_KEY_ESCAPE: i32 = 256;
pub const GLFW_KEY_ENTER: i32 = 257;
pub const GLFW_KEY_TAB: i32 = 258;
pub const GLFW_KEY_BACKSPACE: i32 = 259;
pub const GLFW_KEY_RIGHT: i32 = 262;
pub const GLFW_KEY_LEFT: i32 = 263;
pub const GLFW_KEY_DOWN: i32 = 264;
pub const GLFW_KEY_UP: i32 = 265;
pub const GLFW_KEY_CAPS_LOCK: i32 = 280;
pub const GLFW_KEY_SCROLL_LOCK: i32 = 281;
pub const GLFW_KEY_F1: i32 = 290;
pub const GLFW_KEY_LEFT_SHIFT: i32 = 340;
pub const GLFW_KEY_LEFT_CONTROL: i32 = 341;
pub const GLFW_KEY_LEFT_ALT: i32 = 342;
pub const GLFW_KEY_RIGHT_SHIFT: i32 = 344;
pub const GLFW_KEY_RIGHT_CONTROL: i32 = 345;
pub const GLFW_KEY_RIGHT_ALT: i32 = 346;

#[inline]
pub fn map_android_keycode_to_glfw(android_key: i32) -> i32 {
    match android_key {
        4 => GLFW_KEY_ESCAPE,               // KEYCODE_BACK
        7..=16 => 48 + (android_key - 7),    // KEYCODE_0..KEYCODE_9
        19 => GLFW_KEY_UP,                  // KEYCODE_DPAD_UP
        20 => GLFW_KEY_DOWN,                // KEYCODE_DPAD_DOWN
        21 => GLFW_KEY_LEFT,                // KEYCODE_DPAD_LEFT
        22 => GLFW_KEY_RIGHT,               // KEYCODE_DPAD_RIGHT
        29..=54 => 65 + (android_key - 29),  // KEYCODE_A..KEYCODE_Z
        55 => GLFW_KEY_COMMA,               // KEYCODE_COMMA
        56 => GLFW_KEY_PERIOD,              // KEYCODE_PERIOD
        57 => GLFW_KEY_LEFT_ALT,            // KEYCODE_ALT_LEFT
        58 => GLFW_KEY_RIGHT_ALT,           // KEYCODE_ALT_RIGHT
        59 => GLFW_KEY_LEFT_SHIFT,          // KEYCODE_SHIFT_LEFT
        60 => GLFW_KEY_RIGHT_SHIFT,         // KEYCODE_SHIFT_RIGHT
        61 => GLFW_KEY_TAB,                 // KEYCODE_TAB
        62 => GLFW_KEY_SPACE,               // KEYCODE_SPACE
        66 => GLFW_KEY_ENTER,               // KEYCODE_ENTER
        67 => GLFW_KEY_BACKSPACE,           // KEYCODE_DEL
        68 => GLFW_KEY_GRAVE_ACCENT,        // KEYCODE_GRAVE
        69 => GLFW_KEY_MINUS,               // KEYCODE_MINUS
        70 => GLFW_KEY_EQUAL,               // KEYCODE_EQUALS
        71 => GLFW_KEY_LEFT_BRACKET,        // KEYCODE_LEFT_BRACKET
        72 => GLFW_KEY_RIGHT_BRACKET,       // KEYCODE_RIGHT_BRACKET
        73 => GLFW_KEY_BACKSLASH,           // KEYCODE_BACKSLASH
        74 => GLFW_KEY_SEMICOLON,           // KEYCODE_SEMICOLON
        75 => GLFW_KEY_APOSTROPHE,          // KEYCODE_APOSTROPHE
        76 => GLFW_KEY_SLASH,               // KEYCODE_SLASH
        113 => GLFW_KEY_LEFT_CONTROL,       // KEYCODE_CTRL_LEFT
        114 => GLFW_KEY_RIGHT_CONTROL,      // KEYCODE_CTRL_RIGHT
        115 => GLFW_KEY_CAPS_LOCK,          // KEYCODE_CAPS_LOCK
        116 => GLFW_KEY_SCROLL_LOCK,        // KEYCODE_SCROLL_LOCK
        131..=142 => GLFW_KEY_F1 + (android_key - 131), // KEYCODE_F1..KEYCODE_F12
        _ => GLFW_KEY_UNKNOWN,
    }
}

// -------------------------------------------------------------
// Fast Math & Color Packing
// -------------------------------------------------------------

#[inline]
pub const fn dp_to_px(dp: f32, density: f32) -> f32 {
    dp * density
}

#[inline]
pub const fn px_to_dp(px: f32, density: f32) -> f32 {
    if density <= 0.0 {
        px
    } else {
        px / density
    }
}

#[inline]
pub const fn pack_argb(a: u8, r: u8, g: u8, b: u8) -> u32 {
    ((a as u32) << 24) | ((r as u32) << 16) | ((g as u32) << 8) | (b as u32)
}

#[inline]
pub const fn unpack_argb(color: u32) -> (u8, u8, u8, u8) {
    (
        ((color >> 24) & 0xFF) as u8,
        ((color >> 16) & 0xFF) as u8,
        ((color >> 8) & 0xFF) as u8,
        (color & 0xFF) as u8,
    )
}

// -------------------------------------------------------------
// Fast CRC32 Table
// -------------------------------------------------------------

const fn generate_crc32_table() -> [u32; 256] {
    let mut table = [0u32; 256];
    let mut i = 0usize;
    while i < 256 {
        let mut crc = i as u32;
        let mut j = 0;
        while j < 8 {
            if (crc & 1) != 0 {
                crc = (crc >> 1) ^ 0xEDB88320;
            } else {
                crc >>= 1;
            }
            j += 1;
        }
        table[i] = crc;
        i += 1;
    }
    table
}

static CRC32_TABLE: [u32; 256] = generate_crc32_table();

#[inline]
pub fn compute_crc32(bytes: &[u8]) -> u32 {
    let mut crc: u32 = 0xFFFFFFFF;
    for &b in bytes {
        let idx = ((crc ^ (b as u32)) & 0xFF) as usize;
        crc = (crc >> 8) ^ CRC32_TABLE[idx];
    }
    !crc
}

// -------------------------------------------------------------
// Native Performance Telemetry Engine
// -------------------------------------------------------------

pub const TELEMETRY_WINDOW_SIZE: usize = 120;

#[repr(C)]
#[derive(Clone, Copy, Debug, PartialEq)]
pub struct LibreTelemetrySnapshot {
    pub fps: f32,
    pub frame_time_ms: f32,
    pub one_percent_low: f32,
    pub min_frame_time_ms: f32,
    pub max_frame_time_ms: f32,
    pub frame_count: u64,
    pub cpu_percent: f32,
    pub ram_used_mb: u32,
    pub ram_total_mb: u32,
    pub is_afk_active: bool,
}

pub struct TelemetryEngine {
    frame_times: [f32; TELEMETRY_WINDOW_SIZE],
    head: usize,
    count: usize,
    total_frames: u64,
    last_snapshot: LibreTelemetrySnapshot,
}

impl TelemetryEngine {
    pub const fn new() -> Self {
        Self {
            frame_times: [16.66; TELEMETRY_WINDOW_SIZE],
            head: 0,
            count: 0,
            total_frames: 0,
            last_snapshot: LibreTelemetrySnapshot {
                fps: 60.0,
                frame_time_ms: 16.66,
                one_percent_low: 60.0,
                min_frame_time_ms: 16.66,
                max_frame_time_ms: 16.66,
                frame_count: 0,
                cpu_percent: 0.0,
                ram_used_mb: 0,
                ram_total_mb: 0,
                is_afk_active: false,
            },
        }
    }

    pub fn record_frame(&mut self, delta_ms: f32) {
        let clean_delta = if delta_ms <= 0.001 {
            0.001
        } else if delta_ms > 1000.0 {
            1000.0
        } else {
            delta_ms
        };
        self.frame_times[self.head] = clean_delta;
        self.head = (self.head + 1) % TELEMETRY_WINDOW_SIZE;
        if self.count < TELEMETRY_WINDOW_SIZE {
            self.count += 1;
        }
        self.total_frames += 1;
    }

    pub fn compute_snapshot(&mut self, is_afk: bool, cpu: f32, ram_used: u32, ram_total: u32) -> LibreTelemetrySnapshot {
        if self.count == 0 {
            return self.last_snapshot;
        }

        let mut sum = 0.0f32;
        let mut min = f32::MAX;
        let mut max = 0.0f32;
        let mut sorted = [0.0f32; TELEMETRY_WINDOW_SIZE];

        for i in 0..self.count {
            let val = self.frame_times[i];
            sum += val;
            if val < min {
                min = val;
            }
            if val > max {
                max = val;
            }
            sorted[i] = val;
        }

        let avg_delta = sum / (self.count as f32);
        let fps = if avg_delta > 0.0 { 1000.0 / avg_delta } else { 0.0 };

        // 1% Low FPS: calculate 99th percentile of frame times (worst 1% of frames)
        for i in 1..self.count {
            let mut j = i;
            while j > 0 && sorted[j - 1] > sorted[j] {
                sorted.swap(j - 1, j);
                j -= 1;
            }
        }
        let p99_idx = ((self.count as f32) * 0.99) as usize;
        let p99_idx = p99_idx.min(self.count.saturating_sub(1));
        let p99_time = sorted[p99_idx];
        let one_percent_low = if p99_time > 0.0 { 1000.0 / p99_time } else { 0.0 };

        let latest_idx = (self.head + TELEMETRY_WINDOW_SIZE - 1) % TELEMETRY_WINDOW_SIZE;
        let latest_delta = self.frame_times[latest_idx];

        let snapshot = LibreTelemetrySnapshot {
            fps,
            frame_time_ms: latest_delta,
            one_percent_low,
            min_frame_time_ms: min,
            max_frame_time_ms: max,
            frame_count: self.total_frames,
            cpu_percent: cpu,
            ram_used_mb: ram_used,
            ram_total_mb: ram_total,
            is_afk_active: is_afk,
        };
        self.last_snapshot = snapshot;
        snapshot
    }
}

// -------------------------------------------------------------
// Low-Power AFK & Hibernate Engine
// -------------------------------------------------------------

pub struct AfkEngine {
    is_active: bool,
    target_fps: u32,
    idle_timeout_ms: u64,
    last_input_time_ms: u64,
    afk_entered_time_ms: u64,
    throttled_frames: u64,
}

impl AfkEngine {
    pub const fn new() -> Self {
        Self {
            is_active: false,
            target_fps: 10,
            idle_timeout_ms: 0,
            last_input_time_ms: 0,
            afk_entered_time_ms: 0,
            throttled_frames: 0,
        }
    }

    pub fn enter(&mut self, target_fps: u32, now_ms: u64) {
        self.is_active = true;
        self.target_fps = target_fps.clamp(1, 30);
        self.afk_entered_time_ms = now_ms;
    }

    pub fn exit(&mut self, now_ms: u64) {
        self.is_active = false;
        self.last_input_time_ms = now_ms;
    }

    pub fn toggle(&mut self, target_fps: u32, now_ms: u64) -> bool {
        if self.is_active {
            self.exit(now_ms);
        } else {
            self.enter(target_fps, now_ms);
        }
        self.is_active
    }

    pub fn notify_input(&mut self, now_ms: u64) {
        self.last_input_time_ms = now_ms;
        if self.is_active {
            self.is_active = false;
        }
    }

    pub fn check_idle_timeout(&mut self, now_ms: u64) -> bool {
        if !self.is_active && self.idle_timeout_ms > 0 && self.last_input_time_ms > 0 {
            if now_ms.saturating_sub(self.last_input_time_ms) >= self.idle_timeout_ms {
                self.enter(self.target_fps, now_ms);
                return true;
            }
        }
        false
    }

    #[inline]
    pub fn is_active(&self) -> bool {
        self.is_active
    }

    #[inline]
    pub fn target_frame_duration_ms(&self) -> u32 {
        if self.target_fps == 0 {
            100
        } else {
            1000 / self.target_fps
        }
    }

    pub fn record_throttle(&mut self) {
        self.throttled_frames += 1;
    }
}

pub fn read_proc_memory() -> (u32, u32) {
    if let Ok(content) = std::fs::read_to_string("/proc/self/statm") {
        let parts: Vec<&str> = content.split_whitespace().collect();
        if parts.len() >= 2 {
            if let Ok(pages) = parts[1].parse::<u32>() {
                let resident_mb = (pages * 4) / 1024;
                return (resident_mb, read_total_memory_mb());
            }
        }
    }
    (0, 0)
}

fn read_total_memory_mb() -> u32 {
    if let Ok(content) = std::fs::read_to_string("/proc/meminfo") {
        for line in content.lines() {
            if line.starts_with("MemTotal:") {
                let parts: Vec<&str> = line.split_whitespace().collect();
                if parts.len() >= 2 {
                    if let Ok(kb) = parts[1].parse::<u32>() {
                        return kb / 1024;
                    }
                }
            }
        }
    }
    0
}

// -------------------------------------------------------------
// Fast String / Extension Check (Zero Allocation)
// -------------------------------------------------------------

pub fn has_extension_ignore_case(path: &[u8], ext: &[u8]) -> bool {
    if path.len() < ext.len() + 1 {
        return false;
    }
    let ext_start = path.len() - ext.len();
    if path[ext_start - 1] != b'.' {
        return false;
    }
    for i in 0..ext.len() {
        if path[ext_start + i].to_ascii_lowercase() != ext[i].to_ascii_lowercase() {
            return false;
        }
    }
    true
}

// -------------------------------------------------------------
// C ABI FFI Exports
// -------------------------------------------------------------

static ENGINE: Mutex<ControlsEngine> = Mutex::new(ControlsEngine::new());

#[repr(C)]
pub struct LibreMotionResult {
    pub dx: f32,
    pub dy: f32,
    pub status: i32,
}

#[repr(C)]
pub struct LibreAimResult {
    pub dx: f32,
    pub dy: f32,
}

#[no_mangle]
pub extern "C" fn libre_controls_init() {
    if let Ok(mut engine) = ENGINE.lock() {
        engine.reset();
    }
}

#[no_mangle]
pub extern "C" fn libre_controls_reset() {
    if let Ok(mut engine) = ENGINE.lock() {
        engine.reset();
    }
}

#[no_mangle]
pub extern "C" fn libre_controls_set_smoothing(factor: f32) {
    if let Ok(mut engine) = ENGINE.lock() {
        engine.set_smoothing(factor);
    }
}

#[no_mangle]
pub extern "C" fn libre_controls_process_touch(
    pointer_id: i32,
    action: i32,
    x: f32,
    y: f32,
    sensitivity: f32,
) -> LibreMotionResult {
    if let Ok(mut engine) = ENGINE.lock() {
        let (dx, dy) = engine.process_touch(pointer_id, action, x, y, sensitivity);
        LibreMotionResult {
            dx,
            dy,
            status: 0,
        }
    } else {
        LibreMotionResult {
            dx: 0.0,
            dy: 0.0,
            status: -1,
        }
    }
}

#[no_mangle]
pub extern "C" fn libre_controls_process_joystick(angle: i32, strength: i32, deadzone: i32) -> u32 {
    ControlsEngine::process_joystick(angle, strength, deadzone)
}

#[no_mangle]
pub extern "C" fn libre_controls_process_aim(
    dx: f32,
    dy: f32,
    sensitivity: f32,
    profile: i32,
    accel_exponent: f32,
) -> LibreAimResult {
    let (out_dx, out_dy) = ControlsEngine::process_aim(dx, dy, sensitivity, profile, accel_exponent);
    LibreAimResult { dx: out_dx, dy: out_dy }
}

#[no_mangle]
pub extern "C" fn libre_controls_hit_test_rect(
    x: f32,
    y: f32,
    rects: *const LibreRect,
    count: usize,
) -> i32 {
    if rects.is_null() || count == 0 {
        return -1;
    }
    let slice = unsafe { std::slice::from_raw_parts(rects, count) };
    hit_test_rects(x, y, slice)
}

#[no_mangle]
pub extern "C" fn libre_controls_hit_test_circle(
    x: f32,
    y: f32,
    circles: *const LibreCircle,
    count: usize,
) -> i32 {
    if circles.is_null() || count == 0 {
        return -1;
    }
    let slice = unsafe { std::slice::from_raw_parts(circles, count) };
    hit_test_circles(x, y, slice)
}

#[no_mangle]
pub extern "C" fn libre_controls_map_android_keycode(android_key: i32) -> i32 {
    map_android_keycode_to_glfw(android_key)
}

#[no_mangle]
pub extern "C" fn libre_controls_crc32(data: *const u8, len: usize) -> u32 {
    if data.is_null() || len == 0 {
        return 0;
    }
    let slice = unsafe { std::slice::from_raw_parts(data, len) };
    compute_crc32(slice)
}

#[no_mangle]
pub extern "C" fn libre_controls_pack_argb(a: u8, r: u8, g: u8, b: u8) -> u32 {
    pack_argb(a, r, g, b)
}

#[no_mangle]
pub extern "C" fn libre_controls_dp_to_px(dp: f32, density: f32) -> f32 {
    dp_to_px(dp, density)
}

#[no_mangle]
pub extern "C" fn libre_controls_px_to_dp(px: f32, density: f32) -> f32 {
    px_to_dp(px, density)
}

// -------------------------------------------------------------
// Telemetry & AFK Global State & C ABI Exports
// -------------------------------------------------------------

static TELEMETRY: Mutex<TelemetryEngine> = Mutex::new(TelemetryEngine::new());
static AFK: Mutex<AfkEngine> = Mutex::new(AfkEngine::new());

#[no_mangle]
pub extern "C" fn libre_telemetry_record_frame(delta_ms: f32) {
    if let Ok(mut engine) = TELEMETRY.lock() {
        engine.record_frame(delta_ms);
    }
}

#[no_mangle]
pub extern "C" fn libre_telemetry_get_snapshot(out: *mut LibreTelemetrySnapshot) {
    if out.is_null() {
        return;
    }
    let is_afk = if let Ok(afk) = AFK.lock() {
        afk.is_active()
    } else {
        false
    };
    let (ram_used, ram_total) = read_proc_memory();
    if let Ok(mut engine) = TELEMETRY.lock() {
        let snapshot = engine.compute_snapshot(is_afk, 0.0, ram_used, ram_total);
        unsafe {
            *out = snapshot;
        }
    }
}

#[no_mangle]
pub extern "C" fn libre_afk_enter(target_fps: u32) {
    if let Ok(mut afk) = AFK.lock() {
        afk.enter(target_fps, 0);
    }
}

#[no_mangle]
pub extern "C" fn libre_afk_exit() {
    if let Ok(mut afk) = AFK.lock() {
        afk.exit(0);
    }
}

#[no_mangle]
pub extern "C" fn libre_afk_toggle(target_fps: u32) -> bool {
    if let Ok(mut afk) = AFK.lock() {
        afk.toggle(target_fps, 0)
    } else {
        false
    }
}

#[no_mangle]
pub extern "C" fn libre_afk_is_active() -> bool {
    if let Ok(afk) = AFK.lock() {
        afk.is_active()
    } else {
        false
    }
}

#[no_mangle]
pub extern "C" fn libre_afk_notify_input() {
    if let Ok(mut afk) = AFK.lock() {
        afk.notify_input(0);
    }
}

#[no_mangle]
pub extern "C" fn libre_afk_get_sleep_duration_ms() -> u32 {
    if let Ok(afk) = AFK.lock() {
        if afk.is_active() {
            return afk.target_frame_duration_ms();
        }
    }
    0
}

// -------------------------------------------------------------
// TouchController Mod Bridge IPC
// -------------------------------------------------------------

#[repr(C)]
#[derive(Clone, Copy, Debug, PartialEq)]
pub struct LibreTouchEvent {
    pub pointer_id: i32,
    pub action: i32,
    pub x: f32,
    pub y: f32,
    pub pressure: f32,
    pub timestamp_ns: u64,
}

pub const TOUCH_BRIDGE_CAPACITY: usize = 128;

pub struct TouchBridgeEngine {
    buffer: [LibreTouchEvent; TOUCH_BRIDGE_CAPACITY],
    head: usize,
    tail: usize,
    count: usize,
    enabled: bool,
}

impl TouchBridgeEngine {
    pub const fn new() -> Self {
        Self {
            buffer: [LibreTouchEvent {
                pointer_id: 0,
                action: 0,
                x: 0.0,
                y: 0.0,
                pressure: 0.0,
                timestamp_ns: 0,
            }; TOUCH_BRIDGE_CAPACITY],
            head: 0,
            tail: 0,
            count: 0,
            enabled: true,
        }
    }

    pub fn push(&mut self, event: LibreTouchEvent) {
        if !self.enabled {
            return;
        }
        self.buffer[self.tail] = event;
        self.tail = (self.tail + 1) % TOUCH_BRIDGE_CAPACITY;
        if self.count < TOUCH_BRIDGE_CAPACITY {
            self.count += 1;
        } else {
            self.head = (self.head + 1) % TOUCH_BRIDGE_CAPACITY;
        }
    }

    pub fn poll(&mut self) -> Option<LibreTouchEvent> {
        if self.count == 0 {
            None
        } else {
            let event = self.buffer[self.head];
            self.head = (self.head + 1) % TOUCH_BRIDGE_CAPACITY;
            self.count -= 1;
            Some(event)
        }
    }

    pub fn peek_count(&self) -> usize {
        self.count
    }

    pub fn clear(&mut self) {
        self.head = 0;
        self.tail = 0;
        self.count = 0;
    }

    pub fn set_enabled(&mut self, enabled: bool) {
        self.enabled = enabled;
        if !enabled {
            self.clear();
        }
    }

    pub fn is_enabled(&self) -> bool {
        self.enabled
    }
}

static TOUCH_BRIDGE: Mutex<TouchBridgeEngine> = Mutex::new(TouchBridgeEngine::new());

fn current_time_nanos() -> u64 {
    std::time::SystemTime::now()
        .duration_since(std::time::UNIX_EPOCH)
        .unwrap_or_default()
        .as_nanos() as u64
}

#[no_mangle]
pub extern "C" fn libre_touch_bridge_push(
    pointer_id: i32,
    action: i32,
    x: f32,
    y: f32,
    pressure: f32,
) {
    let now = current_time_nanos();
    if let Ok(mut bridge) = TOUCH_BRIDGE.lock() {
        bridge.push(LibreTouchEvent {
            pointer_id,
            action,
            x,
            y,
            pressure,
            timestamp_ns: now,
        });
    }
}

#[no_mangle]
pub extern "C" fn libre_touch_bridge_poll(out_event: *mut LibreTouchEvent) -> i32 {
    if out_event.is_null() {
        return 0;
    }
    if let Ok(mut bridge) = TOUCH_BRIDGE.lock() {
        if let Some(event) = bridge.poll() {
            unsafe {
                *out_event = event;
            }
            return 1;
        }
    }
    0
}

#[no_mangle]
pub extern "C" fn libre_touch_bridge_peek_count() -> u32 {
    if let Ok(bridge) = TOUCH_BRIDGE.lock() {
        bridge.peek_count() as u32
    } else {
        0
    }
}

#[no_mangle]
pub extern "C" fn libre_touch_bridge_clear() {
    if let Ok(mut bridge) = TOUCH_BRIDGE.lock() {
        bridge.clear();
    }
}

#[no_mangle]
pub extern "C" fn libre_touch_bridge_set_enabled(enabled: bool) {
    if let Ok(mut bridge) = TOUCH_BRIDGE.lock() {
        bridge.set_enabled(enabled);
    }
}

#[no_mangle]
pub extern "C" fn libre_touch_bridge_is_enabled() -> bool {
    if let Ok(bridge) = TOUCH_BRIDGE.lock() {
        bridge.is_enabled()
    } else {
        false
    }
}

// -------------------------------------------------------------
// Unit Tests
// -------------------------------------------------------------

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_joystick_directions() {
        assert_eq!(ControlsEngine::process_joystick(90, 100, 30), DIR_NORTH);
        assert_eq!(ControlsEngine::process_joystick(270, 100, 30), DIR_SOUTH);
        assert_eq!(ControlsEngine::process_joystick(180, 100, 30), DIR_WEST);
        assert_eq!(ControlsEngine::process_joystick(0, 100, 30), DIR_EAST);
        assert_eq!(ControlsEngine::process_joystick(90, 20, 30), DIR_NONE);
    }

    #[test]
    fn test_pointer_tracking() {
        let mut engine = ControlsEngine::new();
        engine.set_smoothing(0.0); // raw

        // Down
        let (dx, dy) = engine.process_touch(0, ACTION_DOWN, 100.0, 100.0, 1.0);
        assert_eq!((dx, dy), (0.0, 0.0));

        // Move
        let (dx, dy) = engine.process_touch(0, ACTION_MOVE, 110.0, 115.0, 1.0);
        assert_eq!((dx, dy), (10.0, 15.0));

        // Up
        let (dx, dy) = engine.process_touch(0, ACTION_UP, 110.0, 115.0, 1.0);
        assert_eq!((dx, dy), (0.0, 0.0));
    }

    #[test]
    fn test_aim_acceleration() {
        // Linear
        let (lx, ly) = ControlsEngine::process_aim(10.0, 20.0, 1.0, AIM_PROFILE_LINEAR, 1.0);
        assert_eq!((lx, ly), (10.0, 20.0));

        // Exponential
        let (ex, ey) = ControlsEngine::process_aim(10.0, 0.0, 1.0, AIM_PROFILE_EXPONENTIAL, 2.0);
        assert!(ex > 10.0);
        assert_eq!(ey, 0.0);

        // Sigmoid
        let (sx, sy) = ControlsEngine::process_aim(5.0, 5.0, 1.0, AIM_PROFILE_SIGMOID, 1.0);
        assert!(sx > 0.0 && sy > 0.0);
    }

    #[test]
    fn test_hit_test_rect() {
        let rects = [
            LibreRect { id: 1, left: 0.0, top: 0.0, right: 100.0, bottom: 100.0 },
            LibreRect { id: 2, left: 150.0, top: 150.0, right: 250.0, bottom: 250.0 },
        ];
        assert_eq!(hit_test_rects(50.0, 50.0, &rects), 1);
        assert_eq!(hit_test_rects(200.0, 200.0, &rects), 2);
        assert_eq!(hit_test_rects(120.0, 120.0, &rects), -1);
    }

    #[test]
    fn test_hit_test_circle() {
        let circles = [
            LibreCircle { id: 10, center_x: 50.0, center_y: 50.0, radius: 25.0 },
        ];
        assert_eq!(hit_test_circles(50.0, 50.0, &circles), 10);
        assert_eq!(hit_test_circles(70.0, 50.0, &circles), 10);
        assert_eq!(hit_test_circles(80.0, 50.0, &circles), -1);
    }

    #[test]
    fn test_keycode_mapping() {
        assert_eq!(map_android_keycode_to_glfw(4), GLFW_KEY_ESCAPE);
        assert_eq!(map_android_keycode_to_glfw(29), 65); // KEYCODE_A -> GLFW_KEY_A
        assert_eq!(map_android_keycode_to_glfw(51), 87); // KEYCODE_W -> GLFW_KEY_W
        assert_eq!(map_android_keycode_to_glfw(62), GLFW_KEY_SPACE);
        assert_eq!(map_android_keycode_to_glfw(113), GLFW_KEY_LEFT_CONTROL);
    }

    #[test]
    fn test_crc32() {
        let data = b"123456789";
        assert_eq!(compute_crc32(data), 0xCBF43926);
    }

    #[test]
    fn test_color_packing() {
        let packed = pack_argb(255, 128, 64, 32);
        assert_eq!(unpack_argb(packed), (255, 128, 64, 32));
    }

    #[test]
    fn test_dp_px_conversions() {
        assert_eq!(dp_to_px(10.0, 2.5), 25.0);
        assert_eq!(px_to_dp(25.0, 2.5), 10.0);
    }

    #[test]
    fn test_has_extension() {
        assert!(has_extension_ignore_case(b"/sdcard/mod.jar", b"jar"));
        assert!(has_extension_ignore_case(b"CONFIG.JSON", b"json"));
        assert!(!has_extension_ignore_case(b"readme.txt", b"jar"));
    }

    #[test]
    fn test_telemetry_fps_and_1percent_low() {
        let mut telemetry = TelemetryEngine::new();
        // Record 100 frames at 16.66ms (~60 FPS)
        for _ in 0..99 {
            telemetry.record_frame(16.66);
        }
        // Introduce one stutter frame at 50ms (20 FPS)
        telemetry.record_frame(50.0);

        let snap = telemetry.compute_snapshot(false, 15.0, 512, 4096);
        assert!(snap.fps >= 55.0 && snap.fps <= 62.0);
        assert!(snap.one_percent_low < 55.0); // Catches the 50ms stutter!
        assert_eq!(snap.frame_count, 100);
        assert_eq!(snap.ram_used_mb, 512);
    }

    #[test]
    fn test_afk_state_transitions() {
        let mut afk = AfkEngine::new();
        assert!(!afk.is_active());

        // Enter AFK
        afk.enter(10, 1000);
        assert!(afk.is_active());
        assert_eq!(afk.target_frame_duration_ms(), 100); // 100ms = 10 FPS

        // Toggle back off
        assert!(!afk.toggle(10, 2000));
        assert!(!afk.is_active());
    }

    #[test]
    fn test_afk_input_wake() {
        let mut afk = AfkEngine::new();
        afk.enter(10, 1000);
        assert!(afk.is_active());

        // Player touches screen or presses key -> immediately wake up
        afk.notify_input(1500);
        assert!(!afk.is_active());
    }

    #[test]
    fn test_touch_bridge_push_and_poll() {
        let mut bridge = TouchBridgeEngine::new();
        assert_eq!(bridge.peek_count(), 0);
        assert_eq!(bridge.poll(), None);

        let event = LibreTouchEvent {
            pointer_id: 1,
            action: 0,
            x: 120.0,
            y: 240.0,
            pressure: 0.8,
            timestamp_ns: 123456789,
        };
        bridge.push(event);
        assert_eq!(bridge.peek_count(), 1);

        let polled = bridge.poll();
        assert_eq!(polled, Some(event));
        assert_eq!(bridge.peek_count(), 0);
        assert_eq!(bridge.poll(), None);
    }

    #[test]
    fn test_touch_bridge_ring_buffer_overwrite() {
        let mut bridge = TouchBridgeEngine::new();
        for i in 0..(TOUCH_BRIDGE_CAPACITY + 10) {
            bridge.push(LibreTouchEvent {
                pointer_id: i as i32,
                action: 2,
                x: i as f32,
                y: i as f32,
                pressure: 1.0,
                timestamp_ns: i as u64,
            });
        }
        assert_eq!(bridge.peek_count(), TOUCH_BRIDGE_CAPACITY);

        // The oldest 10 events (0..9) should have been dropped; first out should be id 10
        let first = bridge.poll().unwrap();
        assert_eq!(first.pointer_id, 10);
    }

    #[test]
    fn test_touch_bridge_enable_disable() {
        let mut bridge = TouchBridgeEngine::new();
        bridge.push(LibreTouchEvent {
            pointer_id: 0,
            action: 0,
            x: 50.0,
            y: 50.0,
            pressure: 1.0,
            timestamp_ns: 100,
        });
        assert_eq!(bridge.peek_count(), 1);

        bridge.set_enabled(false);
        assert!(!bridge.is_enabled());
        assert_eq!(bridge.peek_count(), 0);

        bridge.push(LibreTouchEvent {
            pointer_id: 1,
            action: 0,
            x: 50.0,
            y: 50.0,
            pressure: 1.0,
            timestamp_ns: 200,
        });
        assert_eq!(bridge.peek_count(), 0);

        bridge.set_enabled(true);
        assert!(bridge.is_enabled());
    }

    #[test]
    fn test_touch_bridge_c_abi() {
        libre_touch_bridge_set_enabled(true);
        libre_touch_bridge_clear();
        assert_eq!(libre_touch_bridge_peek_count(), 0);

        libre_touch_bridge_push(2, 0, 100.0, 200.0, 0.5);
        assert_eq!(libre_touch_bridge_peek_count(), 1);

        let mut out = LibreTouchEvent {
            pointer_id: 0,
            action: 0,
            x: 0.0,
            y: 0.0,
            pressure: 0.0,
            timestamp_ns: 0,
        };
        let res = libre_touch_bridge_poll(&mut out);
        assert_eq!(res, 1);
        assert_eq!(out.pointer_id, 2);
        assert_eq!(out.action, 0);
        assert_eq!(out.x, 100.0);
        assert_eq!(out.y, 200.0);
        assert_eq!(out.pressure, 0.5);

        assert_eq!(libre_touch_bridge_peek_count(), 0);
        assert_eq!(libre_touch_bridge_poll(&mut out), 0);
    }
}
