#!/usr/bin/env bash
# git for Android: local operations (add/apply/status/diff/rev-parse/worktree)
# plus https/ssh remotes.  git-remote-https is linked against the static
# libcurl built by tools/curl.sh (which itself links our OpenSSL), so no
# system TLS libraries are needed.
set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/../env.sh"
toolchain_env "${ABI:?ABI is required}"

ensure_tool curl
deps="$(deps_prefix)"
[[ -f "$deps/lib/libcurl.a" ]] || die "static libcurl not found in $deps (rebuild curl)"

ver="$GIT_VER"
tarball="$SRC_DIR/git-$ver.tar.xz"
fetch "https://mirrors.kernel.org/pub/software/scm/git/git-$ver.tar.xz" "$tarball"

srcdir="$TOOL_BUILD/git-$ver"
if [[ ! -f "$srcdir/Makefile" ]]; then
    log "extract git-$ver"
    extract_archive "$tarball" "$srcdir"
    for p in "$PATCH_DIR"/git/*.patch; do
        apply_patch "$p" "$srcdir"
    done
fi
cd "$srcdir"

if [[ ! -f config.mak.autogen ]]; then
    log "configure git for $ABI (api $ANDROID_API)"
    make configure
    ./configure \
        --host="$CROSS_HOST" \
        --prefix=/ \
        ac_cv_fread_reads_directories=yes \
        ac_cv_header_libintl_h=no \
        ac_cv_iconv_omits_bom=no \
        ac_cv_snprintf_returns_bogus=no
fi

MAKE_FLAGS=(
    NO_GETTEXT=1
    NO_EXPAT=1
    NO_OPENSSL=1
    NO_ICONV=1
    NO_TCLTK=1
    NO_PERL=1
    NO_PYTHON=1
    NO_NSEC=1
    NO_RUST=1
    NO_INSTALL_HARDLINKS=1
    INSTALL_SYMLINKS=1
    CSPRNG_METHOD=urandom
    # The generated config.mak.autogen says NO_CURL=YesPlease (there is no
    # curl-config on the build host); an empty command line value overrides it.
    NO_CURL=
    # CURL_CFLAGS/CURL_LDFLAGS are what CURLDIR/curl-config would normally
    # provide; pointing at our static libcurl keeps the host paths out.
    "CURL_CFLAGS=-I$deps/include"
    "CURL_LDFLAGS=-L$deps/lib -lcurl -lssl -lcrypto -lz -ldl"
    CURL_CONFIG=true
)

# SHELL_PATH is what ends up baked into the git binary and the installed
# shell scripts; the build-shell.patch keeps build-time helper scripts on the
# host shell so /system/bin/sh does not have to exist on the build machine.
# SHELL=/bin/sh keeps make's own recipes runnable on the host.
TARGET_SHELL=/system/bin/sh

log "make git"
if ! make -j"$(jobs)" "${MAKE_FLAGS[@]}" \
    SHELL=/bin/sh SHELL_PATH="$TARGET_SHELL" >"$TOOL_BUILD/git-build.log" 2>&1; then
    tail -60 "$TOOL_BUILD/git-build.log"
    die "git build failed, see $TOOL_BUILD/git-build.log"
fi

log "install git -> $TOOL_OUT"
make "${MAKE_FLAGS[@]}" SHELL=/bin/sh SHELL_PATH="$TARGET_SHELL" \
    prefix=/ DESTDIR="$TOOL_OUT" install >>"$TOOL_BUILD/git-build.log" 2>&1

rm -rf "$TOOL_OUT/share/man" "$TOOL_OUT/share/doc" "$TOOL_OUT/share/locale" \
    "$TOOL_OUT/libexec/git-core/git-gui" "$TOOL_OUT/libexec/git-core/gitk"
strip_binaries "$TOOL_OUT"
log "git: $(file -b "$TOOL_OUT/bin/git")"
