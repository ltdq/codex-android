#!/usr/bin/env bash
set -euo pipefail

NATIVE_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
mkdir -p "$NATIVE_ROOT/target"
exec 9>"$NATIVE_ROOT/target/.prepare-rust-std.lock"
flock 9
ORIGINAL="$(rustup run 1.95.0 rustc --print sysroot)"
SYSROOT="$NATIVE_ROOT/rust-sysroot"
SOURCE="$SYSROOT/lib/rustlib/src/rust"
PATCH="$NATIVE_ROOT/patches/rust-std-android-flock.patch"
rustup component add --toolchain 1.95.0 rust-src
mkdir -p "$SOURCE"
[[ ! -L "$SYSROOT" && ! -L "$SOURCE" ]] || { echo "Generated Rust sysroot must not be a symlink" >&2; exit 1; }

# Only rust-src is copied. Compiler binaries and prebuilt host libraries remain read-only links.
for entry in "$ORIGINAL"/*; do
    [[ "${entry##*/}" == lib ]] || ln -sfn "$entry" "$SYSROOT/${entry##*/}"
done
for entry in "$ORIGINAL/lib"/*; do
    [[ "${entry##*/}" == rustlib ]] || ln -sfn "$entry" "$SYSROOT/lib/${entry##*/}"
done
for entry in "$ORIGINAL/lib/rustlib"/*; do
    [[ "${entry##*/}" == src ]] || ln -sfn "$entry" "$SYSROOT/lib/rustlib/${entry##*/}"
done

stamp="$(sha256sum "$PATCH" | cut -d ' ' -f 1)"
if [[ ! -f "$SYSROOT/.std-patch" || "$(<"$SYSROOT/.std-patch")" != "$stamp" ]] ||
    ! git -C "$SOURCE" apply --reverse --check "$PATCH" >/dev/null 2>&1; then
    cp -a "$ORIGINAL/lib/rustlib/src/rust/." "$SOURCE/"
    git -C "$SOURCE" apply --check "$PATCH"
    git -C "$SOURCE" apply "$PATCH"
    printf '%s\n' "$stamp" > "$SYSROOT/.std-patch"
fi
