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

static void apply_common_mobile_optimizations() {
    // 1. Enforce performance & prime core CPU affinity
    make_big_core_affine();

    // 2. Elevate process nice priority to -10 for minimum scheduling latency
    setpriority(PRIO_PROCESS, 0, -10);

    // 3. Universal Mesa & Vulkan / Zink high-performance flags
    setenv("mesa_glthread", "true", 1);
    setenv("MESA_NO_ERROR", "1", 1);
    setenv("LIBGL_NOERROR", "1", 1);
    setenv("ZINK_DESCRIPTORS", "lazy", 1);
    setenv("allow_higher_compat_version", "true", 1);
    setenv("allow_glsl_extension_directive_midshader", "true", 1);
    setenv("glsl_zero_init", "true", 1);
    setenv("MESA_VK_WSI_PRESENT_MODE", "mailbox", 0);
}

extern "C" JNIEXPORT jboolean JNICALL
Java_git_artdeell_mojo_game_renderer_impl_LibreMobileRenderSpec_nativeSetupMobile(
        JNIEnv *env,
        jclass clazz,
        jboolean isAdreno,
        jboolean isMali,
        jboolean isXclipse) {
    LOGI("Activating Libre Mobile Universal Performance Engine (Adreno=%d, Mali=%d, Xclipse=%d)",
         isAdreno, isMali, isXclipse);

    apply_common_mobile_optimizations();

    if (isAdreno) {
        LOGI("Applying Qualcomm Snapdragon / Adreno optimizations");
        setenv("TU_DEBUG", "sysmem", 1);
        setenv("FD_DEV_FEATURES", "enable_tp_ubwc_flag_hint=1", 1);
    } else if (isMali) {
        LOGI("Applying ARM Mali / Immortalis optimizations");
        setenv("ZINK_DEBUG", "compact", 1);
        setenv("PAN_MESA_DEBUG", "gl3", 0);
    } else if (isXclipse) {
        LOGI("Applying Samsung Xclipse / AMD RDNA optimizations");
        setenv("RADV_DEBUG", "nocompact", 0);
    }

    LOGI("Libre Mobile optimizations applied successfully");
    return JNI_TRUE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_git_artdeell_mojo_game_renderer_impl_LibreMobileRenderSpec_nativeSetupSnapdragon(
        JNIEnv *env,
        jclass clazz) {
    return Java_git_artdeell_mojo_game_renderer_impl_LibreMobileRenderSpec_nativeSetupMobile(
            env, clazz, JNI_TRUE, JNI_FALSE, JNI_FALSE);
}
