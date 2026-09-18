#!/usr/bin/env bash
# GNU xz-utils for Android: full xz/lzma tooling so that `xz -z` and
# `tar -cJf` work, not just decompression.
set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/../env.sh"
toolchain_env "${ABI:?ABI is required}"

ver="$XZ_VER"
tarball="$SRC_DIR/xz-$ver.tar.gz"
fetch "https://github.com/tukaani-project/xz/releases/download/v$ver/xz-$ver.tar.gz" "$tarball"

srcdir="$TOOL_BUILD/xz-$ver"
if [[ ! -f "$srcdir/configure" ]]; then
    log "extract xz-$ver"
    extract_archive "$tarball" "$srcdir"
fi
cd "$srcdir"

if [[ ! -f config.status ]]; then
    log "configure xz for $ABI (api $ANDROID_API)"
    ./configure --host="$CROSS_HOST" --prefix=/ --disable-nls \
        --disable-shared --enable-static \
        >"$TOOL_BUILD/xz-config.log" 2>&1
fi

log "make xz"
if ! make -j"$(jobs)" >"$TOOL_BUILD/xz-build.log" 2>&1; then
    tail -60 "$TOOL_BUILD/xz-build.log"
    die "xz build failed, see $TOOL_BUILD/xz-build.log"
fi

log "install xz -> $TOOL_OUT"
make DESTDIR="$TOOL_OUT" install >>"$TOOL_BUILD/xz-build.log" 2>&1
rm -rf "$TOOL_OUT/share/man" "$TOOL_OUT/share/doc" "$TOOL_OUT/include" \
    "$TOOL_OUT/lib/pkgconfig" "$TOOL_OUT/lib/liblzma.a" "$TOOL_OUT/lib/liblzma.la"
strip_binaries "$TOOL_OUT"
log "xz: $(file -b "$TOOL_OUT/bin/xz")"
