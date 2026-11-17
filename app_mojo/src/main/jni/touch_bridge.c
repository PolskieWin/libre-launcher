#include <jni.h>
#include <stdint.h>
#include <stdbool.h>
#include <time.h>
#include <pthread.h>
#include <android/log.h>
#include "touch_bridge.h"

#define TAG "TouchBridge"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)

#define TOUCH_BRIDGE_CAPACITY 128

static LibreTouchEvent s_touch_buffer[TOUCH_BRIDGE_CAPACITY];
static int s_bridge_head = 0;
static int s_bridge_tail = 0;
static int s_bridge_count = 0;
static bool s_bridge_enabled = true;
static pthread_mutex_t s_bridge_mutex = PTHREAD_MUTEX_INITIALIZER;

static uint64_t get_current_time_ns() {
    struct timespec ts;
    clock_gettime(CLOCK_MONOTONIC, &ts);
    return (uint64_t)(ts.tv_sec * 1000000000ULL + ts.tv_nsec);
}

__attribute__((visibility("default")))
void libre_touch_bridge_push(int32_t pointer_id, int32_t action, float x, float y, float pressure) {
    pthread_mutex_lock(&s_bridge_mutex);
    if (!s_bridge_enabled) {
        pthread_mutex_unlock(&s_bridge_mutex);
        return;
    }

    s_touch_buffer[s_bridge_tail].pointer_id = pointer_id;
    s_touch_buffer[s_bridge_tail].action = action;
    s_touch_buffer[s_bridge_tail].x = x;
    s_touch_buffer[s_bridge_tail].y = y;
    s_touch_buffer[s_bridge_tail].pressure = pressure;
    s_touch_buffer[s_bridge_tail].timestamp_ns = get_current_time_ns();

    s_bridge_tail = (s_bridge_tail + 1) % TOUCH_BRIDGE_CAPACITY;
    if (s_bridge_count < TOUCH_BRIDGE_CAPACITY) {
        s_bridge_count++;
    } else {
        s_bridge_head = (s_bridge_head + 1) % TOUCH_BRIDGE_CAPACITY;
    }

    pthread_mutex_unlock(&s_bridge_mutex);
}

__attribute__((visibility("default")))
int32_t libre_touch_bridge_poll(LibreTouchEvent* out_event) {
    if (!out_event) return 0;

    pthread_mutex_lock(&s_bridge_mutex);
    if (s_bridge_count == 0) {
        pthread_mutex_unlock(&s_bridge_mutex);
        return 0;
    }

    *out_event = s_touch_buffer[s_bridge_head];
    s_bridge_head = (s_bridge_head + 1) % TOUCH_BRIDGE_CAPACITY;
    s_bridge_count--;

    pthread_mutex_unlock(&s_bridge_mutex);
    return 1;
}

__attribute__((visibility("default")))
uint32_t libre_touch_bridge_peek_count(void) {
    pthread_mutex_lock(&s_bridge_mutex);
    uint32_t count = (uint32_t)s_bridge_count;
    pthread_mutex_unlock(&s_bridge_mutex);
    return count;
}

__attribute__((visibility("default")))
void libre_touch_bridge_clear(void) {
    pthread_mutex_lock(&s_bridge_mutex);
    s_bridge_head = 0;
    s_bridge_tail = 0;
    s_bridge_count = 0;
    pthread_mutex_unlock(&s_bridge_mutex);
}

__attribute__((visibility("default")))
void libre_touch_bridge_set_enabled(bool enabled) {
    pthread_mutex_lock(&s_bridge_mutex);
    s_bridge_enabled = enabled;
    if (!enabled) {
        s_bridge_head = 0;
        s_bridge_tail = 0;
        s_bridge_count = 0;
    }
    pthread_mutex_unlock(&s_bridge_mutex);
}

__attribute__((visibility("default")))
bool libre_touch_bridge_is_enabled(void) {
    return s_bridge_enabled;
}

JNIEXPORT void JNICALL
Java_git_artdeell_mojo_input_TouchBridge_nativePushTouch(
        JNIEnv *env,
        jclass clazz,
        jint pointer_id,
        jint action,
        jfloat x,
        jfloat y,
        jfloat pressure) {
    (void)env;
    (void)clazz;
    libre_touch_bridge_push(pointer_id, action, x, y, pressure);
}

JNIEXPORT jint JNICALL
Java_git_artdeell_mojo_input_TouchBridge_nativePollTouch(
        JNIEnv *env,
        jclass clazz,
        jfloatArray out_array) {
    (void)clazz;
    if (!out_array) return 0;
    LibreTouchEvent evt;
    if (libre_touch_bridge_poll(&evt)) {
        jfloat buf[6];
        buf[0] = (jfloat)evt.pointer_id;
        buf[1] = (jfloat)evt.action;
        buf[2] = evt.x;
        buf[3] = evt.y;
        buf[4] = evt.pressure;
        buf[5] = (jfloat)(evt.timestamp_ns / 1000000ULL);
        (*env)->SetFloatArrayRegion(env, out_array, 0, 6, buf);
        return 1;
    }
    return 0;
}

JNIEXPORT jint JNICALL
Java_git_artdeell_mojo_input_TouchBridge_nativePeekCount(
        JNIEnv *env,
        jclass clazz) {
    (void)env;
    (void)clazz;
    return (jint)libre_touch_bridge_peek_count();
}

JNIEXPORT void JNICALL
Java_git_artdeell_mojo_input_TouchBridge_nativeClear(
        JNIEnv *env,
        jclass clazz) {
    (void)env;
    (void)clazz;
    libre_touch_bridge_clear();
}

JNIEXPORT void JNICALL
Java_git_artdeell_mojo_input_TouchBridge_nativeSetEnabled(
        JNIEnv *env,
        jclass clazz,
        jboolean enabled) {
    (void)env;
    (void)clazz;
    libre_touch_bridge_set_enabled(enabled == JNI_TRUE);
}

JNIEXPORT jboolean JNICALL
Java_git_artdeell_mojo_input_TouchBridge_nativeIsEnabled(
        JNIEnv *env,
        jclass clazz) {
    (void)env;
    (void)clazz;
    return libre_touch_bridge_is_enabled() ? JNI_TRUE : JNI_FALSE;
}
