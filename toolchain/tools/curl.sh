#!/usr/bin/env bash
# curl for Android, statically linked against our OpenSSL.
set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/../env.sh"
toolchain_env "${ABI:?ABI is required}"

ensure_tool openssl
deps="$(deps_prefix)"
[[ -f "$deps/lib/libssl.a" ]] || die "openssl not found in $deps"

ver="$CURL_VER"
tarball="$SRC_DIR/curl-$ver.tar.xz"
fetch "https://github.com/curl/curl/releases/download/curl-${ver//./_}/curl-$ver.tar.xz" "$tarball"

srcdir="$TOOL_BUILD/curl-$ver"
if [[ ! -f "$srcdir/configure" ]]; then
    log "extract curl-$ver"
    extract_archive "$tarball" "$srcdir"
fi
cd "$srcdir"

if [[ ! -f config.status ]]; then
    log "configure curl for $ABI (api $ANDROID_API)"
    CPPFLAGS="-I$deps/include" LDFLAGS="-L$deps/lib" ./configure \
        --host="$CROSS_HOST" \
        --prefix=/ \
        --disable-shared \
        --enable-static \
        --with-openssl="$deps" \
        --without-libpsl \
        --without-libidn2 \
        --without-brotli \
        --without-zstd \
        --without-librtmp \
        --disable-ldap \
        --disable-ldaps \
        --disable-ares \
        --disable-manual \
        --disable-docs \
        ac_cv_func_getpwuid=yes \
        >"$TOOL_BUILD/curl-config.log" 2>&1
fi

log "make curl"
if ! make -j"$(jobs)" >"$TOOL_BUILD/curl-build.log" 2>&1; then
    tail -60 "$TOOL_BUILD/curl-build.log"
    die "curl build failed, see $TOOL_BUILD/curl-build.log"
fi

# Static libcurl + headers for tools that speak HTTP themselves (git-https).
log "install libcurl -> $deps"
mkdir -p "$deps/lib" "$deps/include"
cp -f lib/.libs/libcurl.a "$deps/lib/"
cp -R include/curl "$deps/include/"

log "install curl -> $TOOL_OUT"
make DESTDIR="$TOOL_OUT" install >>"$TOOL_BUILD/curl-build.log" 2>&1

# CA bundle the app can point CURL_CA_BUNDLE / SSL_CERT_FILE at.
fetch "https://curl.se/ca/cacert.pem" "$TOOL_OUT/share/cacert.pem"

rm -rf "$TOOL_OUT/share/man" "$TOOL_OUT/share/aclocal" "$TOOL_OUT/share/doc" \
    "$TOOL_OUT/lib/libcurl.a" "$TOOL_OUT/lib/libcurl.la" "$TOOL_OUT/lib/pkgconfig"
strip_binaries "$TOOL_OUT"
log "curl: $(file -b "$TOOL_OUT/bin/curl")"
