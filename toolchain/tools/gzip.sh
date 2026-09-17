#!/usr/bin/env bash
# GNU gzip for Android (gzip/gunzip/zcat).
set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/../env.sh"
toolchain_env "${ABI:?ABI is required}"

ver="${GZIP_VER:-1.14}"
tarball="$SRC_DIR/gzip-$ver.tar.xz"
fetch "https://ftp.gnu.org/gnu/gzip/gzip-$ver.tar.xz" "$tarball"

srcdir="$TOOL_BUILD/gzip-$ver"
if [[ ! -f "$srcdir/configure" ]]; then
    log "extract gzip-$ver"
    extract_archive "$tarball" "$srcdir"
fi
cd "$srcdir"

if [[ ! -f config.status ]]; then
    log "configure gzip for $ABI (api $ANDROID_API)"
    ./configure --host="$CROSS_HOST" --prefix=/ --disable-nls >"$TOOL_BUILD/gzip-config.log" 2>&1
fi

log "make gzip"
if ! make -j"$(jobs)" >"$TOOL_BUILD/gzip-build.log" 2>&1; then
    tail -40 "$TOOL_BUILD/gzip-build.log"
    die "gzip build failed"
fi
make DESTDIR="$TOOL_OUT" install >>"$TOOL_BUILD/gzip-build.log" 2>&1
rm -rf "$TOOL_OUT/share/man" "$TOOL_OUT/share/info" "$TOOL_OUT/share/locale" "$TOOL_OUT/include"
strip_binaries "$TOOL_OUT"
log "gzip: $(file -b "$TOOL_OUT/bin/gzip")"
