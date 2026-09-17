#!/usr/bin/env bash
# ripgrep for Android via plain cargo + the NDK linker (no cargo-ndk needed).
set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/../env.sh"
toolchain_env "${ABI:?ABI is required}"

ver="$RIPGREP_VER"
crate="$SRC_DIR/ripgrep-$ver.crate"
fetch "https://static.crates.io/crates/ripgrep/ripgrep-$ver.crate" "$crate"

srcdir="$TOOL_BUILD/ripgrep-$ver"
if [[ ! -f "$srcdir/Cargo.toml" ]]; then
    log "extract ripgrep-$ver"
    extract_archive "$crate" "$srcdir"
fi

target_env="CARGO_TARGET_$(echo "$RUST_TARGET" | tr 'a-z-' 'A-Z_')_LINKER"
export CARGO_TARGET_DIR="$TOOL_BUILD/cargo-target"
export "$target_env=$CC"
export "CC_${RUST_TARGET//-/_}=$CC"
export "AR_${RUST_TARGET//-/_}=$AR"
export "RANLIB_${RUST_TARGET//-/_}=$RANLIB"
export CARGO_PROFILE_RELEASE_LTO=fat
export CARGO_PROFILE_RELEASE_CODEGEN_UNITS=1
export CARGO_PROFILE_RELEASE_OPT_LEVEL=3

log "cargo build ripgrep for $ABI ($RUST_TARGET)"
cargo build --release --target "$RUST_TARGET" --manifest-path "$srcdir/Cargo.toml"

install -m 755 "$CARGO_TARGET_DIR/$RUST_TARGET/release/rg" "$TOOL_OUT/bin/rg"
"$STRIP" --strip-unneeded "$TOOL_OUT/bin/rg" 2>/dev/null || true
log "rg: $(file -b "$TOOL_OUT/bin/rg")"
