//! Libre Controls - High-performance Rust native controls engine for Libre Launcher
//! Designed for zero heap allocation, low latency, and smooth aiming in Minecraft.



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
}

// Global thread-safe singleton engine
use std::sync::Mutex;

static ENGINE: Mutex<ControlsEngine> = Mutex::new(ControlsEngine::new());

#[repr(C)]
pub struct LibreMotionResult {
    pub dx: f32,
    pub dy: f32,
    pub status: i32,
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
}
