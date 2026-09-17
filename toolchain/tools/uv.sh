#!/usr/bin/env bash
# uv (Python package manager) for Android via cargo.
# Note: uv can manage venvs and pure-python packages with the bundled python3;
# building C extensions on-device is not supported (no clang in the APK).
set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/../env.sh"
toolchain_env "${ABI:?ABI is required}"

ver="${UV_VER:-0.12.14}"
crate="$SRC_DIR/uv-$ver.crate"
fetch "https://static.crates.io/crates/uv/uv-$ver.crate" "$crate"

srcdir="$TOOL_BUILD/uv-$ver"
if [[ ! -f "$srcdir/Cargo.toml" ]]; then
    log "extract uv-$ver"
    extract_archive "$crate" "$srcdir"
fi

# uv's MSRV is newer than codex's pinned 1.95 toolchain.
export RUSTUP_TOOLCHAIN="${UV_RUST_TOOLCHAIN:-1.97.1}"
cargo_env

log "cargo build uv for $ABI ($RUST_TARGET)"
cargo build --release --target "$RUST_TARGET" --manifest-path "$srcdir/Cargo.toml"

install -m 755 "$CARGO_TARGET_DIR/$RUST_TARGET/release/uv" "$TOOL_OUT/bin/uv"
"$STRIP" --strip-unneeded "$TOOL_OUT/bin/uv" 2>/dev/null || true
log "uv: $(file -b "$TOOL_OUT/bin/uv")"
