#!/usr/bin/env bash
# Package toolchain/out/<abi> for an Android APK.
#
# Output layout (out/android/<abi>/):
#   jniLibs/lib<slug>.so   executables and dlopen-able modules; copied into the
#                          app's src/main/jniLibs/<abi>/ so Android extracts
#                          them to the native library dir (the only exec-capable
#                          location for an app targeting API 29+).
#   assets/toolchain/...   data files (stdlib .py, git templates, CA bundle);
#                          copied to the app's private files dir on first run.
#   native-manifest.txt    how to rebuild the tree at runtime:
#                            file|rel|libname.so   -> symlink to native lib
#                            link|rel|target       -> plain symlink
#                            data|rel|              -> copied from assets
#
# Usage: ./pack-jnilibs.sh [abi]
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=env.sh
source "$SCRIPT_DIR/env.sh"

ABI="${1:-arm64-v8a}"
SRC="$OUT_ROOT/$ABI"
DIST="$OUT_ROOT/android/$ABI"
JNI="$DIST/jniLibs"
ASSETS="$DIST/assets/toolchain"
MANIFEST="$DIST/native-manifest.txt"

[[ -d "$SRC/bin" ]] || die "missing $SRC/bin; run ./build.sh first"

rm -rf "$DIST"
mkdir -p "$JNI" "$ASSETS"
printf 'abi|%s\n' "$ABI" >"$MANIFEST"

slug() {
    printf '%s' "$1" | tr '/ ' '__' | tr -c 'A-Za-z0-9._-' '_'
}

add_lib() {
    local rel="$1" lib="lib$(slug "$1").so"
    install -m 755 "$SRC/$rel" "$JNI/$lib"
    printf 'file|%s|%s\n' "$rel" "$lib" >>"$MANIFEST"
}

add_link() {
    printf 'link|%s|%s\n' "$1" "$2" >>"$MANIFEST"
}

add_data() {
    local rel="$1"
    install -D -m 644 "$SRC/$rel" "$ASSETS/$rel"
    printf 'data|%s|\n' "$rel" >>"$MANIFEST"
}

libs=0
links=0
data=0
while IFS= read -r path; do
    rel="${path#"$SRC"/}"
    case "$rel" in
        bin/*|sbin/*|libexec/*|share/*|lib/*) ;;
        *) continue ;;
    esac
    case "$rel" in
        *.a|*.la|lib/pkgconfig/*) continue ;;
    esac
    if [[ -L "$path" ]]; then
        add_link "$rel" "$(readlink "$path")"
        links=$((links + 1))
    elif [[ -f "$path" ]]; then
        case "$rel" in
            bin/*|sbin/*|libexec/*|*.so) add_lib "$rel"; libs=$((libs + 1)) ;;
            *) add_data "$rel"; data=$((data + 1)) ;;
        esac
    fi
done < <(find "$SRC/bin" "$SRC/sbin" "$SRC/libexec" "$SRC/share" "$SRC/lib" -mindepth 1 2>/dev/null | sort)

log "packed for $ABI: $libs libs, $links links, $data data files"
check_16kb_alignment "$JNI"
log "  jniLibs: $(du -sh "$JNI" | cut -f1)"
log "  assets:  $(du -sh "$ASSETS" | cut -f1)"
log "  manifest: $MANIFEST"
