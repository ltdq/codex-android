#!/usr/bin/env bash
# Rust-based developer tools, cross-compiled with cargo (same recipe as uv):
#   ruff      Python linter + formatter
#   ast-grep  structural search/replace (sg)
#   fd        friendlier find
set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/../env.sh"
toolchain_env "${ABI:?ABI is required}"
cargo_env

build_install() {
    local crate="$1" ver="$2"; shift 2
    local dir
    dir="$(fetch_crate "$crate" "$ver")"
    log "cargo build $crate $ver"
    if ! cargo build --release --target "$RUST_TARGET" --manifest-path "$dir/Cargo.toml" "$@" \
        >>"$TOOL_BUILD/rust-tools-build.log" 2>&1; then
        tail -60 "$TOOL_BUILD/rust-tools-build.log"
        die "$crate build failed, see $TOOL_BUILD/rust-tools-build.log"
    fi
}

: >"$TOOL_BUILD/rust-tools-build.log"

build_install ruff "$RUFF_VER" --bin ruff
build_install ast-grep "$AST_GREP_VER" --bin ast-grep --bin sg
build_install fd-find "$FD_VER" --bin fd

outdir="$CARGO_TARGET_DIR/$RUST_TARGET/release"
install -m 755 "$outdir/ruff" "$TOOL_OUT/bin/ruff"
install -m 755 "$outdir/ast-grep" "$TOOL_OUT/bin/ast-grep"
ln -sf ast-grep "$TOOL_OUT/bin/sg"
install -m 755 "$outdir/fd" "$TOOL_OUT/bin/fd"
for tool in ruff ast-grep fd; do
    "$STRIP" --strip-unneeded "$TOOL_OUT/bin/$tool" 2>/dev/null || true
    log "$tool: $(file -b "$TOOL_OUT/bin/$tool")"
done
