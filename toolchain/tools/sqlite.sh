#!/usr/bin/env bash
# SQLite: static libsqlite3 + headers into the dependency prefix (so python's
# _sqlite3 can link it) and the sqlite3 CLI into the toolchain bin.
set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/../env.sh"
toolchain_env "${ABI:?ABI is required}"

ver="$SQLITE_VER"
tarball="$SRC_DIR/sqlite-autoconf-$ver.tar.gz"
fetch "https://sqlite.org/2026/sqlite-autoconf-$ver.tar.gz" "$tarball"

srcdir="$TOOL_BUILD/sqlite-autoconf-$ver"
prefix="$(deps_prefix)"
if [[ ! -f "$srcdir/configure" ]]; then
    log "extract sqlite-autoconf-$ver"
    extract_archive "$tarball" "$srcdir"
fi
cd "$srcdir"

if [[ ! -f config.status ]]; then
    log "configure sqlite for $ABI (api $ANDROID_API)"
    ./configure --host="$CROSS_HOST" --prefix="$prefix" \
        --disable-shared --enable-static >"$TOOL_BUILD/sqlite-config.log" 2>&1
fi

log "make sqlite"
if ! make -j"$(jobs)" >"$TOOL_BUILD/sqlite-build.log" 2>&1; then
    tail -40 "$TOOL_BUILD/sqlite-build.log"
    die "sqlite build failed"
fi
make install >>"$TOOL_BUILD/sqlite-build.log" 2>&1

# Static libsqlite3 needs libm/libdl; expose that to pkg-config consumers
# (python's _sqlite3 module otherwise fails to resolve trunc()).
pc="$prefix/lib/pkgconfig/sqlite3.pc"
if [[ -f "$pc" ]]; then
    sed -i 's|^Libs: \(.*\)$|Libs: \1 -lm -ldl|' "$pc"
fi

install -m 755 sqlite3 "$TOOL_OUT/bin/sqlite3"
"$STRIP" --strip-unneeded "$TOOL_OUT/bin/sqlite3" 2>/dev/null || true
log "sqlite3: $(file -b "$TOOL_OUT/bin/sqlite3")"
