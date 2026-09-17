#!/usr/bin/env bash
# GNU bash for Android. Non-interactive use only (codex shell/snapshot);
# readline is the bundled one and NLS is disabled to avoid extra deps.
set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/../env.sh"
toolchain_env "${ABI:?ABI is required}"

ver="$BASH_VER"
tarball="$SRC_DIR/bash-$ver.tar.gz"
fetch "https://ftp.gnu.org/gnu/bash/bash-$ver.tar.gz" "$tarball"

srcdir="$TOOL_BUILD/bash-$ver"
if [[ ! -f "$srcdir/configure" ]]; then
    log "extract bash-$ver"
    extract_archive "$tarball" "$srcdir"
fi
cd "$srcdir"

if [[ ! -f config.status ]]; then
    log "configure bash for $ABI (api $ANDROID_API)"
    CC_FOR_BUILD="${CC_FOR_BUILD:-cc -std=gnu17}" ./configure \
        --host="$CROSS_HOST" \
        --prefix=/ \
        --disable-nls \
        --without-bash-malloc \
        --enable-progcomp \
        --enable-multibyte \
        ac_cv_func_mbsnrtowcs=no \
        bash_cv_job_control_missing=present \
        bash_cv_sys_siglist=yes \
        bash_cv_func_sigsetjmp=present \
        bash_cv_unusable_rtsigs=no \
        bash_cv_getcwd_malloc=yes
fi

log "make bash"
if ! make -j"$(jobs)" >"$TOOL_BUILD/bash-build.log" 2>&1; then
    tail -60 "$TOOL_BUILD/bash-build.log"
    die "bash build failed, see $TOOL_BUILD/bash-build.log"
fi

log "install bash -> $TOOL_OUT"
make DESTDIR="$TOOL_OUT" install >>"$TOOL_BUILD/bash-build.log" 2>&1

rm -rf "$TOOL_OUT/share/man" "$TOOL_OUT/share/info" "$TOOL_OUT/share/doc" \
    "$TOOL_OUT/share/locale" "$TOOL_OUT/bin/bashbug" "$TOOL_OUT/include"
strip_binaries "$TOOL_OUT"
log "bash: $(file -b "$TOOL_OUT/bin/bash")"
