#!/usr/bin/env bash
# procps-ng for Android: `ps` with standard option syntax and output, plus
# kill/pgrep/pkill/pidof/free/uptime/pmap/vmstat for managing the processes an
# agent starts.  Built without ncurses: no top/watch (the app has no PTY).
# Patches under patches/procps/ cover bionic gaps (fopencookie, strverscmp,
# FLT_MIN collision) and /proc files Android does not expose.
set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/../env.sh"
toolchain_env "${ABI:?ABI is required}"

ver="$PROCPS_VER"
tarball="$SRC_DIR/procps-ng-$ver.tar.xz"
fetch "https://sourceforge.net/projects/procps-ng/files/Production/procps-ng-$ver.tar.xz/download" "$tarball"

srcdir="$TOOL_BUILD/procps-ng-$ver"
if [[ ! -f "$srcdir/configure" ]]; then
    log "extract procps-ng-$ver"
    extract_archive "$tarball" "$srcdir"
    for p in "$PATCH_DIR"/procps/*.patch; do
        apply_patch "$p" "$srcdir"
    done
fi
cd "$srcdir"

# libproc uses strverscmp but only top compiles the bundled implementation.
"$CC" $CFLAGS -c local/strverscmp.c -o strverscmp.o
"$AR" cru libstrverscmp.a strverscmp.o

if [[ ! -f config.status ]]; then
    log "configure procps-ng for $ABI (api $ANDROID_API)"
    LIBS="-L$srcdir -lstrverscmp" ./configure --host="$CROSS_HOST" --prefix=/ \
        --disable-nls --without-ncurses --disable-w --without-systemd \
        --disable-shared --enable-static \
        ac_cv_func_malloc_0_nonnull=yes ac_cv_func_realloc_0_nonnull=yes \
        >"$TOOL_BUILD/procps-config.log" 2>&1
fi

log "make procps-ng"
if ! make -j"$(jobs)" LIBS="-L$srcdir -lstrverscmp" >"$TOOL_BUILD/procps-build.log" 2>&1; then
    tail -40 "$TOOL_BUILD/procps-build.log"
    die "procps build failed"
fi
make DESTDIR="$TOOL_OUT" install >>"$TOOL_BUILD/procps-build.log" 2>&1

# Keep only the tools that make sense on Android; drop terminal UIs, systemd
# helpers and the rest of the sysctl/slab tooling.
for tool in pidwait pwdx tload; do
    rm -f "$TOOL_OUT/bin/$tool"
done
rm -f "$TOOL_OUT/sbin/sysctl"
rm -rf "$TOOL_OUT/share/man" "$TOOL_OUT/share/doc" "$TOOL_OUT/share/locale" \
    "$TOOL_OUT/include" "$TOOL_OUT/lib/libproc2.la"
rmdir "$TOOL_OUT/sbin" 2>/dev/null || true
strip_binaries "$TOOL_OUT"
log "procps: $(file -b "$TOOL_OUT/bin/ps")"
