//
// Created by maks on 06.01.2025.
//

#include "jvm_hooks.h"

#include <android/api-level.h>

#include <dlfcn.h>
#include <string.h>
#include <stdlib.h>
#include <stdio.h>
#include <stdint.h>

#define TAG __FILE_NAME__
#include <log.h>

#include <mojoexec.h>

extern const void* libre_glGetString(unsigned int name);
extern void* libre_eglGetProcAddress(const char *procname);

/**
 * Basically a verbatim implementation of ndlopen(), found at
 * https://github.com/PojavLauncherTeam/lwjgl3/blob/3.3.1/modules/lwjgl/core/src/generated/c/linux/org_lwjgl_system_linux_DynamicLinkLoader.c#L11
 * but with our own additions for stuff like vulkanmod.
 */
static jlong ndlopen_bugfix(__attribute__((unused)) JNIEnv *env,
                     __attribute__((unused)) jclass class,
                     jlong filename_ptr,
                     jint jmode) {
    const char* filename = (const char*) filename_ptr;

    // Override vulkan loading to let us load vulkan ourselves
    if(strstr(filename, "libvulkan.so") != NULL || strcmp(filename, "vulkan") == 0) {
        printf("LWJGL linkerhook: replacing load for %s with custom driver\n", filename);
        return (jlong) mojoexec_acq_vulkan_handle();
    }
    // Load renderer using egl_acquire
    if(strstr(filename, "libGLMojo.so") != NULL) {
        printf("LWJGL linkerhook: replacing OpenGL with renderspec driver\n");
        return (jlong) mojoexec_acq_egl_handle();
    }

    // This hook also serves the task of mitigating a bug: the idea is that since, on Android 10 and
    // earlier, the linker doesn't really do namespace nesting.
    // It is not a problem as most of the libraries are in the launcher path, but when you try to run
    // VulkanMod which loads shaderc outside of the default jni libs directory through this method,
    // it can't load it because the path is not in the allowed paths for the anonymous namesapce.
    // This method fixes the issue by being in libpojavexec, and thus being in the classloader namespace

    int mode = (int)jmode;
    void* handle = dlopen(filename, mode);
    if(handle == NULL && filename != NULL) {
        char pathBuf[512];
        const char* nativeLibDir = getenv("POJAV_NATIVE_LIB_DIR");
        if(nativeLibDir != NULL && nativeLibDir[0] != '\0') {
            const char* baseName = strrchr(filename, '/');
            baseName = baseName ? baseName + 1 : filename;

            // 1. Direct match with basename in nativeLibDir
            snprintf(pathBuf, sizeof(pathBuf), "%s/%s", nativeLibDir, baseName);
            handle = dlopen(pathBuf, mode);

            // 2. Try prefixing "lib" and/or suffixing ".so" if missing
            if(handle == NULL) {
                if(strncmp(baseName, "lib", 3) != 0) {
                    snprintf(pathBuf, sizeof(pathBuf), "%s/lib%s.so", nativeLibDir, baseName);
                    handle = dlopen(pathBuf, mode);
                } else if(strstr(baseName, ".so") == NULL) {
                    snprintf(pathBuf, sizeof(pathBuf), "%s/%s.so", nativeLibDir, baseName);
                    handle = dlopen(pathBuf, mode);
                }
            }

            // 3. Known aliases for LWJGL 3.4.3 / MC 26.3+ bootstrap
            if(handle == NULL && strstr(baseName, "shaderc") != NULL) {
                snprintf(pathBuf, sizeof(pathBuf), "%s/libshaderc.so", nativeLibDir);
                handle = dlopen(pathBuf, mode);
            }
            if(handle == NULL && (strstr(baseName, "spvc") != NULL || strstr(baseName, "spirv-cross") != NULL)) {
                snprintf(pathBuf, sizeof(pathBuf), "%s/libspirv-cross-c-shared.so", nativeLibDir);
                handle = dlopen(pathBuf, mode);
                if (handle == NULL) {
                    snprintf(pathBuf, sizeof(pathBuf), "%s/libspvc.so", nativeLibDir);
                    handle = dlopen(pathBuf, mode);
                }
            }
            if(handle == NULL && (strstr(baseName, "vma") != NULL || strstr(baseName, "lwjgl_vma") != NULL)) {
                snprintf(pathBuf, sizeof(pathBuf), "%s/liblwjgl_vma.so", nativeLibDir);
                handle = dlopen(pathBuf, mode);
                if (handle == NULL) {
                    snprintf(pathBuf, sizeof(pathBuf), "%s/libvma.so", nativeLibDir);
                    handle = dlopen(pathBuf, mode);
                }
            }
            if(handle == NULL && (strstr(baseName, "SDL") != NULL || strstr(baseName, "sdl") != NULL)) {
                snprintf(pathBuf, sizeof(pathBuf), "%s/libSDL3.so", nativeLibDir);
                handle = dlopen(pathBuf, mode);
            }
        }

        // 4. Global process fallback for critical bootstrap libraries
        if(handle == NULL) {
            const char* baseName = strrchr(filename, '/');
            baseName = baseName ? baseName + 1 : filename;
            if(strstr(baseName, "shaderc") != NULL || strstr(baseName, "spvc") != NULL ||
               strstr(baseName, "spirv-cross") != NULL || strstr(baseName, "vma") != NULL ||
               strstr(baseName, "lwjgl_vma") != NULL || strstr(baseName, "SDL") != NULL ||
               strstr(baseName, "sdl") != NULL) {
                handle = dlopen(NULL, RTLD_NOW);
                if (handle != NULL) {
                    printf("LWJGL linkerhook: fell back to global handle for %s\n", filename);
                }
            }
        }
        if(handle != NULL) {
            printf("LWJGL linkerhook: resolved %s from nativeLibDir (%s)\n", filename, pathBuf);
        } else {
            printf("LWJGL linkerhook: dlopen failed for %s: %s\n", filename, dlerror());
        }
    }
    return (jlong) handle;
}

/**
 * Hook ndlsym to intercept glGetString and eglGetProcAddress, ensuring
 * Libre Mobile is identified cleanly in the Minecraft F3 debug overlay.
 */
static jlong ndlsym_bugfix(__attribute__((unused)) JNIEnv *env,
                           __attribute__((unused)) jclass class,
                           jlong handle,
                           jlong name_ptr) {
    const char* name = (const char*) name_ptr;
    if(name != NULL) {
        if(strcmp(name, "glGetString") == 0) {
            return (jlong) libre_glGetString;
        }
        if(strcmp(name, "eglGetProcAddress") == 0) {
            return (jlong) libre_eglGetProcAddress;
        }
    }
    return (jlong) dlsym((void*) (intptr_t) handle, name);
}

/**
 * Install the LWJGL dlopen hook. This allows us to mitigate linker bugs and add custom library overrides.
 */
void installLwjglDlopenHook(JNIEnv *env) {
    LOGI("Installing LWJGL dlopen() and dlsym() hooks");
    jclass dynamicLinkLoader = (*env)->FindClass(env, "org/lwjgl/system/linux/DynamicLinkLoader");
    if(dynamicLinkLoader == NULL) {
        LOGE("Failed to find the target class");
        (*env)->ExceptionClear(env);
        return;
    }
    JNINativeMethod loaderMethods[] = {
            {"ndlopen", "(JI)J", (void*) &ndlopen_bugfix},
            {"ndlsym", "(JJ)J", (void*) &ndlsym_bugfix}
    };
    if((*env)->RegisterNatives(env, dynamicLinkLoader, loaderMethods, 2) != 0) {
        LOGE("Failed to register the hooked methods");
        (*env)->ExceptionClear(env);
    }
}