#!/usr/bin/env bash
set -euo pipefail

NATIVE_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
bash "$NATIVE_ROOT/prepare-upstream.sh"
source "$NATIVE_ROOT/android-env.sh" "${1:-arm64-v8a}"
rustup target add --toolchain "$RUSTUP_TOOLCHAIN" "$RUST_TARGET"
build_args=(--release --lib --bin codex-helper)
if [[ "${NATIVE_BUILD_SMOKE:-0}" == 1 ]]; then build_args+=(--bin codex-smoke); fi
cargo build -Z build-std=std,panic_unwind --locked --manifest-path "$NATIVE_ROOT/Cargo.toml" --target "$RUST_TARGET" \
    "${build_args[@]}" --jobs "${NATIVE_BUILD_JOBS:-4}"
mkdir -p "$NATIVE_ROOT/out/$ABI"
install -m 755 "$CARGO_TARGET_DIR/$RUST_TARGET/release/libcodex_android_jni.so" "$NATIVE_ROOT/out/$ABI/"
install -m 755 "$CARGO_TARGET_DIR/$RUST_TARGET/release/codex-helper" "$NATIVE_ROOT/out/$ABI/libcodex_helper.so"
"$STRIP" --strip-unneeded "$NATIVE_ROOT/out/$ABI/libcodex_android_jni.so" "$NATIVE_ROOT/out/$ABI/libcodex_helper.so"
for library in "$NATIVE_ROOT/out/$ABI/"*.so; do
    "$NDK_BIN/llvm-readelf" --program-headers --wide "$library" |
        awk '$1 == "LOAD" { print; if ($NF != "0x4000") bad = 1; count++ } END { exit (bad || !count) }'
done
