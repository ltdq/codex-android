# TODO

当前待办、已知缺口与未验证项。已完成的部分不在本文件里，见 [README.md](../README.md)、
[docs/toolchain.md](toolchain.md) 与 [native/README.md](../native/README.md)。

约定：只记「没做的、做错的、没验证的」；条目里的路径相对仓库根，行号为撰写时位置；
引用上游一律用 `codex/codex-rs/...` 路径。完成一条就删一条。

## 0. 现状基线

工具链、JNI in-process app-server、Kotlin 协议编解码与 Compose UI 骨架都已接线，
`UpstreamSchemaTest` 离线比对 Kotlin 协议类型与上游 schema（字段名、必填方向、枚举 wire 值、
方法集合），`scripts/device-smoke-test.sh` 在真机上验证工具链安装、JNI 启动、
账户/配置/模型/会话读取、`command/exec`、apply_patch、shell 消息流与重启恢复（不发模型请求）。
下面的顺序是「先上真机验证，最后是功能与可选的架构扩展」。

## 1. 真机与真实账户验证（P0，需要账户 + 网络）

离线 smoke 刻意不发模型请求，以下必须用真实账户跑一遍：

- [ ] 登录后的完整 turn：模型请求、流式 delta、工具调用、审批应答、apply_patch、
      `/compact`、会话恢复。
- [ ] `HookStarted/HookCompleted`、`FileChange` 审批、`currentTime/read` 等只有真实服务端
      才会发的路径。
- [ ] 审批 UX 新路径：输入中延后（`ChatWidget.ApprovalTypingIdleDelayMs`）、跨线程审批提示与
      切换、自动审查聚合与「允许一次」覆盖——目前只有 JVM 测试，真机与真实服务端未验证。
- [ ] 登录状态：设备码登录 / API key / 取消 / 过期 / 登出，凭据是否只落在 `files/home/.codex/`。
- [ ] 长会话的流式性能与内存。Kotlin 侧已改增量路径（markdown 只重解析 tail block、
      diff 只解析追加段），但这批改动只在 JVM 测试里验证过，仍需真机 trace 确认。
- [ ] 进程被系统回收后的恢复、以及 `transportLagged` 之后的重新同步。

## 2. UI 功能缺口

### 2.2 多 agent 与会话编排

- [ ] analytics 仪表盘：上游 `tui/src/analytics/` 走 `codex_backend_client::AnalyticsSession`
      的 HTTP 报表（按模型/功能/任务/插件/技能、日期范围），本客户端没有后端 HTTP 通道，
      账户页只有 `account/usage/read` 的每日用量。
- [ ] 自动 recap：`/recap` 手动可用；上游空闲 30 分钟、至少 3 个已完成 turn、距上次 recap
      至少 2 个 turn 的自动调度（`local_settings.tui.auto_recap`）未做。
- [ ] `app/history_ui.kt` 的独立浏览页仍未与 transcript 合并：Ctrl+T 打开的是它，上游没有
      独立历史页（`thread/searchOccurrences`、`thread/timeline/list` 上游也只定义未消费）。
- [ ] dynamic tools 只托管 `codex_tui` 子集：`wait_threads` 明确失败；side 线程未像上游
      `non_delegation_tool_specs` 那样禁用 delegation 三件套。
- [ ] 首屏历史仍是一次 `thread/read(includeTurns=true)` 全量加载；没有
      `initialTurnsPage` / `turnsBackwardsCursor` 的有界首屏（顶部「加载更早」分页已可用）。

## 3. Native / 宿主侧

- [ ] **后端只有 Embedded(in-process) 一种。** LocalDaemon（UDS / `ws://127.0.0.1`）与
      Remote（`ws://host:port`）都没有实现：无 websocket 依赖、无 endpoint 发现、
      无 ws 鉴权接线、无 remote-workspace 语义、无断线重连降级。
      若要做，先确认三件事：loopback 上不开 token 是否被拒、跨 UID 的 UDS 是否可行、
      Android 16+ Linux 终端 / pKVM 与 App 的连接通道（都未验证）。
- [ ] **后端选择与状态隔离**：无 Embedded/LocalDaemon/Remote 的选择与回退，
      `SharedPreferences` 是全局一份（`app.kt:1097`）。
- [ ] **Embedded 子进程 + stdio 形态**（`libcodex_app_server.so` + JSONL 客户端）未做。
      当前设计明确走 in-process，除非要支持「服务端进程可独立存活」，否则不做。
- [ ] **流式 delta 无节流**：`bridge.rs:373-384` 逐条转发；Kotlin 侧 agent/plan 的 markdown
      delta 已按 `Motion.StreamCommitIntervalMs` 合并，reasoning 与命令输出仍逐条提交。
- [ ] **`Lagged` 只降级成错误**（`bridge.rs:377` → `transportLagged` → IOException），
      没有自动重同步/重放。
- [ ] **worker 卡死/崩溃没有看门狗**：只有事件流关闭时报错，App 侧只能提示后手动重试。
- [ ] **PTY / 交互式命令不支持**：`json_rpc_app_server_client.kt:531` 显式
      `require(!tty)`，`process/spawn|write|resize|kill` 全在未实现列表里。
- [ ] **体积与启动耗时**：`libcodex_android_jni.so` 168 MiB + `libcodex_helper.so` 18 MiB
      （已 strip），jniLibs 合计约 361 MB；没有记录到文档，也没有裁剪（`native/Cargo.toml`
      没有 `[features]`，`--no-default-features` 不生效）。
- [ ] **16 KB 页对齐没有门禁**：`native/build.sh:16-18` 只校验自己产出的两个 `.so`，
      toolchain 侧没有 `readelf` 检查（实测 bash/git/curl 都是 `0x4000`）。

## 4. 未验证 / 未知

- [ ] core 在 `danger-full-access` 下是否真的会走 fs helper / arg0 路径；无沙箱退化
      （`exec-server` 的 `process_sandbox` / `fs_sandbox`）未实测；真机 instrumentation
      不含 fs helper 与 tty。
- [ ] `network-proxy` 的 Android 分支指向 Termux 证书路径（`native_certs.rs`），
      在普通 App 上是 no-op；运行期是否被触达未验证（App 侧靠注入
      `SSL_CERT_FILE` / `CURL_CA_BUNDLE` 兜底）。
- [ ] 真机上 diff "显示更多" 分页的滚动位置与长文件 jank 未验证；无截图测试做像素对比。
- [ ] `AgentMessageItem.questions` 是否被服务端用于回传答案（决定是否需要独立的
      `request_user_input` 结果 cell）。

## 5. 不做

- 终端专有机制：vim 模态与键位重绑、crossterm 原始键事件、bracketed paste / Kitty 协议、
  终端光标与 scrollback 重排、ANSI/OSC 标记、终端标题与调色板、BEL/OSC 9、OSC-52、
  sixel 内联图片、pager overlay、PTY 终端网格渲染、daemon 菜单、IDE context IPC、
  `$EDITOR` → PTY。
- 设备端编译工具链：clang/rustc/cmake/ninja/perl；JDK 与 Android 构建工具（aapt2/d8/
  apksigner/Gradle，glibc 程序，bionic 上跑不起来）。见 [toolchain.md](toolchain.md)。
- node/npm（由 bun 取代）、wget（由 curl 取代）。

## 附录 A：上游参考位置

| 主题 | 位置（`codex/codex-rs/`） |
| --- | --- |
| 客户端门面与后端枚举 | `app-server-client/src/lib.rs`（`AppServerClient` / `AppServerTarget`） |
| in-process 事件与背压 | `app-server/src/in_process.rs` |
| 后端选择 / 探测 / 回退 | `tui/src/lib.rs`（`can_reuse_implicit_local_daemon` 等） |
| ws 鉴权参数 | `app-server-transport/src/transport/auth.rs` |
| 协议 schema（字段级对照的输入） | `app-server-protocol/schema/json/`、`schema/precomputed/` |
| markdown 渲染 | `tui/src/markdown_render.rs` + `markdown_render/` |
| 语法高亮 | `tui/src/render/highlight.rs` |
| diff 渲染 | `tui/src/diff_render.rs` + `diff_model.rs` |
| slash 命令全集 | `tui/src/slash_command.rs` |
| 状态卡 / 费率 | `tui/src/status/` |
| hooks 通知负载 | `app-server-protocol/src/protocol/v2/hook.rs` |

上游事实：`ServerNotification` 共 84 条，其中 `rawResponse*` 两条上游 TUI 自己也忽略；
`app-server-protocol` 的 `schema/` 是 `UpstreamSchemaTest` 的输入；它离线比对 Kotlin 类型与上游 schema。

## 附录 B：有意分歧（不是缺口）

- collab / sub-agent 卡片进 transcript（本客户端用独立 Agents 页 + 页头导航，不把卡片插进
  transcript）。
- dynamic / function-call 工具卡片（上游在 transcript 里忽略通用 `FunctionCallOutput`）。
- 只读的细粒度审批开关、线程附件托盘、`project/*` 与 `threadSection/*` 界面：
  上游 TUI 无对应物（有的是协议定义）。
- OAuth 浏览器登录的回调由 app-server 的 localhost 回调服务器完成（`login/src/server.rs`），
  浏览器与内嵌服务同机，Android 端因此不注册自定义 scheme、不需要 `onNewIntent`。
- 凭据不额外套 Keystore/EncryptedSharedPreferences：`auth.json` 由内嵌的 Rust 服务直接读写
  （`login/src/auth/storage.rs`，unix 下 0600），加密文件它读不了；落盘位置在应用私有目录
  `files/home/.codex/`，依赖 Android 沙箱而非密钥库。
