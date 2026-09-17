#!/usr/bin/env bash
# zstd for Android: .zst files and "tar --zstd" (GNU tar shells out to the
# zstd binary; busybox has no zstd applet).
set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/../env.sh"
toolchain_env "${ABI:?ABI is required}"

ver="$ZSTD_VER"
tarball="$SRC_DIR/zstd-$ver.tar.gz"
fetch "https://github.com/facebook/zstd/releases/download/v$ver/zstd-$ver.tar.gz" "$tarball"

srcdir="$TOOL_BUILD/zstd-$ver"
if [[ ! -d "$srcdir/programs" ]]; then
    log "extract zstd-$ver"
    extract_archive "$tarball" "$srcdir"
fi
cd "$srcdir"

log "make zstd"
if ! make -j"$(jobs)" zstd CC="$CC" AR="$AR" RANLIB="$RANLIB" \
    >"$TOOL_BUILD/zstd-build.log" 2>&1; then
    tail -60 "$TOOL_BUILD/zstd-build.log"
    die "zstd build failed, see $TOOL_BUILD/zstd-build.log"
fi

install -m 755 programs/zstd "$TOOL_OUT/bin/zstd"
for link in unzstd zstdcat zstdmt; do
    ln -sf zstd "$TOOL_OUT/bin/$link"
done
"$STRIP" --strip-unneeded "$TOOL_OUT/bin/zstd" 2>/dev/null || true
log "zstd: $(file -b "$TOOL_OUT/bin/zstd")"
