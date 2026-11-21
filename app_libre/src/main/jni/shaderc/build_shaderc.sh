#!/bin/bash
set -euo pipefail

NDK_PATH="${ANDROID_NDK:-${ANDROID_NDK_ROOT:-/data/data/com.termux/files/home/termux-ndk}}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
JNILIBS_DIR="$(cd "${SCRIPT_DIR}/../../jniLibs" && pwd)"

mkdir -p "${JNILIBS_DIR}"

"${NDK_PATH}/ndk-build" \
    -C "${SCRIPT_DIR}" \
    NDK_PROJECT_PATH=. \
    NDK_MODULE_PATH="${NDK_PATH}/sources" \
    APP_BUILD_SCRIPT="${SCRIPT_DIR}/Android.mk" \
    NDK_APPLICATION_MK="${SCRIPT_DIR}/Application.mk" \
    -j$(nproc)

for abi in arm64-v8a armeabi-v7a x86 x86_64; do
    if [ -f "${SCRIPT_DIR}/libs/${abi}/libshaderc.so" ]; then
        mkdir -p "${JNILIBS_DIR}/${abi}"
        cp "${SCRIPT_DIR}/libs/${abi}/libshaderc.so" "${JNILIBS_DIR}/${abi}/libshaderc.so"
    fi
done
