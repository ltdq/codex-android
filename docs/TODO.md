# TODO

当前待办、已知缺口与未验证项。已完成的部分不在本文件里，见 [README.md](../README.md)、
[docs/toolchain.md](toolchain.md) 与 [native/README.md](../native/README.md)。

约定：只记「没做的、做错的、没验证的」；条目里的路径相对仓库根，行号为撰写时位置；
引用上游一律用 `codex/codex-rs/...` 路径。完成一条就删一条。

## 0. 现状基线

工具链、JNI in-process app-server、Kotlin 协议编解码与 Compose UI 骨架都已接线，
`scripts/device-smoke-test.sh` 在真机上验证工具链安装、JNI 启动、账户/配置/模型/会话读取、
`command/exec`、apply_patch、shell 消息流与重启恢复（不发模型请求）。下面的顺序是
「先让已经接好的入口真正可用，再补功能，最后才是可选的架构扩展」。

## 1. 让已接线的入口真正工作（P0）

- [ ] **59 / 166 个客户端方法没有实现。** `JsonRpcAppServerClient` 只 override 了 107 个，
      其余落到 `protocol/app_server_client.kt:562` 的 `unsupported()`，调用只拿到
      `Result.failure`。缺的族：`thread/timeline`、attachments、realtime（voice/audio）、
      remote control、plugin shares、environments、user verification、external-agent config
      import、fuzzy search、hooks 读取、background terminals、`process/*`（含 PTY）、feedback、
      `readServerDiagnostics`、windows sandbox、bedrock。
      其中 54 个在 UI 里已有调用点（hooks 页、`/status` 诊断页、附件托盘、realtime 页……），
      真机上这些入口只会弹错误。`ClientRequestRegistryTest` 只校验「注册表 ↔ 接口」，
      测不出实现类缺方法——需要一条校验绑定完整性的测试。
- [ ] **`/diff` 是空操作**（`app.kt:656`），未提交/未跟踪改动不可达；客户端也没有对应的
      git diff 方法。
- [ ] **FileChange 审批卡拿不到 patch。** 解码用的是上游不存在的字段
      （`json_rpc_app_server_client.kt:639` 的 `WireCodec.changes`、`approvals.kt:42`），
      真服务端不发送 → 审批卡 diff 为空（上游为 `threadId, turnId, itemId, startedAtMs,
      reason?, grantRoot?`）。
- [ ] **hooks 通知没有生产者。** `HookStarted/HookCompleted` 有枚举和 handler，但
      `json_rpc_app_server_client.kt` 里没有任何 `Hook` 解码；Kotlin 形状
      `(threadId, hookId, name, event)` 也与上游 `{threadId, turnId?, run: HookRunSummary}`
      不符（`protocol/protocol/v2/misc.kt:784-798`）。
- [ ] **hooks 开关写死键名**：`bottom_pane/hooks_browser_view.kt:168` 的
      `keyPath = "hooks.${'$'}{hook.id}.enabled"` 是字面量（`${'$'}` 把变量转义掉了），
      上游键名是 `hooks.state`。
- [ ] **不认识的 slash 命令当普通消息发给模型**（`app.kt:194-212`）：命令目录只有
      13 条建议 / 17 条识别，其余整串文本进模型。

## 2. 协议保真（P0）

- [ ] **补一条对照上游 schema 的字段级测试**：输入 `codex/codex-rs/app-server-protocol/schema/json/`
      （或 `schema/precomputed/app-server-exports-experimental.json.zst`），断言
      Kotlin 类型字段名 ⊆ 上游字段名（允许丢字段，禁止发明）、必填/可选一致、枚举 wire 值一致，
      并断言 `ClientRequestMethod` / `ServerNotificationMethod` / `ServerRequestMethod`
      的 wire 集合与上游完全相等。现有覆盖率测试都是自指的，这类问题一条都测不出。
- [ ] **已确认的 wire 漂移**（改完上一条测试后应全部变红）：

  | Kotlin | 上游 | 后果 |
  | --- | --- | --- |
  | `HookStarted/HookCompletedNotification(threadId, hookId, name, event)` | `{threadId, turnId?, run}` | `hookId` 服务端不发，整条丢 |
  | `ThreadMemoryMode { Disabled, Read, ReadWrite }` | `{ Enabled, Disabled }` | `"enabled"` 解不出正确模式（回退 `Disabled`）；写路径靠 `require` 绕开 |
  | `CommandExecutionApprovalDecision` 四值枚举 | 带负载联合（execpolicy / network-policy 修正） | 「同意并记住规则」不可达；`proposedNetworkPolicyAmendments` 无读取者 |
  | `FileChangeApprovalParams.changes` | 上游无此字段 | 审批卡 diff 为空 |
  | `RateLimits(primary, secondary, credits: Int?)`、`RateLimitWindow(label, …)` | `RateLimitSnapshot` + `{usedPercent, windowDurationMins, resetsAt}`、credits 是对象 | `label` 是自造；丢 `limitId` 分桶、`accountId`、reset credits |
  | `AccountInfo(email, planType, organization, loggedIn)` | `Account` tagged union（apiKey / chatgpt / amazonBedrock） | `organization` 上游不存在；丢 `requiresOpenaiAuth` |
  | `Thread` 12 字段 | 29+ 字段（`projectId`/`agentNickname`/`agentRole`/`parentThreadId`/`gitInfo`/`source`…） | 多 agent、projects 页缺数据来源；`archived`/`gitBranch` 是自造 |
  | `ThreadTokenUsage` 扁平（`total` 已能解码） | `{ total, last, modelContextWindow }` | 丢 `last` 与 `cacheWriteInputTokens` |
  | `ModelPreset` 无 `serviceTiers` / `defaultServiceTier` / `hidden` / `inputModalities` | 上游有 | service tier 选择不可达 |
  | `InitializeResponse`、`McpResourceReadResponse`、`WorkspaceMessage` 等 | 同名不同物 | 握手响应解不出来 |
- [ ] **通知注册表漂移**：`ServerNotificationMethod` 79 条 vs 上游 84 条，缺
      `mcpServer/event/stream/notification`、`model/safetyBuffering/updated`、
      `thread/realtime/item/transcript/delta` 的登记（这三个 `AppServerEvent` 变体已存在）；
      `rawResponse*` 两条上游自己忽略，保留忽略即可；`server_messages.kt` 注释里的数字要改。
- [ ] **接口签名比上游窄**：`listThreads` 缺 `cursor/limit/sortKey/sortDirection/…`（无法分页）、
      `listThreadItems` 缺 `turnId/sortDirection`、`listThreadTurns` 缺 `itemsView`、
      `readThread` 缺 `includeTurns`、`startThread` 缺 `config/sandbox/permissions/…`
      （`protocol/app_server_client.kt:578-595`）。
- [ ] `McpElicitationRequest.Url` 模式无人读取（命中 0），URL 型 elicitation 会被当表单渲染。

## 3. 真机与真实账户验证（P0，需要账户 + 网络）

离线 smoke 刻意不发模型请求，以下必须用真实账户跑一遍：

- [ ] 登录后的完整 turn：模型请求、流式 delta、工具调用、审批应答、apply_patch、
      `/compact`、会话恢复。
- [ ] `HookStarted/HookCompleted`、`FileChange` 审批、`currentTime/read` 等只有真实服务端
      才会发的路径。
- [ ] 登录状态：设备码登录 / API key / 取消 / 过期 / 登出，凭据是否只落在 `files/home/.codex/`。
- [ ] 长会话的流式性能与内存（当前每个 delta 都全量重解析 markdown）。
- [ ] 进程被系统回收后的恢复、以及 `transportLagged` 之后的重新同步。

## 4. UI 功能缺口

### 4.1 渲染

- [ ] markdown 表格、数学、删除线、H4-H6、setext、分隔线、缩进代码块、嵌套列表、硬换行
      （`markdown_render.kt` 442 行手写扫描器，无 table/math 代码）。
- [ ] 围栏解析只认 `^```\s*(\S*)\s*$`（`markdown_render.kt:287`）：不支持
      ```` ```rust title=x ````、`~~~`、4 反引号。
- [ ] 无语法高亮（代码围栏、exec 命令、MCP 参数、diff 全是等宽文本）。
- [ ] 链接机制缺失：`[label](url)` 只渲染 label，URL 被丢弃；无 `UriHandler`/本地文件/
      `:line:col`/codex-file-citation 处理（`markdown_render.kt:363-366`）。
- [ ] 流式渲染无提交边界：`Motion.StreamCommitIntervalMs`（`motion.kt:74`）零引用，
      delta 逐个重 upsert 并全量重解析（`chatwidget.kt:635-647`）；未闭合围栏吞到 EOF。
- [ ] exec 输出只截头（`history_cell/exec.kt:256-268` 只 `take(maxLines)`），
      上游是 head + `… +N lines` + tail，并带 bash 高亮与 "Explored" 分组折叠。
- [ ] web search 的 `action` 未建模（`ThreadItem.kt:132-138`），无从显示 `Opened <url>`。
- [ ] safety buffering 通知是显式 no-op（`chatwidget.kt:814`）。
- [ ] 诊断上限 20 条且静默丢弃（`session_state.kt:230,234`），无按 model slug 去重。

### 4.2 diff

- [ ] **header 判定顺序错误**：`diff_model.kt:94` 的 `+++`/`---`/FileHeader 判定排在
      `:98-106` 的 `+`/`-` 之前 → 删除内容里以 `--` 开头、或新增内容里以 `++` 开头的行
      被误判成文件头，gutter 与 `+N/−M` 计数一起错。`DiffModelTest` 没覆盖这个歧义。
- [ ] 缺 rename（`old → new`）、`⋮` hunk 分隔（现在直接渲染 `diff --git` / `@@`）、
      路径按 cwd / git root / home 相对化、tab 展开、`\ No newline at end of file` 处理。
- [ ] `/diff` 空操作（见 §1）。

### 4.3 审批

- [ ] execpolicy / network-policy 修正决策不可表达（protocol 层就没有负载）。
- [ ] approvals reviewer 选择缺失：`approvalsReviewer` 只在协议与 client 里，
      `AskForApproval` 无 `AutoReview`（`protocol/protocol/v2/config_types.kt:9-14`），无 UI。
- [ ] 「输入中延后」门控缺失：上游在用户打字后延迟 1 秒再弹审批（`bottom_pane/mod.rs`）。
- [ ] 跨线程待审批提示、可操作内联 banner 缺失。
- [ ] guardian review 的 `+N more` 聚合缺失。

### 4.4 多 agent 与会话编排

- [ ] `AgentPickerSheet` 未挂载（`app/agent_picker.kt:182` 只有定义）；
      `AgentRosterEntry.statusLabel()/tone()` 在 `app/agents_overview.kt:136` 与
      `status/card.kt:1293,1306` 各一份，哪份生效未验证。
- [ ] agent 导航（上/下一个、按 spawn 顺序）、`/subagents` 状态流、总览的 open/stop/
      archive/rename 操作、总览 usage 列填充。
- [ ] 事件缓冲与 replay、按线程路由缺失：非当前线程的事件仍被丢弃（`chatwidget.kt`），
      `EventBuffer`/`replayFilter`/`threadRouting` 命中 0。
- [ ] worktree 相关整块缺失（上游约 1142 行；`chatwidget/worktree_picker.kt` 现在是目录选择器，
      与文件名不符）。
- [ ] 侧会话（`/side`、`/btw`）、recap、transcript 导出、线程标题自动生成、启动提示、
      `/cd` 与 cwd 语义、goal 的 edit/pause/resume 与 token 预算、dynamic tools 托管、
      branch/PR 元数据、analytics 仪表盘——均命中 0。
- [ ] 服务端历史分页只进独立浏览页（`app/history_ui.kt:549` 是唯一调用点），
      打开的 transcript 不加载更早的页；resume picker 只有一行 `thread.preview`
      （`resume_picker.kt:153`）。
- [ ] `ConnectionState` 已有订阅者，但 backend banner、断线重连 UI、服务端版本提示仍缺。
- [ ] turn 级活动指示器（计时器、折行的工具细节、hook 状态槽位）、turn 完成分隔行、
      标题生成中指示、`InProgress` item 收尾、misalignment 策略缺失。

### 4.5 登录与 onboarding

- [ ] 登录本身已可用（`status/account.kt:148-221`：设备码 + API key + 取消 + 错误态），
      但缺首次启动引导/欢迎屏，只在提交时按 `loggedIn` 拦一次（`app.kt:205`）。
- [ ] OAuth 回调通道：manifest 无 `<data android:scheme>`、Activity 无 `onNewIntent`
      （当前走设备码，影响有限）。
- [ ] 目录信任提示缺失：`trustedProjects` 只解析（`protocol/protocol/v2/config.kt:211,256`），
      无写入方、无提示 UI。
- [ ] Bedrock 只有区域选择 + setup，缺 credential method / AWS profile / 掩码 key；
      `AmazonBedrockAccessKeys` 建了模型从不发送。
- [ ] OSS provider 选择缺失。
- [ ] API key 存在 `auth.json`，未用 Keystore/EncryptedSharedPreferences（未深入验证）。

### 4.6 平台能力（当前在 `app/src/main` 里命中 0）

- [ ] 剪贴板：复制消息、复制代码块、状态卡复制、`/copy`（`ClipboardManager` 命中 0）。
- [ ] 系统通知：`POST_NOTIFICATIONS` 未申请，无 channel（上游有按类型白名单）。
- [ ] 打开链接（同 §4.1）。
- [ ] 图片通路：picker 是 `OpenDocument("*/*")` 且只插 `@path` 文本；composer 只构造
      `UserInput.Text`（`rendering.kt:463`），`onMentionPicked = {}`，`TextElement` 从不构造；
      缺 `LocalImage`、路径粘贴识别、`[Image #N]` 占位、32 MiB 上限。
- [ ] 键盘层（`onKeyEvent` 命中 0）、输入历史与反向搜索。
- [ ] 外部编辑器（`ACTION_EDIT`）、降低动效（`areAnimatorsEnabled`/`ANIMATOR_DURATION_SCALE`）、
      主题界面（32 个内置主题 + `.tmTheme` 不可达）、版本/更新感知（`BuildConfig` 未引用）。
- [ ] feedback 披露：不设 `includeLogs`、丢弃 `reportId`、分类是自由文本。

### 4.7 命令与输入

- [ ] 命令目录 13/17 条 vs 上游约 60 条；`/plan` 无 dispatch 分支、无
      `SetCollaborationMode` 事件、`catalog.collaborationModes` 从不读取 → 计划/目标模式
      无法从 UI 进入。
- [ ] 命令门控缺失：别名抑制、`available_during_task`、feature flag 显隐、精确匹配优先排序。
- [ ] `@` 提及只是读一次 cwd 目录（`rendering.kt:166-171`），不是上游的
      mentions_v2（Plugin/Skill/Task/File/Directory 候选 + 评分 + 搜索模式）；
      `$` skill 弹窗、task mentions、connector mentions 缺失。
- [ ] `!command` 本地 shell 逃逸：`AppEvent.RunShellCommand` 有 handler，composer 不做 `!` 检测。
- [ ] 异步内联提问只能看不能答（`history_cell/messages.kt` 只读列表）；
      `request_user_input` 的「其他」答案会覆盖已选选项。
- [ ] 提交被拒时草稿被清空（上游保留草稿）；composer 无法被禁用（父级拥有输入权 /
      子 agent 线程时应禁用并换占位符）。

### 4.8 管理面

- [ ] hooks 浏览器深度不足：无 trust 操作、无 review-needed 状态、无按事件分组与计数、
      无 handler 细节；`HookEntry` 无 trust 字段。
- [ ] MCP：`authStatus` 未建模（「去登录」按钮无法判断）、startup 进度与警告缺失、
      结果里的 image/audio/resource 投影缺失。
- [ ] 插件目录：无按 marketplace 的 tab、无安装后鉴权流；`PluginEntry` 无 `enabled`。
- [ ] app-link 的 install URL / 确认屏 / 连接器鉴权流缺失。
- [ ] memories 模式无 UI（`SetThreadMemoryMode` 无生产者）；`ThreadMemoryMode` 值集还要按上游改。
- [ ] service tier / fast 模式无 UI；`/status` 打开的是 `server/diagnostics`，
      而会话状态读出缺失。

### 4.9 状态与用量

- [ ] 状态卡字段窄于上游：缺权限摘要、`AGENTS.md` 摘要、model provider、thread name、
      协作模式行、费率条与过期警告、credits/spend-control、按线程 credits/USD、
      各类 token 拆分（部分字段 protocol 层就没有，见 §2）。
- [ ] 费率恢复逻辑缺失（高用量换模型提示、用量警告、恢复期暂挂）；`DiagnosticCode`
      12 个码里没有费率/用量限制码。
- [ ] reset credits / credits nudge 不可达（事件无发射点，credit 列表也拿不到）。
- [ ] 客户端设置项只有 3 个 SharedPreferences 键，无动效/主题/通知设置。

### 4.10 死代码与只写不读（顺手清理）

- [ ] 零引用：`theme/running_outline.kt`（119 行）、`Motion.LoopMs`、
      `Motion.StreamCommitIntervalMs`、`FuzzySearchSession`、`ModelSheet`/`EffortSheet`。
- [ ] 只写不读：`catalog.elicitationCount`、`SessionDiagnostic.willRetry`、
      `workspaceMessages`、`configRequirements`、`TextElement`、`McpElicitationRequest.Url`。
- [ ] 只有 handler 没有生产者的 `AppEvent`：`UnsubscribeThread`、`UpdateThreadMetadata`、
      `InjectThreadItems`、`ApproveGuardianDeniedAction`、`SetThreadMemoryMode`、`SteerTurn`、
      `UpdateTurnSettings`、`SetPermissionProfile`、`UpdateThreadSettings`、`AddAttachment`。
      （`DismissApproval` 不在内：审批框有意不可关闭，它不是缺陷。）

## 5. Native / 宿主侧

- [ ] **后端只有 Embedded(in-process) 一种。** LocalDaemon（UDS / `ws://127.0.0.1`）与
      Remote（`ws://host:port`）都没有实现：无 websocket 依赖、无 endpoint 发现、
      无 ws 鉴权接线、无 remote-workspace 语义、无断线重连降级。
      若要做，先确认三件事：loopback 上不开 token 是否被拒、跨 UID 的 UDS 是否可行、
      Android 16+ Linux 终端 / pKVM 与 App 的连接通道（都未验证）。
- [ ] **后端选择与状态隔离**：无 Embedded/LocalDaemon/Remote 的选择与回退，
      `SharedPreferences` 是全局一份（`app.kt:1097`）。
- [ ] **Embedded 子进程 + stdio 形态**（`libcodex_app_server.so` + JSONL 客户端）未做。
      当前设计明确走 in-process，除非要支持「服务端进程可独立存活」，否则不做。
- [ ] **流式 delta 无节流**：`bridge.rs:373-384` 逐条转发，Kotlin 侧也没有窗口合并。
- [ ] **`Lagged` 只降级成错误**（`bridge.rs:377` → `transportLagged` → IOException），
      没有自动重同步/重放。
- [ ] **worker 卡死/崩溃没有看门狗**：只有事件流关闭时报错，App 侧只能提示后手动重试。
- [ ] **PTY / 交互式命令不支持**：`json_rpc_app_server_client.kt:531` 显式
      `require(!tty)`，`process/spawn|write|resize|kill` 全在未实现列表里。
- [ ] **markdown 交给 Rust**（`libcodex_fmt.so`，pulldown-cmark + syntect）未做；
      与 §4.1 的 Kotlin 实现二选一。若做，要先解决 oniguruma/two-face 依赖
      （`toolchain/env.sh` 里的 `ONIGURUMA_VER` 目前无人引用，`jq.sh` 会删掉头文件）
      与高亮上限（上游 >512 KiB / >10 000 行跳过）。
- [ ] **体积与启动耗时**：`libcodex_android_jni.so` 168 MiB + `libcodex_helper.so` 18 MiB
      （已 strip），jniLibs 合计约 361 MB；没有记录到文档，也没有裁剪（`native/Cargo.toml`
      没有 `[features]`，`--no-default-features` 不生效）。
- [ ] **16 KB 页对齐没有门禁**：`native/build.sh:16-18` 只校验自己产出的两个 `.so`，
      toolchain 侧没有 `readelf` 检查（实测 bash/git/curl 都是 `0x4000`）。

## 6. 未验证 / 未知

- [ ] `AgentRosterEntry.statusLabel()/tone()` 两份定义里哪一份生效（取决于 import 优先级，
      未编译验证）。
- [ ] JNI 字符串编码：`native/src/jni_api.rs` 走 jni crate 的 Modified UTF-8，
      emoji / 非 BMP 字符是否安全未验证。
- [ ] core 在 `danger-full-access` 下是否真的会走 fs helper / arg0 路径；无沙箱退化
      （`exec-server` 的 `process_sandbox` / `fs_sandbox`）未实测；真机 instrumentation
      不含 fs helper 与 tty。
- [ ] `network-proxy` 的 Android 分支指向 Termux 证书路径（`native_certs.rs`），
      在普通 App 上是 no-op；运行期是否被触达未验证（App 侧靠注入
      `SSL_CERT_FILE` / `CURL_CA_BUNDLE` 兜底）。
- [ ] `diff_render.kt` 是否仍有 400 行截断（`take(400)` 无命中，未逐行读完）。
- [ ] `AgentMessageItem.questions` 是否被服务端用于回传答案（决定是否需要独立的
      `request_user_input` 结果 cell）。
- [ ] 协议漂移的统计口径（同名类型、发明字段数）未按当前代码重算。
- [ ] 真机结果没有留档：`artifacts/` 目前只有宿主机 `native-build.log`，
      没有 `device-smoke.txt`（脚本会写，但需要连着设备跑一次）。

## 7. 不做

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
`app-server-protocol` 的 `schema/` 可直接作为 §2 那条测试的输入。

## 附录 B：有意分歧（不是缺口）

- collab / sub-agent 卡片进 transcript（上游走 `/subagents` + agent 导航）。
- dynamic / function-call 工具卡片（上游在 transcript 里忽略通用 `FunctionCallOutput`）。
- 只读的细粒度审批开关、线程附件托盘、`project/*` 与 `threadSection/*` 界面、
  `ModelSheet` / `EffortSheet`：上游 TUI 无对应物（有的是协议定义）。
- 迁移后没有 mock 层：所有数据来自真实 app-server。
