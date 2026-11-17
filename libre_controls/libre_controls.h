#ifndef LIBRE_CONTROLS_H
#define LIBRE_CONTROLS_H

#include <stdint.h>
#include <stddef.h>
#include <stdbool.h>

#ifdef __cplusplus
extern "C" {
#endif

// Directions bitmask
#define LIBRE_DIR_NONE        0
#define LIBRE_DIR_NORTH       (1 << 0)
#define LIBRE_DIR_NORTH_EAST  (1 << 1)
#define LIBRE_DIR_EAST        (1 << 2)
#define LIBRE_DIR_SOUTH_EAST  (1 << 3)
#define LIBRE_DIR_SOUTH       (1 << 4)
#define LIBRE_DIR_SOUTH_WEST  (1 << 5)
#define LIBRE_DIR_WEST        (1 << 6)
#define LIBRE_DIR_NORTH_WEST  (1 << 7)

// Aim Profiles
#define LIBRE_AIM_LINEAR      0
#define LIBRE_AIM_EXPONENTIAL 1
#define LIBRE_AIM_SIGMOID     2

typedef struct {
    float dx;
    float dy;
    int32_t status;
} LibreMotionResult;

typedef struct {
    float dx;
    float dy;
} LibreAimResult;

typedef struct {
    int32_t id;
    float left;
    float top;
    float right;
    float bottom;
} LibreRect;

typedef struct {
    int32_t id;
    float center_x;
    float center_y;
    float radius;
} LibreCircle;

typedef struct {
    float fps;
    float frame_time_ms;
    float one_percent_low;
    float min_frame_time_ms;
    float max_frame_time_ms;
    uint64_t frame_count;
    float cpu_percent;
    uint32_t ram_used_mb;
    uint32_t ram_total_mb;
    bool is_afk_active;
} LibreTelemetrySnapshot;

// Engine Lifecycle & Smoothing
void libre_controls_init(void);
void libre_controls_reset(void);
void libre_controls_set_smoothing(float factor);

// Touch & Motion Processing
LibreMotionResult libre_controls_process_touch(int32_t pointer_id, int32_t action, float x, float y, float sensitivity);
uint32_t libre_controls_process_joystick(int32_t angle, int32_t strength, int32_t deadzone);
LibreAimResult libre_controls_process_aim(float dx, float dy, float sensitivity, int32_t profile, float accel_exponent);

// Spatial Hit Testing (Zero Allocation)
int32_t libre_controls_hit_test_rect(float x, float y, const LibreRect* rects, size_t count);
int32_t libre_controls_hit_test_circle(float x, float y, const LibreCircle* circles, size_t count);

// Fast KeyCode Conversion
int32_t libre_controls_map_android_keycode(int32_t android_key);

// Utilities & Math
uint32_t libre_controls_crc32(const uint8_t* data, size_t len);
uint32_t libre_controls_pack_argb(uint8_t a, uint8_t r, uint8_t g, uint8_t b);
float libre_controls_dp_to_px(float dp, float density);
float libre_controls_px_to_dp(float px, float density);

// Telemetry & AFK Native Controls
typedef struct {
    float avg_fps;
    float one_percent_low;
    float latest_frame_time_ms;
    float jitter_ms;
    uint32_t ram_used_mb;
    float thermal_headroom;
    bool is_hibernate;
    uint64_t total_frames;
} LibreMetrics;

uint64_t libre_on_frame_present(void);
void libre_set_hibernate(bool enabled, uint32_t target_fps);
bool libre_is_hibernate(void);
LibreMetrics libre_compute_metrics(void);
int32_t libre_compute_metrics_packed(int64_t* out_buffer, size_t max_len);

void libre_telemetry_record_frame(float delta_ms);
void libre_telemetry_get_snapshot(LibreTelemetrySnapshot* out);
void libre_afk_enter(uint32_t target_fps);
void libre_afk_exit(void);
bool libre_afk_toggle(uint32_t target_fps);
bool libre_afk_is_active(void);
void libre_afk_notify_input(void);
uint32_t libre_afk_get_sleep_duration_ms(void);

// TouchController Mod Bridge IPC
typedef struct {
    int32_t pointer_id;
    int32_t action;
    float x;
    float y;
    float pressure;
    uint64_t timestamp_ns;
} LibreTouchEvent;

void libre_touch_bridge_push(int32_t pointer_id, int32_t action, float x, float y, float pressure);
int32_t libre_touch_bridge_poll(LibreTouchEvent* out_event);
uint32_t libre_touch_bridge_peek_count(void);
void libre_touch_bridge_clear(void);
void libre_touch_bridge_set_enabled(bool enabled);
bool libre_touch_bridge_is_enabled(void);

int32_t libre_modrinth_build_search_url(
    const char* query,
    const char* project_type,
    const char* mc_version,
    const char* loader,
    const char* category,
    int32_t limit,
    int32_t offset,
    const char* index,
    char* out_buf,
    size_t out_buf_len
);
int32_t libre_modrinth_build_project_url(
    const char* project_id,
    bool get_versions,
    char* out_buf,
    size_t out_buf_len
);
int32_t libre_modrinth_sha512(const uint8_t* data, size_t len, char* out_hex, size_t out_hex_len);
bool libre_modrinth_verify_sha512(const uint8_t* data, size_t len, const char* expected_hex);

#ifdef __cplusplus
}
#endif

#endif // LIBRE_CONTROLS_H
