#ifndef LIBRE_CONTROLS_H
#define LIBRE_CONTROLS_H

#include <stdint.h>

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

typedef struct {
    float dx;
    float dy;
    int32_t status;
} LibreMotionResult;

void libre_controls_init(void);
void libre_controls_reset(void);
void libre_controls_set_smoothing(float factor);
LibreMotionResult libre_controls_process_touch(int32_t pointer_id, int32_t action, float x, float y, float sensitivity);
uint32_t libre_controls_process_joystick(int32_t angle, int32_t strength, int32_t deadzone);

#ifdef __cplusplus
}
#endif

#endif // LIBRE_CONTROLS_H
