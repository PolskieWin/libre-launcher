#include <jni.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <stdbool.h>
#include <sys/resource.h>
#include <time.h>
#include <dlfcn.h>
#include <math.h>
#include <pthread.h>
#include <EGL/egl.h>
#include <android/log.h>

#define TAG "LibreTelemetry"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)

#define TELEMETRY_WINDOW 240

extern void make_big_core_affine();
extern void make_all_cores_affine();

static float s_frame_times[TELEMETRY_WINDOW];
static int s_head = 0;
static int s_count = 0;
static uint64_t s_total_frames = 0;
static uint64_t s_last_frame_time_ns = 0;
static uint64_t s_last_native_present_ns = 0;
static pthread_mutex_t s_telemetry_mutex = PTHREAD_MUTEX_INITIALIZER;

static volatile bool s_is_afk = false;
static volatile int s_target_fps = 5; // Default 5 FPS (200ms frame budget)
static uint64_t s_last_input_time_ms = 0;

// NDK Thermal polling cache (polled strictly at most once every 10 seconds)
static float s_cached_thermal_headroom = 0.0f;
static uint64_t s_last_thermal_poll_ms = 0;

typedef EGLBoolean (*eglSwapBuffers_t)(EGLDisplay dpy, EGLSurface surf);
static eglSwapBuffers_t s_real_eglSwapBuffers = NULL;

typedef EGLBoolean (*eglSwapInterval_t)(EGLDisplay dpy, EGLint interval);
static eglSwapInterval_t s_real_eglSwapInterval = NULL;

static uint64_t get_current_time_ns() {
    struct timespec ts;
    clock_gettime(CLOCK_MONOTONIC, &ts);
    return (uint64_t)(ts.tv_sec * 1000000000ULL + ts.tv_nsec);
}

static uint64_t get_current_time_ms() {
    return get_current_time_ns() / 1000000ULL;
}

__attribute__((visibility("default")))
void libre_on_present_swap(void) {
    uint64_t now_ns = get_current_time_ns();
    uint64_t prev_ns = s_last_frame_time_ns;
    s_last_frame_time_ns = now_ns;
    s_last_native_present_ns = now_ns;

    float delta_ms = 16.66f;
    if (prev_ns > 0 && now_ns > prev_ns) {
        delta_ms = (float)(now_ns - prev_ns) / 1000000.0f;
    }
    if (delta_ms <= 0.001f) delta_ms = 0.001f;
    if (delta_ms > 1000.0f) delta_ms = 1000.0f;

    pthread_mutex_lock(&s_telemetry_mutex);
    s_frame_times[s_head] = delta_ms;
    s_head = (s_head + 1) % TELEMETRY_WINDOW;
    if (s_count < TELEMETRY_WINDOW) s_count++;
    s_total_frames++;
    pthread_mutex_unlock(&s_telemetry_mutex);

    // Hibernate pacing on render thread
    if (s_is_afk && s_target_fps > 0) {
        float budget_ms = 1000.0f / (float)s_target_fps;
        if (delta_ms < budget_ms) {
            useconds_t sleep_us = (useconds_t)((budget_ms - delta_ms) * 1000.0f);
            usleep(sleep_us);
            s_last_frame_time_ns = get_current_time_ns();
        }
    }
}

extern void* mojoexec_acq_egl_handle();
extern void libre_adpf_report_swap();

static pthread_once_t s_egl_swap_once = PTHREAD_ONCE_INIT;

static void init_real_egl_swap() {
    void* egl_handle = mojoexec_acq_egl_handle();
    if (!egl_handle) {
        egl_handle = dlopen("libEGL_mesa.so", RTLD_NOLOAD);
    }
    if (egl_handle) {
        s_real_eglSwapBuffers = (eglSwapBuffers_t)dlsym(egl_handle, "eglSwapBuffers");
    }
    if (!s_real_eglSwapBuffers) {
        void* libegl = dlopen("libEGL.so", RTLD_GLOBAL | RTLD_LAZY);
        if (libegl) {
            s_real_eglSwapBuffers = (eglSwapBuffers_t)dlsym(libegl, "eglSwapBuffers");
        }
    }
    if (!s_real_eglSwapBuffers) {
        s_real_eglSwapBuffers = (eglSwapBuffers_t)dlsym(RTLD_NEXT, "eglSwapBuffers");
    }
    if (s_real_eglSwapBuffers == (eglSwapBuffers_t)&eglSwapBuffers) {
        s_real_eglSwapBuffers = NULL;
    }
}

__attribute__((visibility("default")))
EGLBoolean eglSwapBuffers(EGLDisplay dpy, EGLSurface surf) {
    pthread_once(&s_egl_swap_once, init_real_egl_swap);

    libre_on_present_swap();
    libre_adpf_report_swap();

    if (s_real_eglSwapBuffers) {
        return s_real_eglSwapBuffers(dpy, surf);
    }
    return EGL_TRUE;
}

__attribute__((visibility("default")))
EGLBoolean eglSwapInterval(EGLDisplay dpy, EGLint interval) {
    if (__builtin_expect(!s_real_eglSwapInterval, 0)) {
        void* libegl = dlopen("libEGL.so", RTLD_GLOBAL | RTLD_LAZY);
        if (libegl) {
            s_real_eglSwapInterval = (eglSwapInterval_t)dlsym(libegl, "eglSwapInterval");
        }
        if (!s_real_eglSwapInterval) {
            s_real_eglSwapInterval = (eglSwapInterval_t)dlsym(RTLD_NEXT, "eglSwapInterval");
        }
    }

    if (s_real_eglSwapInterval) {
        return s_real_eglSwapInterval(dpy, interval);
    }
    return EGL_TRUE;
}

typedef void* (*AThermal_acquireManager_fn)(void);
typedef float (*AThermal_getThermalHeadroom_fn)(void*, int);

static void* s_thermal_libandroid = NULL;
static AThermal_acquireManager_fn s_thermal_acquire_fn = NULL;
static AThermal_getThermalHeadroom_fn s_thermal_headroom_fn = NULL;
static pthread_once_t s_thermal_once = PTHREAD_ONCE_INIT;

static void init_thermal_pointers(void) {
    s_thermal_libandroid = dlopen("libandroid.so", RTLD_NOW);
    if (s_thermal_libandroid) {
        s_thermal_acquire_fn = (AThermal_acquireManager_fn)dlsym(s_thermal_libandroid, "AThermal_acquireManager");
        s_thermal_headroom_fn = (AThermal_getThermalHeadroom_fn)dlsym(s_thermal_libandroid, "AThermal_getThermalHeadroom");
        if (!s_thermal_acquire_fn || !s_thermal_headroom_fn) {
            dlclose(s_thermal_libandroid);
            s_thermal_libandroid = NULL;
            s_thermal_acquire_fn = NULL;
            s_thermal_headroom_fn = NULL;
        }
    }
}

static float read_ndk_thermal_headroom() {
    uint64_t now_ms = get_current_time_ms();
    if (s_last_thermal_poll_ms > 0 && (now_ms - s_last_thermal_poll_ms) < 10000ULL) {
        return s_cached_thermal_headroom;
    }
    s_last_thermal_poll_ms = now_ms;

    pthread_once(&s_thermal_once, init_thermal_pointers);

    if (s_thermal_acquire_fn && s_thermal_headroom_fn) {
        void* manager = s_thermal_acquire_fn();
        if (manager) {
            float hr = s_thermal_headroom_fn(manager, 10);
            if (!isnan(hr) && hr >= 0.0f) {
                s_cached_thermal_headroom = hr;
            }
        }
    }
    return s_cached_thermal_headroom;
}

static void read_memory_info(uint32_t *out_used_mb, uint32_t *out_total_mb) {
    *out_used_mb = 0;
    *out_total_mb = 0;

    long page_kb = sysconf(_SC_PAGESIZE) / 1024;
    if (page_kb <= 0) page_kb = 4;

    FILE *f_statm = fopen("/proc/self/statm", "r");
    if (f_statm) {
        unsigned long size, resident, share, text, lib, data, dt;
        if (fscanf(f_statm, "%lu %lu %lu %lu %lu %lu %lu", &size, &resident, &share, &text, &lib, &data, &dt) >= 2) {
            *out_used_mb = (uint32_t)((resident * page_kb) / 1024);
        }
        fclose(f_statm);
    }

    if (*out_used_mb == 0) {
        FILE *f_status = fopen("/proc/self/status", "r");
        if (f_status) {
            char line[128];
            while (fgets(line, sizeof(line), f_status)) {
                if (strncmp(line, "VmRSS:", 6) == 0) {
                    unsigned long rss_kb = 0;
                    if (sscanf(line + 6, "%lu", &rss_kb) == 1) {
                        *out_used_mb = (uint32_t)(rss_kb / 1024);
                    }
                    break;
                }
            }
            fclose(f_status);
        }
    }

    FILE *f_mem = fopen("/proc/meminfo", "r");
    if (f_mem) {
        char line[128];
        while (fgets(line, sizeof(line), f_mem)) {
            if (strncmp(line, "MemTotal:", 9) == 0) {
                unsigned long total_kb = 0;
                if (sscanf(line + 9, "%lu", &total_kb) == 1) {
                    *out_total_mb = (uint32_t)(total_kb / 1024);
                }
                break;
            }
        }
        fclose(f_mem);
    }
}

JNIEXPORT void JNICALL
Java_git_artdeell_mojo_telemetry_LibreTelemetry_nativeRecordFrame(
        JNIEnv *env,
        jclass clazz,
        jfloat delta_ms) {
    (void)env;
    (void)clazz;
    uint64_t now_ns = get_current_time_ns();
    // If native present swap was invoked within 250ms, defer to native present to avoid double counting
    if (s_last_native_present_ns > 0 && (now_ns - s_last_native_present_ns) < 250000000ULL) {
        return;
    }

    if (delta_ms <= 0.001f) delta_ms = 0.001f;
    if (delta_ms > 1000.0f) delta_ms = 1000.0f;

    pthread_mutex_lock(&s_telemetry_mutex);
    s_frame_times[s_head] = delta_ms;
    s_head = (s_head + 1) % TELEMETRY_WINDOW;
    if (s_count < TELEMETRY_WINDOW) s_count++;
    s_total_frames++;
    pthread_mutex_unlock(&s_telemetry_mutex);
}

JNIEXPORT jlong JNICALL
Java_git_artdeell_mojo_telemetry_LibreTelemetry_nativeOnFramePresent(
        JNIEnv *env,
        jclass clazz) {
    (void)env;
    (void)clazz;
    libre_on_present_swap();
    return (jlong)s_total_frames;
}

JNIEXPORT jint JNICALL
Java_git_artdeell_mojo_telemetry_LibreTelemetry_nativeComputeTelemetryPacked(
        JNIEnv *env,
        jclass clazz,
        jlongArray out_array) {
    if (out_array == NULL || (*env)->GetArrayLength(env, out_array) < 8) return 0;

    float fps = 0.0f;
    float one_percent_low = 0.0f;
    float latest_delta = 0.0f;
    float jitter_ms = 0.0f;

    float local_frames[TELEMETRY_WINDOW];
    int count = 0;
    int head = 0;
    uint64_t total_frames = 0;

    pthread_mutex_lock(&s_telemetry_mutex);
    count = s_count;
    head = s_head;
    total_frames = s_total_frames;
    if (count > 0) {
        memcpy(local_frames, s_frame_times, (size_t)count * sizeof(float));
    }
    pthread_mutex_unlock(&s_telemetry_mutex);

    if (count > 0) {
        float sum = 0.0f;
        float min_val = 100000.0f;
        float max_val = 0.0f;
        float sorted[TELEMETRY_WINDOW];

        for (int i = 0; i < count; i++) {
            float val = local_frames[i];
            sum += val;
            if (val < min_val) min_val = val;
            if (val > max_val) max_val = val;
            sorted[i] = val;
        }

        float avg_delta = sum / (float)count;
        fps = avg_delta > 0.0f ? (1000.0f / avg_delta) : 0.0f;

        // Frame Jitter calculation (Mean Absolute Deviation in ms)
        float jitter_sum = 0.0f;
        for (int i = 0; i < count; i++) {
            jitter_sum += fabsf(local_frames[i] - avg_delta);
        }
        jitter_ms = jitter_sum / (float)count;

        // 1% Low FPS (99th percentile worst frame time)
        for (int i = 1; i < count; i++) {
            int j = i;
            while (j > 0 && sorted[j - 1] > sorted[j]) {
                float tmp = sorted[j];
                sorted[j] = sorted[j - 1];
                sorted[j - 1] = tmp;
                j--;
            }
        }
        int p99_idx = (int)((float)count * 0.99f);
        if (p99_idx >= count) p99_idx = count - 1;
        float p99_delta = sorted[p99_idx];
        one_percent_low = p99_delta > 0.0f ? (1000.0f / p99_delta) : 0.0f;

        int latest_idx = (head + TELEMETRY_WINDOW - 1) % TELEMETRY_WINDOW;
        latest_delta = local_frames[latest_idx];
    }

    uint32_t ram_used = 0, ram_total = 0;
    read_memory_info(&ram_used, &ram_total);

    float thermal_headroom = read_ndk_thermal_headroom();

    jlong buf[8];
    buf[0] = (jlong)(fps * 100.0f);
    buf[1] = (jlong)(one_percent_low * 100.0f);
    buf[2] = (jlong)(latest_delta * 100.0f);
    buf[3] = (jlong)(jitter_ms * 100.0f);
    buf[4] = (jlong)ram_used;
    buf[5] = (jlong)(thermal_headroom * 100.0f);
    buf[6] = (jlong)(s_is_afk ? 1 : 0);
    buf[7] = (jlong)total_frames;

    (*env)->SetLongArrayRegion(env, out_array, 0, 8, buf);
    return 8;
}

JNIEXPORT void JNICALL
Java_git_artdeell_mojo_telemetry_LibreTelemetry_nativeGetTelemetry(
        JNIEnv *env,
        jclass clazz,
        jfloatArray out_floats,
        jintArray out_ints) {
    if (!out_floats || !out_ints) return;
    if ((*env)->GetArrayLength(env, out_floats) < 6 || (*env)->GetArrayLength(env, out_ints) < 4) return;

    float local_frames[TELEMETRY_WINDOW];
    int count = 0;
    int head = 0;
    uint64_t total_frames = 0;

    pthread_mutex_lock(&s_telemetry_mutex);
    count = s_count;
    head = s_head;
    total_frames = s_total_frames;
    if (count > 0) {
        memcpy(local_frames, s_frame_times, (size_t)count * sizeof(float));
    }
    pthread_mutex_unlock(&s_telemetry_mutex);

    if (count == 0) return;

    float sum = 0.0f;
    float min_val = 100000.0f;
    float max_val = 0.0f;
    float sorted[TELEMETRY_WINDOW];

    for (int i = 0; i < count; i++) {
        float val = local_frames[i];
        sum += val;
        if (val < min_val) min_val = val;
        if (val > max_val) max_val = val;
        sorted[i] = val;
    }

    float avg_delta = sum / (float)count;
    float fps = avg_delta > 0.0f ? (1000.0f / avg_delta) : 0.0f;

    for (int i = 1; i < count; i++) {
        int j = i;
        while (j > 0 && sorted[j - 1] > sorted[j]) {
            float tmp = sorted[j];
            sorted[j] = sorted[j - 1];
            sorted[j - 1] = tmp;
            j--;
        }
    }
    int p99_idx = (int)((float)count * 0.99f);
    if (p99_idx >= count) p99_idx = count - 1;
    float p99_delta = sorted[p99_idx];
    float one_percent_low = p99_delta > 0.0f ? (1000.0f / p99_delta) : 0.0f;

    int latest_idx = (head + TELEMETRY_WINDOW - 1) % TELEMETRY_WINDOW;
    float latest_delta = local_frames[latest_idx];

    uint32_t ram_used = 0, ram_total = 0;
    read_memory_info(&ram_used, &ram_total);

    jfloat float_buf[6];
    float_buf[0] = fps;
    float_buf[1] = latest_delta;
    float_buf[2] = one_percent_low;
    float_buf[3] = min_val;
    float_buf[4] = max_val;
    float_buf[5] = read_ndk_thermal_headroom();

    jint int_buf[4];
    int_buf[0] = (jint)(total_frames & 0x7FFFFFFF);
    int_buf[1] = (jint)ram_used;
    int_buf[2] = (jint)ram_total;
    int_buf[3] = s_is_afk ? 1 : 0;

    (*env)->SetFloatArrayRegion(env, out_floats, 0, 6, float_buf);
    (*env)->SetIntArrayRegion(env, out_ints, 0, 4, int_buf);
}

JNIEXPORT void JNICALL
Java_git_artdeell_mojo_telemetry_LibreTelemetry_nativeSetAfk(
        JNIEnv *env,
        jclass clazz,
        jboolean active,
        jint target_fps) {
    if (active) {
        s_is_afk = true;
        s_target_fps = target_fps > 0 ? target_fps : 5;
        setpriority(PRIO_PROCESS, 0, 10); // Relax priority
        make_all_cores_affine();          // Relax core affinity
        LOGI("Entered Hibernate AFK mode (%d target FPS, nice 10)", s_target_fps);
    } else {
        s_is_afk = false;
        setpriority(PRIO_PROCESS, 0, -10); // Elevate priority back to real-time
        make_big_core_affine();            // Pin back to prime and big cores
        LOGI("Exited Hibernate AFK mode (restored Snapdragon performance affinity, nice -10)");
    }
}

JNIEXPORT void JNICALL
Java_git_artdeell_mojo_telemetry_LibreTelemetry_nativeSetHibernate(
        JNIEnv *env,
        jclass clazz,
        jboolean active,
        jint target_fps) {
    Java_git_artdeell_mojo_telemetry_LibreTelemetry_nativeSetAfk(env, clazz, active, target_fps);
}

JNIEXPORT jboolean JNICALL
Java_git_artdeell_mojo_telemetry_LibreTelemetry_nativeIsAfk(
        JNIEnv *env,
        jclass clazz) {
    return s_is_afk ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_git_artdeell_mojo_telemetry_LibreTelemetry_nativeIsHibernate(
        JNIEnv *env,
        jclass clazz) {
    return s_is_afk ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_git_artdeell_mojo_telemetry_LibreTelemetry_nativeNotifyInput(
        JNIEnv *env,
        jclass clazz) {
    s_last_input_time_ms = get_current_time_ms();
    if (s_is_afk) {
        Java_git_artdeell_mojo_telemetry_LibreTelemetry_nativeSetAfk(env, clazz, JNI_FALSE, 0);
    }
}

JNIEXPORT void JNICALL
Java_git_artdeell_mojo_telemetry_LibreTelemetry_nativeThrottleSleep(
        JNIEnv *env,
        jclass clazz) {
    if (s_is_afk && s_target_fps > 0) {
        useconds_t sleep_us = (useconds_t)(1000000 / s_target_fps);
        usleep(sleep_us);
    }
}
