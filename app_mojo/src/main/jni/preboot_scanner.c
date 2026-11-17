#include <jni.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <dirent.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <unistd.h>
#include <stdbool.h>
#include <zlib.h>
#include <android/log.h>

#define TAG "PrebootScanner"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

#define MAX_MODS 512
#define MAX_ISSUES 128
#define MAX_DEPS_PER_MOD 64
#define MAX_STR_LEN 256
#define MAX_JSON_BUF (128 * 1024)

typedef struct {
    char mod_id[MAX_STR_LEN];
    char name[MAX_STR_LEN];
    char version[MAX_STR_LEN];
    char file_name[MAX_STR_LEN];
    char file_path[512];
    char depends[MAX_DEPS_PER_MOD][MAX_STR_LEN];
    int depend_count;
    bool has_x86_native;
    bool has_arm64_native;
} ModManifest;

typedef struct {
    char type[64];
    char mod_id[MAX_STR_LEN];
    char title[MAX_STR_LEN];
    char message[512];
    char affected_file[MAX_STR_LEN];
    char fix_action[64];
    char fix_target[MAX_STR_LEN];
} ScanIssue;

static bool is_platform_dependency(const char* dep) {
    if (!dep || !*dep) return true;
    if (strcasecmp(dep, "minecraft") == 0) return true;
    if (strcasecmp(dep, "java") == 0) return true;
    if (strcasecmp(dep, "fabricloader") == 0) return true;
    if (strcasecmp(dep, "fabric") == 0) return true;
    if (strcasecmp(dep, "fabric-api-base") == 0) return true;
    if (strcasecmp(dep, "quilt_loader") == 0) return true;
    if (strcasecmp(dep, "quilted_fabric_api") == 0) return true;
    if (strcasecmp(dep, "mixinextras") == 0) return true;
    if (strcasecmp(dep, "mixinsquared") == 0) return true;
    if (strcasecmp(dep, "fabric-language-kotlin") == 0) return true;
    if (strcasecmp(dep, "fabric-language-scala") == 0) return true;
    return false;
}

static char* extract_json_string(const char* json, const char* key, char* out, size_t out_max) {
    if (!json || !key || !out || out_max == 0) return NULL;
    char pattern[128];
    snprintf(pattern, sizeof(pattern), "\"%s\"", key);
    const char* p = strstr(json, pattern);
    if (!p) return NULL;
    p += strlen(pattern);
    while (*p && (*p == ' ' || *p == '\t' || *p == '\r' || *p == '\n' || *p == ':')) p++;
    if (*p != '\"') return NULL;
    p++;
    size_t i = 0;
    while (*p && *p != '\"' && i + 1 < out_max) {
        if (*p == '\\' && *(p + 1)) p++;
        out[i++] = *p++;
    }
    out[i] = '\0';
    return out;
}

static void parse_depends_object(const char* json, ModManifest* mod) {
    const char* p = strstr(json, "\"depends\"");
    if (!p) return;
    p += strlen("\"depends\"");
    while (*p && *p != '{') {
        if (*p == ']' || *p == '}') return;
        p++;
    }
    if (*p != '{') return;
    p++;

    int depth = 1;
    while (*p && depth > 0 && mod->depend_count < MAX_DEPS_PER_MOD) {
        if (*p == '{') depth++;
        else if (*p == '}') { depth--; if (depth == 0) break; }
        else if (*p == '\"' && depth == 1) {
            p++;
            char dep_id[MAX_STR_LEN];
            size_t di = 0;
            while (*p && *p != '\"' && di + 1 < sizeof(dep_id)) {
                dep_id[di++] = *p++;
            }
            dep_id[di] = '\0';
            if (*p == '\"') p++;

            while (*p && *p != ':' && *p != '}') p++;
            if (*p == ':') p++;

            if (di > 0) {
                strncpy(mod->depends[mod->depend_count++], dep_id, MAX_STR_LEN - 1);
            }
        }
        p++;
    }
}

static bool decompress_deflate(const uint8_t* in_data, size_t in_size, uint8_t* out_data, size_t out_max, size_t* out_size) {
    z_stream strm;
    memset(&strm, 0, sizeof(strm));
    strm.next_in = (Bytef*)in_data;
    strm.avail_in = (uInt)in_size;
    strm.next_out = (Bytef*)out_data;
    strm.avail_out = (uInt)out_max;

    // -MAX_WBITS tells zlib to expect raw Deflate stream without zlib/gzip headers
    if (inflateInit2(&strm, -MAX_WBITS) != Z_OK) {
        return false;
    }

    int ret = inflate(&strm, Z_FINISH);
    if (ret != Z_STREAM_END && ret != Z_OK) {
        inflateEnd(&strm);
        return false;
    }

    *out_size = strm.total_out;
    inflateEnd(&strm);
    return true;
}

static bool scan_jar_file(const char* file_path, const char* file_name, ModManifest* mod) {
    FILE* f = fopen(file_path, "rb");
    if (!f) return false;

    fseek(f, 0, SEEK_END);
    long file_len = ftell(f);
    if (file_len < 22) {
        fclose(f);
        return false;
    }

    long search_len = file_len > 65558 ? 65558 : file_len;
    fseek(f, file_len - search_len, SEEK_SET);

    uint8_t* tail = (uint8_t*)malloc(search_len);
    if (!tail) {
        fclose(f);
        return false;
    }
    if (fread(tail, 1, search_len, f) != (size_t)search_len) {
        free(tail);
        fclose(f);
        return false;
    }

    long eocd_pos = -1;
    for (long i = search_len - 22; i >= 0; i--) {
        if (tail[i] == 0x50 && tail[i + 1] == 0x4B && tail[i + 2] == 0x05 && tail[i + 3] == 0x06) {
            eocd_pos = i;
            break;
        }
    }

    if (eocd_pos < 0) {
        free(tail);
        fclose(f);
        return false;
    }

    uint16_t total_entries = (uint16_t)(tail[eocd_pos + 10] | (tail[eocd_pos + 11] << 8));
    uint32_t cd_offset = (uint32_t)(tail[eocd_pos + 16] | (tail[eocd_pos + 17] << 8) | (tail[eocd_pos + 18] << 16) | (tail[eocd_pos + 19] << 24));
    free(tail);

    if (fseek(f, cd_offset, SEEK_SET) != 0) {
        fclose(f);
        return false;
    }

    bool found_manifest = false;
    char manifest_json[65536];
    manifest_json[0] = '\0';

    for (uint16_t i = 0; i < total_entries; i++) {
        uint8_t chdr[46];
        if (fread(chdr, 1, 46, f) != 46) break;
        if (chdr[0] != 0x50 || chdr[1] != 0x4B || chdr[2] != 0x01 || chdr[3] != 0x02) break;

        uint16_t method = (uint16_t)(chdr[10] | (chdr[11] << 8));
        uint32_t comp_size = (uint32_t)(chdr[20] | (chdr[21] << 8) | (chdr[22] << 16) | (chdr[23] << 24));
        uint32_t uncomp_size = (uint32_t)(chdr[24] | (chdr[25] << 8) | (chdr[26] << 16) | (chdr[27] << 24));
        uint16_t fname_len = (uint16_t)(chdr[28] | (chdr[29] << 8));
        uint16_t extra_len = (uint16_t)(chdr[30] | (chdr[31] << 8));
        uint16_t comment_len = (uint16_t)(chdr[32] | (chdr[33] << 8));
        uint32_t local_offset = (uint32_t)(chdr[42] | (chdr[43] << 8) | (chdr[44] << 16) | (chdr[45] << 24));

        char entry_name[256];
        size_t read_name_len = fname_len < 255 ? fname_len : 255;
        if (fread(entry_name, 1, read_name_len, f) != read_name_len) break;
        entry_name[read_name_len] = '\0';
        if (fname_len > read_name_len) {
            fseek(f, fname_len - read_name_len, SEEK_CUR);
        }
        fseek(f, extra_len + comment_len, SEEK_CUR);

        // Check for native libraries
        if (strstr(entry_name, ".so") || strstr(entry_name, ".dll") || strstr(entry_name, ".dylib")) {
            if (strstr(entry_name, "x86_64") || strstr(entry_name, "x86") || strstr(entry_name, "amd64") || strstr(entry_name, "i386") || strstr(entry_name, "linux-x86")) {
                mod->has_x86_native = true;
            }
            if (strstr(entry_name, "aarch64") || strstr(entry_name, "arm64") || strstr(entry_name, "arm64-v8a")) {
                mod->has_arm64_native = true;
            }

            // Read ELF header for machine code check
            if (strstr(entry_name, ".so") && (!mod->has_x86_native || !mod->has_arm64_native)) {
                long cur_pos = ftell(f);
                fseek(f, local_offset, SEEK_SET);
                uint8_t lhdr[30];
                if (fread(lhdr, 1, 30, f) == 30 && lhdr[0] == 0x50 && lhdr[1] == 0x4B) {
                    uint16_t l_fname = (uint16_t)(lhdr[26] | (lhdr[27] << 8));
                    uint16_t l_extra = (uint16_t)(lhdr[28] | (lhdr[29] << 8));
                    fseek(f, l_fname + l_extra, SEEK_CUR);

                    uint8_t elf_head[20];
                    if (fread(elf_head, 1, 20, f) == 20 && elf_head[0] == 0x7F && elf_head[1] == 'E' && elf_head[2] == 'L' && elf_head[3] == 'F') {
                        uint16_t machine = (uint16_t)(elf_head[18] | (elf_head[19] << 8));
                        if (machine == 0x3E || machine == 0x03) mod->has_x86_native = true;
                        if (machine == 0xB7) mod->has_arm64_native = true;
                    }
                }
                fseek(f, cur_pos, SEEK_SET);
            }
        }

        // Check for fabric.mod.json or quilt.mod.json
        if (!found_manifest && (strcmp(entry_name, "fabric.mod.json") == 0 || strcmp(entry_name, "quilt.mod.json") == 0)) {
            long cur_pos = ftell(f);
            fseek(f, local_offset, SEEK_SET);
            uint8_t lhdr[30];
            if (fread(lhdr, 1, 30, f) == 30 && lhdr[0] == 0x50 && lhdr[1] == 0x4B) {
                uint16_t l_fname = (uint16_t)(lhdr[26] | (lhdr[27] << 8));
                uint16_t l_extra = (uint16_t)(lhdr[28] | (lhdr[29] << 8));
                fseek(f, l_fname + l_extra, SEEK_CUR);

                if (comp_size < 1024 * 1024) {
                    uint8_t* raw = (uint8_t*)malloc(comp_size);
                    if (raw && fread(raw, 1, comp_size, f) == comp_size) {
                        if (method == 0) {
                            size_t copy_len = comp_size < sizeof(manifest_json) - 1 ? comp_size : sizeof(manifest_json) - 1;
                            memcpy(manifest_json, raw, copy_len);
                            manifest_json[copy_len] = '\0';
                            found_manifest = true;
                        } else if (method == 8) {
                            size_t out_size = 0;
                            if (decompress_deflate(raw, comp_size, (uint8_t*)manifest_json, sizeof(manifest_json) - 1, &out_size)) {
                                manifest_json[out_size] = '\0';
                                found_manifest = true;
                            }
                        }
                    }
                    if (raw) free(raw);
                }
            }
            fseek(f, cur_pos, SEEK_SET);
        }
    }

    fclose(f);

    if (found_manifest) {
        strncpy(mod->file_name, file_name, sizeof(mod->file_name) - 1);
        strncpy(mod->file_path, file_path, sizeof(mod->file_path) - 1);

        char id_buf[MAX_STR_LEN] = {0};
        if (extract_json_string(manifest_json, "id", id_buf, sizeof(id_buf))) {
            strncpy(mod->mod_id, id_buf, sizeof(mod->mod_id) - 1);
        } else {
            strncpy(mod->mod_id, file_name, sizeof(mod->mod_id) - 1);
        }

        char name_buf[MAX_STR_LEN] = {0};
        if (extract_json_string(manifest_json, "name", name_buf, sizeof(name_buf))) {
            strncpy(mod->name, name_buf, sizeof(mod->name) - 1);
        } else {
            strncpy(mod->name, mod->mod_id, sizeof(mod->name) - 1);
        }

        char ver_buf[MAX_STR_LEN] = {0};
        if (extract_json_string(manifest_json, "version", ver_buf, sizeof(ver_buf))) {
            strncpy(mod->version, ver_buf, sizeof(mod->version) - 1);
        } else {
            strncpy(mod->version, "1.0.0", sizeof(mod->version) - 1);
        }

        parse_depends_object(manifest_json, mod);
        return true;
    }

    return false;
}

static void escape_json_str(const char* in, char* out, size_t out_max) {
    size_t j = 0;
    for (size_t i = 0; in[i] && j + 2 < out_max; i++) {
        char c = in[i];
        if (c == '\"' || c == '\\') {
            out[j++] = '\\';
            out[j++] = c;
        } else if (c == '\n') {
            out[j++] = '\\';
            out[j++] = 'n';
        } else if (c == '\r') {
            out[j++] = '\\';
            out[j++] = 'r';
        } else if (c == '\t') {
            out[j++] = '\\';
            out[j++] = 't';
        } else {
            out[j++] = c;
        }
    }
    out[j] = '\0';
}

JNIEXPORT jstring JNICALL
Java_git_artdeell_mojo_modding_PrebootScanner_nativeScanMods(
        JNIEnv *env,
        jclass clazz,
        jstring j_mods_dir,
        jstring j_renderer,
        jstring j_abi) {
    (void)clazz;
    if (!j_mods_dir) {
        return (*env)->NewStringUTF(env, "{\"hasIssues\":false,\"scannedModCount\":0,\"issues\":[]}");
    }

    const char* mods_dir = (*env)->GetStringUTFChars(env, j_mods_dir, NULL);
    const char* renderer = j_renderer ? (*env)->GetStringUTFChars(env, j_renderer, NULL) : "libre_mobile";
    const char* abi = j_abi ? (*env)->GetStringUTFChars(env, j_abi, NULL) : "arm64-v8a";

    DIR* dir = opendir(mods_dir);
    if (!dir) {
        if (j_renderer) (*env)->ReleaseStringUTFChars(env, j_renderer, renderer);
        if (j_abi) (*env)->ReleaseStringUTFChars(env, j_abi, abi);
        (*env)->ReleaseStringUTFChars(env, j_mods_dir, mods_dir);
        return (*env)->NewStringUTF(env, "{\"hasIssues\":false,\"scannedModCount\":0,\"issues\":[]}");
    }

    ModManifest* manifests = (ModManifest*)calloc(MAX_MODS, sizeof(ModManifest));
    ScanIssue* issues = (ScanIssue*)calloc(MAX_ISSUES, sizeof(ScanIssue));
    int mod_count = 0;
    int issue_count = 0;

    struct dirent* entry;
    while ((entry = readdir(dir)) != NULL && mod_count < MAX_MODS) {
        const char* name = entry->d_name;
        size_t len = strlen(name);
        if (len > 4 && strcasecmp(name + len - 4, ".jar") == 0) {
            char full_path[512];
            snprintf(full_path, sizeof(full_path), "%s/%s", mods_dir, name);

            ModManifest* m = &manifests[mod_count];
            memset(m, 0, sizeof(ModManifest));
            if (scan_jar_file(full_path, name, m)) {
                mod_count++;
            }
        }
    }
    closedir(dir);

    // 1. Check Duplicate Mod IDs
    for (int i = 0; i < mod_count && issue_count < MAX_ISSUES; i++) {
        for (int j = i + 1; j < mod_count && issue_count < MAX_ISSUES; j++) {
            if (manifests[i].mod_id[0] && manifests[j].mod_id[0] &&
                strcasecmp(manifests[i].mod_id, manifests[j].mod_id) == 0) {

                ScanIssue* issue = &issues[issue_count++];
                strncpy(issue->type, "DUPLICATE_MOD", sizeof(issue->type) - 1);
                strncpy(issue->mod_id, manifests[i].mod_id, sizeof(issue->mod_id) - 1);
                snprintf(issue->title, sizeof(issue->title), "Duplicate Mod: %s", manifests[i].name);
                snprintf(issue->message, sizeof(issue->message),
                         "Multiple JAR files provide mod ID '%s' (%s and %s). Duplicate mods cause crash loops.",
                         manifests[i].mod_id, manifests[i].file_name, manifests[j].file_name);
                strncpy(issue->affected_file, manifests[i].file_name, sizeof(issue->affected_file) - 1);
                strncpy(issue->fix_action, "DISABLE_FILE", sizeof(issue->fix_action) - 1);
                strncpy(issue->fix_target, manifests[i].file_name, sizeof(issue->fix_target) - 1);
                break;
            }
        }
    }

    // 2. Check Missing Dependencies
    for (int i = 0; i < mod_count && issue_count < MAX_ISSUES; i++) {
        for (int d = 0; d < manifests[i].depend_count && issue_count < MAX_ISSUES; d++) {
            const char* dep = manifests[i].depends[d];
            if (is_platform_dependency(dep)) continue;

            bool found = false;
            for (int k = 0; k < mod_count; k++) {
                if (strcasecmp(manifests[k].mod_id, dep) == 0) {
                    found = true;
                    break;
                }
            }

            if (!found) {
                ScanIssue* issue = &issues[issue_count++];
                strncpy(issue->type, "MISSING_DEPENDENCY", sizeof(issue->type) - 1);
                strncpy(issue->mod_id, manifests[i].mod_id, sizeof(issue->mod_id) - 1);
                snprintf(issue->title, sizeof(issue->title), "Missing Dependency: %s", dep);
                snprintf(issue->message, sizeof(issue->message),
                         "Mod '%s' requires '%s', which is missing from your mods folder.",
                         manifests[i].name, dep);
                strncpy(issue->affected_file, manifests[i].file_name, sizeof(issue->affected_file) - 1);
                strncpy(issue->fix_action, "DISABLE_FILE", sizeof(issue->fix_action) - 1);
                strncpy(issue->fix_target, manifests[i].file_name, sizeof(issue->fix_target) - 1);
            }
        }
    }

    // 3. Check Incompatible x86 Native Libraries on ARM
    bool is_arm = (abi == NULL || strncasecmp(abi, "arm", 3) == 0 || strncasecmp(abi, "aarch", 5) == 0);
    if (is_arm) {
        for (int i = 0; i < mod_count && issue_count < MAX_ISSUES; i++) {
            if (manifests[i].has_x86_native && !manifests[i].has_arm64_native) {
                ScanIssue* issue = &issues[issue_count++];
                strncpy(issue->type, "INCOMPATIBLE_NATIVE", sizeof(issue->type) - 1);
                strncpy(issue->mod_id, manifests[i].mod_id, sizeof(issue->mod_id) - 1);
                snprintf(issue->title, sizeof(issue->title), "Incompatible x86 Native Lib: %s", manifests[i].name);
                snprintf(issue->message, sizeof(issue->message),
                         "'%s' bundles PC/x86 native libraries with no Android ARM64 support.",
                         manifests[i].file_name);
                strncpy(issue->affected_file, manifests[i].file_name, sizeof(issue->affected_file) - 1);
                strncpy(issue->fix_action, "DISABLE_FILE", sizeof(issue->fix_action) - 1);
                strncpy(issue->fix_target, manifests[i].file_name, sizeof(issue->fix_target) - 1);
            }
        }
    }

    // 4. Check Incompatible Android Renderers
    bool has_sodium_family = false;
    char sodium_file[MAX_STR_LEN] = {0};
    bool has_vulkanmod = false;
    char vulkan_file[MAX_STR_LEN] = {0};
    bool has_canvas = false;
    char canvas_file[MAX_STR_LEN] = {0};
    bool has_optifine = false;
    char opti_file[MAX_STR_LEN] = {0};

    for (int i = 0; i < mod_count; i++) {
        const char* id = manifests[i].mod_id;
        if (strcasecmp(id, "sodium") == 0 || strcasecmp(id, "embeddium") == 0 ||
            strcasecmp(id, "iris") == 0 || strcasecmp(id, "oculus") == 0 ||
            strcasecmp(id, "xenon") == 0 || strcasecmp(id, "rubidium") == 0) {
            has_sodium_family = true;
            strncpy(sodium_file, manifests[i].file_name, sizeof(sodium_file) - 1);
        }
        if (strcasecmp(id, "vulkanmod") == 0 || strstr(id, "vulkan-mod")) {
            has_vulkanmod = true;
            strncpy(vulkan_file, manifests[i].file_name, sizeof(vulkan_file) - 1);
        }
        if (strcasecmp(id, "canvas") == 0) {
            has_canvas = true;
            strncpy(canvas_file, manifests[i].file_name, sizeof(canvas_file) - 1);
        }
        if (strcasecmp(id, "optifine") == 0 || strcasecmp(id, "optifabric") == 0) {
            has_optifine = true;
            strncpy(opti_file, manifests[i].file_name, sizeof(opti_file) - 1);
        }
    }

    if (has_sodium_family && strcasecmp(renderer, "opengles2") == 0 && issue_count < MAX_ISSUES) {
        ScanIssue* issue = &issues[issue_count++];
        strncpy(issue->type, "INCOMPATIBLE_RENDERER", sizeof(issue->type) - 1);
        strncpy(issue->mod_id, "sodium", sizeof(issue->mod_id) - 1);
        strncpy(issue->title, "Sodium / Iris Incompatible with GL4ES", sizeof(issue->title) - 1);
        strncpy(issue->message,
                "Sodium requires modern Core OpenGL and cannot run with GL4ES. Switch to Libre Mobile or LTW to prevent crashes.",
                sizeof(issue->message) - 1);
        strncpy(issue->affected_file, sodium_file, sizeof(issue->affected_file) - 1);
        strncpy(issue->fix_action, "SWITCH_RENDERER", sizeof(issue->fix_action) - 1);
        strncpy(issue->fix_target, "libre_mobile", sizeof(issue->fix_target) - 1);
    }

    if (has_vulkanmod && (strcasecmp(renderer, "opengles2") == 0 || strcasecmp(renderer, "opengles3_ltw") == 0 || strcasecmp(renderer, "opengles_mobileglues") == 0) && issue_count < MAX_ISSUES) {
        ScanIssue* issue = &issues[issue_count++];
        strncpy(issue->type, "INCOMPATIBLE_RENDERER", sizeof(issue->type) - 1);
        strncpy(issue->mod_id, "vulkanmod", sizeof(issue->mod_id) - 1);
        strncpy(issue->title, "VulkanMod Incompatible with OpenGL", sizeof(issue->title) - 1);
        strncpy(issue->message,
                "VulkanMod requires a Vulkan/Turnip driver and cannot run on OpenGL ES wrappers.",
                sizeof(issue->message) - 1);
        strncpy(issue->affected_file, vulkan_file, sizeof(issue->affected_file) - 1);
        strncpy(issue->fix_action, "DISABLE_FILE", sizeof(issue->fix_action) - 1);
        strncpy(issue->fix_target, vulkan_file, sizeof(issue->fix_target) - 1);
    }

    if (has_canvas && (strcasecmp(renderer, "opengles2") == 0 || strcasecmp(renderer, "opengles3_ltw") == 0) && issue_count < MAX_ISSUES) {
        ScanIssue* issue = &issues[issue_count++];
        strncpy(issue->type, "INCOMPATIBLE_RENDERER", sizeof(issue->type) - 1);
        strncpy(issue->mod_id, "canvas", sizeof(issue->mod_id) - 1);
        strncpy(issue->title, "Canvas Incompatible with Mobile GL", sizeof(issue->title) - 1);
        strncpy(issue->message,
                "Canvas Renderer requires desktop OpenGL (Zink) and crashes under GL4ES or LTW.",
                sizeof(issue->message) - 1);
        strncpy(issue->affected_file, canvas_file, sizeof(issue->affected_file) - 1);
        strncpy(issue->fix_action, "SWITCH_RENDERER", sizeof(issue->fix_action) - 1);
        strncpy(issue->fix_target, "vulkan_zink", sizeof(issue->fix_target) - 1);
    }

    if (has_optifine && has_sodium_family && issue_count < MAX_ISSUES) {
        ScanIssue* issue = &issues[issue_count++];
        strncpy(issue->type, "DUPLICATE_MOD", sizeof(issue->type) - 1);
        strncpy(issue->mod_id, "optifine", sizeof(issue->mod_id) - 1);
        strncpy(issue->title, "Conflicting Shaders: OptiFine + Sodium", sizeof(issue->title) - 1);
        strncpy(issue->message,
                "OptiFine and Sodium conflict directly. Please disable one of them to prevent crash loops.",
                sizeof(issue->message) - 1);
        strncpy(issue->affected_file, opti_file, sizeof(issue->affected_file) - 1);
        strncpy(issue->fix_action, "DISABLE_FILE", sizeof(issue->fix_action) - 1);
        strncpy(issue->fix_target, opti_file, sizeof(issue->fix_target) - 1);
    }

    // Build JSON output
    char* json = (char*)malloc(MAX_JSON_BUF);
    if (!json) {
        free(manifests);
        free(issues);
        if (j_renderer) (*env)->ReleaseStringUTFChars(env, j_renderer, renderer);
        if (j_abi) (*env)->ReleaseStringUTFChars(env, j_abi, abi);
        (*env)->ReleaseStringUTFChars(env, j_mods_dir, mods_dir);
        return (*env)->NewStringUTF(env, "{\"hasIssues\":false,\"scannedModCount\":0,\"issues\":[]}");
    }

    int pos = snprintf(json, MAX_JSON_BUF,
                       "{\n  \"hasIssues\": %s,\n  \"scannedModCount\": %d,\n  \"issues\": [\n",
                       issue_count > 0 ? "true" : "false", mod_count);

    for (int i = 0; i < issue_count && pos < MAX_JSON_BUF - 512; i++) {
        char esc_title[MAX_STR_LEN * 2];
        char esc_msg[1024];
        char esc_file[MAX_STR_LEN * 2];
        char esc_target[MAX_STR_LEN * 2];

        escape_json_str(issues[i].title, esc_title, sizeof(esc_title));
        escape_json_str(issues[i].message, esc_msg, sizeof(esc_msg));
        escape_json_str(issues[i].affected_file, esc_file, sizeof(esc_file));
        escape_json_str(issues[i].fix_target, esc_target, sizeof(esc_target));

        pos += snprintf(json + pos, MAX_JSON_BUF - pos,
                        "    {\n"
                        "      \"type\": \"%s\",\n"
                        "      \"modId\": \"%s\",\n"
                        "      \"title\": \"%s\",\n"
                        "      \"message\": \"%s\",\n"
                        "      \"affectedFile\": \"%s\",\n"
                        "      \"fixAction\": \"%s\",\n"
                        "      \"fixTarget\": \"%s\"\n"
                        "    }%s\n",
                        issues[i].type, issues[i].mod_id, esc_title, esc_msg,
                        esc_file, issues[i].fix_action, esc_target,
                        (i + 1 < issue_count) ? "," : "");
    }

    snprintf(json + pos, MAX_JSON_BUF - pos, "  ]\n}");

    jstring result = (*env)->NewStringUTF(env, json);

    free(json);
    free(manifests);
    free(issues);

    if (j_renderer) (*env)->ReleaseStringUTFChars(env, j_renderer, renderer);
    if (j_abi) (*env)->ReleaseStringUTFChars(env, j_abi, abi);
    (*env)->ReleaseStringUTFChars(env, j_mods_dir, mods_dir);

    return result;
}
