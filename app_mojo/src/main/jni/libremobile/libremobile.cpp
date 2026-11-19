#include "libremobile.h"

#include <android/log.h>
#include <sys/resource.h>
#include <unistd.h>
#include <sched.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <dlfcn.h>
#include <stdint.h>

#define TAG "LibreMobile"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, TAG, __VA_ARGS__)

#define GL_VENDOR   0x1F00
#define GL_RENDERER 0x1F01

typedef unsigned char GLubyte;
typedef unsigned int GLenum;
typedef const GLubyte* (*PFNGLGETSTRINGPROC)(GLenum);
typedef void* (*PFNEGLGETPROCADDRESSPROC)(const char*);

extern "C" void make_big_core_affine();
extern "C" void* mojoexec_acq_egl_handle();

static PFNGLGETSTRINGPROC s_real_glGetString = NULL;
static PFNEGLGETPROCADDRESSPROC s_real_eglGetProcAddress = NULL;

static bool contains_case_insensitive(const char* haystack, const char* needle) {
    if (!haystack || !needle) return false;
    size_t nlen = strlen(needle);
    size_t hlen = strlen(haystack);
    if (nlen > hlen) return false;
    for (size_t i = 0; i <= hlen - nlen; i++) {
        if (strncasecmp(haystack + i, needle, nlen) == 0) return true;
    }
    return false;
}

static const GLubyte* get_real_gl_string(GLenum name) {
    if (__builtin_expect(!s_real_glGetString, 0)) {
        void* egl_handle = mojoexec_acq_egl_handle();
        if (egl_handle) {
            s_real_glGetString = (PFNGLGETSTRINGPROC)dlsym(egl_handle, "glGetString");
        }
        if (!s_real_glGetString) {
            s_real_glGetString = (PFNGLGETSTRINGPROC)dlsym(RTLD_NEXT, "glGetString");
        }
        if (!s_real_glGetString) {
            void* libegl = dlopen("libEGL_mesa.so", RTLD_NOLOAD);
            if (libegl) s_real_glGetString = (PFNGLGETSTRINGPROC)dlsym(libegl, "glGetString");
        }
        if (!s_real_glGetString) {
            void* libdri = dlopen("libgallium_dri.so", RTLD_NOLOAD);
            if (libdri) s_real_glGetString = (PFNGLGETSTRINGPROC)dlsym(libdri, "glGetString");
        }
        if (!s_real_glGetString && s_real_eglGetProcAddress) {
            s_real_glGetString = (PFNGLGETSTRINGPROC)s_real_eglGetProcAddress("glGetString");
        }
    }
    if (s_real_glGetString) {
        return s_real_glGetString(name);
    }
    return NULL;
}

extern "C" __attribute__((visibility("default")))
const unsigned char* libre_glGetString(unsigned int name) {
    const char* lm = getenv("LIBRE_MOBILE_ACTIVE");
    bool is_libre_mobile = (lm != NULL && (strcmp(lm, "1") == 0 || strcasecmp(lm, "true") == 0));

    if (name == GL_RENDERER) {
        const GLubyte* real_str = get_real_gl_string(name);
        const char* str = (const char*)real_str;

        bool has_unwanted_string = false;
        if (str != NULL) {
            if (contains_case_insensitive(str, "turnip") ||
                contains_case_insensitive(str, "zink") ||
                contains_case_insensitive(str, "mesa git") ||
                contains_case_insensitive(str, "mesa")) {
                has_unwanted_string = true;
            }
        }

        if (is_libre_mobile || has_unwanted_string) {
            static const unsigned char s_libre_mobile_renderer[] = "Libremobile 2.00";
            return s_libre_mobile_renderer;
        }

        if (real_str) return real_str;
        static const unsigned char s_fallback_renderer[] = "Libremobile 2.00";
        return s_fallback_renderer;
    }

    return get_real_gl_string(name);
}

extern "C" __attribute__((visibility("default")))
const unsigned char* glGetString(unsigned int name) {
    return libre_glGetString(name);
}

extern "C" __attribute__((visibility("default")))
void* libre_eglGetProcAddress(const char* procname) {
    if (procname && strcmp(procname, "glGetString") == 0) {
        return (void*)&libre_glGetString;
    }
    if (__builtin_expect(!s_real_eglGetProcAddress, 0)) {
        void* libegl = dlopen("libEGL.so", RTLD_GLOBAL | RTLD_LAZY);
        if (libegl) {
            s_real_eglGetProcAddress = (PFNEGLGETPROCADDRESSPROC)dlsym(libegl, "eglGetProcAddress");
        }
        if (!s_real_eglGetProcAddress) {
            s_real_eglGetProcAddress = (PFNEGLGETPROCADDRESSPROC)dlsym(RTLD_NEXT, "eglGetProcAddress");
        }
    }
    if (s_real_eglGetProcAddress) {
        return s_real_eglGetProcAddress(procname);
    }
    return NULL;
}

extern "C" __attribute__((visibility("default")))
void* eglGetProcAddress(const char* procname) {
    return libre_eglGetProcAddress(procname);
}

static void apply_common_mobile_optimizations() {
    // 1. Enforce performance & prime core CPU affinity
    make_big_core_affine();

    // 2. Elevate process nice priority to -10 for minimum scheduling latency
    setpriority(PRIO_PROCESS, 0, -10);

    // 3. Mark Libre Mobile as active
    setenv("LIBRE_MOBILE_ACTIVE", "1", 1);

    // 4. Universal Mesa & Vulkan / Zink high-performance flags with 26.4 snapshots support
    setenv("mesa_glthread", "true", 1);
    setenv("MESA_NO_ERROR", "1", 1);
    setenv("LIBGL_NOERROR", "1", 1);
    setenv("ZINK_DESCRIPTORS", "lazy", 1);
    setenv("allow_higher_compat_version", "true", 1);
    setenv("allow_glsl_extension_directive_midshader", "true", 1);
    setenv("glsl_zero_init", "true", 1);
    setenv("MESA_VK_WSI_PRESENT_MODE", "mailbox", 0);
    setenv("MESA_LOADER_DRIVER_OVERRIDE", "zink", 1);
    setenv("MESA_GLSL_VERSION_OVERRIDE", "460", 1);
    setenv("MESA_GL_VERSION_OVERRIDE", "4.6COMPAT", 1);
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
