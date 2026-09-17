#!/usr/bin/env bash
set -euo pipefail

NATIVE_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
mkdir -p "$NATIVE_ROOT/target"
exec 9>"$NATIVE_ROOT/target/.prepare-upstream.lock"
flock 9
UPSTREAM="$NATIVE_ROOT/../codex"
WORKTREE="$NATIVE_ROOT/build-upstream"
PATCH="$NATIVE_ROOT/patches/android-runtime.patch"
APPLIED="$WORKTREE/.codex-android-applied.patch"
revision="$(git -C "$UPSTREAM" rev-parse HEAD)"

if [[ ! -e "$WORKTREE" ]]; then
    git -C "$UPSTREAM" worktree prune --expire now
    git -C "$UPSTREAM" worktree add --detach "$WORKTREE" "$revision"
fi
[[ -f "$WORKTREE/.git" ]] || { echo "error: $WORKTREE is not a generated Git worktree" >&2; exit 1; }

if [[ "$(git -C "$WORKTREE" rev-parse HEAD)" != "$revision" ]]; then
    if [[ -f "$APPLIED" ]]; then
        git -C "$WORKTREE" apply --reverse --check "$APPLIED"
        git -C "$WORKTREE" apply --reverse "$APPLIED"
        rm "$APPLIED"
    fi
    git -C "$WORKTREE" diff --exit-code
    git -C "$WORKTREE" checkout --detach "$revision"
fi

if ! git -C "$WORKTREE" apply --reverse --check "$PATCH" >/dev/null 2>&1; then
    if [[ -f "$APPLIED" ]]; then
        git -C "$WORKTREE" apply --reverse --check "$APPLIED"
        git -C "$WORKTREE" apply --reverse "$APPLIED"
    fi
    git -C "$WORKTREE" apply --check "$PATCH"
    git -C "$WORKTREE" apply "$PATCH"
fi
cp "$PATCH" "$APPLIED"
