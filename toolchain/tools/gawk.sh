#!/usr/bin/env bash
# GNU awk for Android (provides awk + gawk).
set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/../env.sh"
toolchain_env "${ABI:?ABI is required}"

ver="$GAWK_VER"
tarball="$SRC_DIR/gawk-$ver.tar.xz"
fetch "https://ftp.gnu.org/gnu/gawk/gawk-$ver.tar.xz" "$tarball"

srcdir="$TOOL_BUILD/gawk-$ver"
if [[ ! -f "$srcdir/configure" ]]; then
    log "extract gawk-$ver"
    extract_archive "$tarball" "$srcdir"
    for p in "$PATCH_DIR"/gawk/*.patch; do
        apply_patch "$p" "$srcdir"
    done
fi
cd "$srcdir"

if [[ ! -f config.status ]]; then
    log "configure gawk for $ABI (api $ANDROID_API)"
    ./configure --host="$CROSS_HOST" --prefix=/ --disable-nls >"$TOOL_BUILD/gawk-config.log" 2>&1
fi

log "make gawk"
if ! make -j"$(jobs)" >"$TOOL_BUILD/gawk-build.log" 2>&1; then
    tail -40 "$TOOL_BUILD/gawk-build.log"
    die "gawk build failed"
fi
make DESTDIR="$TOOL_OUT" install >>"$TOOL_BUILD/gawk-build.log" 2>&1
rm -rf "$TOOL_OUT/share/man" "$TOOL_OUT/share/info" "$TOOL_OUT/share/locale" "$TOOL_OUT/include"
strip_binaries "$TOOL_OUT"
log "gawk: $(file -b "$TOOL_OUT/bin/gawk")"
