#!/usr/bin/env bash
# libmagic/file for Android.
set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/../env.sh"
toolchain_env "${ABI:?ABI is required}"

ver="$FILE_VER"
tarball="$SRC_DIR/file-$ver.tar.gz"
# astron.com is unreachable from some networks; macports mirrors the releases.
fetch "https://distfiles.macports.org/file/file-$ver.tar.gz" "$tarball"

srcdir="$TOOL_BUILD/file-$ver"
if [[ ! -f "$srcdir/configure" ]]; then
    log "extract file-$ver"
    extract_archive "$tarball" "$srcdir"
fi
cd "$srcdir"

if [[ ! -f config.status ]]; then
    log "configure file for $ABI (api $ANDROID_API)"
    PKG_CONFIG_LIBDIR="$NDK_SYSROOT/usr/lib/pkgconfig" ./configure \
        --host="$CROSS_HOST" --prefix=/ \
        --disable-shared --enable-static \
        --disable-libseccomp --disable-bzlib --disable-xzlib --disable-lzlib \
        >"$TOOL_BUILD/file-config.log" 2>&1
fi

# magic.mgc can only be compiled by running the (target) file binary; instead
# keep the text magic database and let libmagic read it at runtime.
sed -i 's|^	\$(FILE_COMPILE) -C -m magic$|	touch $@ \&\& cat $(MAGIC_FRAGMENTS) > $@.source|' magic/Makefile

log "make file"
if ! make -j"$(jobs)" FILE_COMPILE=/usr/bin/file >"$TOOL_BUILD/file-build.log" 2>&1; then
    tail -40 "$TOOL_BUILD/file-build.log"
    die "file build failed"
fi
make FILE_COMPILE=/usr/bin/file DESTDIR="$TOOL_OUT" install >>"$TOOL_BUILD/file-build.log" 2>&1

if [[ -f magic/magic.mgc.source ]]; then
    rm -f "$TOOL_OUT/share/misc/magic.mgc"
    install -m 644 magic/magic.mgc.source "$TOOL_OUT/share/misc/magic"
fi
rm -rf "$TOOL_OUT/share/man" "$TOOL_OUT/include"
strip_binaries "$TOOL_OUT"
log "file: $(file -b "$TOOL_OUT/bin/file")"
