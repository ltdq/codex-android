#!/usr/bin/env bash
# GNU findutils for Android: the reference `find` and `xargs` (full -printf,
# -exec ..., -newer/-perm predicates).
set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/../env.sh"
toolchain_env "${ABI:?ABI is required}"

ver="$FINDUTILS_VER"
tarball="$SRC_DIR/findutils-$ver.tar.xz"
fetch "https://ftp.gnu.org/gnu/findutils/findutils-$ver.tar.xz" "$tarball"

srcdir="$TOOL_BUILD/findutils-$ver"
if [[ ! -f "$srcdir/configure" ]]; then
    log "extract findutils-$ver"
    extract_archive "$tarball" "$srcdir"
fi
cd "$srcdir"

if [[ ! -f config.status ]]; then
    log "configure findutils for $ABI (api $ANDROID_API)"
    ./configure --host="$CROSS_HOST" --prefix=/ --disable-nls \
        --without-selinux >"$TOOL_BUILD/findutils-config.log" 2>&1
fi

log "make findutils"
if ! make -j"$(jobs)" >"$TOOL_BUILD/findutils-build.log" 2>&1; then
    tail -40 "$TOOL_BUILD/findutils-build.log"
    die "findutils build failed"
fi
make DESTDIR="$TOOL_OUT" install >>"$TOOL_BUILD/findutils-build.log" 2>&1

# locate/updatedb need a database that nothing builds on-device.
rm -f "$TOOL_OUT/bin/locate" "$TOOL_OUT/bin/updatedb" "$TOOL_OUT/libexec/updatedb"
rmdir "$TOOL_OUT/libexec" 2>/dev/null || true
rm -rf "$TOOL_OUT/share/man" "$TOOL_OUT/share/info" "$TOOL_OUT/share/locale" "$TOOL_OUT/include"
strip_binaries "$TOOL_OUT"
log "findutils: $(file -b "$TOOL_OUT/bin/find")"
