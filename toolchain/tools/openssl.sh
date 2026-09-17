#!/usr/bin/env bash
# OpenSSL (static) for Android. Installed into the per-abi dependency prefix
# so curl and python can link it; also shipped as bin/openssl.
set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/../env.sh"
toolchain_env "${ABI:?ABI is required}"

case "$ABI" in
    arm64-v8a)   ossl_target=android-arm64 ;;
    armeabi-v7a) ossl_target=android-arm ;;
    x86_64)      ossl_target=android-x86_64 ;;
    x86)         ossl_target=android-x86 ;;
esac

ver="$OPENSSL_VER"
tarball="$SRC_DIR/openssl-$ver.tar.gz"
fetch "https://github.com/openssl/openssl/releases/download/openssl-$ver/openssl-$ver.tar.gz" "$tarball"

srcdir="$TOOL_BUILD/openssl-$ver"
prefix="$(deps_prefix)"
if [[ ! -f "$srcdir/Configure" ]]; then
    log "extract openssl-$ver"
    extract_archive "$tarball" "$srcdir"
fi
cd "$srcdir"

if [[ ! -f Makefile ]]; then
    log "configure openssl ($ossl_target, api $ANDROID_API)"
    ANDROID_NDK_ROOT="$ANDROID_NDK_HOME" ./Configure "$ossl_target" \
        -D__ANDROID_API__="$ANDROID_API" \
        --prefix="$prefix" \
        --openssldir="$prefix/etc/ssl" \
        --libdir=lib \
        no-shared no-tests no-legacy \
        >"$TOOL_BUILD/openssl-config.log" 2>&1
fi

log "make openssl"
if ! make -j"$(jobs)" >"$TOOL_BUILD/openssl-build.log" 2>&1; then
    tail -60 "$TOOL_BUILD/openssl-build.log"
    die "openssl build failed, see $TOOL_BUILD/openssl-build.log"
fi

log "install openssl -> $prefix"
make install_sw >>"$TOOL_BUILD/openssl-build.log" 2>&1
"$STRIP" --strip-unneeded "$prefix/lib/libcrypto.a" "$prefix/lib/libssl.a" 2>/dev/null || true

install -m 755 apps/openssl "$TOOL_OUT/bin/openssl"
"$STRIP" --strip-unneeded "$TOOL_OUT/bin/openssl" 2>/dev/null || true
log "openssl: $(file -b "$TOOL_OUT/bin/openssl")"
