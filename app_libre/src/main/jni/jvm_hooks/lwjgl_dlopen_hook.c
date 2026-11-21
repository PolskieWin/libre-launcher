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

static void* s_shaderc_handle = NULL;
static void* s_spvc_handle = NULL;

static const char* mojo_get_native_lib_dir(void) {
    const char* nativeLibDir = getenv("POJAV_NATIVE_LIB_DIR");
    if(nativeLibDir != NULL && nativeLibDir[0] != '\0') {
        return nativeLibDir;
    }
    if(mojoexec_native_dir != NULL && mojoexec_native_dir[0] != '\0') {
        return mojoexec_native_dir;
    }
    return NULL;
}

static void* mojo_try_dlopen(const char* path, int mode) {
    if(path == NULL || path[0] == '\0') return NULL;
    void* h = dlopen(path, mode);
    if(h == NULL) {
        h = mojoexec_dlopen(path, mode);
    }
    return h;
}

static void* mojo_load_shaderc(int mode) {
    if(s_shaderc_handle != NULL) return s_shaderc_handle;
    const char* nativeLibDir = mojo_get_native_lib_dir();
    char pathBuf[512];
    void* handle = NULL;

    if(nativeLibDir != NULL && nativeLibDir[0] != '\0') {
        snprintf(pathBuf, sizeof(pathBuf), "%s/libshaderc.so", nativeLibDir);
        handle = mojo_try_dlopen(pathBuf, mode | RTLD_GLOBAL);
    }
    if(handle == NULL) {
        handle = mojo_try_dlopen("libshaderc.so", mode | RTLD_GLOBAL);
    }
    if(handle != NULL) {
        s_shaderc_handle = handle;
    }
    return handle;
}

static void* mojo_load_spvc(int mode) {
    if(s_spvc_handle != NULL) return s_spvc_handle;
    const char* nativeLibDir = mojo_get_native_lib_dir();
    char pathBuf[512];
    void* handle = NULL;

    if(nativeLibDir != NULL && nativeLibDir[0] != '\0') {
        snprintf(pathBuf, sizeof(pathBuf), "%s/libspirv-cross-c-shared.so", nativeLibDir);
        handle = mojo_try_dlopen(pathBuf, mode | RTLD_GLOBAL);
        if(handle == NULL) {
            snprintf(pathBuf, sizeof(pathBuf), "%s/libspvc.so", nativeLibDir);
            handle = mojo_try_dlopen(pathBuf, mode | RTLD_GLOBAL);
        }
    }
    if(handle == NULL) {
        handle = mojo_try_dlopen("libspirv-cross-c-shared.so", mode | RTLD_GLOBAL);
        if(handle == NULL) {
            handle = mojo_try_dlopen("libspvc.so", mode | RTLD_GLOBAL);
        }
    }
    if(handle != NULL) {
        s_spvc_handle = handle;
    }
    return handle;
}

/**
 * Basically a verbatim implementation of ndlopen(), found at
 * https://github.com/PojavLauncherTeam/lwjgl3/blob/3.3.1/modules/lwjgl/core/src/generated/c/linux/org_lwjgl_system_linux_DynamicLinkLoader.c#L11
 * with robust active-namespace loading for shaderc and spvc.
 */
static jlong ndlopen_bugfix(__attribute__((unused)) JNIEnv *env,
                     __attribute__((unused)) jclass class,
                     jlong filename_ptr,
                     jint jmode) {
    const char* filename = (const char*) filename_ptr;
    if(filename == NULL) return 0;

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

    int mode = (int)jmode;
    const char* baseName = strrchr(filename, '/');
    baseName = baseName ? baseName + 1 : filename;

    // Handle shaderc: open in active linker namespace and cache handle
    if(strstr(baseName, "shaderc") != NULL) {
        void* handle = mojo_try_dlopen(filename, mode | RTLD_GLOBAL);
        if(handle == NULL) {
            handle = mojo_load_shaderc(mode);
        } else {
            s_shaderc_handle = handle;
        }
        if(handle != NULL) {
            printf("LWJGL linkerhook: resolved shaderc handle %p for %s\n", handle, filename);
            return (jlong) handle;
        }
        printf("LWJGL linkerhook: failed to load shaderc for %s: %s\n", filename, dlerror());
        return 0;
    }

    // Handle spvc: open in active linker namespace and cache handle
    if(strstr(baseName, "spvc") != NULL || strstr(baseName, "spirv-cross") != NULL) {
        void* handle = mojo_try_dlopen(filename, mode | RTLD_GLOBAL);
        if(handle == NULL) {
            handle = mojo_load_spvc(mode);
        } else {
            s_spvc_handle = handle;
        }
        if(handle != NULL) {
            printf("LWJGL linkerhook: resolved spvc handle %p for %s\n", handle, filename);
            return (jlong) handle;
        }
        printf("LWJGL linkerhook: failed to load spvc for %s: %s\n", filename, dlerror());
        return 0;
    }

    void* handle = mojo_try_dlopen(filename, mode);
    if(handle == NULL) {
        char pathBuf[512];
        const char* nativeLibDir = mojo_get_native_lib_dir();
        if(nativeLibDir != NULL && nativeLibDir[0] != '\0') {
            // 1. Direct match with basename in nativeLibDir
            snprintf(pathBuf, sizeof(pathBuf), "%s/%s", nativeLibDir, baseName);
            handle = mojo_try_dlopen(pathBuf, mode);

            // 2. Try prefixing "lib" and/or suffixing ".so" if missing
            if(handle == NULL) {
                if(strncmp(baseName, "lib", 3) != 0) {
                    snprintf(pathBuf, sizeof(pathBuf), "%s/lib%s.so", nativeLibDir, baseName);
                    handle = mojo_try_dlopen(pathBuf, mode);
                } else if(strstr(baseName, ".so") == NULL) {
                    snprintf(pathBuf, sizeof(pathBuf), "%s/%s.so", nativeLibDir, baseName);
                    handle = mojo_try_dlopen(pathBuf, mode);
                }
            }

            // 3. Known aliases for other libraries (vma, SDL)
            if(handle == NULL && (strstr(baseName, "vma") != NULL || strstr(baseName, "lwjgl_vma") != NULL)) {
                snprintf(pathBuf, sizeof(pathBuf), "%s/liblwjgl_vma.so", nativeLibDir);
                handle = mojo_try_dlopen(pathBuf, mode);
                if(handle == NULL) {
                    snprintf(pathBuf, sizeof(pathBuf), "%s/libvma.so", nativeLibDir);
                    handle = mojo_try_dlopen(pathBuf, mode);
                }
            }
            if(handle == NULL && (strstr(baseName, "SDL") != NULL || strstr(baseName, "sdl") != NULL)) {
                snprintf(pathBuf, sizeof(pathBuf), "%s/libSDL3.so", nativeLibDir);
                handle = mojo_try_dlopen(pathBuf, mode);
            }
        }

        // 4. Global process fallback for other bootstrap libraries (excluding shaderc and spvc)
        if(handle == NULL) {
            if(strstr(baseName, "vma") != NULL || strstr(baseName, "lwjgl_vma") != NULL ||
               strstr(baseName, "SDL") != NULL || strstr(baseName, "sdl") != NULL) {
                handle = dlopen(NULL, RTLD_NOW);
                if(handle != NULL) {
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
 * Libre Mobile is identified cleanly in the Minecraft F3 debug overlay,
 * and ensuring shaderc and spvc symbols always resolve cleanly.
 */

// Vulkan F3 driverInfo trimmer: shortens "Vulkan 1.4.363 turnip Mesa driver (...)" to "Vulkan 1.4.363"
typedef struct {
    int32_t sType;
    void* pNext;
} LibreVkBaseOutStructure;

typedef struct {
    int32_t sType;
    void* pNext;
    uint32_t driverID;
    char driverName[256];
    char driverInfo[256];
    uint8_t conformanceVersion[4];
} LibreVkPhysicalDeviceDriverProperties;

typedef void (*PFN_vkVoidFunction_libre)(void);
typedef PFN_vkVoidFunction_libre (*PFN_vkGetInstanceProcAddr_libre)(void* instance, const char* pName);
typedef void (*PFN_vkGetPhysicalDeviceProperties2_libre)(void* physicalDevice, void* pProperties);

static PFN_vkGetInstanceProcAddr_libre s_orig_vkGetInstanceProcAddr = NULL;
static PFN_vkGetPhysicalDeviceProperties2_libre s_orig_vkGetPhysicalDeviceProperties2 = NULL;
static PFN_vkGetPhysicalDeviceProperties2_libre s_orig_vkGetPhysicalDeviceProperties2KHR = NULL;

static void libre_trim_vk_driver_props(void* pProperties) {
    if (pProperties == NULL) return;
    LibreVkBaseOutStructure* cur = ((LibreVkBaseOutStructure*)pProperties)->pNext;
    while (cur != NULL) {
        // VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_DRIVER_PROPERTIES = 1000196001
        if (cur->sType == 1000196001) {
            LibreVkPhysicalDeviceDriverProperties* dp = (LibreVkPhysicalDeviceDriverProperties*)cur;
            dp->driverInfo[0] = '\0';
        }
        cur = cur->pNext;
    }
}

static void libre_vkGetPhysicalDeviceProperties2(void* physicalDevice, void* pProperties) {
    if (s_orig_vkGetPhysicalDeviceProperties2) {
        s_orig_vkGetPhysicalDeviceProperties2(physicalDevice, pProperties);
        libre_trim_vk_driver_props(pProperties);
    }
}

static void libre_vkGetPhysicalDeviceProperties2KHR(void* physicalDevice, void* pProperties) {
    if (s_orig_vkGetPhysicalDeviceProperties2KHR) {
        s_orig_vkGetPhysicalDeviceProperties2KHR(physicalDevice, pProperties);
        libre_trim_vk_driver_props(pProperties);
    }
}

static PFN_vkVoidFunction_libre libre_vkGetInstanceProcAddr(void* instance, const char* pName) {
    if (s_orig_vkGetInstanceProcAddr == NULL) return NULL;
    PFN_vkVoidFunction_libre fn = s_orig_vkGetInstanceProcAddr(instance, pName);
    if (fn != NULL && pName != NULL) {
        if (strcmp(pName, "vkGetPhysicalDeviceProperties2") == 0) {
            s_orig_vkGetPhysicalDeviceProperties2 = (PFN_vkGetPhysicalDeviceProperties2_libre)fn;
            return (PFN_vkVoidFunction_libre)libre_vkGetPhysicalDeviceProperties2;
        }
        if (strcmp(pName, "vkGetPhysicalDeviceProperties2KHR") == 0) {
            s_orig_vkGetPhysicalDeviceProperties2KHR = (PFN_vkGetPhysicalDeviceProperties2_libre)fn;
            return (PFN_vkVoidFunction_libre)libre_vkGetPhysicalDeviceProperties2KHR;
        }
    }
    return fn;
}

static jlong ndlsym_bugfix(__attribute__((unused)) JNIEnv *env,
                           __attribute__((unused)) jclass class,
                           jlong handle_val,
                           jlong name_ptr) {
    const char* name = (const char*) name_ptr;
    if(name == NULL) return 0;

    if(strcmp(name, "glGetString") == 0) {
        return (jlong) libre_glGetString;
    }
    if(strcmp(name, "eglGetProcAddress") == 0) {
        return (jlong) libre_eglGetProcAddress;
    }

    void* handle = (void*) (intptr_t) handle_val;
    void* sym = NULL;

    // Check target handle first if non-null and not RTLD_DEFAULT
    if(handle != NULL && handle != RTLD_DEFAULT) {
        sym = dlsym(handle, name);
        if(sym != NULL) return (jlong) sym;
    }

    // Shaderc function resolution fallback
    if(strncmp(name, "shaderc_", 8) == 0) {
        if(s_shaderc_handle != NULL) {
            sym = dlsym(s_shaderc_handle, name);
        }
        if(sym == NULL) {
            s_shaderc_handle = mojo_load_shaderc(RTLD_NOW);
            if(s_shaderc_handle != NULL) {
                sym = dlsym(s_shaderc_handle, name);
            }
        }
        if(sym == NULL) {
            sym = dlsym(RTLD_DEFAULT, name);
        }
        if(sym != NULL) {
            return (jlong) sym;
        }
        const char* err = dlerror();
        LOGE("LWJGL linkerhook: shaderc symbol lookup failed for %s (%s)", name, err ? err : "not found");
        printf("LWJGL linkerhook: shaderc symbol lookup failed for %s (%s)\n", name, err ? err : "not found");
    }

    // SPVC / SPIRV-Cross function resolution fallback
    if(strncmp(name, "spvc_", 5) == 0 || strncmp(name, "spirv_", 6) == 0) {
        if(s_spvc_handle != NULL) {
            sym = dlsym(s_spvc_handle, name);
        }
        if(sym == NULL) {
            s_spvc_handle = mojo_load_spvc(RTLD_NOW);
            if(s_spvc_handle != NULL) {
                sym = dlsym(s_spvc_handle, name);
            }
        }
        if(sym == NULL) {
            sym = dlsym(RTLD_DEFAULT, name);
        }
        if(sym != NULL) {
            return (jlong) sym;
        }
        const char* err = dlerror();
        LOGE("LWJGL linkerhook: spvc symbol lookup failed for %s (%s)", name, err ? err : "not found");
        printf("LWJGL linkerhook: spvc symbol lookup failed for %s (%s)\n", name, err ? err : "not found");
    }

    // Global handle fallback
    return (jlong) dlsym(handle ? handle : RTLD_DEFAULT, name);
}

/**
 * Install the LWJGL dlopen hook. This allows us to mitigate linker bugs and add custom library overrides.
 */

__attribute__((visibility("default"), used)) void setutxent(void) {}
__attribute__((visibility("default"), used)) void* getutxent(void) { return NULL; }
__attribute__((visibility("default"), used)) void endutxent(void) {}

void installLwjglDlopenHook(JNIEnv *env) {
    // Promote libmojort.so to RTLD_GLOBAL so Java 25 FFM SymbolLookup.defaultLookup() finds setutxent/getutxent/endutxent
    dlopen("libmojort.so", RTLD_NOLOAD | RTLD_GLOBAL);
    const char* libDir = mojo_get_native_lib_dir();
    if (libDir != NULL && libDir[0] != '\0') {
        char mojortPath[512];
        snprintf(mojortPath, sizeof(mojortPath), "%s/libmojort.so", libDir);
        dlopen(mojortPath, RTLD_NOLOAD | RTLD_GLOBAL);
        mojoexec_dlopen(mojortPath, RTLD_NOW | RTLD_GLOBAL);
        char shimPath[512];
        snprintf(shimPath, sizeof(shimPath), "%s/libutxent_shim.so", libDir);
        dlopen(shimPath, RTLD_NOW | RTLD_GLOBAL);
        mojoexec_dlopen(shimPath, RTLD_NOW | RTLD_GLOBAL);
    }

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