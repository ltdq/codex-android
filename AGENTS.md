# AGENTS

Codex Android replaces `codex-tui` with a Compose UI and runs Codex on Android from an
external toolchain: the command-line tools the kernel spawns are cross-compiled into the
APK. The UI is a port, so ported files follow the upstream layout
(`codex/codex-rs/tui/src/`) — same module paths, same file names, upstream path named
where a declaration mirrors one — which is what keeps upstream changes easy to follow.

Comments and commit messages follow the Linux kernel, AOSP and Kubernetes conventions:
terse, specific, and about the change itself rather than the work that produced it.

## Comments

Say what the code does and why it is needed, in English, in one to three lines. Never
how it works: where the how needs explaining, rewrite the code. Put the comment at the
head of the declaration rather than inside the body, add nothing the signature and the
names already say, and keep the upstream path when a declaration mirrors one.
Over-commenting is the failure, not under-commenting. Fix a stale comment in the same
commit that made it stale.

## TODOs

A TODO records the problem, not the way out of it: what is missing, wrong or inconsistent,
and the evidence that shows it — normally the upstream path and the code that disagrees.
No fix direction, no design, no "should"; whoever picks it up reads the code and decides.

## Commits

Subject in the imperative mood, as an order to the codebase: `toolchain: pin llvm and
binutils to a release tag`, not `Pinned …`. Prefix it with the area (`app:`, `native:`,
`toolchain:`, `build:`, `docs:`) instead of a `feat:`/`fix:` type, keep it to 50
characters where the change allows and never past 72, and leave off the trailing period.
Blank line, then a body hard-wrapped at 72 columns: the problem in a sentence or two,
then the fix, in plain prose rather than a walk through the diff. One change per commit.
End with the author's `Signed-off-by`.
