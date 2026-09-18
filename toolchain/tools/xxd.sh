#!/usr/bin/env bash
# xxd for Android, built from the single-file vim implementation.  Agents use
# `xxd`/`xxd -r` for binary and hex round-trips; only that one source file is
# compiled out of the vim tarball.
set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/../env.sh"
toolchain_env "${ABI:?ABI is required}"

ver="$VIM_VER"
tarball="$SRC_DIR/vim-$ver.tar.gz"
fetch "https://github.com/vim/vim/archive/refs/tags/v$ver.tar.gz" "$tarball"

srcdir="$TOOL_BUILD/vim-$ver"
if [[ ! -f "$srcdir/src/xxd/xxd.c" ]]; then
    log "extract vim-$ver"
    extract_archive "$tarball" "$srcdir"
fi

log "compile xxd"
# shellcheck disable=SC2086
"$CC" $CFLAGS $LDFLAGS -o "$TOOL_OUT/bin/xxd" "$srcdir/src/xxd/xxd.c"
"$STRIP" --strip-unneeded "$TOOL_OUT/bin/xxd" 2>/dev/null || true
log "xxd: $(file -b "$TOOL_OUT/bin/xxd")"
