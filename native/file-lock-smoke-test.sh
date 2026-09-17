#!/usr/bin/env bash
set -euo pipefail
NATIVE_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$NATIVE_ROOT/android-env.sh" "${1:-arm64-v8a}"
cargo build -Z build-std=std,panic_unwind --release --locked \
    --manifest-path "$NATIVE_ROOT/probes/file-lock/Cargo.toml" --target "$RUST_TARGET" \
    --jobs "${NATIVE_BUILD_JOBS:-4}"
remote="$(adb shell mktemp -d /data/local/tmp/codex-lock-smoke.XXXXXX | tr -d '\r')"
[[ "$remote" == /data/local/tmp/codex-lock-smoke.* ]] || { echo "Invalid device scratch path" >&2; exit 1; }
trap 'adb shell rm -r "$remote" >/dev/null' EXIT
adb push "$CARGO_TARGET_DIR/$RUST_TARGET/release/codex-android-file-lock-probe" "$remote/probe" >/dev/null
adb shell chmod 700 "$remote/probe"
adb shell "$remote/probe" "$remote/file.lock"
