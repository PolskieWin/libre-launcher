#ifndef TOUCH_BRIDGE_H
#define TOUCH_BRIDGE_H

#include <stdint.h>
#include <stdbool.h>

#ifdef __cplusplus
extern "C" {
#endif

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

#ifdef __cplusplus
}
#endif

#endif // TOUCH_BRIDGE_H
