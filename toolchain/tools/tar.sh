#!/usr/bin/env bash
# GNU tar for Android (more featureful than the busybox applet).
set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/../env.sh"
toolchain_env "${ABI:?ABI is required}"

ver="${TAR_VER:-1.35}"
tarball="$SRC_DIR/tar-$ver.tar.xz"
fetch "https://ftp.gnu.org/gnu/tar/tar-$ver.tar.xz" "$tarball"

srcdir="$TOOL_BUILD/tar-$ver"
if [[ ! -f "$srcdir/configure" ]]; then
    log "extract tar-$ver"
    extract_archive "$tarball" "$srcdir"
fi
cd "$srcdir"

if [[ ! -f config.status ]]; then
    log "configure tar for $ABI (api $ANDROID_API)"
    ./configure --host="$CROSS_HOST" --prefix=/ --disable-nls \
        --without-selinux --without-posix-acls --without-xattrs \
        >"$TOOL_BUILD/tar-config.log" 2>&1
fi

log "make tar"
if ! make -j"$(jobs)" >"$TOOL_BUILD/tar-build.log" 2>&1; then
    tail -40 "$TOOL_BUILD/tar-build.log"
    die "tar build failed"
fi
make DESTDIR="$TOOL_OUT" install >>"$TOOL_BUILD/tar-build.log" 2>&1
rm -rf "$TOOL_OUT/share/man" "$TOOL_OUT/share/info" "$TOOL_OUT/share/locale" "$TOOL_OUT/include"
strip_binaries "$TOOL_OUT"
log "tar: $(file -b "$TOOL_OUT/bin/tar")"
