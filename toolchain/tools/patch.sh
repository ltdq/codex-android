#!/usr/bin/env bash
# GNU patch for Android: applies git/unified diffs with the usual options
# (-p, -R, --dry-run, ...), which the busybox applet only partially supports.
set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/../env.sh"
toolchain_env "${ABI:?ABI is required}"

ver="$PATCH_VER"
tarball="$SRC_DIR/patch-$ver.tar.xz"
fetch "https://ftp.gnu.org/gnu/patch/patch-$ver.tar.xz" "$tarball"

srcdir="$TOOL_BUILD/patch-$ver"
if [[ ! -f "$srcdir/configure" ]]; then
    log "extract patch-$ver"
    extract_archive "$tarball" "$srcdir"
fi
cd "$srcdir"

if [[ ! -f config.status ]]; then
    log "configure patch for $ABI (api $ANDROID_API)"
    ./configure --host="$CROSS_HOST" --prefix=/ --disable-nls \
        >"$TOOL_BUILD/patch-config.log" 2>&1
fi

log "make patch"
if ! make -j"$(jobs)" >"$TOOL_BUILD/patch-build.log" 2>&1; then
    tail -60 "$TOOL_BUILD/patch-build.log"
    die "patch build failed, see $TOOL_BUILD/patch-build.log"
fi

log "install patch -> $TOOL_OUT"
make DESTDIR="$TOOL_OUT" install >>"$TOOL_BUILD/patch-build.log" 2>&1
rm -rf "$TOOL_OUT/share/man" "$TOOL_OUT/share/info"
strip_binaries "$TOOL_OUT"
log "patch: $(file -b "$TOOL_OUT/bin/patch")"
