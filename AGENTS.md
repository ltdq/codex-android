# AGENTS

本文件只写在本仓库工作时的规范。架构、构建与运行见 [README.md](README.md)，
待办见 [docs/TODO.md](docs/TODO.md)。

## 改动边界

| 路径 | 内容 | 规则 |
| --- | --- | --- |
| `codex/` | 上游 openai/codex submodule，固定提交 | 只读。Android 兼容改动做成 patch 放 `native/patches/` |
| `native/` | JNI bridge、codex-helper、Rust 交叉编译 | 由 `prepare-upstream.sh` 在 `native/build-upstream/` 工作树里打补丁，产物进 `native/out/` |
| `toolchain/` | 交叉编译进 APK 的运行时工具 | 一个工具一个 `tools/<tool>.sh`，版本号集中在 `env.sh` |
| `android/` | Compose UI、协议客户端、Android 运行时 | 协议形状以 `codex/codex-rs/app-server-protocol` 为准 |
| `docs/` | 工具链说明、待办 | 见「文档」 |

## 约定

- **上游优先**：行为、字段名、枚举 wire 值都对齐 `codex-rs`；新增 wire 字段前先查
  `app-server-protocol` 的 schema，不要发明字段。
- **不提交构建产物**：`native/out/`、`toolchain/out/`、`**/build/`、`artifacts/`、
  `local.properties` 都不入库；APK 内的 jniLibs 与 assets 由 `stageRuntime` 生成。
- **文档中文、代码注释英文**；注释写「为什么」，引用上游时给 `codex-rs/...` 路径。
- **文档只写当前有效的事实**：完成的条目从待办里删除，不要留成已勾选的历史。
- **Kotlin 块注释会嵌套**：注释正文里不要出现 `/*`（路径写成 `config/…`），
  `SourceCommentTest` 会拦下这类静默吞掉整个文件的写法。
- 改工具链时同步四处：`tools/<tool>.sh`、`env.sh` 版本号、README 工具表、
  `toolchain/device-smoke-test.sh`。
- 改协议或客户端时同步三处：`protocol/protocol/**` 的 wire 类型、
  `json_rpc_app_server_client.kt` 的绑定、`app/src/test` 的 JVM 测试。
- 优先复用 `miuix` 已有组件，遵循 `miuix` 风格。

## 验证

| 改动范围 | 命令 |
| --- | --- |
| Kotlin | `cd android && ./gradlew :app:testDebugUnitTest -PskipNativeBuild` |
| 全量 APK | `cd android && ./gradlew :app:assembleDebug` |
| native / Rust | `bash native/build.sh`，宿主内核验证 `bash native/smoke-test.sh` |
| toolchain | `cd toolchain && ./build.sh [tool] && ./pack-jnilibs.sh arm64-v8a` |
| 真机集成 | `bash scripts/device-smoke-test.sh -PskipNativeBuild` |

模型生成与登录需要真实账户和网络，离线测试不能替代（见 `docs/TODO.md`）。
