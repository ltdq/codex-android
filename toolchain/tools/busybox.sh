#!/usr/bin/env bash
# busybox for Android: broad coverage of POSIX/GNU-ish utilities (sed, awk,
# grep, find, tar, xz, unzip, less, ps, diff, patch, nc, ...) via applet
# symlinks. Config and patches adapted from termux-packages.
set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/../env.sh"
toolchain_env "${ABI:?ABI is required}"

ver="$BUSYBOX_VER"
tarball="$SRC_DIR/busybox-$ver.tar.bz2"
fetch "https://busybox.net/downloads/busybox-$ver.tar.bz2" "$tarball"

srcdir="$TOOL_BUILD/busybox-$ver"
if [[ ! -f "$srcdir/Makefile" ]]; then
    log "extract busybox-$ver"
    extract_archive "$tarball" "$srcdir"
    for p in "$PATCH_DIR"/busybox/*.patch; do
        apply_patch "$p" "$srcdir"
    done
fi
cd "$srcdir"

if [[ ! -f .config ]]; then
    log "configure busybox"
    sed -e "s|@TERMUX_PREFIX@|/|g" \
        -e "s|@TERMUX_SYSROOT@|$NDK_SYSROOT|g" \
        -e "s|@TERMUX_HOST_PLATFORM@|$CROSS_PREFIX|g" \
        -e "s|@TERMUX_CFLAGS@|$CFLAGS|g" \
        -e "s|@TERMUX_LDFLAGS@|$LDFLAGS|g" \
        -e "s|@TERMUX_LDLIBS@||g" \
        "$TOOLCHAIN_ROOT/config/busybox.config" >.config
    sed -i \
        -e 's/^CONFIG_BUILD_LIBBUSYBOX=y/# CONFIG_BUILD_LIBBUSYBOX is not set/' \
        -e 's/^CONFIG_SELINUX=y/# CONFIG_SELINUX is not set/' \
        -e 's/^CONFIG_INSTALL_APPLET_DONT=y/# CONFIG_INSTALL_APPLET_DONT is not set/' \
        -e 's/^# CONFIG_INSTALL_APPLET_SYMLINKS is not set/CONFIG_INSTALL_APPLET_SYMLINKS=y/' \
        .config
    make oldconfig CC="$CC" AR="$AR" RANLIB="$RANLIB" NM="$NM" STRIP="$STRIP" \
        HOSTCC="${HOSTCC:-cc}" >/dev/null </dev/null
fi

BUSYBOX_MAKE_VARS=(
    CC="$CC" AR="$AR" RANLIB="$RANLIB" NM="$NM" STRIP="$STRIP"
    HOSTCC="${HOSTCC:-cc}"
)

log "make busybox"
if ! make -j"$(jobs)" "${BUSYBOX_MAKE_VARS[@]}" >"$TOOL_BUILD/busybox-build.log" 2>&1; then
    tail -60 "$TOOL_BUILD/busybox-build.log"
    die "busybox build failed, see $TOOL_BUILD/busybox-build.log"
fi

log "install busybox -> $TOOL_OUT"
rm -rf "$TOOL_BUILD/busybox-install"
make CONFIG_PREFIX="$TOOL_BUILD/busybox-install" "${BUSYBOX_MAKE_VARS[@]}" install \
    >>"$TOOL_BUILD/busybox-build.log" 2>&1

# Copy the binary and recreate the applet symlinks ourselves so that
# dedicated tools (bash, git, rg, curl, openssl, python, ...) win.
install -m 755 "$TOOL_BUILD/busybox-install/bin/busybox" "$TOOL_OUT/bin/busybox"
for dir in bin sbin; do
    [[ -d "$TOOL_BUILD/busybox-install/$dir" ]] || continue
    mkdir -p "$TOOL_OUT/$dir"
    for link in "$TOOL_BUILD/busybox-install/$dir"/*; do
        name="$(basename "$link")"
        case "$name" in
            busybox) continue ;;
            bash|git|rg|curl|openssl|python|python3|python3.*|awk|gawk|make|jq|sqlite3|sed|file) continue ;;
        esac
        if [[ -e "$TOOL_OUT/bin/$name" || -e "$TOOL_OUT/sbin/$name" ]]; then
            continue
        fi
        ln -sf ../bin/busybox "$TOOL_OUT/$dir/$name"
    done
done
rm -f "$TOOL_OUT/linuxrc"

"$STRIP" --strip-unneeded "$TOOL_OUT/bin/busybox" 2>/dev/null || true
applets=$(ls "$TOOL_BUILD/busybox-install/bin" "$TOOL_BUILD/busybox-install/sbin" 2>/dev/null | wc -l)
log "busybox: $(file -b "$TOOL_OUT/bin/busybox") ($applets applet links)"
