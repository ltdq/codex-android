#!/usr/bin/env bash
# GNU bc/dc for Android: arbitrary-precision arithmetic for shell pipelines
# (`echo 'scale=2; 1/3' | bc`).  Small, no dependencies beyond libc/libm.
set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/../env.sh"
toolchain_env "${ABI:?ABI is required}"

ver="$BC_VER"
tarball="$SRC_DIR/bc-$ver.tar.gz"
fetch "https://ftp.gnu.org/gnu/bc/bc-$ver.tar.gz" "$tarball"

srcdir="$TOOL_BUILD/bc-$ver"
if [[ ! -f "$srcdir/configure" ]]; then
    log "extract bc-$ver"
    extract_archive "$tarball" "$srcdir"
fi
cd "$srcdir"

if [[ ! -f config.status ]]; then
    log "configure bc for $ABI (api $ANDROID_API)"
    ./configure --host="$CROSS_HOST" --prefix=/ --disable-nls \
        >"$TOOL_BUILD/bc-config.log" 2>&1
fi

log "make bc"
if ! make -j"$(jobs)" >"$TOOL_BUILD/bc-build.log" 2>&1; then
    tail -40 "$TOOL_BUILD/bc-build.log"
    die "bc build failed"
fi
make DESTDIR="$TOOL_OUT" install >>"$TOOL_BUILD/bc-build.log" 2>&1
rm -rf "$TOOL_OUT/share/man" "$TOOL_OUT/share/info" "$TOOL_OUT/share/locale" "$TOOL_OUT/include"
strip_binaries "$TOOL_OUT"
log "bc: $(file -b "$TOOL_OUT/bin/bc")"
