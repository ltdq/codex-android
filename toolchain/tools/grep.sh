#!/usr/bin/env bash
# GNU grep for Android: standard BRE/ERE/fixed-string handling and settings
# (-r, -i, -w, --include, ...) exactly as documented by GNU.
set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/../env.sh"
toolchain_env "${ABI:?ABI is required}"

ver="$GREP_VER"
tarball="$SRC_DIR/grep-$ver.tar.xz"
fetch "https://ftp.gnu.org/gnu/grep/grep-$ver.tar.xz" "$tarball"

srcdir="$TOOL_BUILD/grep-$ver"
if [[ ! -f "$srcdir/configure" ]]; then
    log "extract grep-$ver"
    extract_archive "$tarball" "$srcdir"
fi
cd "$srcdir"

if [[ ! -f config.status ]]; then
    log "configure grep for $ABI (api $ANDROID_API)"
    ./configure --host="$CROSS_HOST" --prefix=/ --disable-nls \
        >"$TOOL_BUILD/grep-config.log" 2>&1
fi

log "make grep"
if ! make -j"$(jobs)" >"$TOOL_BUILD/grep-build.log" 2>&1; then
    tail -40 "$TOOL_BUILD/grep-build.log"
    die "grep build failed"
fi
make DESTDIR="$TOOL_OUT" install >>"$TOOL_BUILD/grep-build.log" 2>&1
rm -rf "$TOOL_OUT/share/man" "$TOOL_OUT/share/info" "$TOOL_OUT/share/locale" "$TOOL_OUT/include"

# grep >= 3.8 no longer installs egrep/fgrep; GNU grep still switches matcher
# when invoked under those names.
ln -sf grep "$TOOL_OUT/bin/egrep"
ln -sf grep "$TOOL_OUT/bin/fgrep"
strip_binaries "$TOOL_OUT"
log "grep: $(file -b "$TOOL_OUT/bin/grep")"
