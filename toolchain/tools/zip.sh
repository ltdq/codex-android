#!/usr/bin/env bash
# Info-ZIP zip for Android: create zip archives with the standard CLI
# (`zip -r out.zip dir`).  7zz can also do this, but not under `zip`.
set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/../env.sh"
toolchain_env "${ABI:?ABI is required}"

ver="$ZIP_VER"
stem="zip${ver//./}"
tarball="$SRC_DIR/$stem.tar.gz"
fetch "https://downloads.sourceforge.net/infozip/$stem.tar.gz" "$tarball"

srcdir="$TOOL_BUILD/zip-$ver"
if [[ ! -f "$srcdir/zip.c" ]]; then
    log "extract zip-$ver"
    extract_archive "$tarball" "$srcdir"
    for p in "$PATCH_DIR"/zip/*.patch; do
        apply_patch "$p" "$srcdir"
    done
fi
cd "$srcdir"

log "make zip"
if ! make -j"$(jobs)" -f unix/Makefile generic \
    CC="$CC $CFLAGS $LDFLAGS" LD="$CC $LDFLAGS" \
    >"$TOOL_BUILD/zip-build.log" 2>&1; then
    tail -40 "$TOOL_BUILD/zip-build.log"
    die "zip build failed"
fi

install -m 755 zip "$TOOL_OUT/bin/zip"
"$STRIP" --strip-unneeded "$TOOL_OUT/bin/zip" 2>/dev/null || true
log "zip: $(file -b "$TOOL_OUT/bin/zip")"
