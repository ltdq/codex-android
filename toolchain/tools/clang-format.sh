#!/usr/bin/env bash
# clang-format for Android: cross-compile just the clang-format binary out of
# the LLVM monorepo.  Statically linked against libc++, so it is a single
# self-contained executable (used to format/edit C, C++, Java, JS, JSON, ...).
set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/../env.sh"
toolchain_env "${ABI:?ABI is required}"

command -v cmake >/dev/null || die "host cmake is required"
command -v ninja >/dev/null || die "host ninja is required"

# Let LLVM's build system own the flags; env.sh's global -O3 -flto are meant
# for configure/make projects and would only slow this build down.  The
# compiler/archiver variables have to go too: CMake would otherwise use the
# Android clang for the native tablegen build.  Both builds find the NDK
# through ANDROID_NDK_HOME in the CMake toolchain file.
unset CFLAGS CXXFLAGS LDFLAGS CPPFLAGS CC CXX AR RANLIB NM STRIP OBJCOPY
ndk_strip="$NDK_BIN/llvm-strip"

ver="${LLVM_VER:-23.1.1}"
tarball="$SRC_DIR/llvm-project-$ver.src.tar.xz"
fetch "https://github.com/llvm/llvm-project/releases/download/llvmorg-$ver/llvm-project-$ver.src.tar.xz" "$tarball"

srcdir="$TOOL_BUILD/llvm-project-$ver"
if [[ ! -d "$srcdir/llvm" ]]; then
    log "extract llvm-project-$ver"
    extract_archive "$tarball" "$srcdir"
fi

# Cross-compiling LLVM needs tablegen binaries that run on the build host;
# build those natively once (LLVM_NATIVE_TOOL_DIR) instead of letting LLVM
# build a NATIVE sub-project with the Android toolchain.
hostflags=(
    -DCMAKE_BUILD_TYPE=Release
    -DLLVM_ENABLE_PROJECTS=clang
    -DLLVM_TARGETS_TO_BUILD=AArch64
    -DLLVM_ENABLE_TERMINFO=OFF
    -DLLVM_ENABLE_ZLIB=OFF
    -DLLVM_ENABLE_ZSTD=OFF
    -DLLVM_ENABLE_LIBXML2=OFF
    -DLLVM_ENABLE_LIBEDIT=OFF
    -DLLVM_ENABLE_ASSERTIONS=OFF
    -DLLVM_INCLUDE_TESTS=OFF
    -DLLVM_INCLUDE_EXAMPLES=OFF
    -DLLVM_INCLUDE_BENCHMARKS=OFF
    -DLLVM_INCLUDE_DOCS=OFF
    -DCLANG_ENABLE_STATIC_ANALYZER=OFF
    -DCLANG_ENABLE_ARCMT=OFF
)
nativedir="$srcdir/build-native"
if [[ ! -x "$nativedir/bin/llvm-tblgen" ]]; then
    log "configure native tablegen tools"
    cmake -S "$srcdir/llvm" -B "$nativedir" -G Ninja "${hostflags[@]}" \
        >"$TOOL_BUILD/clang-format-native-config.log" 2>&1 || {
        tail -40 "$TOOL_BUILD/clang-format-native-config.log"
        die "native tablegen configure failed"
    }
    log "build native tablegen tools"
    if ! cmake --build "$nativedir" --target llvm-tblgen clang-tblgen llvm-min-tblgen \
        -j "$(jobs)" >"$TOOL_BUILD/clang-format-native-build.log" 2>&1; then
        tail -60 "$TOOL_BUILD/clang-format-native-build.log"
        die "native tablegen build failed"
    fi
fi

builddir="$srcdir/build-android"
if [[ ! -f "$builddir/build.ninja" ]]; then
    log "configure clang-format for $ABI (api $ANDROID_API)"
    cmake -S "$srcdir/llvm" -B "$builddir" -G Ninja \
        -DCMAKE_TOOLCHAIN_FILE="$ANDROID_NDK_HOME/build/cmake/android.toolchain.cmake" \
        -DANDROID_ABI="$ABI" \
        -DANDROID_PLATFORM="android-$ANDROID_API" \
        -DANDROID_STL=c++_static \
        -DLLVM_NATIVE_TOOL_DIR="$nativedir/bin" \
        "${hostflags[@]}" \
        >"$TOOL_BUILD/clang-format-config.log" 2>&1 || {
        tail -40 "$TOOL_BUILD/clang-format-config.log"
        die "clang-format configure failed"
    }
fi

log "build clang-format (this takes a while)"
if ! cmake --build "$builddir" --target clang-format -j "$(jobs)" \
    >"$TOOL_BUILD/clang-format-build.log" 2>&1; then
    tail -60 "$TOOL_BUILD/clang-format-build.log"
    die "clang-format build failed, see $TOOL_BUILD/clang-format-build.log"
fi

install -m 755 "$builddir/bin/clang-format" "$TOOL_OUT/bin/clang-format"
"$ndk_strip" --strip-unneeded "$TOOL_OUT/bin/clang-format" 2>/dev/null || true
log "clang-format: $(file -b "$TOOL_OUT/bin/clang-format")"
