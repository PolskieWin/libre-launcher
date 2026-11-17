#include "libremobile.h"

#include <android/log.h>
#include <sys/resource.h>
#include <unistd.h>
#include <sched.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#define TAG "LibreMobile"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, TAG, __VA_ARGS__)

extern "C" void make_big_core_affine();

extern "C" JNIEXPORT jboolean JNICALL
Java_git_artdeell_mojo_game_renderer_impl_LibreMobileRenderSpec_nativeSetupSnapdragon(
        JNIEnv *env,
        jclass clazz) {
    LOGI("Activating Libre Mobile Snapdragon Performance Engine");

    // 1. Enforce Snapdragon Performance & Prime Core Affinity
    make_big_core_affine();

    // 2. Elevate process nice priority to -10 for minimum scheduling latency
    setpriority(PRIO_PROCESS, 0, -10);

    // 3. Set Turnip / Adreno environment flags for maximum throughput
    setenv("TU_DEBUG", "sysmem", 1);
    setenv("FD_DEV_FEATURES", "enable_tp_ubwc_flag_hint=1", 1);
    setenv("mesa_glthread", "true", 1);
    setenv("MESA_NO_ERROR", "1", 1);
    setenv("LIBGL_NOERROR", "1", 1);

    LOGI("Libre Mobile Snapdragon optimizations applied successfully");
    return JNI_TRUE;
}
