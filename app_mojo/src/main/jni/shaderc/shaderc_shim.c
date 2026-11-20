#include <stdlib.h>
#include <string.h>
#include <stdint.h>
#include <stdbool.h>
#include <stdio.h>

#define SHADERC_EXPORT __attribute__((visibility("default")))

typedef enum {
    shaderc_target_env_vulkan,
    shaderc_target_env_opengl,
    shaderc_target_env_opengl_compat,
    shaderc_target_env_webgpu,
    shaderc_target_env_default = shaderc_target_env_vulkan
} shaderc_target_env;

typedef enum {
    shaderc_compilation_status_success = 0,
    shaderc_compilation_status_invalid_stage = 1,
    shaderc_compilation_status_compilation_error = 2,
    shaderc_compilation_status_internal_error = 3,
    shaderc_compilation_status_null_result_object = 4,
    shaderc_compilation_status_invalid_assembly = 5,
    shaderc_compilation_status_validation_error = 6,
    shaderc_compilation_status_transformation_error = 7,
    shaderc_compilation_status_configuration_error = 8,
} shaderc_compilation_status;

typedef enum {
    shaderc_source_language_glsl,
    shaderc_source_language_hlsl,
} shaderc_source_language;

typedef enum {
    shaderc_profile_none,
    shaderc_profile_core,
    shaderc_profile_compatibility,
    shaderc_profile_es,
} shaderc_profile;

typedef enum {
    shaderc_optimization_level_zero,
    shaderc_optimization_level_size,
    shaderc_optimization_level_performance,
} shaderc_optimization_level;

struct shaderc_compiler {
    int initialized;
};

struct shaderc_compile_options {
    shaderc_source_language lang;
    shaderc_optimization_level opt_level;
    int version;
    shaderc_profile profile;
    shaderc_target_env target_env;
    uint32_t env_version;
    int spirv_version;
    bool suppress_warnings;
    bool warnings_as_errors;
    bool auto_bind_uniforms;
    bool auto_combined_image_sampler;
    bool hlsl_io_mapping;
    bool hlsl_offsets;
    bool auto_map_locations;
    bool hlsl_functionality1;
    bool hlsl_16bit_types;
    bool vulkan_rules_relaxed;
    bool invert_y;
    bool nan_clamp;
};

struct shaderc_compilation_result {
    shaderc_compilation_status status;
    size_t num_errors;
    size_t num_warnings;
    size_t length;
    char* data;
    char* error_message;
};

typedef struct shaderc_compiler* shaderc_compiler_t;
typedef struct shaderc_compile_options* shaderc_compile_options_t;
typedef struct shaderc_compilation_result* shaderc_compilation_result_t;

SHADERC_EXPORT shaderc_compiler_t shaderc_compiler_initialize(void) {
    struct shaderc_compiler* c = (struct shaderc_compiler*) calloc(1, sizeof(struct shaderc_compiler));
    if (c != NULL) {
        c->initialized = 1;
    }
    return c;
}

SHADERC_EXPORT void shaderc_compiler_release(shaderc_compiler_t compiler) {
    if (compiler != NULL) {
        free(compiler);
    }
}

SHADERC_EXPORT shaderc_compile_options_t shaderc_compile_options_initialize(void) {
    struct shaderc_compile_options* opts = (struct shaderc_compile_options*) calloc(1, sizeof(struct shaderc_compile_options));
    if (opts != NULL) {
        opts->lang = shaderc_source_language_glsl;
        opts->opt_level = shaderc_optimization_level_zero;
        opts->profile = shaderc_profile_none;
        opts->target_env = shaderc_target_env_vulkan;
    }
    return opts;
}

SHADERC_EXPORT shaderc_compile_options_t shaderc_compile_options_clone(const shaderc_compile_options_t options) {
    if (options == NULL) {
        return shaderc_compile_options_initialize();
    }
    struct shaderc_compile_options* clone = (struct shaderc_compile_options*) malloc(sizeof(struct shaderc_compile_options));
    if (clone != NULL) {
        memcpy(clone, options, sizeof(struct shaderc_compile_options));
    }
    return clone;
}

SHADERC_EXPORT void shaderc_compile_options_release(shaderc_compile_options_t options) {
    if (options != NULL) {
        free(options);
    }
}

SHADERC_EXPORT void shaderc_compile_options_add_macro_definition(
    shaderc_compile_options_t options, const char* name, size_t name_length,
    const char* value, size_t value_length) {
    (void) options;
    (void) name;
    (void) name_length;
    (void) value;
    (void) value_length;
}

SHADERC_EXPORT void shaderc_compile_options_set_source_language(
    shaderc_compile_options_t options, int lang) {
    if (options != NULL) {
        options->lang = (shaderc_source_language) lang;
    }
}

SHADERC_EXPORT void shaderc_compile_options_set_generate_debug_info(
    shaderc_compile_options_t options) {
    (void) options;
}

SHADERC_EXPORT void shaderc_compile_options_set_optimization_level(
    shaderc_compile_options_t options, int level) {
    if (options != NULL) {
        options->opt_level = (shaderc_optimization_level) level;
    }
}

SHADERC_EXPORT void shaderc_compile_options_set_forced_version_profile(
    shaderc_compile_options_t options, int version, int profile) {
    if (options != NULL) {
        options->version = version;
        options->profile = (shaderc_profile) profile;
    }
}

SHADERC_EXPORT void shaderc_compile_options_set_include_callbacks(
    shaderc_compile_options_t options, void* resolver, void* result_releaser, void* user_data) {
    (void) options;
    (void) resolver;
    (void) result_releaser;
    (void) user_data;
}

SHADERC_EXPORT void shaderc_compile_options_set_suppress_warnings(
    shaderc_compile_options_t options) {
    if (options != NULL) {
        options->suppress_warnings = true;
    }
}

SHADERC_EXPORT void shaderc_compile_options_set_target_env(
    shaderc_compile_options_t options, int target, uint32_t version) {
    if (options != NULL) {
        options->target_env = (shaderc_target_env) target;
        options->env_version = version;
    }
}

SHADERC_EXPORT void shaderc_compile_options_set_target_spirv(
    shaderc_compile_options_t options, int version) {
    if (options != NULL) {
        options->spirv_version = version;
    }
}

SHADERC_EXPORT void shaderc_compile_options_set_warnings_as_errors(
    shaderc_compile_options_t options) {
    if (options != NULL) {
        options->warnings_as_errors = true;
    }
}

SHADERC_EXPORT void shaderc_compile_options_set_limit(
    shaderc_compile_options_t options, int limit, int value) {
    (void) options;
    (void) limit;
    (void) value;
}

SHADERC_EXPORT void shaderc_compile_options_set_auto_bind_uniforms(
    shaderc_compile_options_t options, bool auto_bind) {
    if (options != NULL) {
        options->auto_bind_uniforms = auto_bind;
    }
}

SHADERC_EXPORT void shaderc_compile_options_set_auto_combined_image_sampler(
    shaderc_compile_options_t options, bool upgrade) {
    if (options != NULL) {
        options->auto_combined_image_sampler = upgrade;
    }
}

SHADERC_EXPORT void shaderc_compile_options_set_hlsl_io_mapping(
    shaderc_compile_options_t options, bool hlsl_iomap) {
    if (options != NULL) {
        options->hlsl_io_mapping = hlsl_iomap;
    }
}

SHADERC_EXPORT void shaderc_compile_options_set_hlsl_offsets(
    shaderc_compile_options_t options, bool hlsl_offsets) {
    if (options != NULL) {
        options->hlsl_offsets = hlsl_offsets;
    }
}

SHADERC_EXPORT void shaderc_compile_options_set_binding_base(
    shaderc_compile_options_t options, int kind, uint32_t base) {
    (void) options;
    (void) kind;
    (void) base;
}

SHADERC_EXPORT void shaderc_compile_options_set_binding_base_for_stage(
    shaderc_compile_options_t options, int shader_kind, int kind, uint32_t base) {
    (void) options;
    (void) shader_kind;
    (void) kind;
    (void) base;
}

SHADERC_EXPORT void shaderc_compile_options_set_preserve_bindings(
    shaderc_compile_options_t options, bool preserve) {
    (void) options;
    (void) preserve;
}

SHADERC_EXPORT void shaderc_compile_options_set_max_id_bound(
    shaderc_compile_options_t options, uint32_t bound) {
    (void) options;
    (void) bound;
}

SHADERC_EXPORT void shaderc_compile_options_set_auto_map_locations(
    shaderc_compile_options_t options, bool auto_map) {
    if (options != NULL) {
        options->auto_map_locations = auto_map;
    }
}

SHADERC_EXPORT void shaderc_compile_options_set_hlsl_register_set_and_binding_for_stage(
    shaderc_compile_options_t options, int shader_kind, const char* reg, const char* set, const char* binding) {
    (void) options;
    (void) shader_kind;
    (void) reg;
    (void) set;
    (void) binding;
}

SHADERC_EXPORT void shaderc_compile_options_set_hlsl_register_set_and_binding(
    shaderc_compile_options_t options, const char* reg, const char* set, const char* binding) {
    (void) options;
    (void) reg;
    (void) set;
    (void) binding;
}

SHADERC_EXPORT void shaderc_compile_options_set_hlsl_functionality1(
    shaderc_compile_options_t options, bool enable) {
    if (options != NULL) {
        options->hlsl_functionality1 = enable;
    }
}

SHADERC_EXPORT void shaderc_compile_options_set_hlsl_16bit_types(
    shaderc_compile_options_t options, bool enable) {
    if (options != NULL) {
        options->hlsl_16bit_types = enable;
    }
}

SHADERC_EXPORT void shaderc_compile_options_set_vulkan_rules_relaxed(
    shaderc_compile_options_t options, bool enable) {
    if (options != NULL) {
        options->vulkan_rules_relaxed = enable;
    }
}

SHADERC_EXPORT void shaderc_compile_options_set_invert_y(
    shaderc_compile_options_t options, bool enable) {
    if (options != NULL) {
        options->invert_y = enable;
    }
}

SHADERC_EXPORT void shaderc_compile_options_set_nan_clamp(
    shaderc_compile_options_t options, bool enable) {
    if (options != NULL) {
        options->nan_clamp = enable;
    }
}

static shaderc_compilation_result_t create_empty_result(void) {
    struct shaderc_compilation_result* res = (struct shaderc_compilation_result*) calloc(1, sizeof(struct shaderc_compilation_result));
    if (res != NULL) {
        res->status = shaderc_compilation_status_success;
        res->length = 0;
        res->data = (char*) calloc(1, 4);
        res->error_message = (char*) calloc(1, 1);
    }
    return res;
}

SHADERC_EXPORT shaderc_compilation_result_t shaderc_compile_into_spv(
    const shaderc_compiler_t compiler, const char* source_text, size_t source_text_size,
    int shader_kind, const char* input_file_name, const char* entry_point_name,
    const shaderc_compile_options_t additional_options) {
    (void) compiler;
    (void) source_text;
    (void) source_text_size;
    (void) shader_kind;
    (void) input_file_name;
    (void) entry_point_name;
    (void) additional_options;
    return create_empty_result();
}

SHADERC_EXPORT shaderc_compilation_result_t shaderc_compile_into_spv_assembly(
    const shaderc_compiler_t compiler, const char* source_text, size_t source_text_size,
    int shader_kind, const char* input_file_name, const char* entry_point_name,
    const shaderc_compile_options_t additional_options) {
    (void) compiler;
    (void) source_text;
    (void) source_text_size;
    (void) shader_kind;
    (void) input_file_name;
    (void) entry_point_name;
    (void) additional_options;
    return create_empty_result();
}

SHADERC_EXPORT shaderc_compilation_result_t shaderc_compile_into_preprocessed_text(
    const shaderc_compiler_t compiler, const char* source_text, size_t source_text_size,
    int shader_kind, const char* input_file_name, const char* entry_point_name,
    const shaderc_compile_options_t additional_options) {
    (void) compiler;
    (void) source_text;
    (void) source_text_size;
    (void) shader_kind;
    (void) input_file_name;
    (void) entry_point_name;
    (void) additional_options;
    return create_empty_result();
}

SHADERC_EXPORT shaderc_compilation_result_t shaderc_assemble_into_spv(
    const shaderc_compiler_t compiler, const char* source_assembly,
    size_t source_assembly_size, const shaderc_compile_options_t additional_options) {
    (void) compiler;
    (void) source_assembly;
    (void) source_assembly_size;
    (void) additional_options;
    return create_empty_result();
}

SHADERC_EXPORT void shaderc_result_release(shaderc_compilation_result_t result) {
    if (result != NULL) {
        if (result->data != NULL) free(result->data);
        if (result->error_message != NULL) free(result->error_message);
        free(result);
    }
}

SHADERC_EXPORT size_t shaderc_result_get_length(const shaderc_compilation_result_t result) {
    return result != NULL ? result->length : 0;
}

SHADERC_EXPORT size_t shaderc_result_get_num_warnings(const shaderc_compilation_result_t result) {
    return result != NULL ? result->num_warnings : 0;
}

SHADERC_EXPORT size_t shaderc_result_get_num_errors(const shaderc_compilation_result_t result) {
    return result != NULL ? result->num_errors : 0;
}

SHADERC_EXPORT shaderc_compilation_status shaderc_result_get_compilation_status(const shaderc_compilation_result_t result) {
    return result != NULL ? result->status : shaderc_compilation_status_null_result_object;
}

SHADERC_EXPORT const char* shaderc_result_get_bytes(const shaderc_compilation_result_t result) {
    return (result != NULL && result->data != NULL) ? result->data : "";
}

SHADERC_EXPORT const char* shaderc_result_get_error_message(const shaderc_compilation_result_t result) {
    return (result != NULL && result->error_message != NULL) ? result->error_message : "";
}

SHADERC_EXPORT void shaderc_get_spv_version(unsigned int* version, unsigned int* revision) {
    if (version != NULL) *version = 0x00010600;
    if (revision != NULL) *revision = 1;
}

SHADERC_EXPORT bool shaderc_parse_version_profile(const char* str, int* version, int* profile) {
    if (str == NULL || version == NULL || profile == NULL) return false;
    int v = 0;
    const char* p = str;
    while (*p >= '0' && *p <= '9') {
        v = v * 10 + (*p - '0');
        p++;
    }
    *version = v;
    if (strcmp(p, "core") == 0) {
        *profile = (int) shaderc_profile_core;
    } else if (strcmp(p, "compatibility") == 0) {
        *profile = (int) shaderc_profile_compatibility;
    } else if (strcmp(p, "es") == 0) {
        *profile = (int) shaderc_profile_es;
    } else {
        *profile = (int) shaderc_profile_none;
    }
    return true;
}
