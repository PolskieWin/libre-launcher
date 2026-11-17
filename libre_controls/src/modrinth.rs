//! Native Modrinth client query builder and streaming SHA-512 cryptographic verification.

use std::os::raw::c_char;
use std::ffi::CStr;

// SHA-512 initial hash values (FIPS 180-4)
const H_INIT: [u64; 8] = [
    0x6a09e667f3bcc908,
    0xbb67ae8584caa73b,
    0x3c6ef372fe94f82b,
    0xa54ff53a5f1d36f1,
    0x510e527fade682d1,
    0x9b05688c2b3e6c1f,
    0x1f83d9abfb41bd6b,
    0x5be0cd19137e2179,
];

// SHA-512 round constants (FIPS 180-4)
const K: [u64; 80] = [
    0x428a2f98d728ae22, 0x7137449123ef65cd, 0xb5c0fbcfec4d3b2f, 0xe9b5dba58189dbbc,
    0x3956c25bf348b538, 0x59f111f1b605d019, 0x923f82a4af194f9b, 0xab1c5ed5da6d8118,
    0xd807aa98a3030242, 0x12835b0145706fbe, 0x243185be4ee4b28c, 0x550c7dc3d5ffb4e2,
    0x72be5d74f27b896f, 0x80deb1fe3b1696b1, 0x9bdc06a725c71235, 0xc19bf174cf692694,
    0xe49b69c19ef14ad2, 0xefbe4786384f25e3, 0x0fc19dc68b8cd5b5, 0x240ca1cc77ac9c65,
    0x2de92c6f592b0275, 0x4a7484aa6ea6e483, 0x5cb0a9dcbd41fbd4, 0x76f988da831153b5,
    0x983e5152ee66dfab, 0xa831c66d2db43210, 0xb00327c898fb213f, 0xbf597fc7beef0ee4,
    0xc6e00bf33da88fc2, 0xd5a79147930aa725, 0x06ca6351e003826f, 0x142929670a0e6e70,
    0x27b70a8546d22ffc, 0x2e1b21385c26c926, 0x4d2c6dfc5ac42aed, 0x53380d139d95b3df,
    0x650a73548baf63de, 0x766a0abb3c77b2a8, 0x81c2c92e47edaee6, 0x92722c851482353b,
    0xa2bfe8a14cf10364, 0xa81a664bbc423001, 0xc24b8b70d0f89791, 0xc76c51a30654be30,
    0xd192e819d6ef5218, 0xd69906245565a910, 0xf40e35855771202a, 0x106aa07032bbd1b8,
    0x19a4c116b8d2d0c8, 0x1e376c085141ab53, 0x2748774cdf8eeb99, 0x34b0bcb5e19b48a8,
    0x391c0cb3c5c95a63, 0x4ed8aa4ae3418acb, 0x5b9cca4f7763e373, 0x682e6ff3d6b2b8a3,
    0x748f82ee5defb2fc, 0x78a5636f43172f60, 0x84c87814a1f0ab72, 0x8cc702081a6439ec,
    0x90befffa23631e28, 0xa4506cebde82bde9, 0xbef9a3f7b2c67915, 0xc67178f2e372532b,
    0xca273eceea26619c, 0xd186b8c721c0c207, 0xeada7dd6cde0eb1e, 0xf57d4f7fee6ed178,
    0x06f067aa72176fba, 0x0a637dc5a2c898a6, 0x113f9804bef90dae, 0x1b710b35131c471b,
    0x28db77f523047d84, 0x32caab7b40c72493, 0x3c9ebe0a15c9bebc, 0x431d67c49c100d4c,
    0x4cc5d4becb3e42b6, 0x597f299cfc657e2a, 0x5fcb6fab3ad6faec, 0x6c44198c4a475817,
];

pub struct Sha512Hasher {
    state: [u64; 8],
    buffer: [u8; 128],
    buf_len: usize,
    total_len: u128,
}

impl Sha512Hasher {
    pub const fn new() -> Self {
        Self {
            state: H_INIT,
            buffer: [0u8; 128],
            buf_len: 0,
            total_len: 0,
        }
    }

    pub fn update(&mut self, mut data: &[u8]) {
        self.total_len += data.len() as u128;

        if self.buf_len > 0 {
            let needed = 128 - self.buf_len;
            if data.len() >= needed {
                self.buffer[self.buf_len..128].copy_from_slice(&data[..needed]);
                Self::process_block(&mut self.state, &self.buffer);
                data = &data[needed..];
                self.buf_len = 0;
            } else {
                self.buffer[self.buf_len..self.buf_len + data.len()].copy_from_slice(data);
                self.buf_len += data.len();
                return;
            }
        }

        while data.len() >= 128 {
            Self::process_block(&mut self.state, &data[..128]);
            data = &data[128..];
        }

        if !data.is_empty() {
            self.buffer[..data.len()].copy_from_slice(data);
            self.buf_len = data.len();
        }
    }

    pub fn finalize(mut self) -> [u8; 64] {
        self.buffer[self.buf_len] = 0x80;
        self.buf_len += 1;

        if self.buf_len > 112 {
            self.buffer[self.buf_len..128].fill(0);
            Self::process_block(&mut self.state, &self.buffer);
            self.buffer[..112].fill(0);
        } else {
            self.buffer[self.buf_len..112].fill(0);
        }

        let bit_len = self.total_len * 8;
        self.buffer[112..128].copy_from_slice(&bit_len.to_be_bytes());
        Self::process_block(&mut self.state, &self.buffer);

        let mut out = [0u8; 64];
        for (i, word) in self.state.iter().enumerate() {
            out[i * 8..(i + 1) * 8].copy_from_slice(&word.to_be_bytes());
        }
        out
    }

    fn process_block(state: &mut [u64; 8], block: &[u8]) {
        let mut w = [0u64; 80];
        for i in 0..16 {
            let chunk: [u8; 8] = block[i * 8..(i + 1) * 8].try_into().unwrap();
            w[i] = u64::from_be_bytes(chunk);
        }

        for i in 16..80 {
            let s0 = w[i - 15].rotate_right(1) ^ w[i - 15].rotate_right(8) ^ (w[i - 15] >> 7);
            let s1 = w[i - 2].rotate_right(19) ^ w[i - 2].rotate_right(61) ^ (w[i - 2] >> 6);
            w[i] = w[i - 16].wrapping_add(s0).wrapping_add(w[i - 7]).wrapping_add(s1);
        }

        let mut a = state[0];
        let mut b = state[1];
        let mut c = state[2];
        let mut d = state[3];
        let mut e = state[4];
        let mut f = state[5];
        let mut g = state[6];
        let mut h = state[7];

        for i in 0..80 {
            let s1 = e.rotate_right(14) ^ e.rotate_right(18) ^ e.rotate_right(41);
            let ch = (e & f) ^ ((!e) & g);
            let temp1 = h.wrapping_add(s1).wrapping_add(ch).wrapping_add(K[i]).wrapping_add(w[i]);
            let s0 = a.rotate_right(28) ^ a.rotate_right(34) ^ a.rotate_right(39);
            let maj = (a & b) ^ (a & c) ^ (b & c);
            let temp2 = s0.wrapping_add(maj);

            h = g;
            g = f;
            f = e;
            e = d.wrapping_add(temp1);
            d = c;
            c = b;
            b = a;
            a = temp1.wrapping_add(temp2);
        }

        state[0] = state[0].wrapping_add(a);
        state[1] = state[1].wrapping_add(b);
        state[2] = state[2].wrapping_add(c);
        state[3] = state[3].wrapping_add(d);
        state[4] = state[4].wrapping_add(e);
        state[5] = state[5].wrapping_add(f);
        state[6] = state[6].wrapping_add(g);
        state[7] = state[7].wrapping_add(h);
    }
}

pub fn sha512_digest(data: &[u8]) -> [u8; 64] {
    let mut hasher = Sha512Hasher::new();
    hasher.update(data);
    hasher.finalize()
}

pub fn sha512_hex(data: &[u8]) -> String {
    let digest = sha512_digest(data);
    let mut hex = String::with_capacity(128);
    for b in digest {
        use std::fmt::Write;
        let _ = write!(hex, "{:02x}", b);
    }
    hex
}

pub fn verify_sha512(data: &[u8], expected_hex: &str) -> bool {
    let actual_hex = sha512_hex(data);
    actual_hex.eq_ignore_ascii_case(expected_hex.trim())
}

fn url_encode(input: &str) -> String {
    let mut encoded = String::with_capacity(input.len() * 2);
    for b in input.bytes() {
        match b {
            b'a'..=b'z' | b'A'..=b'Z' | b'0'..=b'9' | b'-' | b'_' | b'.' | b'~' => {
                encoded.push(b as char);
            }
            _ => {
                use std::fmt::Write;
                let _ = write!(encoded, "%{:02X}", b);
            }
        }
    }
    encoded
}

pub fn build_search_url(
    query: &str,
    project_type: &str,
    mc_version: Option<&str>,
    loader: Option<&str>,
    category: Option<&str>,
    limit: usize,
    offset: usize,
    index: &str,
) -> String {
    let mut facets = String::from("[");
    facets.push_str(&format!("[\"project_type:{}\"]", project_type));

    if let Some(ver) = mc_version {
        if !ver.is_empty() {
            facets.push_str(&format!(",[\"versions:{}\"]", ver));
        }
    }
    if let Some(ldr) = loader {
        if !ldr.is_empty() {
            facets.push_str(&format!(",[\"categories:{}\"]", ldr));
        }
    }
    if let Some(cat) = category {
        if !cat.is_empty() {
            facets.push_str(&format!(",[\"categories:{}\"]", cat));
        }
    }
    facets.push(']');

    format!(
        "https://api.modrinth.com/v2/search?query={}&facets={}&limit={}&offset={}&index={}",
        url_encode(query),
        url_encode(&facets),
        limit,
        offset,
        index
    )
}

pub fn build_project_url(project_id: &str, get_versions: bool) -> String {
    if get_versions {
        format!("https://api.modrinth.com/v2/project/{}/version", project_id)
    } else {
        format!("https://api.modrinth.com/v2/project/{}", project_id)
    }
}

pub fn build_version_file_url(hash: &str, algorithm: &str) -> String {
    format!("https://api.modrinth.com/v2/version_file/{}?algorithm={}", hash, algorithm)
}

// -----------------------------------------------------------------------------
// C ABI Bindings
// -----------------------------------------------------------------------------

#[no_mangle]
pub extern "C" fn libre_modrinth_build_search_url(
    c_query: *const c_char,
    c_project_type: *const c_char,
    c_mc_version: *const c_char,
    c_loader: *const c_char,
    c_category: *const c_char,
    limit: i32,
    offset: i32,
    c_index: *const c_char,
    out_buf: *mut c_char,
    out_buf_len: usize,
) -> i32 {
    if out_buf.is_null() || out_buf_len == 0 {
        return -1;
    }

    let to_str = |p: *const c_char| -> Option<&str> {
        if p.is_null() {
            None
        } else {
            unsafe { CStr::from_ptr(p).to_str().ok() }
        }
    };

    let query = to_str(c_query).unwrap_or("");
    let project_type = to_str(c_project_type).unwrap_or("mod");
    let mc_ver = to_str(c_mc_version);
    let loader = to_str(c_loader);
    let category = to_str(c_category);
    let index = to_str(c_index).unwrap_or("relevance");

    let url = build_search_url(
        query,
        project_type,
        mc_ver,
        loader,
        category,
        limit.max(1) as usize,
        offset.max(0) as usize,
        index,
    );

    let bytes = url.as_bytes();
    if bytes.len() + 1 > out_buf_len {
        return -2;
    }

    unsafe {
        std::ptr::copy_nonoverlapping(bytes.as_ptr(), out_buf as *mut u8, bytes.len());
        *out_buf.add(bytes.len()) = 0;
    }
    bytes.len() as i32
}

#[no_mangle]
pub extern "C" fn libre_modrinth_build_project_url(
    c_project_id: *const c_char,
    get_versions: bool,
    out_buf: *mut c_char,
    out_buf_len: usize,
) -> i32 {
    if c_project_id.is_null() || out_buf.is_null() || out_buf_len == 0 {
        return -1;
    }

    let project_id = match unsafe { CStr::from_ptr(c_project_id).to_str() } {
        Ok(s) => s,
        Err(_) => return -1,
    };

    let url = build_project_url(project_id, get_versions);
    let bytes = url.as_bytes();
    if bytes.len() + 1 > out_buf_len {
        return -2;
    }

    unsafe {
        std::ptr::copy_nonoverlapping(bytes.as_ptr(), out_buf as *mut u8, bytes.len());
        *out_buf.add(bytes.len()) = 0;
    }
    bytes.len() as i32
}

#[no_mangle]
pub extern "C" fn libre_modrinth_sha512(
    data: *const u8,
    len: usize,
    out_hex: *mut c_char,
    out_hex_len: usize,
) -> i32 {
    if data.is_null() || out_hex.is_null() || out_hex_len < 129 {
        return -1;
    }

    let slice = unsafe { std::slice::from_raw_parts(data, len) };
    let hex = sha512_hex(slice);
    let bytes = hex.as_bytes();

    unsafe {
        std::ptr::copy_nonoverlapping(bytes.as_ptr(), out_hex as *mut u8, 128);
        *out_hex.add(128) = 0;
    }
    128
}

#[no_mangle]
pub extern "C" fn libre_modrinth_verify_sha512(
    data: *const u8,
    len: usize,
    expected_hex: *const c_char,
) -> bool {
    if data.is_null() || expected_hex.is_null() {
        return false;
    }

    let expected = match unsafe { CStr::from_ptr(expected_hex).to_str() } {
        Ok(s) => s,
        Err(_) => return false,
    };

    let slice = unsafe { std::slice::from_raw_parts(data, len) };
    verify_sha512(slice, expected)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_sha512_empty() {
        let expected = "cf83e1357eefb8bdf1542850d66d8007d620e4050b5715dc83f4a921d36ce9ce47d0d13c5d85f2b0ff8318d2877eec2f63b931bd47417a81a538327af927da3e";
        assert_eq!(sha512_hex(b""), expected);
        assert!(verify_sha512(b"", expected));
    }

    #[test]
    fn test_sha512_abc() {
        let expected = "ddaf35a193617abacc417349ae20413112e6fa4e89a97ea20a9eeee64b55d39a2192992a274fc1a836ba3c23a3feebbd454d4423643ce80e2a9ac94fa54ca49f";
        assert_eq!(sha512_hex(b"abc"), expected);
        assert!(verify_sha512(b"abc", expected));
    }

    #[test]
    fn test_sha512_long() {
        let text = b"The quick brown fox jumps over the lazy dog";
        let expected = "07e547d9586f6a73f73fbac0435ed76951218fb7d0c8d788a309d785436bbb642e93a252a954f23912547d1e8a3b5ed6e1bfd7097821233fa0538f3db854fee6";
        assert_eq!(sha512_hex(text), expected);
        assert!(verify_sha512(text, expected));
        assert!(!verify_sha512(text, "invalid"));
    }

    #[test]
    fn test_build_search_url() {
        let url = build_search_url("sodium", "mod", Some("1.21.1"), Some("fabric"), None, 20, 0, "relevance");
        assert!(url.starts_with("https://api.modrinth.com/v2/search?query=sodium"));
        assert!(url.contains("limit=20"));
        assert!(url.contains("offset=0"));
    }

    #[test]
    fn test_build_project_url() {
        let url = build_project_url("AANobbMI", true);
        assert_eq!(url, "https://api.modrinth.com/v2/project/AANobbMI/version");

        let url2 = build_project_url("sodium", false);
        assert_eq!(url2, "https://api.modrinth.com/v2/project/sodium");
    }

    #[test]
    fn test_c_abi() {
        let mut buf = [0 as c_char; 256];
        let id = std::ffi::CString::new("test-mod").unwrap();
        let res = libre_modrinth_build_project_url(id.as_ptr(), false, buf.as_mut_ptr(), buf.len());
        assert!(res > 0);
        let out_str = unsafe { CStr::from_ptr(buf.as_ptr()).to_str().unwrap() };
        assert_eq!(out_str, "https://api.modrinth.com/v2/project/test-mod");
    }
}
