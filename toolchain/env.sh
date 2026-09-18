#!/usr/bin/env bash
# Shared environment for cross-compiling Android binaries.
# Sourced by build.sh; tool scripts receive the exported variables.

set -euo pipefail

TOOLCHAIN_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SRC_DIR="$TOOLCHAIN_ROOT/src"
BUILD_ROOT="$TOOLCHAIN_ROOT/build"
OUT_ROOT="$TOOLCHAIN_ROOT/out"
PATCH_DIR="$TOOLCHAIN_ROOT/patches"

ANDROID_API="${ANDROID_API:-36}"

BASH_VER="${BASH_VER:-5.3}"
GIT_VER="${GIT_VER:-2.55.0}"
RIPGREP_VER="${RIPGREP_VER:-15.2.0}"
OPENSSL_VER="${OPENSSL_VER:-3.5.8}"
CURL_VER="${CURL_VER:-8.22.0}"
LIBFFI_VER="${LIBFFI_VER:-3.8.0}"
PYTHON_VER="${PYTHON_VER:-3.14.7}"
BUSYBOX_VER="${BUSYBOX_VER:-1.38.0}"
MAKE_VER="${MAKE_VER:-4.4.1}"
SED_VER="${SED_VER:-4.10}"
GAWK_VER="${GAWK_VER:-5.4.1}"
SQLITE_VER="${SQLITE_VER:-3530400}"
JQ_VER="${JQ_VER:-1.8.2}"
ONIGURUMA_VER="${ONIGURUMA_VER:-6.9.10}"
FILE_VER="${FILE_VER:-5.46}"
BUN_VER="${BUN_VER:-1.4.2}"
DIFFUTILS_VER="${DIFFUTILS_VER:-3.12}"
PATCH_VER="${PATCH_VER:-2.8}"
XZ_VER="${XZ_VER:-5.8.4}"
ZSTD_VER="${ZSTD_VER:-1.5.7}"
LLVM_VER="${LLVM_VER:-23.1.1}"
YQ_VER="${YQ_VER:-4.53.6}"
SHFMT_VER="${SHFMT_VER:-3.14.1}"
RUFF_VER="${RUFF_VER:-0.16.7}"
AST_GREP_VER="${AST_GREP_VER:-0.45.3}"
FD_VER="${FD_VER:-10.5.0}"

if [[ -z "${ANDROID_NDK_HOME:-}" ]]; then
    for sdk_root in "$HOME/Android/Sdk" "${ANDROID_HOME:-}" "${ANDROID_SDK_ROOT:-}" /opt/android-sdk; do
        [[ -d "$sdk_root/ndk" ]] || continue
        ANDROID_NDK_HOME="$sdk_root/ndk/$(ls -1 "$sdk_root/ndk" | sort -V | tail -1)"
        break
    done
fi
if [[ ! -d "${ANDROID_NDK_HOME:-}" ]]; then
    echo "error: Android NDK not found, set ANDROID_NDK_HOME" >&2
    exit 1
fi

HOST_TAG=linux-x86_64
NDK_BIN="$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/$HOST_TAG/bin"
NDK_SYSROOT="$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/$HOST_TAG/sysroot"

log() { printf '\033[1;34m==>\033[0m %s\n' "$*" >&2; }
die() { printf '\033[1;31merror:\033[0m %s\n' "$*" >&2; exit 1; }

# Android device ABI (arm64 only for now).
abi_clang_prefix() {
    case "$1" in
        arm64-v8a) echo aarch64-linux-android ;;
        *) die "unsupported abi: $1 (only arm64-v8a)" ;;
    esac
}

abi_rust_target() {
    case "$1" in
        arm64-v8a) echo aarch64-linux-android ;;
        *) die "unsupported abi: $1 (only arm64-v8a)" ;;
    esac
}

abi_host_triple() {
    case "$1" in
        arm64-v8a) echo aarch64-linux-android ;;
        *) die "unsupported abi: $1 (only arm64-v8a)" ;;
    esac
}

# Export CC/AR/... plus per-abi directories for the given abi.
toolchain_env() {
    local abi="$1" prefix
    prefix="$(abi_clang_prefix "$abi")"

    export ABI="$abi"
    export CROSS_PREFIX="$prefix"
    export CROSS_HOST="$(abi_host_triple "$abi")"
    export RUST_TARGET="$(abi_rust_target "$abi")"
    export ANDROID_NDK_HOME ANDROID_API NDK_BIN NDK_SYSROOT

    export CC="$NDK_BIN/${prefix}${ANDROID_API}-clang"
    export CXX="$NDK_BIN/${prefix}${ANDROID_API}-clang++"
    export AR="$NDK_BIN/llvm-ar"
    export RANLIB="$NDK_BIN/llvm-ranlib"
    export NM="$NDK_BIN/llvm-nm"
    export STRIP="$NDK_BIN/llvm-strip"
    export OBJCOPY="$NDK_BIN/llvm-objcopy"

    export CFLAGS="-O3 -fPIC -std=gnu17 -flto -fomit-frame-pointer -ffunction-sections -fdata-sections"
    export CXXFLAGS="-O3 -fPIC -std=gnu++17 -flto -fomit-frame-pointer -ffunction-sections -fdata-sections"
    export LDFLAGS="-Wl,--build-id=sha1 -flto -Wl,--gc-sections"

    export SRC_DIR BUILD_ROOT OUT_ROOT PATCH_DIR TOOLCHAIN_ROOT
    export TOOL_BUILD="$BUILD_ROOT/$abi"
    export TOOL_OUT="$OUT_ROOT/$abi"
    export PATH="$NDK_BIN:$PATH"

    [[ -x "$CC" ]] || die "compiler not found: $CC"
    mkdir -p "$SRC_DIR" "$TOOL_BUILD" "$TOOL_OUT/bin" "$TOOL_OUT/lib" "$TOOL_OUT/share"
}

# fetch <url> <dest-file>
fetch() {
    local url="$1" dest="$2"
    if [[ -s "$dest" ]]; then
        return 0
    fi
    mkdir -p "$(dirname "$dest")"
    log "fetch $url"
    curl -fL --retry 3 --connect-timeout 15 -o "$dest.part" "$url"
    mv "$dest.part" "$dest"
}

# extract_archive <archive> <dest-dir> [strip-components]
extract_archive() {
    local archive="$1" dest="$2" strip="${3:-1}"
    rm -rf "$dest"
    mkdir -p "$dest"
    case "$archive" in
        *.tar.gz|*.tgz|*.crate) tar -xzf "$archive" -C "$dest" --strip-components="$strip" ;;
        *.tar.xz) tar -xJf "$archive" -C "$dest" --strip-components="$strip" ;;
        *.tar.bz2) tar -xjf "$archive" -C "$dest" --strip-components="$strip" ;;
        *.zip) unzip -q "$archive" -d "$dest" ;;
        *) die "unknown archive type: $archive" ;;
    esac
}

# apply_patch <patch-file> <workdir>
apply_patch() {
    local patch="$1" workdir="$2"
    log "apply $(basename "$patch")"
    patch -p1 -d "$workdir" --forward --no-backup-if-mismatch <"$patch"
}

strip_binaries() {
    local dir="$1"
    find "$dir/bin" -type f -exec "$STRIP" --strip-unneeded {} + 2>/dev/null || true
}

# check_16kb_alignment <dir>: every loadable ELF in <dir> must be 16 KB page compatible.
#
# API 35+ devices with 16 KB pages refuse to exec or dlopen a library whose LOAD segments are not
# 16 KB aligned, and that failure appears on those devices only, so the build gates on it here.
# Non-ELF files (toolchain scripts that live under bin/) have no LOAD segments and are skipped;
# GO binaries use 0x10000, which is a larger multiple of 0x4000 and equally valid.
check_16kb_alignment() {
    local dir="$1" file align value bad=0 checked=0 reported=0
    while IFS= read -r -d '' file; do
        reported=0
        while IFS= read -r align; do
            [[ -n "$align" ]] || continue
            checked=$((checked + 1))
            value=$((align))
            if (( value < 16384 || value % 16384 != 0 )); then
                bad=1
                if (( reported == 0 )); then
                    reported=1
                    printf 'error: %s has a LOAD segment with p_align %s, not a multiple of 0x4000\n' \
                        "$file" "$align" >&2
                fi
            fi
        done < <("$NDK_BIN/llvm-readelf" --program-headers --wide "$file" 2>/dev/null |
            awk '$1 == "LOAD" { print $NF }')
    done < <(find "$dir" -type f -print0)
    if [[ $bad -ne 0 ]]; then
        die "$dir contains binaries that are not 16 KB page aligned"
    fi
    [[ $checked -gt 0 ]] || die "$dir has no loadable binaries to check"
}

jobs() {
    if command -v nproc >/dev/null; then nproc; else echo 4; fi
}

# ensure_tool <name>: build a tool dependency unless its stamp exists.
ensure_tool() {
    local tool="$1"
    if [[ -f "$TOOL_BUILD/.stamps/$tool" ]]; then
        return 0
    fi
    log "dependency: build $tool"
    mkdir -p "$TOOL_BUILD/.stamps"
    bash "$TOOLCHAIN_ROOT/tools/$tool.sh"
    touch "$TOOL_BUILD/.stamps/$tool"
}

# Shared prefix for cross-compiled third-party dependencies (openssl, libffi).
deps_prefix() {
    echo "$TOOL_BUILD/deps"
}

# fetch_crate <crate> <version>: download a crate from crates.io, extract it
# into $TOOL_BUILD/<crate>-<version> and print the directory.
fetch_crate() {
    local crate="$1" ver="$2"
    local file="$SRC_DIR/$crate-$ver.crate"
    local dir="$TOOL_BUILD/$crate-$ver"
    fetch "https://static.crates.io/crates/$crate/$crate-$ver.crate" "$file"
    if [[ ! -f "$dir/Cargo.toml" ]]; then
        log "extract $crate-$ver"
        extract_archive "$file" "$dir"
    fi
    printf '%s\n' "$dir"
}

# Export everything `cargo build --release --target $RUST_TARGET` needs for the
# NDK: linker/archiver per target, full LTO release profile and the libgcc
# shim some crates ask for (-lgcc does not exist in the NDK).
cargo_env() {
    export RUSTUP_TOOLCHAIN="${RUSTUP_TOOLCHAIN:-1.97.1}"
    export CARGO_TARGET_DIR="${CARGO_TARGET_DIR:-$TOOL_BUILD/cargo-target}"
    export "CARGO_TARGET_$(echo "$RUST_TARGET" | tr 'a-z-' 'A-Z_')_LINKER=$CC"
    export "CC_${RUST_TARGET//-/_}=$CC"
    export "AR_${RUST_TARGET//-/_}=$AR"
    export "RANLIB_${RUST_TARGET//-/_}=$RANLIB"
    export CARGO_PROFILE_RELEASE_LTO="${CARGO_PROFILE_RELEASE_LTO:-fat}"
    export CARGO_PROFILE_RELEASE_CODEGEN_UNITS="${CARGO_PROFILE_RELEASE_CODEGEN_UNITS:-1}"
    export CARGO_PROFILE_RELEASE_OPT_LEVEL="${CARGO_PROFILE_RELEASE_OPT_LEVEL:-3}"

    local shim="$TOOL_BUILD/libgcc-shim" builtins
    mkdir -p "$shim"
    builtins=$(echo "$NDK_BIN"/../lib/clang/*/lib/linux/libclang_rt.builtins-aarch64-android.a)
    [[ -f "$builtins" ]] || die "compiler-rt builtins not found"
    ln -sf "$builtins" "$shim/libgcc.a"
    export RUSTFLAGS="${RUSTFLAGS:-} -Lnative=$shim"
}
