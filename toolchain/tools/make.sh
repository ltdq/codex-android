#!/usr/bin/env bash
# GNU make for Android.
set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/../env.sh"
toolchain_env "${ABI:?ABI is required}"

ver="$MAKE_VER"
tarball="$SRC_DIR/make-$ver.tar.gz"
fetch "https://ftp.gnu.org/gnu/make/make-$ver.tar.gz" "$tarball"

srcdir="$TOOL_BUILD/make-$ver"
if [[ ! -f "$srcdir/configure" ]]; then
    log "extract make-$ver"
    extract_archive "$tarball" "$srcdir"
    for p in "$PATCH_DIR"/make/*.patch; do
        apply_patch "$p" "$srcdir"
    done
fi
cd "$srcdir"

if [[ ! -f config.status ]]; then
    log "configure make for $ABI (api $ANDROID_API)"
    ./configure --host="$CROSS_HOST" --prefix=/ --disable-nls \
        ac_cv_func_confstr=no >"$TOOL_BUILD/make-config.log" 2>&1
fi

log "make"
if ! make -j"$(jobs)" >"$TOOL_BUILD/make-build.log" 2>&1; then
    tail -40 "$TOOL_BUILD/make-build.log"
    die "make build failed"
fi
make DESTDIR="$TOOL_OUT" install >>"$TOOL_BUILD/make-build.log" 2>&1
rm -rf "$TOOL_OUT/share/man" "$TOOL_OUT/share/info" "$TOOL_OUT/share/locale" "$TOOL_OUT/include"
strip_binaries "$TOOL_OUT"
log "make: $(file -b "$TOOL_OUT/bin/make")"
