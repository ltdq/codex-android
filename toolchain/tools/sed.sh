#!/usr/bin/env bash
# GNU sed for Android.
set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/../env.sh"
toolchain_env "${ABI:?ABI is required}"

ver="$SED_VER"
tarball="$SRC_DIR/sed-$ver.tar.xz"
fetch "https://ftp.gnu.org/gnu/sed/sed-$ver.tar.xz" "$tarball"

srcdir="$TOOL_BUILD/sed-$ver"
if [[ ! -f "$srcdir/configure" ]]; then
    log "extract sed-$ver"
    extract_archive "$tarball" "$srcdir"
fi
cd "$srcdir"

if [[ ! -f config.status ]]; then
    log "configure sed for $ABI (api $ANDROID_API)"
    ./configure --host="$CROSS_HOST" --prefix=/ --disable-nls >"$TOOL_BUILD/sed-config.log" 2>&1
fi

log "make sed"
if ! make -j"$(jobs)" >"$TOOL_BUILD/sed-build.log" 2>&1; then
    tail -40 "$TOOL_BUILD/sed-build.log"
    die "sed build failed"
fi
make DESTDIR="$TOOL_OUT" install >>"$TOOL_BUILD/sed-build.log" 2>&1
rm -rf "$TOOL_OUT/share/man" "$TOOL_OUT/share/info" "$TOOL_OUT/share/locale" "$TOOL_OUT/include"
strip_binaries "$TOOL_OUT"
log "sed: $(file -b "$TOOL_OUT/bin/sed")"
