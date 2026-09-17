#!/usr/bin/env bash
# 7-Zip for Android (7zz).
set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/../env.sh"
toolchain_env "${ABI:?ABI is required}"

ver="${SEVENZIP_VER:-26.03}"
tarball="$SRC_DIR/7z${ver//./}-src.tar.xz"
fetch "https://github.com/ip7z/7zip/releases/download/$ver/7z${ver//./}-src.tar.xz" "$tarball"

srcdir="$TOOL_BUILD/7zip-$ver"
if [[ ! -d "$srcdir/CPP" ]]; then
    log "extract 7zip-$ver"
    extract_archive "$tarball" "$srcdir" 0
fi
cd "$srcdir/CPP/7zip/Bundles/Alone2"

log "make 7zz"
if ! make -j"$(jobs)" -f ../../cmpl_clang_arm64.mak \
    CC="$CC" CXX="$CXX" AR="$AR" \
    CFLAGS_BASE_LIST="-c -O3" LDFLAGS="-flto" LIB2="-ldl" \
    >"$TOOL_BUILD/7zip-build.log" 2>&1; then
    tail -60 "$TOOL_BUILD/7zip-build.log"
    die "7zip build failed"
fi

install -m 755 b/c_arm64/7zz "$TOOL_OUT/bin/7zz"
"$STRIP" --strip-unneeded "$TOOL_OUT/bin/7zz" 2>/dev/null || true
log "7zz: $(file -b "$TOOL_OUT/bin/7zz")"
