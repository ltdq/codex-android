#!/usr/bin/env bash
# jq for Android, using the vendored oniguruma.
set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/../env.sh"
toolchain_env "${ABI:?ABI is required}"

ver="$JQ_VER"
tarball="$SRC_DIR/jq-$ver.tar.gz"
fetch "https://github.com/jqlang/jq/releases/download/jq-$ver/jq-$ver.tar.gz" "$tarball"

srcdir="$TOOL_BUILD/jq-$ver"
if [[ ! -f "$srcdir/configure" ]]; then
    log "extract jq-$ver"
    extract_archive "$tarball" "$srcdir"
fi
cd "$srcdir"

if [[ ! -f config.status ]]; then
    log "configure jq for $ABI (api $ANDROID_API)"
    ./configure --host="$CROSS_HOST" --prefix=/ \
        --with-oniguruma=builtin \
        --disable-shared --enable-static \
        --disable-maintainer-mode \
        --disable-docs \
        >"$TOOL_BUILD/jq-config.log" 2>&1
fi

log "make jq"
if ! make -j"$(jobs)" >"$TOOL_BUILD/jq-build.log" 2>&1; then
    tail -60 "$TOOL_BUILD/jq-build.log"
    die "jq build failed"
fi
make DESTDIR="$TOOL_OUT" install >>"$TOOL_BUILD/jq-build.log" 2>&1
rm -rf "$TOOL_OUT/share/man" "$TOOL_OUT/share/doc" "$TOOL_OUT/include"
strip_binaries "$TOOL_OUT"
log "jq: $(file -b "$TOOL_OUT/bin/jq")"
