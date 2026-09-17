#!/usr/bin/env bash
set -euo pipefail
NATIVE_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
compiler="$(rustup which --toolchain 1.95.0 rustc)"
exec "$compiler" --sysroot "$NATIVE_ROOT/rust-sysroot" "$@"
