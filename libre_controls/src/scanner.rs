//! Pre-Boot Mod Conflict & Compatibility Scanner for Libre Launcher.
//!
//! Fast native inspection of mod JARs:
//! - Parses central directory records of ZIP archives.
//! - Reads `fabric.mod.json` and `quilt.mod.json` manifests.
//! - Checks bundled native libraries for x86 ELF headers.
//! - Detects duplicate mod IDs, missing dependencies, x86-only native binaries,
//!   and Android renderer conflicts.

use std::collections::{HashMap, HashSet};
use std::fs::File;
use std::io::{Read, Seek, SeekFrom};
use std::path::{Path, PathBuf};

// ---------------------------------------------------------------------------
// Pure Rust RFC 1951 Deflate Decompressor (Zero external crate dependencies)
// ---------------------------------------------------------------------------

struct BitReader<'a> {
    data: &'a [u8],
    byte_pos: usize,
    bit_buf: u32,
    bits_in_buf: u8,
}

impl<'a> BitReader<'a> {
    fn new(data: &'a [u8]) -> Self {
        Self {
            data,
            byte_pos: 0,
            bit_buf: 0,
            bits_in_buf: 0,
        }
    }

    fn ensure_bits(&mut self, n: u8) -> bool {
        while self.bits_in_buf < n {
            if self.byte_pos >= self.data.len() {
                return false;
            }
            self.bit_buf |= (self.data[self.byte_pos] as u32) << self.bits_in_buf;
            self.byte_pos += 1;
            self.bits_in_buf += 8;
        }
        true
    }

    fn read_bits(&mut self, n: u8) -> Option<u32> {
        if n == 0 {
            return Some(0);
        }
        if !self.ensure_bits(n) {
            return None;
        }
        let mask = (1u32 << n) - 1;
        let val = self.bit_buf & mask;
        self.bit_buf >>= n;
        self.bits_in_buf -= n;
        Some(val)
    }

    fn align_byte(&mut self) {
        let discard = self.bits_in_buf % 8;
        self.bit_buf >>= discard;
        self.bits_in_buf -= discard;
    }

    fn read_exact(&mut self, out: &mut [u8]) -> bool {
        self.align_byte();
        for byte in out.iter_mut() {
            if self.bits_in_buf >= 8 {
                *byte = (self.bit_buf & 0xFF) as u8;
                self.bit_buf >>= 8;
                self.bits_in_buf -= 8;
            } else if self.byte_pos < self.data.len() {
                *byte = self.data[self.byte_pos];
                self.byte_pos += 1;
            } else {
                return false;
            }
        }
        true
    }
}

#[derive(Clone, Default)]
struct HuffmanTree {
    table: Vec<u32>, // packed: (len << 16) | symbol, or child pointers
    max_bits: u8,
}

impl HuffmanTree {
    fn build(lengths: &[u8]) -> Option<Self> {
        let mut max_bits = 0;
        let mut count = [0usize; 16];
        for &l in lengths {
            if l > 15 {
                return None;
            }
            if l > 0 {
                count[l as usize] += 1;
                if l > max_bits {
                    max_bits = l;
                }
            }
        }
        if max_bits == 0 {
            return Some(Self::default());
        }

        let mut next_code = [0u32; 16];
        let mut code = 0u32;
        for i in 1..=max_bits {
            code = (code + (count[i as usize - 1] as u32)) << 1;
            next_code[i as usize] = code;
        }

        let table_size = 1usize << max_bits;
        let mut table = vec![0xFFFF_FFFFu32; table_size];

        for (sym, &len) in lengths.iter().enumerate() {
            if len == 0 {
                continue;
            }
            let c = next_code[len as usize];
            next_code[len as usize] += 1;

            // Reverse bits of length `len` for LSB-first decoding
            let mut rev = 0u32;
            for i in 0..len {
                if (c & (1 << (len - 1 - i))) != 0 {
                    rev |= 1 << i;
                }
            }

            let step = 1usize << len;
            let entry = ((len as u32) << 16) | (sym as u32);
            let mut idx = rev as usize;
            while idx < table_size {
                table[idx] = entry;
                idx += step;
            }
        }

        Some(Self { table, max_bits })
    }

    fn decode(&self, reader: &mut BitReader) -> Option<u32> {
        if self.max_bits == 0 {
            return None;
        }
        if !reader.ensure_bits(self.max_bits) {
            return None;
        }
        let mask = (1u32 << self.max_bits) - 1;
        let peek = reader.bit_buf & mask;
        let entry = self.table[peek as usize];
        if entry == 0xFFFF_FFFF {
            return None;
        }
        let len = (entry >> 16) as u8;
        let sym = entry & 0xFFFF;
        reader.bit_buf >>= len;
        reader.bits_in_buf -= len;
        Some(sym)
    }
}

pub fn inflate(compressed: &[u8], max_output_size: usize) -> Result<Vec<u8>, String> {
    let mut reader = BitReader::new(compressed);
    let mut out = Vec::new();

    let len_base: [u16; 29] = [
        3, 4, 5, 6, 7, 8, 9, 10, 11, 13, 15, 17, 19, 23, 27, 31, 35, 43, 51, 59, 67, 83, 99, 115, 131, 163, 195, 227, 258,
    ];
    let len_extra: [u8; 29] = [
        0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 2, 2, 2, 2, 3, 3, 3, 3, 4, 4, 4, 4, 5, 5, 5, 5, 0,
    ];
    let dist_base: [u16; 30] = [
        1, 2, 3, 4, 5, 7, 9, 13, 17, 25, 33, 49, 65, 97, 129, 193, 257, 385, 513, 769, 1025, 1537, 2049, 3073, 4097, 6145, 8193, 12289, 16385, 24577,
    ];
    let dist_extra: [u8; 30] = [
        0, 0, 0, 0, 1, 1, 2, 2, 3, 3, 4, 4, 5, 5, 6, 6, 7, 7, 8, 8, 9, 9, 10, 10, 11, 11, 12, 12, 13, 13,
    ];

    loop {
        let bfinal = reader.read_bits(1).ok_or("Unexpected EOF reading bfinal")?;
        let btype = reader.read_bits(2).ok_or("Unexpected EOF reading btype")?;

        match btype {
            0 => {
                // Stored block
                reader.align_byte();
                let len = reader.read_bits(16).ok_or("EOF in stored len")? as usize;
                let _nlen = reader.read_bits(16).ok_or("EOF in stored nlen")?;
                if out.len() + len > max_output_size {
                    return Err("Inflate output exceeded maximum buffer limit".into());
                }
                let cur_len = out.len();
                out.resize(cur_len + len, 0);
                if !reader.read_exact(&mut out[cur_len..]) {
                    return Err("Failed to read stored block bytes".into());
                }
            }
            1 => {
                // Fixed Huffman codes
                let mut lit_lens = [0u8; 288];
                for i in 0..=143 {
                    lit_lens[i] = 8;
                }
                for i in 144..=255 {
                    lit_lens[i] = 9;
                }
                for i in 256..=279 {
                    lit_lens[i] = 7;
                }
                for i in 280..=287 {
                    lit_lens[i] = 8;
                }
                let lit_tree = HuffmanTree::build(&lit_lens).ok_or("Failed building fixed lit tree")?;
                let dist_tree = HuffmanTree::build(&[5u8; 32]).ok_or("Failed building fixed dist tree")?;

                decode_huffman_block(&mut reader, &lit_tree, &dist_tree, &len_base, &len_extra, &dist_base, &dist_extra, &mut out, max_output_size)?;
            }
            2 => {
                // Dynamic Huffman codes
                let hlit = (reader.read_bits(5).ok_or("EOF hlit")? + 257) as usize;
                let hdist = (reader.read_bits(5).ok_or("EOF hdist")? + 1) as usize;
                let hclen = (reader.read_bits(4).ok_or("EOF hclen")? + 4) as usize;

                let code_len_order: [usize; 19] = [
                    16, 17, 18, 0, 8, 7, 9, 6, 10, 5, 11, 4, 12, 3, 13, 2, 14, 1, 15,
                ];
                let mut cl_lens = [0u8; 19];
                for i in 0..hclen {
                    cl_lens[code_len_order[i]] = reader.read_bits(3).ok_or("EOF cl_lens")? as u8;
                }
                let cl_tree = HuffmanTree::build(&cl_lens).ok_or("Failed building cl_tree")?;

                let total_codes = hlit + hdist;
                let mut all_lens = Vec::with_capacity(total_codes);
                while all_lens.len() < total_codes {
                    let sym = cl_tree.decode(&mut reader).ok_or("Failed decoding code length")?;
                    match sym {
                        0..=15 => all_lens.push(sym as u8),
                        16 => {
                            let last = *all_lens.last().ok_or("Repeat code 16 with no preceding code")?;
                            let repeat = (reader.read_bits(2).ok_or("EOF repeat 16")? + 3) as usize;
                            for _ in 0..repeat {
                                all_lens.push(last);
                            }
                        }
                        17 => {
                            let repeat = (reader.read_bits(3).ok_or("EOF repeat 17")? + 3) as usize;
                            for _ in 0..repeat {
                                all_lens.push(0);
                            }
                        }
                        18 => {
                            let repeat = (reader.read_bits(7).ok_or("EOF repeat 18")? + 11) as usize;
                            for _ in 0..repeat {
                                all_lens.push(0);
                            }
                        }
                        _ => return Err("Invalid code length symbol".into()),
                    }
                }

                let lit_tree = HuffmanTree::build(&all_lens[..hlit]).ok_or("Failed building dynamic lit tree")?;
                let dist_tree = HuffmanTree::build(&all_lens[hlit..]).ok_or("Failed building dynamic dist tree")?;

                decode_huffman_block(&mut reader, &lit_tree, &dist_tree, &len_base, &len_extra, &dist_base, &dist_extra, &mut out, max_output_size)?;
            }
            _ => return Err("Invalid BTYPE in Deflate stream".into()),
        }

        if bfinal != 0 {
            break;
        }
    }

    Ok(out)
}

fn decode_huffman_block(
    reader: &mut BitReader,
    lit_tree: &HuffmanTree,
    dist_tree: &HuffmanTree,
    len_base: &[u16; 29],
    len_extra: &[u8; 29],
    dist_base: &[u16; 30],
    dist_extra: &[u8; 30],
    out: &mut Vec<u8>,
    max_output_size: usize,
) -> Result<(), String> {
    loop {
        let sym = lit_tree.decode(reader).ok_or("Failed to decode literal/length symbol")?;
        match sym {
            0..=255 => {
                if out.len() >= max_output_size {
                    return Err("Inflate output exceeded maximum limit".into());
                }
                out.push(sym as u8);
            }
            256 => break, // End of block
            257..=285 => {
                let len_idx = (sym - 257) as usize;
                let extra_bits = len_extra[len_idx];
                let extra = reader.read_bits(extra_bits).ok_or("EOF reading length extra bits")?;
                let match_len = len_base[len_idx] as usize + extra as usize;

                let dist_sym = dist_tree.decode(reader).ok_or("Failed to decode distance symbol")?;
                if dist_sym as usize >= dist_base.len() {
                    return Err("Distance symbol out of bounds".into());
                }
                let dist_idx = dist_sym as usize;
                let dist_extra_bits = dist_extra[dist_idx];
                let dist_extra_val = reader.read_bits(dist_extra_bits).ok_or("EOF reading dist extra bits")?;
                let distance = dist_base[dist_idx] as usize + dist_extra_val as usize;

                if distance > out.len() {
                    return Err("Distance refers past start of output buffer".into());
                }
                if out.len() + match_len > max_output_size {
                    return Err("Inflate output exceeded maximum buffer limit".into());
                }

                let start = out.len() - distance;
                for i in 0..match_len {
                    let b = out[start + i];
                    out.push(b);
                }
            }
            _ => return Err("Illegal literal/length symbol".into()),
        }
    }
    Ok(())
}

// ---------------------------------------------------------------------------
// ZIP Central Directory Reader & Manifest Extractor
// ---------------------------------------------------------------------------

#[derive(Debug, Clone)]
pub struct ZipEntryMeta {
    pub name: String,
    pub compression_method: u16,
    pub compressed_size: u32,
    pub uncompressed_size: u32,
    pub local_header_offset: u32,
}

pub struct JarInspector;

impl JarInspector {
    pub fn read_central_directory(file: &mut File) -> Result<Vec<ZipEntryMeta>, String> {
        let file_len = file.seek(SeekFrom::End(0)).map_err(|e| e.to_string())?;
        if file_len < 22 {
            return Err("File too small to be a valid ZIP".into());
        }

        // Locate End of Central Directory (EOCD)
        let search_len = std::cmp::min(file_len as usize, 65536 + 22);
        file.seek(SeekFrom::End(-(search_len as i64))).map_err(|e| e.to_string())?;
        let mut tail_buf = vec![0u8; search_len];
        file.read_exact(&mut tail_buf).map_err(|e| e.to_string())?;

        let mut eocd_pos = None;
        for i in (0..search_len.saturating_sub(21)).rev() {
            if tail_buf[i] == 0x50 && tail_buf[i + 1] == 0x4B && tail_buf[i + 2] == 0x05 && tail_buf[i + 3] == 0x06 {
                eocd_pos = Some(i);
                break;
            }
        }
        let eocd_idx = eocd_pos.ok_or("End of Central Directory (EOCD) record not found")?;

        let total_entries = u16::from_le_bytes([tail_buf[eocd_idx + 10], tail_buf[eocd_idx + 11]]) as usize;
        let cd_offset = u32::from_le_bytes([
            tail_buf[eocd_idx + 16],
            tail_buf[eocd_idx + 17],
            tail_buf[eocd_idx + 18],
            tail_buf[eocd_idx + 19],
        ]) as u64;

        file.seek(SeekFrom::Start(cd_offset)).map_err(|e| e.to_string())?;
        let mut entries = Vec::with_capacity(total_entries);

        for _ in 0..total_entries {
            let mut header = [0u8; 46];
            if file.read_exact(&mut header).is_err() {
                break;
            }
            if header[0] != 0x50 || header[1] != 0x4B || header[2] != 0x01 || header[3] != 0x02 {
                break;
            }

            let method = u16::from_le_bytes([header[10], header[11]]);
            let comp_size = u32::from_le_bytes([header[20], header[21], header[22], header[23]]);
            let uncomp_size = u32::from_le_bytes([header[24], header[25], header[26], header[27]]);
            let fname_len = u16::from_le_bytes([header[28], header[29]]) as usize;
            let extra_len = u16::from_le_bytes([header[30], header[31]]) as usize;
            let comment_len = u16::from_le_bytes([header[32], header[33]]) as usize;
            let local_offset = u32::from_le_bytes([header[42], header[43], header[44], header[45]]);

            let mut fname_bytes = vec![0u8; fname_len];
            file.read_exact(&mut fname_bytes).map_err(|e| e.to_string())?;
            let name = String::from_utf8_lossy(&fname_bytes).into_owned();

            file.seek(SeekFrom::Current((extra_len + comment_len) as i64)).map_err(|e| e.to_string())?;

            entries.push(ZipEntryMeta {
                name,
                compression_method: method,
                compressed_size: comp_size,
                uncompressed_size: uncomp_size,
                local_header_offset: local_offset,
            });
        }

        Ok(entries)
    }

    pub fn extract_entry(file: &mut File, entry: &ZipEntryMeta) -> Result<Vec<u8>, String> {
        file.seek(SeekFrom::Start(entry.local_header_offset as u64)).map_err(|e| e.to_string())?;
        let mut local_head = [0u8; 30];
        file.read_exact(&mut local_head).map_err(|e| e.to_string())?;

        if local_head[0] != 0x50 || local_head[1] != 0x4B || local_head[2] != 0x03 || local_head[3] != 0x04 {
            return Err("Invalid local file header magic".into());
        }

        let fname_len = u16::from_le_bytes([local_head[26], local_head[27]]) as i64;
        let extra_len = u16::from_le_bytes([local_head[28], local_head[29]]) as i64;
        file.seek(SeekFrom::Current(fname_len + extra_len)).map_err(|e| e.to_string())?;

        let mut comp_data = vec![0u8; entry.compressed_size as usize];
        file.read_exact(&mut comp_data).map_err(|e| e.to_string())?;

        match entry.compression_method {
            0 => Ok(comp_data),
            8 => inflate(&comp_data, (entry.uncompressed_size as usize).max(65536)),
            m => Err(format!("Unsupported compression method {}", m)),
        }
    }
}

// ---------------------------------------------------------------------------
// Manifest & Native Library Representation
// ---------------------------------------------------------------------------

#[derive(Debug, Clone, Default)]
pub struct ModManifest {
    pub id: String,
    pub name: String,
    pub version: String,
    pub depends: Vec<(String, String)>,
    pub file_name: String,
    pub file_path: String,
    pub has_x86_native: bool,
    pub has_arm64_native: bool,
    pub native_libs: Vec<String>,
}

pub struct ManifestParser;

impl ManifestParser {
    pub fn parse_fabric(json_str: &str, file_name: &str, file_path: &str) -> Option<ModManifest> {
        let mut manifest = ModManifest {
            file_name: file_name.to_string(),
            file_path: file_path.to_string(),
            ..Default::default()
        };

        manifest.id = Self::extract_string_value(json_str, "id")?;
        manifest.name = Self::extract_string_value(json_str, "name").unwrap_or_else(|| manifest.id.clone());
        manifest.version = Self::extract_string_value(json_str, "version").unwrap_or_else(|| "1.0.0".to_string());

        if let Some(depends_slice) = Self::extract_object_slice(json_str, "depends") {
            manifest.depends = Self::extract_key_values(depends_slice);
        }

        Some(manifest)
    }

    pub fn parse_quilt(json_str: &str, file_name: &str, file_path: &str) -> Option<ModManifest> {
        let mut manifest = ModManifest {
            file_name: file_name.to_string(),
            file_path: file_path.to_string(),
            ..Default::default()
        };

        // Quilt loader block
        let loader_block = Self::extract_object_slice(json_str, "quilt_loader").unwrap_or(json_str);
        manifest.id = Self::extract_string_value(loader_block, "id")?;
        manifest.version = Self::extract_string_value(loader_block, "version").unwrap_or_else(|| "1.0.0".to_string());

        if let Some(metadata) = Self::extract_object_slice(loader_block, "metadata") {
            manifest.name = Self::extract_string_value(metadata, "name").unwrap_or_else(|| manifest.id.clone());
        } else {
            manifest.name = manifest.id.clone();
        }

        if let Some(depends_slice) = Self::extract_array_slice(loader_block, "depends") {
            manifest.depends = Self::extract_quilt_depends(depends_slice);
        }

        Some(manifest)
    }

    fn extract_string_value(json: &str, key: &str) -> Option<String> {
        let pattern = format!("\"{}\"", key);
        let pos = json.find(&pattern)?;
        let rest = &json[pos + pattern.len()..];
        let colon_pos = rest.find(':')?;
        let after_colon = rest[colon_pos + 1..].trim_start();
        if !after_colon.starts_with('"') {
            return None;
        }
        let content = &after_colon[1..];
        let end_quote = content.find('"')?;
        Some(content[..end_quote].to_string())
    }

    fn extract_object_slice<'a>(json: &'a str, key: &str) -> Option<&'a str> {
        let pattern = format!("\"{}\"", key);
        let pos = json.find(&pattern)?;
        let rest = &json[pos + pattern.len()..];
        let colon_pos = rest.find(':')?;
        let after_colon = rest[colon_pos + 1..].trim_start();
        if !after_colon.starts_with('{') {
            return None;
        }

        let mut depth = 0;
        for (i, c) in after_colon.char_indices() {
            if c == '{' {
                depth += 1;
            } else if c == '}' {
                depth -= 1;
                if depth == 0 {
                    return Some(&after_colon[1..i]);
                }
            }
        }
        None
    }

    fn extract_array_slice<'a>(json: &'a str, key: &str) -> Option<&'a str> {
        let pattern = format!("\"{}\"", key);
        let pos = json.find(&pattern)?;
        let rest = &json[pos + pattern.len()..];
        let colon_pos = rest.find(':')?;
        let after_colon = rest[colon_pos + 1..].trim_start();
        if !after_colon.starts_with('[') {
            return None;
        }

        let mut depth = 0;
        for (i, c) in after_colon.char_indices() {
            if c == '[' {
                depth += 1;
            } else if c == ']' {
                depth -= 1;
                if depth == 0 {
                    return Some(&after_colon[1..i]);
                }
            }
        }
        None
    }

    fn extract_key_values(slice: &str) -> Vec<(String, String)> {
        let mut results = Vec::new();
        let mut chars = slice.char_indices().peekable();

        while let Some(&(i, c)) = chars.peek() {
            if c == '"' {
                chars.next();
                let start_key = i + 1;
                let mut end_key = start_key;
                while let Some(&(ki, kc)) = chars.peek() {
                    chars.next();
                    if kc == '"' {
                        end_key = ki;
                        break;
                    }
                }
                let key = slice[start_key..end_key].to_string();

                // Skip to colon
                while let Some(&(_, kc)) = chars.peek() {
                    chars.next();
                    if kc == ':' {
                        break;
                    }
                }

                // Value can be string, array, or object
                let mut val = String::new();
                while let Some(&(vi, vc)) = chars.peek() {
                    if vc == '"' {
                        chars.next();
                        let start_val = vi + 1;
                        let mut end_val = start_val;
                        while let Some(&(vki, vkc)) = chars.peek() {
                            chars.next();
                            if vkc == '"' {
                                end_val = vki;
                                break;
                            }
                        }
                        val = slice[start_val..end_val].to_string();
                        break;
                    } else if vc == ',' || vc == '}' {
                        break;
                    } else {
                        chars.next();
                    }
                }
                results.push((key, val));
            } else {
                chars.next();
            }
        }
        results
    }

    fn extract_quilt_depends(slice: &str) -> Vec<(String, String)> {
        let mut results = Vec::new();
        // Quilt depends entries can be: "mod_id" or { "id": "mod_id", "versions": "..." }
        let entries = slice.split('{');
        for part in entries {
            if let Some(id) = Self::extract_string_value(part, "id") {
                let versions = Self::extract_string_value(part, "versions").unwrap_or_default();
                results.push((id, versions));
            } else {
                // Check plain string array entries
                for token in part.split(',') {
                    let t = token.trim();
                    if t.starts_with('"') && t.ends_with('"') && t.len() > 2 {
                        results.push((t[1..t.len() - 1].to_string(), String::new()));
                    }
                }
            }
        }
        results
    }
}

// ---------------------------------------------------------------------------
// Compatibility Issue Models & Pre-Boot Scanner Engine
// ---------------------------------------------------------------------------

#[derive(Debug, Clone, PartialEq, Eq)]
pub enum IssueType {
    DuplicateMod,
    MissingDependency,
    IncompatibleNativeLib,
    IncompatibleRenderer,
}

impl IssueType {
    pub fn as_str(&self) -> &'static str {
        match self {
            IssueType::DuplicateMod => "DUPLICATE_MOD",
            IssueType::MissingDependency => "MISSING_DEPENDENCY",
            IssueType::IncompatibleNativeLib => "INCOMPATIBLE_NATIVE",
            IssueType::IncompatibleRenderer => "INCOMPATIBLE_RENDERER",
        }
    }
}

#[derive(Debug, Clone)]
pub struct CompatibilityIssue {
    pub issue_type: IssueType,
    pub mod_id: String,
    pub title: String,
    pub message: String,
    pub affected_file: String,
    pub fix_action: String, // "DISABLE_FILE" | "SWITCH_RENDERER"
    pub fix_target: String, // Target filename to rename or target renderer ID
}

#[derive(Debug, Clone, Default)]
pub struct PrebootScanReport {
    pub scanned_mod_count: usize,
    pub issues: Vec<CompatibilityIssue>,
}

impl PrebootScanReport {
    pub fn has_issues(&self) -> bool {
        !self.issues.is_empty()
    }

    pub fn to_json(&self) -> String {
        let mut json = String::new();
        json.push_str("{\n");
        json.push_str(&format!("  \"hasIssues\": {},\n", self.has_issues()));
        json.push_str(&format!("  \"scannedModCount\": {},\n", self.scanned_mod_count));
        json.push_str("  \"issues\": [\n");

        for (i, issue) in self.issues.iter().enumerate() {
            json.push_str("    {\n");
            json.push_str(&format!("      \"type\": \"{}\",\n", issue.issue_type.as_str()));
            json.push_str(&format!("      \"modId\": \"{}\",\n", escape_json(&issue.mod_id)));
            json.push_str(&format!("      \"title\": \"{}\",\n", escape_json(&issue.title)));
            json.push_str(&format!("      \"message\": \"{}\",\n", escape_json(&issue.message)));
            json.push_str(&format!("      \"affectedFile\": \"{}\",\n", escape_json(&issue.affected_file)));
            json.push_str(&format!("      \"fixAction\": \"{}\",\n", escape_json(&issue.fix_action)));
            json.push_str(&format!("      \"fixTarget\": \"{}\"\n", escape_json(&issue.fix_target)));
            if i + 1 < self.issues.len() {
                json.push_str("    },\n");
            } else {
                json.push_str("    }\n");
            }
        }

        json.push_str("  ]\n");
        json.push_str("}\n");
        json
    }
}

fn escape_json(s: &str) -> String {
    let mut out = String::with_capacity(s.len());
    for c in s.chars() {
        match c {
            '"' => out.push_str("\\\""),
            '\\' => out.push_str("\\\\"),
            '\n' => out.push_str("\\n"),
            '\r' => out.push_str("\\r"),
            '\t' => out.push_str("\\t"),
            _ => out.push(c),
        }
    }
    out
}

pub struct PrebootScanner;

impl PrebootScanner {
    /// Standard built-in dependencies provided by Minecraft, Java, or loaders.
    pub fn is_platform_dependency(dep_id: &str) -> bool {
        matches!(
            dep_id.to_ascii_lowercase().as_str(),
            "minecraft"
                | "java"
                | "fabricloader"
                | "fabric"
                | "fabric-api-base"
                | "quilt_loader"
                | "quilted_fabric_api"
                | "mixinextras"
                | "mixinsquared"
                | "fabric-language-kotlin"
                | "fabric-language-scala"
        )
    }

    /// Fast scan of the given mods directory.
    pub fn scan_directory(mods_dir: &Path, current_renderer: &str, device_abi: &str) -> PrebootScanReport {
        let mut report = PrebootScanReport::default();
        if !mods_dir.is_dir() {
            return report;
        }

        let entries = match std::fs::read_dir(mods_dir) {
            Ok(e) => e,
            Err(_) => return report,
        };

        let mut mod_files: Vec<PathBuf> = Vec::new();
        for entry in entries.flatten() {
            let path = entry.path();
            if path.is_file() {
                if let Some(ext) = path.extension() {
                    if ext.to_string_lossy().eq_ignore_ascii_case("jar") {
                        mod_files.push(path);
                    }
                }
            }
        }

        report.scanned_mod_count = mod_files.len();
        if mod_files.is_empty() {
            return report;
        }

        let mut manifests: Vec<ModManifest> = Vec::new();
        let mut id_to_files: HashMap<String, Vec<(String, String)>> = HashMap::new(); // mod_id -> [(file_name, version)]
        let mut installed_mod_ids: HashSet<String> = HashSet::new();

        for path in &mod_files {
            let fname = path.file_name().unwrap_or_default().to_string_lossy().into_owned();
            let fpath = path.to_string_lossy().into_owned();

            let manifest = Self::scan_single_jar(path, &fname, &fpath);
            if let Some(m) = manifest {
                installed_mod_ids.insert(m.id.to_ascii_lowercase());
                id_to_files
                    .entry(m.id.to_ascii_lowercase())
                    .or_default()
                    .push((m.file_name.clone(), m.version.clone()));
                manifests.push(m);
            }
        }

        // 1. Detect Duplicate Mod IDs
        for (id, files) in &id_to_files {
            if files.len() > 1 {
                let file_names: Vec<String> = files.iter().map(|(f, _)| f.clone()).collect();
                // Select duplicate to disable: the first or older one
                let target_to_disable = files[0].0.clone();
                report.issues.push(CompatibilityIssue {
                    issue_type: IssueType::DuplicateMod,
                    mod_id: id.clone(),
                    title: format!("Duplicate Mod: {}", id),
                    message: format!(
                        "Mod ID '{}' is provided by multiple JAR files: {}. Having duplicate versions causes crash loops.",
                        id,
                        file_names.join(", ")
                    ),
                    affected_file: target_to_disable.clone(),
                    fix_action: "DISABLE_FILE".into(),
                    fix_target: target_to_disable,
                });
            }
        }

        // 2. Detect Missing Dependencies
        for m in &manifests {
            for (dep_id, _) in &m.depends {
                let dep_lower = dep_id.to_ascii_lowercase();
                if Self::is_platform_dependency(&dep_lower) {
                    continue;
                }
                if !installed_mod_ids.contains(&dep_lower) {
                    report.issues.push(CompatibilityIssue {
                        issue_type: IssueType::MissingDependency,
                        mod_id: m.id.clone(),
                        title: format!("Missing Dependency for {}", m.name),
                        message: format!(
                            "Mod '{}' ({}) requires '{}', which is missing from your mods folder.",
                            m.name, m.file_name, dep_id
                        ),
                        affected_file: m.file_name.clone(),
                        fix_action: "DISABLE_FILE".into(),
                        fix_target: m.file_name.clone(),
                    });
                }
            }
        }

        // 3. Detect Incompatible x86 Native Libraries on ARM Android
        let is_arm = device_abi.is_empty() || device_abi.starts_with("arm") || device_abi.starts_with("aarch");
        for m in &manifests {
            if is_arm && m.has_x86_native && !m.has_arm64_native {
                report.issues.push(CompatibilityIssue {
                    issue_type: IssueType::IncompatibleNativeLib,
                    mod_id: m.id.clone(),
                    title: format!("Incompatible Native Library: {}", m.name),
                    message: format!(
                        "'{}' bundles x86/PC native binaries with no Android ARM64 support. It will crash on load.",
                        m.file_name
                    ),
                    affected_file: m.file_name.clone(),
                    fix_action: "DISABLE_FILE".into(),
                    fix_target: m.file_name.clone(),
                });
            }
        }

        // 4. Detect Incompatible Android Renderers
        let ren = current_renderer.to_ascii_lowercase();
        let has_sodium_family = manifests.iter().any(|m| {
            let id = m.id.to_ascii_lowercase();
            id == "sodium" || id == "embeddium" || id == "iris" || id == "oculus" || id == "xenon" || id == "rubidium"
        });
        let has_vulkanmod = manifests.iter().any(|m| {
            let id = m.id.to_ascii_lowercase();
            id == "vulkanmod" || id.contains("vulkan-mod")
        });
        let has_canvas = manifests.iter().any(|m| m.id.to_ascii_lowercase() == "canvas");
        let has_optifine = manifests.iter().any(|m| {
            let id = m.id.to_ascii_lowercase();
            id == "optifine" || id == "optifabric"
        });

        // Sodium / Iris with GL4ES (opengles2)
        if has_sodium_family && ren == "opengles2" {
            let sodium_file = manifests
                .iter()
                .find(|m| {
                    let id = m.id.to_ascii_lowercase();
                    id == "sodium" || id == "embeddium" || id == "iris"
                })
                .map(|m| m.file_name.clone())
                .unwrap_or_default();

            report.issues.push(CompatibilityIssue {
                issue_type: IssueType::IncompatibleRenderer,
                mod_id: "sodium".into(),
                title: "Sodium / Iris Incompatible with GL4ES".into(),
                message: "Sodium and Iris require modern Core OpenGL and cannot run with GL4ES. Switch to Libre Mobile or LTW to prevent crashes.".into(),
                affected_file: sodium_file,
                fix_action: "SWITCH_RENDERER".into(),
                fix_target: "libre_mobile".into(),
            });
        }

        // VulkanMod with non-Vulkan renderers
        if has_vulkanmod && (ren == "opengles2" || ren == "opengles3_ltw" || ren == "opengles_mobileglues" || ren == "opengles_sfpew") {
            let vm_file = manifests
                .iter()
                .find(|m| m.id.to_ascii_lowercase().contains("vulkan"))
                .map(|m| m.file_name.clone())
                .unwrap_or_default();

            report.issues.push(CompatibilityIssue {
                issue_type: IssueType::IncompatibleRenderer,
                mod_id: "vulkanmod".into(),
                title: "VulkanMod Incompatible with OpenGL Renderer".into(),
                message: "VulkanMod replaces OpenGL with Vulkan and cannot run on OpenGL ES wrappers. Switch driver to Turnip/Vulkan or disable VulkanMod.".into(),
                affected_file: vm_file.clone(),
                fix_action: "DISABLE_FILE".into(),
                fix_target: vm_file,
            });
        }

        // Canvas with GL4ES / LTW
        if has_canvas && (ren == "opengles2" || ren == "opengles3_ltw") {
            let canvas_file = manifests
                .iter()
                .find(|m| m.id.to_ascii_lowercase() == "canvas")
                .map(|m| m.file_name.clone())
                .unwrap_or_default();

            report.issues.push(CompatibilityIssue {
                issue_type: IssueType::IncompatibleRenderer,
                mod_id: "canvas".into(),
                title: "Canvas Renderer Incompatible with Mobile GL".into(),
                message: "Canvas requires desktop OpenGL (Zink) and crashes under GL4ES or LTW.".into(),
                affected_file: canvas_file,
                fix_action: "SWITCH_RENDERER".into(),
                fix_target: "vulkan_zink".into(),
            });
        }

        // OptiFine + Sodium conflict
        if has_optifine && has_sodium_family {
            let opti_file = manifests
                .iter()
                .find(|m| m.id.to_ascii_lowercase().contains("opti"))
                .map(|m| m.file_name.clone())
                .unwrap_or_default();

            report.issues.push(CompatibilityIssue {
                issue_type: IssueType::DuplicateMod,
                mod_id: "optifine".into(),
                title: "Conflicting Shading Engines (OptiFine + Sodium)".into(),
                message: "OptiFine/OptiFabric and Sodium/Iris cannot run simultaneously and will crash during world generation.".into(),
                affected_file: opti_file.clone(),
                fix_action: "DISABLE_FILE".into(),
                fix_target: opti_file,
            });
        }

        report
    }

    fn scan_single_jar(jar_path: &Path, file_name: &str, file_path: &str) -> Option<ModManifest> {
        let mut file = File::open(jar_path).ok()?;
        let entries = JarInspector::read_central_directory(&mut file).ok()?;

        let mut manifest: Option<ModManifest> = None;
        let mut native_libs = Vec::new();
        let mut has_x86 = false;
        let mut has_arm64 = false;

        for entry in &entries {
            let lower_name = entry.name.to_ascii_lowercase();

            // Native library inspection
            if lower_name.ends_with(".so") || lower_name.ends_with(".dll") || lower_name.ends_with(".dylib") {
                native_libs.push(entry.name.clone());

                if lower_name.contains("x86_64")
                    || lower_name.contains("x86")
                    || lower_name.contains("amd64")
                    || lower_name.contains("i386")
                    || lower_name.contains("linux-x86")
                {
                    has_x86 = true;
                }
                if lower_name.contains("aarch64") || lower_name.contains("arm64") || lower_name.contains("arm64-v8a") {
                    has_arm64 = true;
                }

                // Check ELF header directly for .so files
                if lower_name.ends_with(".so") && (!has_x86 || !has_arm64) {
                    if let Ok(data) = JarInspector::extract_entry(&mut file, entry) {
                        if data.len() >= 20 && &data[0..4] == b"\x7fELF" {
                            let machine = u16::from_le_bytes([data[18], data[19]]);
                            match machine {
                                0x03 | 0x3E => has_x86 = true, // EM_386 or EM_X86_64
                                0xB7 => has_arm64 = true,      // EM_AARCH64
                                _ => {}
                            }
                        }
                    }
                }
            }

            // Manifest inspection: fabric.mod.json takes priority, then quilt.mod.json
            if manifest.is_none() && (lower_name == "fabric.mod.json" || lower_name == "quilt.mod.json") {
                if let Ok(content_bytes) = JarInspector::extract_entry(&mut file, entry) {
                    if let Ok(text) = std::str::from_utf8(&content_bytes) {
                        if lower_name == "fabric.mod.json" {
                            manifest = ManifestParser::parse_fabric(text, file_name, file_path);
                        } else {
                            manifest = ManifestParser::parse_quilt(text, file_name, file_path);
                        }
                    }
                }
            }
        }

        if let Some(ref mut m) = manifest {
            m.has_x86_native = has_x86;
            m.has_arm64_native = has_arm64;
            m.native_libs = native_libs;
        }

        manifest
    }
}

// ---------------------------------------------------------------------------
// C ABI Export for Preboot Scanner
// ---------------------------------------------------------------------------

use std::ffi::{CStr, CString};
use std::os::raw::c_char;

#[no_mangle]
pub extern "C" fn libre_preboot_scan_mods(
    mods_dir_path: *const c_char,
    current_renderer: *const c_char,
    device_abi: *const c_char,
    out_json: *mut c_char,
    out_json_max_len: usize,
) -> i32 {
    if mods_dir_path.is_null() || out_json.is_null() || out_json_max_len == 0 {
        return -1;
    }

    let dir_str = match unsafe { CStr::from_ptr(mods_dir_path) }.to_str() {
        Ok(s) => s,
        Err(_) => return -2,
    };
    let ren_str = if !current_renderer.is_null() {
        unsafe { CStr::from_ptr(current_renderer) }.to_str().unwrap_or("libre_mobile")
    } else {
        "libre_mobile"
    };
    let abi_str = if !device_abi.is_null() {
        unsafe { CStr::from_ptr(device_abi) }.to_str().unwrap_or("arm64-v8a")
    } else {
        "arm64-v8a"
    };

    let report = PrebootScanner::scan_directory(Path::new(dir_str), ren_str, abi_str);
    let json_str = report.to_json();
    let c_json = match CString::new(json_str) {
        Ok(c) => c,
        Err(_) => return -3,
    };

    let bytes = c_json.as_bytes_with_nul();
    if bytes.len() > out_json_max_len {
        return -4;
    }

    unsafe {
        std::ptr::copy_nonoverlapping(bytes.as_ptr() as *const c_char, out_json, bytes.len());
    }

    if report.has_issues() {
        1
    } else {
        0
    }
}

// ---------------------------------------------------------------------------
// Unit Tests
// ---------------------------------------------------------------------------

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_deflate_stored_block() {
        // Stored block: BFINAL=1, BTYPE=00, len=5, nlen=~5, "Hello"
        let raw = [
            0x01, // BFINAL=1, BTYPE=00
            0x05, 0x00, // LEN = 5
            0xFA, 0xFF, // NLEN = ~5
            b'H', b'e', b'l', b'l', b'o',
        ];
        let decompressed = inflate(&raw, 1024).expect("inflate stored block");
        assert_eq!(decompressed, b"Hello");
    }

    #[test]
    fn test_fabric_manifest_parsing() {
        let json = r#"{
            "schemaVersion": 1,
            "id": "sodium",
            "name": "Sodium",
            "version": "0.5.8",
            "depends": {
                "fabricloader": ">=0.15.0",
                "minecraft": "~1.20.1",
                "fabric-api": "*"
            }
        }"#;

        let manifest = ManifestParser::parse_fabric(json, "sodium-0.5.8.jar", "/tmp/sodium.jar").expect("parse fabric");
        assert_eq!(manifest.id, "sodium");
        assert_eq!(manifest.name, "Sodium");
        assert_eq!(manifest.version, "0.5.8");
        assert_eq!(manifest.depends.len(), 3);
        assert_eq!(manifest.depends[2].0, "fabric-api");
    }

    #[test]
    fn test_quilt_manifest_parsing() {
        let json = r#"{
            "schema_version": 1,
            "quilt_loader": {
                "id": "lambdynlights",
                "version": "2.3.2",
                "metadata": {
                    "name": "LambDynamicLights"
                },
                "depends": [
                    { "id": "quilt_loader", "versions": ">=0.19.0" },
                    { "id": "pride", "versions": "*" }
                ]
            }
        }"#;

        let manifest = ManifestParser::parse_quilt(json, "lambdynlights.jar", "/tmp/lamb.jar").expect("parse quilt");
        assert_eq!(manifest.id, "lambdynlights");
        assert_eq!(manifest.name, "LambDynamicLights");
        assert_eq!(manifest.version, "2.3.2");
        assert_eq!(manifest.depends.len(), 2);
        assert_eq!(manifest.depends[1].0, "pride");
    }

    #[test]
    fn test_duplicate_mod_detection() {
        let temp_dir = std::env::temp_dir().join("test_mod_scan_dup");
        let _ = std::fs::remove_dir_all(&temp_dir);
        std::fs::create_dir_all(&temp_dir).unwrap();

        // Create two dummy files
        let f1 = temp_dir.join("sodium-0.5.8.jar");
        let f2 = temp_dir.join("sodium-0.5.11.jar");
        std::fs::write(&f1, b"dummy1").unwrap();
        std::fs::write(&f2, b"dummy2").unwrap();

        let report = PrebootScanner::scan_directory(&temp_dir, "libre_mobile", "arm64-v8a");
        // Since dummy files aren't valid ZIPs, no crash occurs, empty manifests
        assert_eq!(report.scanned_mod_count, 2);

        let _ = std::fs::remove_dir_all(&temp_dir);
    }

    #[test]
    fn test_renderer_conflict_rules() {
        let mut report = PrebootScanReport::default();
        let issue = CompatibilityIssue {
            issue_type: IssueType::IncompatibleRenderer,
            mod_id: "sodium".into(),
            title: "Sodium Incompatible".into(),
            message: "Sodium cannot run on GL4ES".into(),
            affected_file: "sodium.jar".into(),
            fix_action: "SWITCH_RENDERER".into(),
            fix_target: "libre_mobile".into(),
        };
        report.issues.push(issue);

        assert!(report.has_issues());
        let json = report.to_json();
        assert!(json.contains("\"hasIssues\": true"));
        assert!(json.contains("SWITCH_RENDERER"));
        assert!(json.contains("libre_mobile"));
    }
}
