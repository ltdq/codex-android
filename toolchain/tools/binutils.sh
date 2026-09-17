#!/usr/bin/env bash
# GNU binutils for Android: readelf, objdump, nm, strings, objcopy, strip, ar,
# ranlib, addr2line, size, c++filt, as, ld, ...
# Configured native (host == target == aarch64-linux-android).
set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/../env.sh"
toolchain_env "${ABI:?ABI is required}"

ver="${BINUTILS_VER:-2.47}"
tarball="$SRC_DIR/binutils-$ver.tar.xz"
fetch "https://ftp.gnu.org/gnu/binutils/binutils-$ver.tar.xz" "$tarball"

srcdir="$TOOL_BUILD/binutils-$ver"
if [[ ! -f "$srcdir/configure" ]]; then
    log "extract binutils-$ver"
    extract_archive "$tarball" "$srcdir"
fi
cd "$srcdir"

if [[ ! -f config.status ]]; then
    log "configure binutils for $ABI (api $ANDROID_API)"
    PKG_CONFIG_LIBDIR="$NDK_SYSROOT/usr/lib/pkgconfig" ./configure \
        --build=x86_64-pc-linux-gnu \
        --host="$CROSS_HOST" \
        --target="$CROSS_HOST" \
        --prefix=/ \
        --disable-nls \
        --disable-werror \
        --disable-gdb \
        --disable-gprof \
        --disable-gprofng \
        --disable-sim \
        --disable-gold \
        --disable-multilib \
        --without-zstd \
        --disable-libdecnumber \
        --disable-readline \
        >"$TOOL_BUILD/binutils-config.log" 2>&1
fi

log "make binutils"
if ! make -j"$(jobs)" MAKEINFO=true >"$TOOL_BUILD/binutils-build.log" 2>&1; then
    tail -60 "$TOOL_BUILD/binutils-build.log"
    die "binutils build failed"
fi
make MAKEINFO=true DESTDIR="$TOOL_OUT" install >>"$TOOL_BUILD/binutils-build.log" 2>&1

# Native build: also expose unprefixed names (readelf, objdump, ...).
for path in "$TOOL_OUT"/bin/"$CROSS_HOST"-*; do
    [[ -e "$path" ]] || continue
    plain="${path##*/"$CROSS_HOST"-}"
    [[ -e "$TOOL_OUT/bin/$plain" ]] && continue
    ln -sf "${path##*/}" "$TOOL_OUT/bin/$plain"
done

rm -rf "$TOOL_OUT/share/man" "$TOOL_OUT/share/info" "$TOOL_OUT/share/locale" "$TOOL_OUT/include"
strip_binaries "$TOOL_OUT"
log "readelf: $(file -b "$TOOL_OUT/bin/readelf" 2>/dev/null || echo built)"
