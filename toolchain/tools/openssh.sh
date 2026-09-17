#!/usr/bin/env bash
# OpenSSH client tools for Android (ssh, scp, sftp, ssh-keygen, ssh-agent).
set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/../env.sh"
toolchain_env "${ABI:?ABI is required}"

ensure_tool openssl
deps="$(deps_prefix)"

ver="${OPENSSH_VER:-10.5p1}"
tarball="$SRC_DIR/openssh-$ver.tar.gz"
fetch "https://cdn.openbsd.org/pub/OpenBSD/OpenSSH/portable/openssh-$ver.tar.gz" "$tarball"

srcdir="$TOOL_BUILD/openssh-$ver"
if [[ ! -f "$srcdir/configure" ]]; then
    log "extract openssh-$ver"
    extract_archive "$tarball" "$srcdir"
    for p in "$PATCH_DIR"/openssh/*.patch; do
        apply_patch "$p" "$srcdir"
    done
fi
cd "$srcdir"

if [[ ! -f config.status ]]; then
    log "configure openssh for $ABI (api $ANDROID_API)"
    CPPFLAGS="-I$deps/include -DHAVE_ATTRIBUTE__SENTINEL__=1 -D_PATH_MAILDIR='\"/var/mail\"'" \
    LDFLAGS="-L$deps/lib" \
    PKG_CONFIG_LIBDIR="$deps/lib/pkgconfig:$NDK_SYSROOT/usr/lib/pkgconfig" \
    ./configure \
        --host="$CROSS_HOST" \
        --prefix=/ \
        --sysconfdir=/etc/ssh \
        --with-ssl-dir="$deps" \
        --without-openssl-header-check \
        --with-privsep-path=/data/local/tmp \
        --with-privsep-user=nobody \
        --disable-strip \
        --disable-utmp \
        --disable-wtmp \
        --disable-lastlog \
        --without-selinux \
        --without-pam \
        --without-zlib-version-check \
        >"$TOOL_BUILD/openssh-config.log" 2>&1
fi

log "make openssh"
if ! make -j"$(jobs)" >"$TOOL_BUILD/openssh-build.log" 2>&1; then
    tail -60 "$TOOL_BUILD/openssh-build.log"
    die "openssh build failed"
fi
make DESTDIR="$TOOL_OUT" install >>"$TOOL_BUILD/openssh-build.log" 2>&1
rm -rf "$TOOL_OUT/share/man" \
    "$TOOL_OUT/sbin/sshd" \
    "$TOOL_OUT/libexec/ssh-keysign" "$TOOL_OUT/libexec/sftp-server" \
    "$TOOL_OUT/libexec/ssh-pkcs11-helper" "$TOOL_OUT/etc"
strip_binaries "$TOOL_OUT"
log "ssh: $(file -b "$TOOL_OUT/bin/ssh")"
