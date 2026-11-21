#include <jni.h>
#include <stdint.h>
#include <stdbool.h>
#include <dlfcn.h>
#include <pthread.h>

typedef struct { float dx; float dy; int32_t status; } LibreMotionResult;
typedef struct { float dx; float dy; } LibreAimResult;

typedef void (*fn_init_t)(void);
typedef void (*fn_reset_t)(void);
typedef void (*fn_smoothing_t)(float);
typedef LibreMotionResult (*fn_touch_t)(int32_t, int32_t, float, float, float);
typedef int32_t (*fn_joy_id_t)(int32_t, int32_t, int32_t);
typedef LibreAimResult (*fn_aim_t)(float, float, float, int32_t, float);
typedef int32_t (*fn_key_t)(int32_t);

static pthread_once_t s_once = PTHREAD_ONCE_INIT;
static void* s_lib = NULL;
static fn_init_t p_init = NULL;
static fn_reset_t p_reset = NULL;
static fn_smoothing_t p_smoothing = NULL;
static fn_touch_t p_touch = NULL;
static fn_joy_id_t p_joy_id = NULL;
static fn_aim_t p_aim = NULL;
static fn_key_t p_key = NULL;

static void resolve_rust_symbols(void) {
    s_lib = dlopen("liblibre_controls.so", RTLD_NOW | RTLD_LOCAL);
    if (!s_lib) return;
    p_init = (fn_init_t)dlsym(s_lib, "libre_controls_init");
    p_reset = (fn_reset_t)dlsym(s_lib, "libre_controls_reset");
    p_smoothing = (fn_smoothing_t)dlsym(s_lib, "libre_controls_set_smoothing");
    p_touch = (fn_touch_t)dlsym(s_lib, "libre_controls_process_touch");
    p_joy_id = (fn_joy_id_t)dlsym(s_lib, "libre_controls_process_joystick_id");
    p_aim = (fn_aim_t)dlsym(s_lib, "libre_controls_process_aim");
    p_key = (fn_key_t)dlsym(s_lib, "libre_controls_map_android_keycode");
    if (p_init) p_init();
}

static inline bool ensure_loaded(void) {
    pthread_once(&s_once, resolve_rust_symbols);
    return s_lib != NULL;
}

JNIEXPORT jboolean JNICALL
Java_git_artdeell_mojo_input_LibreControls_nativeInit(JNIEnv* env, jclass clazz) {
    (void)env; (void)clazz;
    return ensure_loaded() ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_git_artdeell_mojo_input_LibreControls_nativeReset(JNIEnv* env, jclass clazz) {
    (void)env; (void)clazz;
    if (ensure_loaded() && p_reset) p_reset();
}

JNIEXPORT void JNICALL
Java_git_artdeell_mojo_input_LibreControls_nativeSetSmoothing(JNIEnv* env, jclass clazz, jfloat factor) {
    (void)env; (void)clazz;
    if (ensure_loaded() && p_smoothing) p_smoothing(factor);
}

JNIEXPORT jint JNICALL
Java_git_artdeell_mojo_input_LibreControls_nativeProcessJoystick(
        JNIEnv* env, jclass clazz, jint angle, jint strength, jint deadzone) {
    (void)env; (void)clazz;
    if (ensure_loaded() && p_joy_id) {
        return (jint)p_joy_id(angle, strength, deadzone);
    }
    if (strength <= deadzone) return -1;
    int norm = ((angle % 360) + 360) % 360;
    return ((int)((norm + 22.5f) / 45.0f)) % 8;
}

JNIEXPORT void JNICALL
Java_git_artdeell_mojo_input_LibreControls_nativeProcessTouch(
        JNIEnv* env, jclass clazz, jint pointerId, jint action, jfloat x, jfloat y, jfloat sensitivity, jfloatArray outDelta) {
    (void)clazz;
    if (!outDelta) return;
    jfloat res[2] = { 0.0f, 0.0f };
    if (ensure_loaded() && p_touch) {
        LibreMotionResult r = p_touch(pointerId, action, x, y, sensitivity);
        res[0] = r.dx;
        res[1] = r.dy;
    }
    (*env)->SetFloatArrayRegion(env, outDelta, 0, 2, res);
}

JNIEXPORT void JNICALL
Java_git_artdeell_mojo_input_LibreControls_nativeProcessAim(
        JNIEnv* env, jclass clazz, jfloat dx, jfloat dy, jfloat sensitivity, jint profile, jfloat accelExp, jfloatArray outDelta) {
    (void)clazz;
    if (!outDelta) return;
    jfloat res[2] = { dx * sensitivity, dy * sensitivity };
    if (ensure_loaded() && p_aim) {
        LibreAimResult r = p_aim(dx, dy, sensitivity, profile, accelExp);
        res[0] = r.dx;
        res[1] = r.dy;
    }
    (*env)->SetFloatArrayRegion(env, outDelta, 0, 2, res);
}

JNIEXPORT jint JNICALL
Java_git_artdeell_mojo_input_LibreControls_nativeMapKeycode(JNIEnv* env, jclass clazz, jint androidKey) {
    (void)env; (void)clazz;
    if (ensure_loaded() && p_key) return (jint)p_key(androidKey);
    return 0;
}
