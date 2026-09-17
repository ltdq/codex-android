#!/usr/bin/env bash
# CPython for Android with all extension modules linked statically into the
# interpreter (no dlopen from app data, no lib-dynload).
set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/../env.sh"
toolchain_env "${ABI:?ABI is required}"

ensure_tool openssl
ensure_tool libffi
ensure_tool sqlite
deps="$(deps_prefix)"

ver="$PYTHON_VER"
major="${ver%.*}"
tarball="$SRC_DIR/Python-$ver.tar.xz"
fetch "https://www.python.org/ftp/python/$ver/Python-$ver.tar.xz" "$tarball"

srcdir="$TOOL_BUILD/Python-$ver"
if [[ ! -f "$srcdir/configure" ]]; then
    log "extract Python-$ver"
    extract_archive "$tarball" "$srcdir"
    for p in "$PATCH_DIR"/python/*.patch; do
        apply_patch "$p" "$srcdir"
    done
fi
cd "$srcdir"

host_python="${HOST_PYTHON:-python3}"
"$host_python" -c "import sys; sys.exit(0 if sys.version.startswith('$major.') else 1)" \
    || die "host python must be $major.x (set HOST_PYTHON)"
build_triple="$(cc -dumpmachine 2>/dev/null || echo x86_64-pc-linux-gnu)"

if [[ ! -f config.status ]]; then
    log "configure Python for $ABI (api $ANDROID_API)"
    # PKG_CONFIG_LIBDIR (not PKG_CONFIG_PATH) keeps pkg-config from seeing
    # host libraries when probing for optional modules.
    CPPFLAGS="-I$deps/include -I$NDK_SYSROOT/usr/include" \
    LDFLAGS="-L$deps/lib" \
    LIBS="-lm -ldl" \
    PKG_CONFIG_LIBDIR="$deps/lib/pkgconfig:$NDK_SYSROOT/usr/lib/pkgconfig" \
    ./configure \
        --host="$CROSS_HOST" \
        --build="$build_triple" \
        --with-build-python="$host_python" \
        --prefix=/ \
        --without-ensurepip \
        --disable-test-modules \
        --with-openssl="$deps" \
        --with-system-ffi \
        ac_cv_file__dev_ptmx=yes \
        ac_cv_file__dev_ptc=no \
        ac_cv_func_wcsftime=no \
        ac_cv_func_ftime=no \
        ac_cv_func_faccessat=no \
        ac_cv_func_link=no \
        ac_cv_func_linkat=no \
        ac_cv_buggy_getaddrinfo=no \
        ac_cv_little_endian_double=yes \
        ac_cv_posix_semaphores_enabled=yes \
        ac_cv_func_sem_open=yes \
        ac_cv_func_sem_timedwait=yes \
        ac_cv_func_sem_getvalue=yes \
        ac_cv_func_sem_unlink=yes \
        ac_cv_func_shm_open=no \
        ac_cv_func_shm_unlink=no \
        ac_cv_working_tzset=yes \
        ac_cv_header_sys_xattr_h=no \
        >"$TOOL_BUILD/python-config.log" 2>&1
fi

log "make Python"
if ! make -j"$(jobs)" >"$TOOL_BUILD/python-build.log" 2>&1; then
    tail -80 "$TOOL_BUILD/python-build.log"
    die "python build failed, see $TOOL_BUILD/python-build.log"
fi

log "install Python -> $TOOL_OUT"
make DESTDIR="$TOOL_OUT" install >>"$TOOL_BUILD/python-build.log" 2>&1

rm -rf "$TOOL_OUT/lib/python$major"/{test,idlelib,tkinter,turtledemo,ensurepip,lib2to3} \
    "$TOOL_OUT/lib/python$major"/site-packages \
    "$TOOL_OUT/lib/python$major"/config-* \
    "$TOOL_OUT/lib/libpython$major.a" \
    "$TOOL_OUT/lib/pkgconfig" \
    "$TOOL_OUT/bin/idle"* \
    "$TOOL_OUT/bin/python"*-config \
    "$TOOL_OUT/share/man" "$TOOL_OUT/include"
find "$TOOL_OUT/lib/python$major" -name __pycache__ -type d -prune -exec rm -rf {} +
strip_binaries "$TOOL_OUT"
log "python: $(file -b "$TOOL_OUT/bin/python$major")"
