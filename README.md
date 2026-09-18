# Codex Android

把 [openai/codex](https://github.com/openai/codex) 的 Rust 内核以 JNI 库编进 Android Compose 应用：
UI 通过上游 app-server 协议访问同进程内的 `codex-core`，命令交给 App 内置的 Android 工具链执行。

## 目录

```text
android/                 Gradle 项目，Android Studio 从这里打开
  app/src/main/          Compose UI、协议客户端、Android 运行时
  app/src/test/          JVM 协议、UI 状态和 manifest 测试
  app/src/androidTest/   真机 JNI / RPC / 工具链集成验证
native/                  JNI bridge、Codex helper 与 Rust 交叉编译入口
  out/arm64-v8a/         构建生成的 JNI 库和可执行 helper
codex/                   上游 openai/codex Git submodule，固定提交
toolchain/               bash/git/rg/python/bun 等交叉编译工具链
docs/                    工具链说明与待办
scripts/                 真机冒烟脚本
```

## 构建

需要 JDK 21、Android SDK 37、NDK r30、Rust 1.95.0 的 `aarch64-linux-android` target 和 `rust-src`。
当前支持 arm64-v8a，最低 Android API 36。首次克隆后执行
`git submodule update --init --recursive`。

Android 必要兼容补丁位于 `native/patches/`，构建时在 `native/build-upstream/` 独立工作树应用，
`codex/` 保持上游原样。标准库补丁、helper 与运行时契约见 [native/README.md](native/README.md)。

```bash
# 工具链已有产物时无需重复全量构建
cd toolchain
./build.sh
./pack-jnilibs.sh arm64-v8a
cd ..

# Gradle 自动调用 native/build.sh，再打包工具链与 JNI 产物
cd android
./gradlew :app:assembleDebug
```

APK：`android/app/build/outputs/apk/debug/app-debug.apk`。应用 ID 与包名均为 `com.cy.codex`。
`native/build.sh` 使用 Cargo 增量构建；修改 Kotlin 后，
可在已有原生产物基础上用 `-PskipNativeBuild` 跳过 Rust 构建，缺少 JNI 或 helper 时打包会直接报错。
`assemble*` 结束后还会执行 `verify*ToolchainAssets`，逐条核对 `native-manifest.txt` 里每个 `data`
条目确实进了 APK——工具链必须原样打包，资产合并静默丢文件会直接失败在构建期。

## 运行架构

```text
Compose UI / CodexApp
  -> JsonRpcAppServerClient
  -> NativeRpcTransport / NativeBridge
  -> codex-app-server::in_process
  -> codex-core / exec-server
  -> App 内置 bash、git、rg、python、bun 等工具
```

- `CodexApplication` 持有会话与协程，Activity 重建不会重启 native 会话。
- JNI 启动在 IO 线程完成，使用上游 `initialize` / `initialized` 握手。
- 通信在 JNI 边界只编解码一次 JSON：消息以 UTF-8 字节（`ByteArray`）传递，并带
  `JsonRpcMessageKind`（request/notification/response/error）标记。Rust 按标记用 `serde_json`
  直接反序列化成 typed `ClientRequest`/`ClientNotification`（不再解析 JSON-RPC 信封再转一次
  `Value`），事件用 `serde_json::to_vec` 从 typed `ServerNotification` 一次写出；Kotlin 侧用
  kotlinx.serialization 的 `JsonElement` 解析/编码一次。字节传输同时避免了 Java 字符串的
  Modified UTF-8 转换，非 BMP 字符（emoji）不再经过变更编码。
- 无本地演示账户、示例项目或脚本回放；配置、模型、会话和消息来自真实服务端。
- 未登录也能启动，模型请求需要有效账户：账户页面提供 ChatGPT 设备码与 API key 两种登录。
  宿主机 Codex 凭据不会复制到设备。
- Android 进程被系统终止后，下次启动重新连接并读取磁盘上的会话；没有后台常驻保证。
- 命令运行在 Android App UID 的权限边界内。Android 不支持上游 Linux namespace
  沙箱，因此嵌入运行时使用 `danger-full-access`；这不代表拥有 root 或其他 App 的私有数据权限。
- 协议方法、通知与类型以 `codex/codex-rs/app-server-protocol` 为准；当前客户端只绑定了其中
  一部分方法，其余调用返回失败，清单见 [docs/TODO.md](docs/TODO.md)。

## 私有数据

```text
files/runtime/toolchain/  APK 数据与指向 nativeLibraryDir 的工具 symlink
files/home/              HOME
files/home/.codex/       CODEX_HOME，配置、认证与会话记录
files/home/.codex/config.toml 首次生成、之后保留的用户配置
files/home/.codex/log/   Codex 日志目录
files/home/.config/     XDG_CONFIG_HOME
files/home/.local/      XDG 数据和状态
files/workspaces/default/ 默认工作目录
files/tmp/               临时文件
cache/toolchain/         可清理的工具缓存
```

工具链更新采用独立 staging 目录，保留 HOME、配置和工作区。APK 使用
`extractNativeLibs=true` 和 `useLegacyPackaging=true`，使 Android 可以从安装目录执行
内置工具。`PATH`、`SHELL`、`PYTHONHOME`、`GIT_EXEC_PATH`、`GIT_SSL_CAINFO`、
`SSL_CERT_FILE` 等由运行时注入，完整清单与 symlink 规则见 [docs/toolchain.md](docs/toolchain.md#apk-侧接入)。
账户及工作区数据不参与 Android 自动备份。

## 验证

```bash
cd android
./gradlew :app:testDebugUnitTest -PskipNativeBuild
./gradlew :app:assembleDebug :app:assembleDebugAndroidTest -PskipNativeBuild
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r -t app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w \
  com.cy.codex.test/com.cy.codex.runtime.RuntimeSmokeInstrumentation
```

真机测试在真实 App UID 下验证工具链安装、JNI 启动、账户/配置/模型/会话读取、文件读写、
`command/exec`、apply_patch、新建会话、shell 消息流和重启后恢复，不发送模型请求。模型生成和
登录需要有效账户及网络连接，不能由离线测试替代。

`scripts/device-smoke-test.sh -PskipNativeBuild` 可执行同一套打包、安装和验证，报告写到
`artifacts/device-smoke.txt`。`native/file-lock-smoke-test.sh` 单独验证 Android 标准库的互斥锁、
共享锁、竞争和释放；`native/smoke-test.sh` 使用临时 CODEX_HOME 验证宿主机上的真实内核与 helper。

仅修改 JVM 层时，可执行
`./gradlew :app:testDebugUnitTest -x :app:stageRuntime -PskipNativeBuild`，
该命令不产生可安装 APK，也不验证 JNI。

## 文档

- [docs/TODO.md](docs/TODO.md)：当前待办、已知缺口与未验证项
- [docs/toolchain.md](docs/toolchain.md)：工具链构建、工具清单与取舍
- [native/README.md](native/README.md)：JNI/helper 构建、补丁与运行时契约
- [AGENTS.md](AGENTS.md)：在本仓库工作时的规范
