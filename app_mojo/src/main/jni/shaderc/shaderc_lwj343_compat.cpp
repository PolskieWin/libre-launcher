#include <shaderc/shaderc.h>
#include <android/log.h>
#include <stdbool.h>
#include <stdint.h>

#define COMPAT_TAG "ShadercCompat"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, COMPAT_TAG, __VA_ARGS__)

extern "C" {

__attribute__((visibility("default"), used))
void shaderc_compile_options_set_preserve_bindings(shaderc_compile_options_t options, bool preserve_bindings) {
    if (options != nullptr && preserve_bindings) {
        shaderc_compile_options_set_auto_bind_uniforms(options, false);
        shaderc_compile_options_set_auto_map_locations(options, true);
    }
}

__attribute__((visibility("default"), used))
void shaderc_compile_options_set_max_id_bound(shaderc_compile_options_t options, unsigned int max_id_bound) {
    (void)options;
    (void)max_id_bound;
}

__attribute__((visibility("default"), used))
void shaderc_compile_options_set_vulkan_rules_relaxed(shaderc_compile_options_t options, bool relaxed) {
    (void)options;
    (void)relaxed;
}

// Weak fallback stubs for SPIRV-Tools 2024–2026 validator/optimizer options required by LWJGL 3.4.3+
#define SPV_OPT_BOOL_STUB(fn_name) \
    __attribute__((weak, visibility("default"), used)) \
    void fn_name(void* options, bool val) { (void)options; (void)val; }

#define SPV_OPT_U32_STUB(fn_name) \
    __attribute__((weak, visibility("default"), used)) \
    void fn_name(void* options, uint32_t val) { (void)options; (void)val; }

SPV_OPT_BOOL_STUB(spvValidatorOptionsSetAllowOffsetTextureOperand)
SPV_OPT_BOOL_STUB(spvValidatorOptionsSetAllowVulkan32BitBitwise)
SPV_OPT_BOOL_STUB(spvValidatorOptionsSetBeforeHlslLegalization)
SPV_OPT_BOOL_STUB(spvValidatorOptionsSetFriendlyNames)
SPV_OPT_BOOL_STUB(spvValidatorOptionsSetRelaxBlockLayout)
SPV_OPT_BOOL_STUB(spvValidatorOptionsSetRelaxLogicalPointer)
SPV_OPT_BOOL_STUB(spvValidatorOptionsSetRelaxStoreStruct)
SPV_OPT_BOOL_STUB(spvValidatorOptionsSetScalarBlockLayout)
SPV_OPT_BOOL_STUB(spvValidatorOptionsSetSkipBlockLayout)
SPV_OPT_BOOL_STUB(spvValidatorOptionsSetUniformBufferStandardLayout)
SPV_OPT_BOOL_STUB(spvValidatorOptionsSetWorkgroupScalarBlockLayout)
SPV_OPT_BOOL_STUB(spvValidatorOptionsSetAllowLocalSizeId)
SPV_OPT_BOOL_STUB(spvValidatorOptionsSetAllowSmBuiltins)
SPV_OPT_U32_STUB(spvValidatorOptionsSetUniversalLimit)

SPV_OPT_BOOL_STUB(spvOptimizerOptionsSetRunValidator)
SPV_OPT_BOOL_STUB(spvOptimizerOptionsSetPreserveBindings)
SPV_OPT_BOOL_STUB(spvOptimizerOptionsSetPreserveSpecConstants)
SPV_OPT_U32_STUB(spvOptimizerOptionsSetMaxIdBound)

}

extern "C" {

// Weak fallback stubs for SPIRV-Tools reducer options in LWJGL 3.4.3 SPV$Functions
__attribute__((weak, visibility("default"), used))
void* spvReducerOptionsCreate(void) { return nullptr; }

__attribute__((weak, visibility("default"), used))
void spvReducerOptionsDestroy(void* options) { (void)options; }

__attribute__((weak, visibility("default"), used))
void spvReducerOptionsSetStepLimit(void* options, uint32_t limit) { (void)options; (void)limit; }

__attribute__((weak, visibility("default"), used))
void spvReducerOptionsSetFailOnValidationError(void* options, bool fail) { (void)options; (void)fail; }

__attribute__((weak, visibility("default"), used))
void spvReducerOptionsSetTargetFunction(void* options, uint32_t target) { (void)options; (void)target; }

}

extern "C" {

// Weak fallback stubs for SPIRV-Tools fuzzer options in LWJGL 3.4.3 SPV$Functions (lines 67-71)
__attribute__((weak, visibility("default"), used))
void* spvFuzzerOptionsCreate(void) { return nullptr; }

__attribute__((weak, visibility("default"), used))
void spvFuzzerOptionsDestroy(void* options) { (void)options; }

__attribute__((weak, visibility("default"), used))
void spvFuzzerOptionsEnableReplayValidation(void* options) { (void)options; }

__attribute__((weak, visibility("default"), used))
void spvFuzzerOptionsSetRandomSeed(void* options, uint32_t seed) { (void)options; (void)seed; }

__attribute__((weak, visibility("default"), used))
void spvFuzzerOptionsSetReplayRange(void* options, int32_t range) { (void)options; (void)range; }

__attribute__((weak, visibility("default"), used))
void spvFuzzerOptionsSetShrinkerStepLimit(void* options, uint32_t limit) { (void)options; (void)limit; }

__attribute__((weak, visibility("default"), used))
void spvFuzzerOptionsEnableFuzzerPassValidation(void* options) { (void)options; }

__attribute__((weak, visibility("default"), used))
void spvFuzzerOptionsEnableAllPasses(void* options) { (void)options; }

}
