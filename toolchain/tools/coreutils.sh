#!/usr/bin/env bash
# GNU coreutils for Android.  The reference implementation of the POSIX
# userland (ls/cp/mv/rm/cat/sort/head/tail/stat/du/df/md5sum/...), built in
# single-binary mode: one `coreutils` executable plus one symlink per program,
# so the agent gets GNU behavior without ~100 separate binaries.
set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/../env.sh"
toolchain_env "${ABI:?ABI is required}"

ver="$COREUTILS_VER"
tarball="$SRC_DIR/coreutils-$ver.tar.xz"
fetch "https://ftp.gnu.org/gnu/coreutils/coreutils-$ver.tar.xz" "$tarball"

srcdir="$TOOL_BUILD/coreutils-$ver"
if [[ ! -f "$srcdir/configure" ]]; then
    log "extract coreutils-$ver"
    extract_archive "$tarball" "$srcdir"
    for p in "$PATCH_DIR"/coreutils/*.patch; do
        apply_patch "$p" "$srcdir"
    done
fi
cd "$srcdir"

if [[ ! -f config.status ]]; then
    log "configure coreutils for $ABI (api $ANDROID_API)"
    # users/who/pinky need utmp, which Android does not provide; xattr and
    # SELinux are not usable inside the app sandbox either.
    ./configure --host="$CROSS_HOST" --prefix=/ --disable-nls \
        --without-selinux --disable-xattr --enable-single-binary=symlinks \
        --enable-no-install-program=pinky,users,who \
        gl_cv_host_operating_system=Android \
        >"$TOOL_BUILD/coreutils-config.log" 2>&1
fi

log "make coreutils"
if ! make -j"$(jobs)" >"$TOOL_BUILD/coreutils-build.log" 2>&1; then
    tail -60 "$TOOL_BUILD/coreutils-build.log"
    die "coreutils build failed, see $TOOL_BUILD/coreutils-build.log"
fi

log "install coreutils -> $TOOL_OUT"
make DESTDIR="$TOOL_OUT" install >>"$TOOL_BUILD/coreutils-build.log" 2>&1
rm -rf "$TOOL_OUT/share/man" "$TOOL_OUT/share/info" "$TOOL_OUT/share/locale" \
    "$TOOL_OUT/include"
strip_binaries "$TOOL_OUT"
links=$(find "$TOOL_OUT/bin" -maxdepth 1 -lname coreutils | wc -l)
log "coreutils: $(file -b "$TOOL_OUT/bin/coreutils") ($links program links)"
