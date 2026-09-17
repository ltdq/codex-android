#!/usr/bin/env bash
# Bun (JS/TS runtime, package manager and test runner) for Android.
#
# Bun >= 1.3.14 publishes official Android builds; unlike node there is no
# cross-compile step, we just repackage the upstream aarch64 PIE (it only
# needs libc/libm/libdl, and executes JS itself, so nothing depends on
# /usr/bin/env being present in a shebang).
#
# bunx is installed as a wrapper around "bun x" (upstream only ships the `bun`
# binary) so JS CLI packages / MCP servers run without node/npm.
set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/../env.sh"
toolchain_env "${ABI:?ABI is required}"

case "$ABI" in
    arm64-v8a) bun_arch=aarch64 ;;
    *) die "bun: no upstream build for $ABI (android supports aarch64/x64 only)" ;;
esac

ver="$BUN_VER"
zip="$SRC_DIR/bun-linux-$bun_arch-android-$ver.zip"
fetch "https://github.com/oven-sh/bun/releases/download/bun-v$ver/bun-linux-$bun_arch-android.zip" "$zip"

srcdir="$TOOL_BUILD/bun-$ver-$ABI"
rm -rf "$srcdir"
mkdir -p "$srcdir"
unzip -q -o "$zip" -d "$srcdir"

bin="$(find "$srcdir" -type f -name bun | head -1)"
[[ -n "$bin" ]] || die "bun binary not found in $zip"
install -m 755 "$bin" "$TOOL_OUT/bin/bun"

# bunx is bun's npx mode; the wrapper keeps it working no matter what the
# executable is named in the native library dir.
rm -f "$TOOL_OUT/bin/npx" # stale artifact from older builds
cat >"$TOOL_OUT/bin/bunx" <<'WRAP'
#!/system/bin/sh
dir="$(cd "$(dirname "$0")" && pwd)"
exec "$dir/bun" x "$@"
WRAP
chmod 755 "$TOOL_OUT/bin/bunx"

log "bun: $(file -b "$TOOL_OUT/bin/bun")"
