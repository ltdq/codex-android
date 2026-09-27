# Android 工具链

交叉编译进 APK、供 `codex-core` spawn 的命令行工具（arm64-v8a）。声明在
`toolchain/build.gradle.kts`，一个工具一个 `tool { }` 块；源码取自 `third_party/` 的
git submodule（固定发布 tag）或上游 release tarball，`-PgnuMirror=` 换下载镜像。

| 工具 | 版本 |
| --- | --- |
| `7zz` | 26.03 |
| `ast-grep`（`sg`） | 0.45.3 |
| `bash`（`sh`） | 5.3 |
| `bc`（`dc`） | 1.08.2 |
| `binutils` | 2.47 |
| `bun`（`bunx`） | 1.4.2 |
| `bzip2`（`bunzip2` `bzcat`） | 1.0.8 |
| `clang-format` | 23.1.2 |
| `coreutils` | 9.12 |
| `curl` | 8.22.0 |
| `diffutils` | 3.12 |
| `fd` | 10.5.0 |
| `file` | 5.48 |
| `findutils`（`find` `xargs`） | 4.11.0 |
| `gawk` | 5.4.1 |
| `git` | 2.55.0 |
| `gofmt` | Go 1.27 |
| `grep`（`egrep` `fgrep`） | 3.12 |
| `gzip` | 1.15 |
| `jq` | 1.8.2 |
| `libffi` | 3.8.0 |
| `make` | 4.4.1 |
| `openssh`（`ssh` `scp` `sftp` `ssh-keygen`） | 10.5p1 |
| `openssl` | 3.6.4 |
| `patch` | 2.8 |
| `procps`（`ps` `kill` `pgrep` `pkill` `free` `uptime` `vmstat`） | 4.0.7 |
| `python` | 3.14.7 |
| `ripgrep`（`rg`） | 15.2.0 |
| `ruff` | 0.16.9 |
| `sed` | 4.10 |
| `shfmt` | 3.14.1 |
| `sqlite`（`sqlite3`） | 3.53.4 |
| `tar` | 1.35 |
| `tree` | 2.3.2 |
| `unzip`（`zipinfo`） | 6.0 |
| `uv` | 0.12.19 |
| `which` | 2.25 |
| `xxd` | vim 9.2.1133 |
| `xz` | 5.8.4 |
| `yq` | 4.53.6 |
| `zip` | 3.0 |
| `zstd`（`unzstd` `zstdcat` `zstdmt`） | 1.5.7 |
