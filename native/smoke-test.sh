#!/usr/bin/env bash
set -euo pipefail
NATIVE_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
bash "$NATIVE_ROOT/prepare-upstream.sh"
export RUSTUP_TOOLCHAIN=1.95.0
export CARGO_TARGET_DIR="${CARGO_TARGET_DIR:-$NATIVE_ROOT/host-target}"
cargo build --locked --manifest-path "$NATIVE_ROOT/Cargo.toml" --bin codex-helper --bin codex-smoke --jobs "${NATIVE_BUILD_JOBS:-4}"
scratch="$(mktemp -d)"
trap 'rm -rf "$scratch"' EXIT
CODEX_HOME="$scratch/runtime/codex" "$CARGO_TARGET_DIR/debug/codex-smoke" "$scratch/runtime" "$CARGO_TARGET_DIR/debug/codex-helper"
