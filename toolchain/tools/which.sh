#!/usr/bin/env bash
# GNU which for Android: `which -a`, `which --version` and PATH semantics that
# scripts expect (bash's `type -p` stays the faster builtin alternative).
set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/../env.sh"
toolchain_env "${ABI:?ABI is required}"

ver="$WHICH_VER"
tarball="$SRC_DIR/which-$ver.tar.gz"
fetch "https://carlowood.github.io/which/which-$ver.tar.gz" "$tarball"

srcdir="$TOOL_BUILD/which-$ver"
if [[ ! -f "$srcdir/configure" ]]; then
    log "extract which-$ver"
    extract_archive "$tarball" "$srcdir"
fi
cd "$srcdir"

if [[ ! -f config.status ]]; then
    log "configure which for $ABI (api $ANDROID_API)"
    ./configure --host="$CROSS_HOST" --prefix=/ >"$TOOL_BUILD/which-config.log" 2>&1
fi

log "make which"
if ! make -j"$(jobs)" >"$TOOL_BUILD/which-build.log" 2>&1; then
    tail -40 "$TOOL_BUILD/which-build.log"
    die "which build failed"
fi
make DESTDIR="$TOOL_OUT" install >>"$TOOL_BUILD/which-build.log" 2>&1
rm -rf "$TOOL_OUT/share/man" "$TOOL_OUT/share/info" "$TOOL_OUT/share/locale"
strip_binaries "$TOOL_OUT"
log "which: $(file -b "$TOOL_OUT/bin/which")"
