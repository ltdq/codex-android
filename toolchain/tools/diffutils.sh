#!/usr/bin/env bash
# GNU diffutils for Android (diff/diff3/sdiff/cmp), used when reviewing and
# creating patches and directory diffs.
set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/../env.sh"
toolchain_env "${ABI:?ABI is required}"

ver="$DIFFUTILS_VER"
tarball="$SRC_DIR/diffutils-$ver.tar.xz"
fetch "https://ftp.gnu.org/gnu/diffutils/diffutils-$ver.tar.xz" "$tarball"

srcdir="$TOOL_BUILD/diffutils-$ver"
if [[ ! -f "$srcdir/configure" ]]; then
    log "extract diffutils-$ver"
    extract_archive "$tarball" "$srcdir"
fi
cd "$srcdir"

if [[ ! -f config.status ]]; then
    log "configure diffutils for $ABI (api $ANDROID_API)"
    # gnulib's strcasecmp test insists on running a program; the value it
    # guesses for non-Solaris hosts is fine.
    ./configure --host="$CROSS_HOST" --prefix=/ --disable-nls \
        gl_cv_lib_sigsegv=no \
        gl_cv_func_strcasecmp_works=yes \
        >"$TOOL_BUILD/diffutils-config.log" 2>&1
fi

log "make diffutils"
if ! make -j"$(jobs)" >"$TOOL_BUILD/diffutils-build.log" 2>&1; then
    tail -60 "$TOOL_BUILD/diffutils-build.log"
    die "diffutils build failed, see $TOOL_BUILD/diffutils-build.log"
fi

log "install diffutils -> $TOOL_OUT"
make DESTDIR="$TOOL_OUT" install >>"$TOOL_BUILD/diffutils-build.log" 2>&1
rm -rf "$TOOL_OUT/share/man" "$TOOL_OUT/share/info"
strip_binaries "$TOOL_OUT"
log "diff: $(file -b "$TOOL_OUT/bin/diff")"
