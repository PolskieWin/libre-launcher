#include "libremobile.h"

#include <android/log.h>
#include <sys/resource.h>
#include <sys/system_properties.h>
#include <unistd.h>
#include <sched.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <dlfcn.h>
#include <stdint.h>
#include <pthread.h>
#include <time.h>
#include <EGL/egl.h>

#define TAG "LibreMobile"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, TAG, __VA_ARGS__)

#define GL_VENDOR   0x1F00
#define GL_RENDERER 0x1F01

typedef unsigned char GLubyte;
typedef unsigned int GLenum;
typedef const GLubyte* (*PFNGLGETSTRINGPROC)(GLenum);

// ADPF function prototypes from libandroid.so (API 33+)
struct APerformanceHintManager;
typedef struct APerformanceHintManager APerformanceHintManager;
struct APerformanceHintSession;
typedef struct APerformanceHintSession APerformanceHintSession;

typedef APerformanceHintManager* (*pfn_APerformanceHint_getManager)();
typedef APerformanceHintSession* (*pfn_APerformanceHint_createSession)(
        APerformanceHintManager*, const int32_t*, size_t, int64_t);
typedef int (*pfn_APerformanceHint_reportActualWorkDuration)(
        APerformanceHintSession*, int64_t);
typedef int (*pfn_APerformanceHint_updateTargetWorkDuration)(
        APerformanceHintSession*, int64_t);
typedef void (*pfn_APerformanceHint_closeSession)(APerformanceHintSession*);

extern "C" void make_big_core_affine();
extern "C" void* mojoexec_acq_egl_handle();
extern "C" EGLBoolean eglSwapBuffers(EGLDisplay dpy, EGLSurface surf);

static pthread_once_t s_init_once = PTHREAD_ONCE_INIT;
static bool s_libre_mobile_active = false;
static PFNGLGETSTRINGPROC s_real_glGetString = NULL;
static PFNEGLGETPROCADDRESSPROC s_real_eglGetProcAddress = NULL;

static pfn_APerformanceHint_getManager s_pfn_getManager = NULL;
static pfn_APerformanceHint_createSession s_pfn_createSession = NULL;
static pfn_APerformanceHint_reportActualWorkDuration s_pfn_reportActualWorkDuration = NULL;
static pfn_APerformanceHint_updateTargetWorkDuration s_pfn_updateTargetWorkDuration = NULL;
static pfn_APerformanceHint_closeSession s_pfn_closeSession = NULL;

static APerformanceHintManager* s_adpf_manager = NULL;
static APerformanceHintSession* s_adpf_session = NULL;
static int64_t s_adpf_target_duration_ns = 8333333LL;
static struct timespec s_last_swap_time = {0, 0};

// Flagship SoC Lookup Table (< 4 KB)
static const uint16_t s_flagship_soc_ids[] = {
    // Flagship 8xx & 7+ Gen Series
    339, 356, 361, 362, 367, 393, 400, 415, 439, 456, 457, 459, 482,
    501, 502, 519, 530, 531, 536, 540, 552, 557, 577, 603, 619, 621, 623,
    673, 674, 693, 709, 710,
    // Mid-range 7xx & 6 Gen 1/3/4 Series (excludes low-end 4xx & 680/685/695)
    475, 499, 506, 507, 537, 538, 546, 591, 592, 608, 609,
    645, 646, 649, 655, 656, 681, 682, 686, 687, 697
};
static const size_t s_flagship_soc_count = sizeof(s_flagship_soc_ids) / sizeof(s_flagship_soc_ids[0]);

static const uint16_t s_adreno_7xx_8xx_soc_ids[] = {
    457, 482, 519, 530, 531, 536, 540, 552, 557, 577, 603, 619, 621, 623, 693
};
static const size_t s_adreno_7xx_8xx_soc_count = sizeof(s_adreno_7xx_8xx_soc_ids) / sizeof(s_adreno_7xx_8xx_soc_ids[0]);

static const char* const s_flagship_platform_names[] = {
    // Flagships
    "sm8150", "sm8250", "sm8350", "sm8450", "sm8475", "sm8550", "sm8650", "sm8735", "sm8750", "sm8850",
    "sm7475", "sm7675", "kona", "lahaina", "taro", "kalama", "pineapple", "sun", "canoe",
    // Mid-range 7-series & 6 Gen 1/3/4 (excludes low-end 4xx / 680 / 685 / 695)
    "sm7325", "sm7350", "sm7435", "sm7450", "sm7550", "sm7635", "sm7750",
    "sm6435", "sm6450", "sm6475", "sm6635",
    "yupik", "cedros", "cape", "parrot", "crow", "volcano", "cliffs"
};
static const size_t s_flagship_platform_count = sizeof(s_flagship_platform_names) / sizeof(s_flagship_platform_names[0]);

static const char* const s_adreno_7xx_8xx_names[] = {
    "sm8450", "sm8475", "sm8550", "sm8650", "sm8750", "sm7475", "sm7675",
    "taro", "kalama", "pineapple", "sun"
};
static const size_t s_adreno_7xx_8xx_name_count = sizeof(s_adreno_7xx_8xx_names) / sizeof(s_adreno_7xx_8xx_names[0]);

static bool id_in_array(uint16_t id, const uint16_t* arr, size_t count) {
    for (size_t i = 0; i < count; i++) {
        if (arr[i] == id) return true;
    }
    return false;
}

static bool str_in_list(const char* target, const char* const* list, size_t count) {
    if (!target || !target[0]) return false;
    for (size_t i = 0; i < count; i++) {
        if (strcasestr(target, list[i]) != NULL) return true;
    }
    return false;
}

static uint16_t read_soc_id() {
    FILE* fp = fopen("/sys/devices/soc0/soc_id", "r");
    if (!fp) {
        fp = fopen("/sys/devices/system/soc/soc0/id", "r");
    }
    if (!fp) return 0;
    char buf[32];
    uint16_t id = 0;
    if (fgets(buf, sizeof(buf), fp) != NULL) {
        id = (uint16_t)atoi(buf);
    }
    fclose(fp);
    return id;
}

static bool detect_is_flagship_adreno() {
    uint16_t soc_id = read_soc_id();
    if (soc_id > 0 && id_in_array(soc_id, s_flagship_soc_ids, s_flagship_soc_count)) {
        return true;
    }
    char prop[PROP_VALUE_MAX];
    if (__system_property_get("ro.soc.model", prop) > 0) {
        if (str_in_list(prop, s_flagship_platform_names, s_flagship_platform_count)) return true;
    }
    if (__system_property_get("ro.board.platform", prop) > 0) {
        if (str_in_list(prop, s_flagship_platform_names, s_flagship_platform_count)) return true;
    }
    return false;
}

static bool detect_is_adreno_7xx_8xx() {
    uint16_t soc_id = read_soc_id();
    if (soc_id > 0 && id_in_array(soc_id, s_adreno_7xx_8xx_soc_ids, s_adreno_7xx_8xx_soc_count)) {
        return true;
    }
    char prop[PROP_VALUE_MAX];
    if (__system_property_get("ro.soc.model", prop) > 0) {
        if (str_in_list(prop, s_adreno_7xx_8xx_names, s_adreno_7xx_8xx_name_count)) return true;
    }
    if (__system_property_get("ro.board.platform", prop) > 0) {
        if (str_in_list(prop, s_adreno_7xx_8xx_names, s_adreno_7xx_8xx_name_count)) return true;
    }
    return false;
}

static void init_adpf() {
    void* libandroid = dlopen("libandroid.so", RTLD_NOW | RTLD_LOCAL);
    if (!libandroid) return;

    s_pfn_getManager = (pfn_APerformanceHint_getManager)dlsym(libandroid, "APerformanceHint_getManager");
    s_pfn_createSession = (pfn_APerformanceHint_createSession)dlsym(libandroid, "APerformanceHint_createSession");
    s_pfn_reportActualWorkDuration = (pfn_APerformanceHint_reportActualWorkDuration)dlsym(libandroid, "APerformanceHint_reportActualWorkDuration");
    s_pfn_updateTargetWorkDuration = (pfn_APerformanceHint_updateTargetWorkDuration)dlsym(libandroid, "APerformanceHint_updateTargetWorkDuration");
    s_pfn_closeSession = (pfn_APerformanceHint_closeSession)dlsym(libandroid, "APerformanceHint_closeSession");

    if (s_pfn_getManager) {
        s_adpf_manager = s_pfn_getManager();
    }
}

static void init_libremobile_pointers_internal() {
    const char* lm = getenv("LIBRE_MOBILE_ACTIVE");
    s_libre_mobile_active = (lm != NULL && (strcmp(lm, "1") == 0 || strcasecmp(lm, "true") == 0));

    void* egl_handle = mojoexec_acq_egl_handle();
    if (!egl_handle) {
        egl_handle = dlopen("libEGL_mesa.so", RTLD_NOLOAD);
    }
    if (egl_handle) {
        s_real_eglGetProcAddress = (PFNEGLGETPROCADDRESSPROC)dlsym(egl_handle, "eglGetProcAddress");
        s_real_glGetString = (PFNGLGETSTRINGPROC)dlsym(egl_handle, "glGetString");
    }
    if (!s_real_eglGetProcAddress) {
        s_real_eglGetProcAddress = (PFNEGLGETPROCADDRESSPROC)dlsym(RTLD_NEXT, "eglGetProcAddress");
    }
    if (!s_real_eglGetProcAddress) {
        void* libegl = dlopen("libEGL.so", RTLD_GLOBAL | RTLD_LAZY);
        if (libegl) {
            s_real_eglGetProcAddress = (PFNEGLGETPROCADDRESSPROC)dlsym(libegl, "eglGetProcAddress");
            if (!s_real_glGetString) {
                s_real_glGetString = (PFNGLGETSTRINGPROC)dlsym(libegl, "glGetString");
            }
        }
    }
    if (!s_real_glGetString && s_real_eglGetProcAddress) {
        s_real_glGetString = (PFNGLGETSTRINGPROC)s_real_eglGetProcAddress("glGetString");
    }
    if (!s_real_glGetString) {
        s_real_glGetString = (PFNGLGETSTRINGPROC)dlsym(RTLD_NEXT, "glGetString");
    }

    init_adpf();
}

static inline void ensure_initialized() {
    pthread_once(&s_init_once, init_libremobile_pointers_internal);
}

extern "C" __attribute__((visibility("default")))
const unsigned char* libre_glGetString(unsigned int name) {
    ensure_initialized();
    if (s_libre_mobile_active && name == GL_RENDERER) {
        static const unsigned char s_libre_mobile_renderer[] = "Libre Mobile";
        return s_libre_mobile_renderer;
    }
    if (!s_real_glGetString) {
        void* gl_handle = dlopen("libGL.so", RTLD_NOLOAD);
        if (!gl_handle) gl_handle = dlopen("libGL.so.1", RTLD_NOLOAD);
        if (!gl_handle) gl_handle = dlopen("libGLESv2.so", RTLD_NOLOAD);
        if (gl_handle) {
            s_real_glGetString = (PFNGLGETSTRINGPROC)dlsym(gl_handle, "glGetString");
        }
        if (!s_real_glGetString && s_real_eglGetProcAddress) {
            s_real_glGetString = (PFNGLGETSTRINGPROC)s_real_eglGetProcAddress("glGetString");
        }
        if (!s_real_glGetString) {
            s_real_glGetString = (PFNGLGETSTRINGPROC)dlsym(RTLD_DEFAULT, "glGetString");
        }
    }
    if (s_real_glGetString) {
        const unsigned char* str = s_real_glGetString(name);
        if (str != NULL) return str;
    }
    // Safe fallbacks to prevent crashes in Minecraft 26.4 / LWJGL
    if (name == 0x1F00 /* GL_VENDOR */) {
        static const unsigned char s_fallback_vendor[] = "Libre Launcher";
        return s_fallback_vendor;
    } else if (name == 0x1F01 /* GL_RENDERER */) {
        static const unsigned char s_fallback_renderer[] = "Libre Mobile";
        return s_fallback_renderer;
    } else if (name == 0x1F02 /* GL_VERSION */) {
        static const unsigned char s_fallback_version[] = "4.6.0 Libre Mobile";
        return s_fallback_version;
    } else if (name == 0x8B8C /* GL_SHADING_LANGUAGE_VERSION */) {
        static const unsigned char s_fallback_glsl[] = "4.60 Libre Mobile";
        return s_fallback_glsl;
    }
    static const unsigned char s_empty_str[] = "";
    return s_empty_str;
}

extern "C" __attribute__((visibility("default")))
const unsigned char* glGetString(unsigned int name) {
    return libre_glGetString(name);
}

extern "C" __attribute__((visibility("default")))
void libre_adpf_report_swap() {
    ensure_initialized();

    struct timespec now;
    clock_gettime(CLOCK_MONOTONIC, &now);

    if (s_pfn_createSession && s_adpf_manager && !s_adpf_session) {
        int32_t tid = (int32_t)gettid();
        s_adpf_session = s_pfn_createSession(s_adpf_manager, &tid, 1, s_adpf_target_duration_ns);
    }

    if (s_last_swap_time.tv_sec != 0 && s_adpf_session && s_pfn_reportActualWorkDuration) {
        int64_t dur = (int64_t)(now.tv_sec - s_last_swap_time.tv_sec) * 1000000000LL +
                      (int64_t)(now.tv_nsec - s_last_swap_time.tv_nsec);
        if (dur > 0 && dur < 500000000LL) {
            s_pfn_reportActualWorkDuration(s_adpf_session, dur);
        }
    }
    s_last_swap_time = now;
}

extern "C" __attribute__((visibility("default")))
void* libre_eglGetProcAddress(const char* procname) {
    if (!procname) return NULL;
    ensure_initialized();

    if (strcmp(procname, "glGetString") == 0) {
        return (void*)&libre_glGetString;
    }
    if (strcmp(procname, "eglSwapBuffers") == 0) {
        return (void*)&eglSwapBuffers;
    }
    if (s_real_eglGetProcAddress) {
        return (void*)s_real_eglGetProcAddress(procname);
    }
    return NULL;
}

extern "C" __attribute__((visibility("default")))
__eglMustCastToProperFunctionPointerType eglGetProcAddress(const char* procname) {
    return (__eglMustCastToProperFunctionPointerType)libre_eglGetProcAddress(procname);
}

static void apply_common_mobile_optimizations() {
    make_big_core_affine();
    setpriority(PRIO_PROCESS, 0, -10);

    setenv("LIBRE_MOBILE_ACTIVE", "1", 1);
    setenv("MESA_LOADER_DRIVER_OVERRIDE", "zink", 1);
    setenv("MESA_GLSL_VERSION_OVERRIDE", "460", 1);
    setenv("MESA_GL_VERSION_OVERRIDE", "4.6COMPAT", 1);
    setenv("allow_higher_compat_version", "true", 1);
    setenv("allow_glsl_extension_directive_midshader", "true", 1);
    setenv("MESA_NO_ERROR", "1", 1);
    setenv("LIBGL_NOERROR", "1", 1);
    setenv("glsl_zero_init", "false", 1);
    setenv("mesa_glthread", "false", 1);
    setenv("vblank_mode", "0", 1);
    setenv("ZINK_DESCRIPTORS", "auto", 1);

    setenv("MESA_EXTENSION_OVERRIDE",
           "+GL_EXT_texture_filter_anisotropic "
           "+GL_ARB_buffer_storage "
           "+GL_ARB_clip_control "
           "+GL_ARB_shader_draw_parameters "
           "+GL_ARB_direct_state_access "
           "+GL_ARB_vertex_attrib_binding "
           "+GL_ARB_multi_bind "
           "+GL_ARB_gpu_shader5 "
           "+GL_EXT_clip_cull_distance "
           "+GL_ARB_shader_storage_buffer_object "
           "+GL_ARB_compute_shader "
           "+GL_ARB_explicit_uniform_location "
           "+GL_ARB_multi_draw_indirect "
           "+GL_ARB_texture_query_levels "
           "+GL_ARB_conservative_depth "
           "+GL_ARB_shader_image_load_store "
           "+GL_ARB_shading_language_include "
           "+GL_ARB_polygon_offset_clamp "
           "+GL_ARB_texture_storage "
           "+GL_ARB_arrays_of_arrays "
           "+GL_ARB_clear_texture", 1);
}

extern "C" JNIEXPORT jboolean JNICALL
Java_git_artdeell_mojo_game_renderer_impl_LibreMobileRenderSpec_nativeIsFlagshipAdreno(
        JNIEnv *env,
        jclass clazz) {
    return detect_is_flagship_adreno() ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_git_artdeell_mojo_game_renderer_impl_LibreMobileRenderSpec_nativeSetupMobile(
        JNIEnv *env,
        jclass clazz,
        jboolean isAdreno,
        jboolean isMali,
        jboolean isXclipse) {
    LOGI("Activating Libre Mobile Universal Engine (Adreno=%d, Mali=%d, Xclipse=%d)",
         isAdreno, isMali, isXclipse);

    apply_common_mobile_optimizations();

    if (isAdreno) {
        bool isFlagship = detect_is_flagship_adreno();
        bool is7xx8xx = detect_is_adreno_7xx_8xx();

        if (isFlagship) {
            LOGI("Configuring Flagship Tuned Turnip profile (7xx/8xx=%d)", is7xx8xx);
            setenv("MESA_VK_WSI_PRESENT_MODE", "mailbox", 1);
            unsetenv("FD_DEV_FEATURES");
            unsetenv("TU_DEBUG");
        } else {
            LOGI("Configuring Mid-Range Standard Turnip profile");
            unsetenv("FD_DEV_FEATURES");
            unsetenv("TU_DEBUG");
            unsetenv("MESA_VK_WSI_PRESENT_MODE");
        }
    } else if (isMali) {
        LOGI("Configuring ARM Mali / Immortalis profile");
        unsetenv("FD_DEV_FEATURES");
        unsetenv("TU_DEBUG");
        setenv("ZINK_DEBUG", "compact", 1);
        setenv("PAN_MESA_DEBUG", "gl3", 0);
    } else if (isXclipse) {
        LOGI("Configuring Samsung Xclipse profile");
        unsetenv("FD_DEV_FEATURES");
        unsetenv("TU_DEBUG");
        setenv("RADV_DEBUG", "nocompact", 0);
    } else {
        unsetenv("FD_DEV_FEATURES");
        unsetenv("TU_DEBUG");
    }

    return JNI_TRUE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_git_artdeell_mojo_game_renderer_impl_LibreMobileRenderSpec_nativeSetupSnapdragon(
        JNIEnv *env,
        jclass clazz) {
    return Java_git_artdeell_mojo_game_renderer_impl_LibreMobileRenderSpec_nativeSetupMobile(
            env, clazz, JNI_TRUE, JNI_FALSE, JNI_FALSE);
}
