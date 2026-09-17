#!/usr/bin/env bash
# libffi (static) for Android, installed into the per-abi dependency prefix.
set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/../env.sh"
toolchain_env "${ABI:?ABI is required}"

ver="$LIBFFI_VER"
tarball="$SRC_DIR/libffi-$ver.tar.gz"
fetch "https://github.com/libffi/libffi/releases/download/v$ver/libffi-$ver.tar.gz" "$tarball"

srcdir="$TOOL_BUILD/libffi-$ver"
prefix="$(deps_prefix)"
if [[ ! -f "$srcdir/configure" ]]; then
    log "extract libffi-$ver"
    extract_archive "$tarball" "$srcdir"
fi
cd "$srcdir"

if [[ ! -f config.status ]]; then
    log "configure libffi for $ABI (api $ANDROID_API)"
    ./configure \
        --host="$CROSS_HOST" \
        --prefix="$prefix" \
        --disable-shared \
        --enable-static \
        --disable-docs \
        >"$TOOL_BUILD/libffi-config.log" 2>&1
fi

log "make libffi"
if ! make -j"$(jobs)" >"$TOOL_BUILD/libffi-build.log" 2>&1; then
    tail -40 "$TOOL_BUILD/libffi-build.log"
    die "libffi build failed, see $TOOL_BUILD/libffi-build.log"
fi
make install >>"$TOOL_BUILD/libffi-build.log" 2>&1
log "libffi installed -> $prefix"
