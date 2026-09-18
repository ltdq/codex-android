#!/usr/bin/env bash
# tree for Android: recursive directory listings in the standard format
# (agents inspect trees constantly).
set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/../env.sh"
toolchain_env "${ABI:?ABI is required}"

ver="$TREE_VER"
tarball="$SRC_DIR/tree-$ver.tar.gz"
fetch "https://github.com/Old-Man-Programmer/tree/archive/refs/tags/$ver.tar.gz" "$tarball"

srcdir="$TOOL_BUILD/tree-$ver"
if [[ ! -f "$srcdir/Makefile" ]]; then
    log "extract tree-$ver"
    extract_archive "$tarball" "$srcdir"
fi
cd "$srcdir"

log "make tree"
if ! make -j"$(jobs)" CC="$CC" CFLAGS="$CFLAGS" LDFLAGS="$LDFLAGS" \
    >"$TOOL_BUILD/tree-build.log" 2>&1; then
    tail -40 "$TOOL_BUILD/tree-build.log"
    die "tree build failed"
fi

install -m 755 tree "$TOOL_OUT/bin/tree"
"$STRIP" --strip-unneeded "$TOOL_OUT/bin/tree" 2>/dev/null || true
log "tree: $(file -b "$TOOL_OUT/bin/tree")"
