#!/usr/bin/env bash
# bzip2 for Android (bzip2/bunzip2/bzcat).  tar reads and writes -j archives
# through this, and .bz2 files show up in real repositories.
set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/../env.sh"
toolchain_env "${ABI:?ABI is required}"

ver="$BZIP2_VER"
tarball="$SRC_DIR/bzip2-$ver.tar.gz"
fetch "https://sourceware.org/pub/bzip2/bzip2-$ver.tar.gz" "$tarball"

srcdir="$TOOL_BUILD/bzip2-$ver"
if [[ ! -f "$srcdir/Makefile" ]]; then
    log "extract bzip2-$ver"
    extract_archive "$tarball" "$srcdir"
fi
cd "$srcdir"

log "make bzip2"
if ! make -j"$(jobs)" CC="$CC" AR="$AR" RANLIB="$RANLIB" \
    CFLAGS="$CFLAGS" LDFLAGS="$LDFLAGS" bzip2 bzip2recover \
    >"$TOOL_BUILD/bzip2-build.log" 2>&1; then
    tail -40 "$TOOL_BUILD/bzip2-build.log"
    die "bzip2 build failed"
fi

install -m 755 bzip2 "$TOOL_OUT/bin/bzip2"
install -m 755 bzip2recover "$TOOL_OUT/bin/bzip2recover"
ln -sf bzip2 "$TOOL_OUT/bin/bunzip2"
ln -sf bzip2 "$TOOL_OUT/bin/bzcat"
"$STRIP" --strip-unneeded "$TOOL_OUT/bin/bzip2" "$TOOL_OUT/bin/bzip2recover" 2>/dev/null || true
log "bzip2: $(file -b "$TOOL_OUT/bin/bzip2")"
