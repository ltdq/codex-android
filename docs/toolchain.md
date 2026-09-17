# Android 工具链

交叉编译进 APK、供 `codex-core` spawn 的命令行工具。项目结构与构建入口见根目录
[README.md](../README.md)，当前待办见 [TODO.md](TODO.md)。

## Toolchain（给 codex-core 提供的环境）

`codex-core` 会 spawn `bash` / `git` / `rg` 等外部命令；Android 上 `/system/bin` 只有
toybox 且 app 私有目录不可执行（targetSdk ≥ 29 的 W^X）。因此这些工具用 NDK
交叉编译成 aarch64 可执行文件，并以 `lib<name>.so` 形式放进 APK 的 jniLibs，安装时
被解包到 app 的 native library 目录（唯一可执行的位置）；运行时在 app 私有目录里用
symlink 重建目录树并注入 PATH。

### 构建

```bash
cd toolchain
./build.sh                      # 默认 arm64-v8a，构建全部工具
./build.sh --abi arm64-v8a,x86_64 git bash
./build.sh --list               # 查看工具列表
./pack-jnilibs.sh arm64-v8a     # 打包成 APK 用的 jniLibs + assets + manifest
./device-smoke-test.sh          # adb 真机冒烟测试（bash/git/rg/curl/openssl/python/bun）
```

产物在 `toolchain/out/android/<abi>/`：

```
jniLibs/lib*.so          # 拷进 app/src/main/jniLibs/<abi>/
assets/toolchain/...     # 数据文件（python stdlib、git templates、cacert.pem），拷进 assets
native-manifest.txt      # 运行时重建目录树的清单
```

当前工具（arm64-v8a，API 36，`-O3 -flto` 尽可能开启）：

| 类别 | 工具 |
| --- | --- |
| shell | `bash` 5.3、busybox(ash/…) |
| VCS | `git` 2.55（https/ssh remote，builtins + libexec 脚本） |
| 搜索/文本 | `rg` 15.2、GNU `sed` 4.10、`gawk` 5.4、GNU `diffutils` 3.12、GNU `patch` 2.8、busybox grep/find/vi/less/tree/nc/ps/top/…（280 applets） |
| 网络 | `curl` 8.22（HTTPS）、`ssh`/`scp`/`sftp`/`ssh-keygen`（OpenSSH 10.5）、`openssl` 3.5；busybox `wget`（仅 HTTP）兜底 |
| 语言 | `python` 3.14.7（ssl/zlib/ctypes/hashlib/sqlite3）、`uv` 0.12（venv/纯 Python 包）、`bun` 1.4 + `bunx`（上游官方 Android 预编译） |
| 代码分析/修改 | `clang-format` 23（C/C++/Java/JS/JSON 格式化）、`ruff` 0.16（Python lint/format）、`ast-grep` 0.45（结构化搜索/替换，别名 `sg`）、`fd` 10.5、`shfmt` 3.14、`gofmt`、`yq` 4.53（YAML/JSON） |
| 归档 | GNU `tar` 1.35、`gzip` 1.14、GNU `xz` 5.8、`zstd` 1.5、busybox bzip2/unzip、`7zz` 26.03 |
| 构建/数据 | `make` 4.4、`sqlite3` 3.53、`jq` 1.8、`file` 5.46 |
| 二进制工具 | binutils 2.47：`readelf` `objdump` `nm` `strings` `objcopy` `strip` `ar` `as` `ld` `addr2line` `size` `c++filt` |

arm64-v8a 的 jniLibs 目前约 361MB（bun 83、ast-grep 46、uv 42、ruff 19、yq 14 占大头）；
不需要的工具把 `tools/<tool>.sh` 删掉再跑 `pack-jnilibs.sh` 即可（out/ 里的产物也可手动删）。

### 重复工具的取舍

同样能力只保留一个实现，优先「上游有 Android 成品 / 不需要打 patch / 性能强 / 体积小 /
依赖少」：

- **JS 运行时：只留 `bun`，不编 node。** bun ≥ 1.3.14 有官方 `bun-linux-aarch64-android`
  预编译（`tools/bun.sh` 只解包，零交叉编译），二进制仅依赖 `libc/libm/libdl`；
  `bun x` 自己解释 JS，不经过 shebang，所以在 Android（没有 `/usr/bin/env`）上跑 CLI /
  MCP server 比 npm 更稳。node 需要 V8 + 完整 LTO 的交叉编译、维护 3 个补丁、还多出
  ~110MB，收益与 bun 重叠，遂移除。
  上游 Android 包只发 `bun` 一个二进制，`bunx` 由 `tools/bun.sh` 包装成 `bun x`；
  不提供 `npx` 别名，MCP 配置直接写 `bunx <pkg>` 或 `bun x <pkg>`。
- **下载工具：只留 `curl`。** `wget` 需要 Android patch、与 curl 功能重叠，移除；
  busybox 的 `wget` applet 仍在，纯 HTTP 场合可兜底（busybox 未开 HTTPS）。
- **GNU 小工具 vs busybox applet：** `sed`/`gawk`/`tar`/`gzip`/`xz`/`diff`/`patch` 保留
  GNU 版（都只有几百 KB、无额外依赖、兼容性最好；`diff`/`patch`/`xz` 会覆盖 busybox 的
  同名 applet——busybox 的 `xz` 只能解压，`tar -cJf` 会失败）；busybox 里同名 applet 仍随
  busybox 提供，`gawk` 比 busybox awk 完整得多。
- 其余无重叠：`rg`、`git`、`python`/`uv`、`openssl`、`openssh`、`7zz`、`make`、`sqlite3`、
  `jq`、`file`、`binutils` 各司其职。
- kit 之外的目标（编译器/JDK/Android 构建工具）见文末「刻意不加」。

注意：设备上装不了带 native addon 的 JS/包（没有 clang/node-gyp），bun/npm 类工具链
只能跑纯 JS；`bun install -g` 的全局 bin 落在 `<root>/bin`，缓存默认 `$HOME/.bun`。

### 分析/修改类工具怎么编的

- `clang-format` 从 LLVM 23 源码编出（`tools/clang-format.sh`）：交叉编译 LLVM 需要能在
  构建机上运行的 tablegen，所以先在宿主上编 `llvm-tblgen`/`clang-tblgen`/`llvm-min-tblgen`，
  再用 `-DLLVM_NATIVE_TOOL_DIR` 指向它们做交叉编译；产物静态链接 libc++，单文件 3.9MB。
  另外注意 NDK 的 `android.toolchain.cmake` 不能泄漏给宿主子构建，脚本里会先清掉
  `CC/CXX/AR/...` 环境变量。
- `ruff`/`ast-grep`/`fd` 用 cargo 交叉编译（和 `uv` 同一套 `cargo_env`：NDK linker +
  `libgcc` shim + 完整 LTO）；`yq`/`shfmt`/`gofmt` 用宿主 Go 交叉编译（`GOOS=android`）。
- 这些工具都只做**读代码/改代码/格式化**，不包含编译器。

### 刻意不加

- **编译器/构建工具**（clang、rustc、cmake、ninja、pkg-config、perl）：设备不做编译，
  加了只增体积（数百 MB）。
- **JDK + Android SDK 构建工具**（aapt2/d8/apksigner/zipalign、Gradle）：这些是 glibc
  程序，bionic 上跑不起来，需要 proot/glibc 层或远端构建，不属于本工具链。

### APK 侧接入

App 的 Gradle 项目位于 `android/`。`stageRuntime` 会从工具链产物目录生成
`jniLibs` 和 assets，无需手动复制到源码目录；安装配置（`useLegacyPackaging` +
`extractNativeLibs="true"`）见根目录 README。首次启动时（或版本变化时）在 `filesDir` 里重建环境：

1. 解包 assets/toolchain 到 `files/runtime/toolchain/` 的临时 staging 目录，完成后替换旧工具链；
2. 按 `native-manifest.txt` 建 symlink：
   - `file|lib/python3.14/lib-dynload/_ssl.cpython-314-aarch64-linux-android.so|liblib_python3.14_lib-dynload__ssl.cpython-314-aarch64-linux-android.so.so`
     → `ln -s "$nativeLibraryDir/<第三列>" "<toolchainRoot>/<第二列>"`
   - `link|bin/python3|python3.14` → `ln -s python3.14 <toolchainRoot>/bin/python3`
   - `data|lib/python3.14/ssl.py|` 已由第 1 步放置，忽略
3. 给 codex 的子进程注入环境变量：

```
PATH=<toolchainRoot>/bin:<toolchainRoot>/sbin
SHELL=CODEX_SHELL=<toolchainRoot>/bin/bash
HOME=<filesDir>/home
CODEX_HOME=CODEX_SQLITE_HOME=<filesDir>/home/.codex
GIT_EXEC_PATH=<toolchainRoot>/libexec/git-core
GIT_TEMPLATE_DIR=<toolchainRoot>/share/git-core/templates
PYTHONHOME=<toolchainRoot>
TMPDIR=<filesDir>/tmp
CURL_CA_BUNDLE=SSL_CERT_FILE=<toolchainRoot>/share/cacert.pem
GIT_SSL_CAINFO=<toolchainRoot>/share/cacert.pem
```

注意 `git` 的 https 走静态 libcurl（`git-remote-https`），libcurl 认 `CURL_CA_BUNDLE`，
但 git 自己只认 `GIT_SSL_CAINFO` / `http.sslCAInfo`，两者都要给。

注意：`files/runtime/toolchain` 里的 symlink 指向 native lib 目录中的只读文件，因此 codex 的
`git stash`/`git rebase` 等 libexec shell 脚本、python 的扩展模块都能正常 exec /
dlopen（Android 只拦截**可写**文件的动态加载）。`TMPDIR` 必须指向可写目录，bash
heredoc / git 临时文件依赖它。

`HOME` 与工具链目录分离，升级工具链不会删除用户配置和工作区。运行时通过
`BUN_INSTALL_CACHE_DIR` 等变量将可清理缓存放到 `cacheDir/toolchain`。
`bun`/`bunx` 通过 jniLibs 的 symlink 运行。

