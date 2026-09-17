#!/usr/bin/env bash

NATIVE_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$NATIVE_ROOT/../toolchain/env.sh"
ABI="${1:-arm64-v8a}"
toolchain_env "$ABI"
export RUSTUP_TOOLCHAIN=1.95.0
export CARGO_TARGET_DIR="$NATIVE_ROOT/target"
export CARGO_PROFILE_RELEASE_LTO=thin
export CARGO_PROFILE_RELEASE_CODEGEN_UNITS=8
export CARGO_PROFILE_RELEASE_OPT_LEVEL=2
cargo_env
# C build scripts also build host tools; scope NDK flags to the target.
export CFLAGS_aarch64_linux_android="-O2 -fPIC"
export CXXFLAGS_aarch64_linux_android="-O2 -fPIC -std=c++17"
export CXX_aarch64_linux_android="$CXX"
export CODEX_ANDROID_BUILTINS="$("$CC" --print-libgcc-file-name)"
unset CC CXX AR RANLIB CFLAGS CXXFLAGS LDFLAGS
export RUSTFLAGS="$RUSTFLAGS -C link-arg=-Wl,-z,max-page-size=16384"
export OPENSSL_DIR="$TOOL_BUILD/deps"
export OPENSSL_STATIC=1
export PKG_CONFIG_ALLOW_CROSS=1
export PKG_CONFIG_LIBDIR_aarch64_linux_android="$TOOL_BUILD/deps/lib/pkgconfig"
export LZMA_API_STATIC=1
bash "$NATIVE_ROOT/prepare-rust-std.sh"
export RUSTC="$NATIVE_ROOT/rustc-android.sh"
# Cargo's build-std remains unstable; the compiler and rust-src stay pinned together.
export RUSTC_BOOTSTRAP=1
