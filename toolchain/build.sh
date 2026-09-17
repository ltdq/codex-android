#!/usr/bin/env bash
# Cross-compile the Android toolchain binaries codex-core expects in its
# environment (bash, git, ripgrep, ...).
#
# Usage:
#   ./build.sh                       # build all tools for arm64-v8a
#   ./build.sh --abi arm64-v8a,x86_64
#   ./build.sh git bash              # build only the named tools
#   ./build.sh --force git           # ignore existing build stamps
#   ./build.sh --list

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=env.sh
source "$SCRIPT_DIR/env.sh"

ALL_ABIS="arm64-v8a"
DEFAULT_ABIS="arm64-v8a"

usage() {
    sed -n '2,12p' "$0" | sed 's/^# \{0,1\}//'
    echo
    echo "Available tools: $(cd "$SCRIPT_DIR/tools" && ls -1 *.sh 2>/dev/null | sed 's/\.sh$//' | tr '\n' ' ')"
}

FORCE=0
ABIS=()
TOOLS=()
while [[ $# -gt 0 ]]; do
    case "$1" in
        -a|--abi) ABIS+=("$2"); shift 2 ;;
        --api)  ANDROID_API="$2"; shift 2 ;;
        --force) FORCE=1; shift ;;
        --list)
            (cd "$SCRIPT_DIR/tools" && ls -1 *.sh | sed 's/\.sh$//')
            exit 0
            ;;
        -h|--help) usage; exit 0 ;;
        -*) die "unknown option: $1" ;;
        *) TOOLS+=("$1"); shift ;;
    esac
done
export ANDROID_API

if [[ ${#ABIS[@]} -eq 0 ]]; then
    ABIS=("$DEFAULT_ABIS")
fi
split_abis=()
for entry in "${ABIS[@]}"; do
    IFS=',' read -ra parts <<<"$entry"
    split_abis+=("${parts[@]}")
done
ABIS=("${split_abis[@]}")

if [[ ${#TOOLS[@]} -eq 0 ]]; then
    mapfile -t TOOLS < <(cd "$SCRIPT_DIR/tools" && ls -1 *.sh | sed 's/\.sh$//')
fi
for tool in "${TOOLS[@]}"; do
    [[ -f "$SCRIPT_DIR/tools/$tool.sh" ]] || die "no such tool: $tool"
done

log "NDK:   $ANDROID_NDK_HOME"
log "API:   $ANDROID_API"
log "ABIs:  ${ABIS[*]}"
log "Tools: ${TOOLS[*]}"

for abi in "${ABIS[@]}"; do
    toolchain_env "$abi"
    log "=== $abi ==="
    for tool in "${TOOLS[@]}"; do
        stamp="$TOOL_BUILD/.stamps/$tool"
        stale=0
        if [[ -n "$(find "$SCRIPT_DIR/tools/$tool.sh" "$SCRIPT_DIR/env.sh" "$PATCH_DIR" -newer "$stamp" -print -quit 2>/dev/null)" ]]; then
            stale=1
        fi
        if [[ $FORCE -eq 0 && -f "$stamp" && $stale -eq 0 ]]; then
            log "skip $tool (up to date)"
            continue
        fi
        log "build $tool ($abi)"
        mkdir -p "$(dirname "$stamp")"
        rm -f "$stamp"
        bash "$SCRIPT_DIR/tools/$tool.sh"
        touch "$stamp"
    done
done

log "artifacts:"
for abi in "${ABIS[@]}"; do
    find "$OUT_ROOT/$abi/bin" -maxdepth 1 \( -type f -o -type l \) 2>/dev/null | sort | sed "s|^|  |"
done
