#include <shaderc/shaderc.h>
#include <android/log.h>
#include <stdbool.h>
#include <stdint.h>

#define COMPAT_TAG "ShadercCompat"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, COMPAT_TAG, __VA_ARGS__)

extern "C" {

/**
 * Sets whether the compiler should preserve all bindings, even when those
 * bindings are not used. Required by LWJGL 3.4.3+ and modern Minecraft 26.3+ pipelines.
 * In NDK shaderc, delegates to disabling auto-binding of uniforms and ensuring auto-mapping
 * locations is preserved without crashing.
 */
__attribute__((visibility("default"), used))
void shaderc_compile_options_set_preserve_bindings(shaderc_compile_options_t options, bool preserve_bindings) {
    if (options != nullptr) {
        if (preserve_bindings) {
            shaderc_compile_options_set_auto_bind_uniforms(options, false);
            shaderc_compile_options_set_auto_map_locations(options, true);
        }
    }
}

/**
 * Sets the maximum value for any ID generated in the SPIR-V module.
 * Required by LWJGL 3.4.3+ Shaderc$Functions.
 */
__attribute__((visibility("default"), used))
void shaderc_compile_options_set_max_id_bound(shaderc_compile_options_t options, unsigned int max_id_bound) {
    (void)options;
    (void)max_id_bound;
}

/**
 * Sets whether the compiler should relax Vulkan rules for SPIR-V validation.
 * Required by LWJGL 3.4.3+ Shaderc$Functions.
 */
__attribute__((visibility("default"), used))
void shaderc_compile_options_set_vulkan_rules_relaxed(shaderc_compile_options_t options, bool relaxed) {
    (void)options;
    (void)relaxed;
}

}
