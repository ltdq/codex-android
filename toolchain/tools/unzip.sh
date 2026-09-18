#!/usr/bin/env bash
# Info-ZIP unzip/zipinfo for Android: the standard `unzip` CLI
# (`unzip -l/-o/-d`, `zipinfo -1`).  7zz can also extract zip archives, but
# scripts and agents expect the Info-ZIP command line.  Patches under
# patches/unzip/ are the Debian security/portability backports carried by
# termux-packages (upstream unzip 6.0 predates all of them).
set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/../env.sh"
toolchain_env "${ABI:?ABI is required}"

ver="$UNZIP_VER"
stem="unzip${ver//./}"
tarball="$SRC_DIR/$stem.tar.gz"
fetch "https://downloads.sourceforge.net/infozip/$stem.tar.gz" "$tarball"

srcdir="$TOOL_BUILD/unzip-$ver"
if [[ ! -f "$srcdir/unzip.c" ]]; then
    log "extract unzip-$ver"
    extract_archive "$tarball" "$srcdir"
    for p in "$PATCH_DIR"/unzip/*.patch; do
        apply_patch "$p" "$srcdir"
    done
fi
cd "$srcdir"

cflags="$CFLAGS -I. -DUNIX -DLARGE_FILE_SUPPORT -DUNICODE_SUPPORT \
-DUNICODE_WCHAR -DUTF8_MAYBE_NATIVE -DNO_LCHMOD -DNOMEMCPY -DNO_WORKING_ISPRINT \
-DDATE_FORMAT=DF_YMD"

log "make unzip"
if ! make -j"$(jobs)" -f unix/Makefile unzips zipinfo CC="$CC" LD="$CC" \
    CF="$cflags" LF2="$LDFLAGS" >"$TOOL_BUILD/unzip-build.log" 2>&1; then
    tail -40 "$TOOL_BUILD/unzip-build.log"
    die "unzip build failed"
fi

install -m 755 unzip "$TOOL_OUT/bin/unzip"
ln -sf unzip "$TOOL_OUT/bin/zipinfo"
"$STRIP" --strip-unneeded "$TOOL_OUT/bin/unzip" 2>/dev/null || true
log "unzip: $(file -b "$TOOL_OUT/bin/unzip")"
