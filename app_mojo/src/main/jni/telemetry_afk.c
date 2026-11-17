#include <jni.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <stdbool.h>
#include <sys/resource.h>
#include <time.h>
#include <android/log.h>

#define TAG "LibreTelemetry"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)

#define TELEMETRY_WINDOW 120

extern void make_big_core_affine();
extern void make_all_cores_affine();

static float s_frame_times[TELEMETRY_WINDOW];
static int s_head = 0;
static int s_count = 0;
static uint64_t s_total_frames = 0;

static volatile bool s_is_afk = false;
static volatile int s_target_fps = 10;
static uint64_t s_last_input_time_ms = 0;

static uint64_t get_current_time_ms() {
    struct timespec ts;
    clock_gettime(CLOCK_MONOTONIC, &ts);
    return (uint64_t)(ts.tv_sec * 1000ULL + ts.tv_nsec / 1000000ULL);
}

static void read_memory_info(uint32_t *out_used_mb, uint32_t *out_total_mb) {
    *out_used_mb = 0;
    *out_total_mb = 0;

    FILE *f_statm = fopen("/proc/self/statm", "r");
    if (f_statm) {
        unsigned long size, resident, share, text, lib, data, dt;
        if (fscanf(f_statm, "%lu %lu %lu %lu %lu %lu %lu", &size, &resident, &share, &text, &lib, &data, &dt) >= 2) {
            *out_used_mb = (uint32_t)((resident * 4) / 1024);
        }
        fclose(f_statm);
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
    if (delta_ms <= 0.001f) delta_ms = 0.001f;
    if (delta_ms > 1000.0f) delta_ms = 1000.0f;

    s_frame_times[s_head] = delta_ms;
    s_head = (s_head + 1) % TELEMETRY_WINDOW;
    if (s_count < TELEMETRY_WINDOW) s_count++;
    s_total_frames++;
}

JNIEXPORT void JNICALL
Java_git_artdeell_mojo_telemetry_LibreTelemetry_nativeGetTelemetry(
        JNIEnv *env,
        jclass clazz,
        jfloatArray out_floats,
        jintArray out_ints) {
    if (s_count == 0) return;

    float sum = 0.0f;
    float min_val = 100000.0f;
    float max_val = 0.0f;
    float sorted[TELEMETRY_WINDOW];

    for (int i = 0; i < s_count; i++) {
        float val = s_frame_times[i];
        sum += val;
        if (val < min_val) min_val = val;
        if (val > max_val) max_val = val;
        sorted[i] = val;
    }

    float avg_delta = sum / (float)s_count;
    float fps = avg_delta > 0.0f ? (1000.0f / avg_delta) : 0.0f;

    // Insertion sort for 1% low calculation without allocation
    for (int i = 1; i < s_count; i++) {
        int j = i;
        while (j > 0 && sorted[j - 1] > sorted[j]) {
            float tmp = sorted[j];
            sorted[j] = sorted[j - 1];
            sorted[j - 1] = tmp;
            j--;
        }
    }
    int p99_idx = (int)((float)s_count * 0.99f);
    if (p99_idx >= s_count) p99_idx = s_count - 1;
    float p99_delta = sorted[p99_idx];
    float one_percent_low = p99_delta > 0.0f ? (1000.0f / p99_delta) : 0.0f;

    int latest_idx = (s_head + TELEMETRY_WINDOW - 1) % TELEMETRY_WINDOW;
    float latest_delta = s_frame_times[latest_idx];

    uint32_t ram_used = 0, ram_total = 0;
    read_memory_info(&ram_used, &ram_total);

    jfloat float_buf[6];
    float_buf[0] = fps;
    float_buf[1] = latest_delta;
    float_buf[2] = one_percent_low;
    float_buf[3] = min_val;
    float_buf[4] = max_val;
    float_buf[5] = 0.0f; // reserved for cpu load

    jint int_buf[4];
    int_buf[0] = (jint)(s_total_frames & 0x7FFFFFFF);
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
        s_target_fps = target_fps > 0 && target_fps <= 30 ? target_fps : 10;
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

JNIEXPORT jboolean JNICALL
Java_git_artdeell_mojo_telemetry_LibreTelemetry_nativeIsAfk(
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
