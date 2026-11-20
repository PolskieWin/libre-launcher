#include <stdlib.h>
#include <string.h>
#include <stdint.h>
#include <stdbool.h>

#define SPVC_EXPORT __attribute__((visibility("default")))

typedef enum spvc_result {
    SPVC_SUCCESS = 0,
    SPVC_ERROR_INVALID_PARAMETER = -1,
    SPVC_ERROR_INVALID_ARGUMENT = -2,
    SPVC_ERROR_OUT_OF_MEMORY = -3,
    SPVC_ERROR_INTERNAL_ERROR = -4,
    SPVC_ERROR_UNSUPPORTED_ABI = -5
} spvc_result;

SPVC_EXPORT void spvc_get_version(unsigned int* major, unsigned int* minor, unsigned int* patch) {
    if (major) *major = 0;
    if (minor) *minor = 57;
    if (patch) *patch = 0;
}

SPVC_EXPORT const char* spvc_get_commit_revision_and_timestamp(void) {
    return "spvc-libre-launcher";
}

SPVC_EXPORT void spvc_msl_vertex_attribute_init(void* attr) { if (attr) memset(attr, 0, 32); }
SPVC_EXPORT void spvc_msl_shader_interface_var_init(void* var) { if (var) memset(var, 0, 32); }
SPVC_EXPORT void spvc_msl_shader_input_init(void* input) { if (input) memset(input, 0, 32); }
SPVC_EXPORT void spvc_msl_shader_interface_var_init_2(void* var) { if (var) memset(var, 0, 32); }
SPVC_EXPORT void spvc_msl_resource_binding_init(void* b) { if (b) memset(b, 0, 32); }
SPVC_EXPORT void spvc_msl_resource_binding_init_2(void* b) { if (b) memset(b, 0, 32); }
SPVC_EXPORT unsigned int spvc_msl_get_aux_buffer_struct_version(void) { return 1; }
SPVC_EXPORT void spvc_msl_constexpr_sampler_init(void* s) { if (s) memset(s, 0, 32); }
SPVC_EXPORT void spvc_msl_sampler_ycbcr_conversion_init(void* conv) { if (conv) memset(conv, 0, 32); }
SPVC_EXPORT void spvc_hlsl_resource_binding_init(void* b) { if (b) memset(b, 0, 32); }

SPVC_EXPORT spvc_result spvc_context_create(void** context) {
    if (!context) return SPVC_ERROR_INVALID_ARGUMENT;
    *context = calloc(1, 16);
    return SPVC_SUCCESS;
}

SPVC_EXPORT void spvc_context_destroy(void* context) {
    if (context) free(context);
}

SPVC_EXPORT void spvc_context_release_allocations(void* context) { (void) context; }

SPVC_EXPORT const char* spvc_context_get_last_error_string(void* context) {
    (void) context;
    return "";
}

SPVC_EXPORT void spvc_context_set_error_callback(void* context, void* cb, void* userdata) {
    (void) context; (void) cb; (void) userdata;
}

SPVC_EXPORT spvc_result spvc_context_parse_spirv(void* context, const uint32_t* spirv, size_t word_count, void** parsed_ir) {
    (void) context; (void) spirv; (void) word_count;
    if (parsed_ir) *parsed_ir = (void*) 1;
    return SPVC_SUCCESS;
}

SPVC_EXPORT spvc_result spvc_context_create_compiler(void* context, int backend, void* parsed_ir, int mode, void** compiler) {
    (void) context; (void) backend; (void) parsed_ir; (void) mode;
    if (compiler) *compiler = (void*) 1;
    return SPVC_SUCCESS;
}

SPVC_EXPORT uint32_t spvc_compiler_get_current_id_bound(void* compiler) { (void) compiler; return 0; }

SPVC_EXPORT spvc_result spvc_compiler_create_compiler_options(void* compiler, void** options) {
    (void) compiler;
    if (options) *options = (void*) 1;
    return SPVC_SUCCESS;
}

SPVC_EXPORT spvc_result spvc_compiler_options_set_bool(void* options, int opt, bool val) {
    (void) options; (void) opt; (void) val;
    return SPVC_SUCCESS;
}

SPVC_EXPORT spvc_result spvc_compiler_options_set_uint(void* options, int opt, unsigned int val) {
    (void) options; (void) opt; (void) val;
    return SPVC_SUCCESS;
}

SPVC_EXPORT spvc_result spvc_compiler_install_compiler_options(void* compiler, void* options) {
    (void) compiler; (void) options;
    return SPVC_SUCCESS;
}

SPVC_EXPORT spvc_result spvc_compiler_compile(void* compiler, const char** source) {
    (void) compiler;
    if (source) *source = "";
    return SPVC_SUCCESS;
}

SPVC_EXPORT spvc_result spvc_compiler_add_header_line(void* compiler, const char* line) {
    (void) compiler; (void) line;
    return SPVC_SUCCESS;
}

SPVC_EXPORT spvc_result spvc_compiler_require_extension(void* compiler, const char* ext) {
    (void) compiler; (void) ext;
    return SPVC_SUCCESS;
}

SPVC_EXPORT size_t spvc_compiler_get_num_required_extensions(void* compiler) { (void) compiler; return 0; }
SPVC_EXPORT const char* spvc_compiler_get_required_extension(void* compiler, size_t index) { (void) compiler; (void) index; return ""; }

SPVC_EXPORT spvc_result spvc_compiler_flatten_buffer_block(void* compiler, uint32_t id) {
    (void) compiler; (void) id;
    return SPVC_SUCCESS;
}

SPVC_EXPORT bool spvc_compiler_variable_is_depth_or_compare(void* compiler, uint32_t id) { (void) compiler; (void) id; return false; }
SPVC_EXPORT spvc_result spvc_compiler_mask_stage_output_by_location(void* compiler, uint32_t loc, uint32_t comp) {
    (void) compiler; (void) loc; (void) comp;
    return SPVC_SUCCESS;
}
SPVC_EXPORT spvc_result spvc_compiler_mask_stage_output_by_builtin(void* compiler, int builtin) {
    (void) compiler; (void) builtin;
    return SPVC_SUCCESS;
}

SPVC_EXPORT spvc_result spvc_compiler_hlsl_set_root_constants_layout(void* compiler, const void* layout, size_t num_layouts) {
    (void) compiler; (void) layout; (void) num_layouts;
    return SPVC_SUCCESS;
}
SPVC_EXPORT spvc_result spvc_compiler_hlsl_add_vertex_attribute_remap(void* compiler, const void* remap, size_t remaps) {
    (void) compiler; (void) remap; (void) remaps;
    return SPVC_SUCCESS;
}
SPVC_EXPORT uint32_t spvc_compiler_hlsl_remap_num_workgroups_builtin(void* compiler) { (void) compiler; return 0; }
SPVC_EXPORT spvc_result spvc_compiler_hlsl_set_resource_binding_flags(void* compiler, unsigned int flags) {
    (void) compiler; (void) flags;
    return SPVC_SUCCESS;
}
SPVC_EXPORT spvc_result spvc_compiler_hlsl_add_resource_binding(void* compiler, const void* b) {
    (void) compiler; (void) b;
    return SPVC_SUCCESS;
}
SPVC_EXPORT bool spvc_compiler_hlsl_is_resource_used(void* compiler, int model, unsigned int set, unsigned int binding) {
    (void) compiler; (void) model; (void) set; (void) binding;
    return false;
}

SPVC_EXPORT bool spvc_compiler_msl_is_rasterization_disabled(void* compiler) { (void) compiler; return false; }
SPVC_EXPORT bool spvc_compiler_msl_needs_swizzle_buffer(void* compiler) { (void) compiler; return false; }
SPVC_EXPORT bool spvc_compiler_msl_needs_buffer_size_buffer(void* compiler) { (void) compiler; return false; }
SPVC_EXPORT bool spvc_compiler_msl_needs_output_buffer(void* compiler) { (void) compiler; return false; }
SPVC_EXPORT bool spvc_compiler_msl_needs_patch_output_buffer(void* compiler) { (void) compiler; return false; }
SPVC_EXPORT bool spvc_compiler_msl_needs_input_threadgroup_mem(void* compiler) { (void) compiler; return false; }
SPVC_EXPORT spvc_result spvc_compiler_msl_add_vertex_attribute(void* compiler, const void* va) { (void) compiler; (void) va; return SPVC_SUCCESS; }
SPVC_EXPORT spvc_result spvc_compiler_msl_add_resource_binding(void* compiler, const void* rb) { (void) compiler; (void) rb; return SPVC_SUCCESS; }
SPVC_EXPORT spvc_result spvc_compiler_msl_add_resource_binding_2(void* compiler, const void* rb) { (void) compiler; (void) rb; return SPVC_SUCCESS; }
SPVC_EXPORT spvc_result spvc_compiler_msl_add_shader_input(void* compiler, const void* si) { (void) compiler; (void) si; return SPVC_SUCCESS; }
SPVC_EXPORT spvc_result spvc_compiler_msl_add_shader_input_2(void* compiler, const void* si) { (void) compiler; (void) si; return SPVC_SUCCESS; }
SPVC_EXPORT spvc_result spvc_compiler_msl_add_shader_output(void* compiler, const void* so) { (void) compiler; (void) so; return SPVC_SUCCESS; }
SPVC_EXPORT spvc_result spvc_compiler_msl_add_shader_output_2(void* compiler, const void* so) { (void) compiler; (void) so; return SPVC_SUCCESS; }
SPVC_EXPORT spvc_result spvc_compiler_msl_add_discrete_descriptor_set(void* compiler, unsigned int desc_set) { (void) compiler; (void) desc_set; return SPVC_SUCCESS; }
SPVC_EXPORT spvc_result spvc_compiler_msl_set_argument_buffer_device_address_space(void* compiler, unsigned int desc_set, bool dev_addr) {
    (void) compiler; (void) desc_set; (void) dev_addr;
    return SPVC_SUCCESS;
}
SPVC_EXPORT bool spvc_compiler_msl_is_vertex_attribute_used(void* compiler, unsigned int loc) { (void) compiler; (void) loc; return false; }
SPVC_EXPORT bool spvc_compiler_msl_is_shader_input_used(void* compiler, unsigned int loc) { (void) compiler; (void) loc; return false; }
SPVC_EXPORT bool spvc_compiler_msl_is_shader_output_used(void* compiler, unsigned int loc) { (void) compiler; (void) loc; return false; }
SPVC_EXPORT bool spvc_compiler_msl_is_resource_used(void* compiler, int model, unsigned int set, unsigned int binding) {
    (void) compiler; (void) model; (void) set; (void) binding;
    return false;
}
SPVC_EXPORT spvc_result spvc_compiler_msl_remap_constexpr_sampler(void* compiler, uint32_t id, const void* s) { (void) compiler; (void) id; (void) s; return SPVC_SUCCESS; }
SPVC_EXPORT spvc_result spvc_compiler_msl_remap_constexpr_sampler_by_binding(void* compiler, unsigned int desc_set, unsigned int binding, const void* s) {
    (void) compiler; (void) desc_set; (void) binding; (void) s;
    return SPVC_SUCCESS;
}
SPVC_EXPORT spvc_result spvc_compiler_msl_remap_constexpr_sampler_ycbcr(void* compiler, uint32_t id, const void* s, const void* conv) {
    (void) compiler; (void) id; (void) s; (void) conv;
    return SPVC_SUCCESS;
}
SPVC_EXPORT spvc_result spvc_compiler_msl_remap_constexpr_sampler_by_binding_ycbcr(void* compiler, unsigned int desc_set, unsigned int binding, const void* s, const void* conv) {
    (void) compiler; (void) desc_set; (void) binding; (void) s; (void) conv;
    return SPVC_SUCCESS;
}
SPVC_EXPORT spvc_result spvc_compiler_msl_set_fragment_output_components(void* compiler, unsigned int mask, unsigned int count) {
    (void) compiler; (void) mask; (void) count;
    return SPVC_SUCCESS;
}
SPVC_EXPORT unsigned int spvc_compiler_msl_get_automatic_resource_binding(void* compiler, uint32_t id) { (void) compiler; (void) id; return 0; }
SPVC_EXPORT unsigned int spvc_compiler_msl_get_automatic_resource_binding_secondary(void* compiler, uint32_t id) { (void) compiler; (void) id; return 0; }
SPVC_EXPORT spvc_result spvc_compiler_msl_add_dynamic_buffer(void* compiler, unsigned int desc_set, unsigned int binding, unsigned int index) {
    (void) compiler; (void) desc_set; (void) binding; (void) index;
    return SPVC_SUCCESS;
}
SPVC_EXPORT spvc_result spvc_compiler_msl_add_inline_uniform_block(void* compiler, unsigned int desc_set, unsigned int binding) {
    (void) compiler; (void) desc_set; (void) binding;
    return SPVC_SUCCESS;
}
SPVC_EXPORT spvc_result spvc_compiler_msl_set_combined_sampler_suffix(void* compiler, const char* suffix) { (void) compiler; (void) suffix; return SPVC_SUCCESS; }
SPVC_EXPORT const char* spvc_compiler_msl_get_combined_sampler_suffix(void* compiler) { (void) compiler; return ""; }

SPVC_EXPORT spvc_result spvc_compiler_get_active_interface_variables(void* compiler, const uint32_t** vars, size_t* num_vars) {
    (void) compiler;
    if (vars) *vars = NULL;
    if (num_vars) *num_vars = 0;
    return SPVC_SUCCESS;
}
SPVC_EXPORT spvc_result spvc_compiler_set_enabled_interface_variables(void* compiler, const uint32_t* vars, size_t num_vars) {
    (void) compiler; (void) vars; (void) num_vars;
    return SPVC_SUCCESS;
}
SPVC_EXPORT spvc_result spvc_compiler_create_shader_resources(void* compiler, void** resources) {
    (void) compiler;
    if (resources) *resources = (void*) 1;
    return SPVC_SUCCESS;
}
SPVC_EXPORT spvc_result spvc_compiler_create_shader_resources_for_active_variables(void* compiler, void** resources, const uint32_t* active_vars, size_t num_active_vars) {
    (void) compiler; (void) active_vars; (void) num_active_vars;
    if (resources) *resources = (void*) 1;
    return SPVC_SUCCESS;
}

SPVC_EXPORT spvc_result spvc_resources_get_resource_list_for_type(void* resources, int type, const void** list, size_t* num_res) {
    (void) resources; (void) type;
    if (list) *list = NULL;
    if (num_res) *num_res = 0;
    return SPVC_SUCCESS;
}
SPVC_EXPORT spvc_result spvc_resources_get_builtin_resource_list_for_type(void* resources, int type, const void** list, size_t* num_res) {
    (void) resources; (void) type;
    if (list) *list = NULL;
    if (num_res) *num_res = 0;
    return SPVC_SUCCESS;
}

SPVC_EXPORT void spvc_compiler_set_decoration(void* compiler, uint32_t id, int decoration, unsigned int arg) {
    (void) compiler; (void) id; (void) decoration; (void) arg;
}
SPVC_EXPORT void spvc_compiler_set_decoration_string(void* compiler, uint32_t id, int decoration, const char* arg) {
    (void) compiler; (void) id; (void) decoration; (void) arg;
}
SPVC_EXPORT void spvc_compiler_set_name(void* compiler, uint32_t id, const char* name) {
    (void) compiler; (void) id; (void) name;
}
SPVC_EXPORT void spvc_compiler_set_member_decoration(void* compiler, uint32_t id, unsigned int member_index, int decoration, unsigned int arg) {
    (void) compiler; (void) id; (void) member_index; (void) decoration; (void) arg;
}
SPVC_EXPORT void spvc_compiler_set_member_decoration_string(void* compiler, uint32_t id, unsigned int member_index, int decoration, const char* arg) {
    (void) compiler; (void) id; (void) member_index; (void) decoration; (void) arg;
}
SPVC_EXPORT void spvc_compiler_set_member_name(void* compiler, uint32_t id, unsigned int member_index, const char* name) {
    (void) compiler; (void) id; (void) member_index; (void) name;
}
SPVC_EXPORT void spvc_compiler_unset_decoration(void* compiler, uint32_t id, int decoration) {
    (void) compiler; (void) id; (void) decoration;
}
SPVC_EXPORT void spvc_compiler_unset_member_decoration(void* compiler, uint32_t id, unsigned int member_index, int decoration) {
    (void) compiler; (void) id; (void) member_index; (void) decoration;
}
SPVC_EXPORT bool spvc_compiler_has_decoration(void* compiler, uint32_t id, int decoration) {
    (void) compiler; (void) id; (void) decoration;
    return false;
}
SPVC_EXPORT bool spvc_compiler_has_member_decoration(void* compiler, uint32_t id, unsigned int member_index, int decoration) {
    (void) compiler; (void) id; (void) member_index; (void) decoration;
    return false;
}
SPVC_EXPORT const char* spvc_compiler_get_name(void* compiler, uint32_t id) {
    (void) compiler; (void) id;
    return "";
}
SPVC_EXPORT unsigned int spvc_compiler_get_decoration(void* compiler, uint32_t id, int decoration) {
    (void) compiler; (void) id; (void) decoration;
    return 0;
}
SPVC_EXPORT const char* spvc_compiler_get_decoration_string(void* compiler, uint32_t id, int decoration) {
    (void) compiler; (void) id; (void) decoration;
    return "";
}
SPVC_EXPORT unsigned int spvc_compiler_get_member_decoration(void* compiler, uint32_t id, unsigned int member_index, int decoration) {
    (void) compiler; (void) id; (void) member_index; (void) decoration;
    return 0;
}
SPVC_EXPORT const char* spvc_compiler_get_member_decoration_string(void* compiler, uint32_t id, unsigned int member_index, int decoration) {
    (void) compiler; (void) id; (void) member_index; (void) decoration;
    return "";
}
SPVC_EXPORT const char* spvc_compiler_get_member_name(void* compiler, uint32_t id, unsigned int member_index) {
    (void) compiler; (void) id; (void) member_index;
    return "";
}

SPVC_EXPORT spvc_result spvc_compiler_get_entry_points(void* compiler, const void** entry_points, size_t* num_entry_points) {
    (void) compiler;
    if (entry_points) *entry_points = NULL;
    if (num_entry_points) *num_entry_points = 0;
    return SPVC_SUCCESS;
}
SPVC_EXPORT spvc_result spvc_compiler_set_entry_point(void* compiler, const char* name, int model) {
    (void) compiler; (void) name; (void) model;
    return SPVC_SUCCESS;
}
SPVC_EXPORT spvc_result spvc_compiler_rename_entry_point(void* compiler, const char* old_name, const char* new_name, int model) {
    (void) compiler; (void) old_name; (void) new_name; (void) model;
    return SPVC_SUCCESS;
}
SPVC_EXPORT const char* spvc_compiler_get_cleansed_entry_point_name(void* compiler, const char* name, int model) {
    (void) compiler; (void) model;
    return name ? name : "";
}
SPVC_EXPORT void spvc_compiler_set_execution_mode(void* compiler, int mode) { (void) compiler; (void) mode; }
SPVC_EXPORT void spvc_compiler_unset_execution_mode(void* compiler, int mode) { (void) compiler; (void) mode; }
SPVC_EXPORT void spvc_compiler_set_execution_mode_with_arguments(void* compiler, int mode, unsigned int a, unsigned int b, unsigned int c) {
    (void) compiler; (void) mode; (void) a; (void) b; (void) c;
}
SPVC_EXPORT spvc_result spvc_compiler_get_execution_modes(void* compiler, const int** modes, size_t* num_modes) {
    (void) compiler;
    if (modes) *modes = NULL;
    if (num_modes) *num_modes = 0;
    return SPVC_SUCCESS;
}
SPVC_EXPORT unsigned int spvc_compiler_get_execution_mode_argument(void* compiler, int mode) { (void) compiler; (void) mode; return 0; }
SPVC_EXPORT unsigned int spvc_compiler_get_execution_mode_argument_by_index(void* compiler, int mode, unsigned int index) {
    (void) compiler; (void) mode; (void) index;
    return 0;
}
SPVC_EXPORT int spvc_compiler_get_execution_model(void* compiler) { (void) compiler; return 0; }
SPVC_EXPORT void spvc_compiler_update_active_builtins(void* compiler) { (void) compiler; }
SPVC_EXPORT bool spvc_compiler_has_active_builtin(void* compiler, int builtin, int model) {
    (void) compiler; (void) builtin; (void) model;
    return false;
}
SPVC_EXPORT void* spvc_compiler_get_type_handle(void* compiler, uint32_t id) { (void) compiler; (void) id; return (void*) 1; }

SPVC_EXPORT uint32_t spvc_type_get_base_type_id(void* type) { (void) type; return 0; }
SPVC_EXPORT int spvc_type_get_basetype(void* type) { (void) type; return 0; }
SPVC_EXPORT unsigned int spvc_type_get_bit_width(void* type) { (void) type; return 32; }
SPVC_EXPORT unsigned int spvc_type_get_vector_size(void* type) { (void) type; return 1; }
SPVC_EXPORT unsigned int spvc_type_get_columns(void* type) { (void) type; return 1; }
SPVC_EXPORT unsigned int spvc_type_get_num_array_dimensions(void* type) { (void) type; return 0; }
SPVC_EXPORT bool spvc_type_array_dimension_is_literal(void* type, unsigned int dim) { (void) type; (void) dim; return true; }
SPVC_EXPORT spvc_result spvc_type_get_array_dimension(void* type, unsigned int dim, uint32_t* value) {
    (void) type; (void) dim;
    if (value) *value = 0;
    return SPVC_SUCCESS;
}
SPVC_EXPORT unsigned int spvc_type_get_num_member_types(void* type) { (void) type; return 0; }
SPVC_EXPORT uint32_t spvc_type_get_member_type(void* type, unsigned int index) { (void) type; (void) index; return 0; }
SPVC_EXPORT int spvc_type_get_storage_class(void* type) { (void) type; return 0; }
SPVC_EXPORT uint32_t spvc_type_get_image_sampled_type(void* type) { (void) type; return 0; }
SPVC_EXPORT int spvc_type_get_image_dimension(void* type) { (void) type; return 0; }
SPVC_EXPORT bool spvc_type_get_image_is_depth(void* type) { (void) type; return false; }
SPVC_EXPORT bool spvc_type_get_image_arrayed(void* type) { (void) type; return false; }
SPVC_EXPORT bool spvc_type_get_image_multisampled(void* type) { (void) type; return false; }
SPVC_EXPORT bool spvc_type_get_image_is_storage(void* type) { (void) type; return false; }
SPVC_EXPORT int spvc_type_get_image_storage_format(void* type) { (void) type; return 0; }
SPVC_EXPORT int spvc_type_get_image_access_qualifier(void* type) { (void) type; return 0; }

SPVC_EXPORT spvc_result spvc_compiler_get_declared_struct_size(void* compiler, void* type, size_t* size) {
    (void) compiler; (void) type;
    if (size) *size = 0;
    return SPVC_SUCCESS;
}
SPVC_EXPORT spvc_result spvc_compiler_get_declared_struct_size_runtime_array(void* compiler, void* type, size_t count, size_t* size) {
    (void) compiler; (void) type; (void) count;
    if (size) *size = 0;
    return SPVC_SUCCESS;
}
SPVC_EXPORT spvc_result spvc_compiler_get_declared_struct_member_size(void* compiler, void* type, unsigned int member, size_t* size) {
    (void) compiler; (void) type; (void) member;
    if (size) *size = 0;
    return SPVC_SUCCESS;
}
SPVC_EXPORT spvc_result spvc_compiler_type_struct_member_offset(void* compiler, void* type, unsigned int member, size_t* offset) {
    (void) compiler; (void) type; (void) member;
    if (offset) *offset = 0;
    return SPVC_SUCCESS;
}
SPVC_EXPORT spvc_result spvc_compiler_type_struct_member_array_stride(void* compiler, void* type, unsigned int member, size_t* stride) {
    (void) compiler; (void) type; (void) member;
    if (stride) *stride = 0;
    return SPVC_SUCCESS;
}
SPVC_EXPORT spvc_result spvc_compiler_type_struct_member_matrix_stride(void* compiler, void* type, unsigned int member, size_t* stride) {
    (void) compiler; (void) type; (void) member;
    if (stride) *stride = 0;
    return SPVC_SUCCESS;
}

SPVC_EXPORT spvc_result spvc_compiler_build_dummy_sampler_for_combined_images(void* compiler, uint32_t* id) {
    (void) compiler;
    if (id) *id = 0;
    return SPVC_SUCCESS;
}
SPVC_EXPORT spvc_result spvc_compiler_build_combined_image_samplers(void* compiler) {
    (void) compiler;
    return SPVC_SUCCESS;
}
SPVC_EXPORT spvc_result spvc_compiler_get_combined_image_samplers(void* compiler, const void** samplers, size_t* num_samplers) {
    (void) compiler;
    if (samplers) *samplers = NULL;
    if (num_samplers) *num_samplers = 0;
    return SPVC_SUCCESS;
}
SPVC_EXPORT spvc_result spvc_compiler_get_specialization_constants(void* compiler, const void** constants, size_t* num_constants) {
    (void) compiler;
    if (constants) *constants = NULL;
    if (num_constants) *num_constants = 0;
    return SPVC_SUCCESS;
}
SPVC_EXPORT void* spvc_compiler_get_constant_handle(void* compiler, uint32_t id) { (void) compiler; (void) id; return (void*) 1; }
SPVC_EXPORT spvc_result spvc_compiler_get_work_group_size_specialization_constants(void* compiler, void* x, void* y, void* z) {
    (void) compiler; (void) x; (void) y; (void) z;
    return SPVC_SUCCESS;
}
SPVC_EXPORT spvc_result spvc_compiler_get_active_buffer_ranges(void* compiler, uint32_t id, const void** ranges, size_t* num_ranges) {
    (void) compiler; (void) id;
    if (ranges) *ranges = NULL;
    if (num_ranges) *num_ranges = 0;
    return SPVC_SUCCESS;
}

SPVC_EXPORT float spvc_constant_get_scalar_fp16(void* constant, unsigned int col, unsigned int row) { (void) constant; (void) col; (void) row; return 0.0f; }
SPVC_EXPORT float spvc_constant_get_scalar_fp32(void* constant, unsigned int col, unsigned int row) { (void) constant; (void) col; (void) row; return 0.0f; }
SPVC_EXPORT double spvc_constant_get_scalar_fp64(void* constant, unsigned int col, unsigned int row) { (void) constant; (void) col; (void) row; return 0.0; }
SPVC_EXPORT unsigned int spvc_constant_get_scalar_u32(void* constant, unsigned int col, unsigned int row) { (void) constant; (void) col; (void) row; return 0; }
SPVC_EXPORT int spvc_constant_get_scalar_i32(void* constant, unsigned int col, unsigned int row) { (void) constant; (void) col; (void) row; return 0; }
SPVC_EXPORT unsigned short spvc_constant_get_scalar_u16(void* constant, unsigned int col, unsigned int row) { (void) constant; (void) col; (void) row; return 0; }
SPVC_EXPORT short spvc_constant_get_scalar_i16(void* constant, unsigned int col, unsigned int row) { (void) constant; (void) col; (void) row; return 0; }
SPVC_EXPORT unsigned char spvc_constant_get_scalar_u8(void* constant, unsigned int col, unsigned int row) { (void) constant; (void) col; (void) row; return 0; }
SPVC_EXPORT signed char spvc_constant_get_scalar_i8(void* constant, unsigned int col, unsigned int row) { (void) constant; (void) col; (void) row; return 0; }
SPVC_EXPORT spvc_result spvc_constant_get_subconstants(void* constant, const uint32_t** subconstants, size_t* num_subconstants) {
    (void) constant;
    if (subconstants) *subconstants = NULL;
    if (num_subconstants) *num_subconstants = 0;
    return SPVC_SUCCESS;
}
SPVC_EXPORT uint64_t spvc_constant_get_scalar_u64(void* constant, unsigned int col, unsigned int row) { (void) constant; (void) col; (void) row; return 0; }
SPVC_EXPORT int64_t spvc_constant_get_scalar_i64(void* constant, unsigned int col, unsigned int row) { (void) constant; (void) col; (void) row; return 0; }
SPVC_EXPORT uint32_t spvc_constant_get_type(void* constant) { (void) constant; return 0; }

SPVC_EXPORT void spvc_constant_set_scalar_fp16(void* constant, unsigned int col, unsigned int row, unsigned short val) { (void) constant; (void) col; (void) row; (void) val; }
SPVC_EXPORT void spvc_constant_set_scalar_fp32(void* constant, unsigned int col, unsigned int row, float val) { (void) constant; (void) col; (void) row; (void) val; }
SPVC_EXPORT void spvc_constant_set_scalar_fp64(void* constant, unsigned int col, unsigned int row, double val) { (void) constant; (void) col; (void) row; (void) val; }
SPVC_EXPORT void spvc_constant_set_scalar_u32(void* constant, unsigned int col, unsigned int row, unsigned int val) { (void) constant; (void) col; (void) row; (void) val; }
SPVC_EXPORT void spvc_constant_set_scalar_i32(void* constant, unsigned int col, unsigned int row, int val) { (void) constant; (void) col; (void) row; (void) val; }
SPVC_EXPORT void spvc_constant_set_scalar_u64(void* constant, unsigned int col, unsigned int row, uint64_t val) { (void) constant; (void) col; (void) row; (void) val; }
SPVC_EXPORT void spvc_constant_set_scalar_i64(void* constant, unsigned int col, unsigned int row, int64_t val) { (void) constant; (void) col; (void) row; (void) val; }
SPVC_EXPORT void spvc_constant_set_scalar_u16(void* constant, unsigned int col, unsigned int row, unsigned short val) { (void) constant; (void) col; (void) row; (void) val; }
SPVC_EXPORT void spvc_constant_set_scalar_i16(void* constant, unsigned int col, unsigned int row, short val) { (void) constant; (void) col; (void) row; (void) val; }
SPVC_EXPORT void spvc_constant_set_scalar_u8(void* constant, unsigned int col, unsigned int row, unsigned char val) { (void) constant; (void) col; (void) row; (void) val; }
SPVC_EXPORT void spvc_constant_set_scalar_i8(void* constant, unsigned int col, unsigned int row, signed char val) { (void) constant; (void) col; (void) row; (void) val; }

SPVC_EXPORT bool spvc_compiler_get_binary_offset_for_decoration(void* compiler, uint32_t id, int decoration, unsigned int* offset) {
    (void) compiler; (void) id; (void) decoration;
    if (offset) *offset = 0;
    return false;
}
SPVC_EXPORT bool spvc_compiler_buffer_is_hlsl_counter_buffer(void* compiler, uint32_t id) { (void) compiler; (void) id; return false; }
SPVC_EXPORT bool spvc_compiler_buffer_get_hlsl_counter_buffer(void* compiler, uint32_t id, uint32_t* counter_id) {
    (void) compiler; (void) id;
    if (counter_id) *counter_id = 0;
    return false;
}
SPVC_EXPORT spvc_result spvc_compiler_get_declared_capabilities(void* compiler, const int** capabilities, size_t* num_capabilities) {
    (void) compiler;
    if (capabilities) *capabilities = NULL;
    if (num_capabilities) *num_capabilities = 0;
    return SPVC_SUCCESS;
}
SPVC_EXPORT spvc_result spvc_compiler_get_declared_extensions(void* compiler, const char*** extensions, size_t* num_extensions) {
    (void) compiler;
    if (extensions) *extensions = NULL;
    if (num_extensions) *num_extensions = 0;
    return SPVC_SUCCESS;
}
SPVC_EXPORT const char* spvc_compiler_get_remapped_declared_block_name(void* compiler, uint32_t id) { (void) compiler; (void) id; return ""; }
SPVC_EXPORT spvc_result spvc_compiler_get_buffer_block_decorations(void* compiler, uint32_t id, const int** decorations, size_t* num_decorations) {
    (void) compiler; (void) id;
    if (decorations) *decorations = NULL;
    if (num_decorations) *num_decorations = 0;
    return SPVC_SUCCESS;
}
