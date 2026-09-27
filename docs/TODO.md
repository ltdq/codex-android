# TODO

与上游 TUI 逐模块对比后的缺口（基线：`codex/` submodule `41f9084b3081`；范围
`tui/src/bottom_pane/`、`tui/src/chatwidget/` + `history_cell/` + `streaming/`、app 级模块与
slash 命令），
以及对比中发现的协议解析/绑定缺陷。已完成能力见 [README.md](../README.md) 与
[docs/toolchain.md](toolchain.md)；终端专有、平台与工具链约束项见第 6 节。

约定：路径相对仓库根；引用上游一律用 `codex/codex-rs/...`；改协议同步 wire 类型、
`json_rpc_app_server_client.kt` 绑定与 JVM 测试三处；每条只写问题与证据，不写修复方向；
完成后删除。

## 0. 协议解析与绑定缺陷

`wire_codec.kt` 的 `item()` 变体与字段解析已逐条对齐上游（19 个变体、字段名与 camelCase
拼写、`UserInput.Image` 的 `{url} | {fileId}` 联合）；`app/src/test/.../wire_codec_tests.kt`
覆盖这些解析点。审批 `_meta`（persist 双形态、`tool_params_display`、`tool_suggestion`）、
elicitation 五变体、permission profile、workspace messages、`rateLimitUpsell`/backend banner、
MCP 元数据、web search results 与 hook 输出均已接线并有 JVM 测试覆盖。

- [ ] **userVerification 端到端仍受上游限制**：Kotlin 侧就绪层已齐（`openai/userVerification`
      变体解析与路由、verify/cancel 卡、Android Keystore P-256/SHA-256 提供方、proof 经
      elicitation content 回传），但真实 challenge 不会出现——上游 `user-verification` crate
      只有 macOS provider，app-server 激活门也只放行 codex-tui/Codex Desktop 且要求
      `device_supported()`。
备注：`MemoryCitationEntry` 的全部字段与 `HookPromptFragment.hookRunId` 在上游为必填，Kotlin
侧给了默认值——沿用本仓既有的宽松解析风格，不是 wire 错误。

## 1. 交互能力

- [ ] **回退重编辑（backtrack）**：上游 Esc-Esc 打开 transcript、选中用户消息后回退并把它
      重新填进输入框（`tui/src/app_backtrack.rs`）。Kotlin 只有只读 Ctrl+T
      （`app/history_ui.kt`）与 `/revert <itemId>`；用户消息 cell 没有可点击的回退入口。
- [ ] **steer（运行中插话）**：上游运行中提交是 pending steer 并有预览/编辑
      （`tui/src/bottom_pane/pending_input_preview.rs`）；Kotlin 运行中一律走
      `thread/queue`（`chatwidget.kt` 的 `submitInput`），`steerTurn` 协议已绑定但无调用点。
- [ ] **中断时恢复输入**：上游把 pending steer / 队列草稿并回 composer
      （`tui/src/chatwidget/input_restore.rs`）；Kotlin 中断后只重读服务端队列，草稿不回流。
- [ ] **审批决定回执**：上游在 transcript 里记「You approved … / denied / timed out」
      （`tui/src/history_cell/approvals.rs`）；Kotlin 只关弹窗并改状态，没有回执 cell。
- [ ] **Plan 实施提示**：上游问「Implement this plan? / 清空上下文实施」
      （`tui/src/chatwidget/plan_implementation.rs`）；Kotlin 有 plan 模式与时间线，无此一步。
- [ ] **安全缓冲重试**：上游给「换更快的模型重试 / 继续等待 / 了解更多」
      （`tui/src/chatwidget/safety_buffering.rs`）；Kotlin 只报一条诊断。
- [ ] **实时语音（realtime）**：目前是壳，无 WebRTC/录音/字幕，`Realtime*` 通知全部丢弃
      （`chatwidget/realtime.kt`）；上游 `tui/src/chatwidget/realtime.rs`、
      `realtime_split_flap.rs`、`realtime_settings.rs`。含麦克风/扬声器电平条、静音提示、
      连接阶段、voice 选择与设置页。
- [ ] **goal 持久状态指示**：上游 footer 常驻 Active/Paused/Blocked/UsageLimited/
      BudgetLimited/Complete 与用量，恢复会话时提示「Resume paused goal?」
      （`tui/src/chatwidget/goal_status.rs`、`goal_menu.rs`）；Kotlin 只在 GoalSheet 打开时可见。
- [ ] **通知类型与优先级**：上游有 PlanModePrompt、user-input 请求等类型并按优先级聚合
      （`tui/src/chatwidget/notifications.rs`）；Kotlin 只有 TurnComplete + ApprovalRequested。

## 2. Transcript 渲染

- [ ] **Computer/CUA 活动聚合**：相邻 `cua_repl`/node-repl 调用合并为
      「Using/Used computer · N actions」（上游 `tui/src/history_cell/computer_activity.rs`）；
      Kotlin 一次调用一张通用 MCP 卡片，无 `cua` 处理。
- [ ] **Mermaid 图**：完成的 mermaid fence 渲染成图，失败回退代码块
      （上游 `tui/src/markdown_render/mermaid.rs`）；Kotlin 当普通代码块。
- [ ] **inline visualization**：`::codex-inline-vis{…}` 指令（上游
      `tui/src/inline_visualization.rs`）；Kotlin 只处理 `:codex-file-citation{…}`。
- [ ] **数学排版**：上游对受支持的 TeX 子集做有界排版（`tui/src/markdown_render/math/`）；
      Kotlin 显示为 mono 斜体源码。
- [ ] **后台线程的 hook 结果回放**：`HookCompleted` 只落到当时的会话态，后台子线程的
      hook 运行不会回放进它的 transcript；hook 标识靠 `hooks/list` 元数据按
      (event, displayOrder, sourcePath) join，会话中途改 hook 配置会静默错配。
- [ ] **compaction 进度**：上游有实时标题与「Context compacted · 3s」
      （`tui/src/chatwidget/compaction.rs`）；Kotlin 只有静态通知。
- [ ] **unified exec 等待/交互 cell**：上游区分「Waited for background terminal」与
      「Interacted with background terminal」（`tui/src/history_cell/exec.rs`）；Kotlin 把
      stdin 混进命令输出，等待不可见。
- [ ] **嵌套 review turn**：上游在 transcript/backtrack 里隐藏 review 内部 turn
      （`tui/src/app_backtrack.rs`）；Kotlin 每个 finished turn 都插分隔。
- [ ] **启动警告 cell**：上游在 transcript 顶部提示「N startup issues」
      （`tui/src/history_cell/startup_warnings.rs`）；Kotlin 只在 `/mcp` 页可见。
- [ ] **turn 分隔符的 runtime metrics**（工具/推理调用数、TTFT/TBT，
      `tui/src/history_cell/separators.rs`）：wire 没有该数据。

## 3. 会话与工作区

- [ ] **resume picker**：上游可按 Created/Updated/Recency/Section 排序、按 All/Cwd/来源过滤，
      展开预览是完整 transcript，删除前有二次确认，打开归档会话时给「解档并恢复」
      （`tui/src/resume_picker/`、`tui/src/unarchive_prompt.rs`）；Kotlin 只按最近活动排序，
      过滤只有搜索词与归档/活跃两项，预览只取用户消息文本与助手文本。
- [ ] **worktree**：上游有 owner/thread 绑定、remove/copy 与新建会话/fork 的「在哪运行」选择
      （`tui/src/worktree_browser.rs`、`chatwidget/worktree_picker.rs`）；Kotlin
      `app/worktrees.kt` 只有 `git worktree list/add`。
- [ ] **新工作区缺少信任确认**：`WorkspacePickerScreen` 的 `onPicked` 直接 `NewThread(path)`，
      `ProjectsScreen.CreateProject`/`UpdateProject` 改路径时也不确认；信任门槛只在
      `createThread`/`openThread` 前出现（`TrustProjectSheet`）。
- [ ] **`/cd` 语义**：上游在当前会话内换目录（`tui/src/app/working_directory.rs`）；
      Kotlin 会新开空会话（`app.kt`），`/pwd` 也会打开目录选择器。
- [ ] **resume/fork 的 cwd 提示**：上游问「用会话 cwd 还是当前 cwd」并记住选择
      （`tui/src/cwd_prompt.rs`、`session_resume.rs`）；Kotlin 固定用服务端记录值。
- [ ] **additional dirs**：上游可增删可写根（`tui/src/additional_dirs.rs`）；
      Kotlin 设置页只读展示。

## 4. 设置、引导与更新

- [ ] **statusline 配置**：`/statusline` 选择/排序/实时预览页脚条目
      （上游 `tui/src/bottom_pane/status_line_setup.rs`、`status_surface_preview.rs`）；
      Kotlin 只有固定状态卡。
- [ ] **startup hooks 信任审查**：启动时对新增/变更的 hooks 做阻塞式信任确认
      （上游 `tui/src/startup_hooks_review.rs`）；Kotlin 只在用户打开 `/hooks` 时逐条信任，
      没有「全部信任」。
- [ ] **主题**：语法高亮主题列表、实时预览与自定义主题（上游 `tui/src/theme_picker.rs`）；
      Kotlin 只能选 System/Light/Dark。
- [ ] **experimental 开关**：失败后保留意图可重试、按 stage 门控、发现失败提示
      （上游 `tui/src/bottom_pane/experimental_features_view.rs`）；Compose 已在写入失败时显示
      snackbar 并回读列表，但没有 stage 门控、失败后的重试意图或发现失败提示。
- [ ] **skills 展示与搜索**：上游用 `interface.displayName/shortDescription` 并支持模糊过滤
      （`tui/src/skills_helpers.rs`）；Kotlin 用 raw `name`/`description`，没有搜索框。
- [ ] **`@` 提及**：上游含 skills 与已授权 connector（`app://`）、搜索模式切换、footer 提示与
      高亮（`tui/src/task_mentions.rs`、`bottom_pane/mentions_v2/`）；Kotlin 只有
      plugins/tasks/files/directories。
- [ ] **模型/effort 默认值**：上游在模型弹层里可「设为默认」、Plan 模式单独覆盖、auto-model
      分组与 Ultra 并发警告（`tui/src/chatwidget/model_popups.rs`）；Compose 侧没有对应入口。
- [ ] **review 分支/commit 选择器**：上游列出真实分支与 commit
      （`chatwidget/review_popups.rs`）；Kotlin 要求手输。
- [ ] **feedback**：上游区分内外部受众的披露、上传后给 issue 链接、附件选择
      （`tui/src/bottom_pane/feedback_view.rs`）；Kotlin 只有分类/理由/日志同意。
- [ ] **backend/workspace banner 通用化**：上游 `actionable_banner.rs` 支持标题/描述/CTA/关闭
      （account mismatch、用量恢复、workspace owner 提示等）；Kotlin 的
      `app/session_status.kt` 明确不解析 banner，只有硬编码横幅与连接中断横幅。
- [ ] **model migration 一次性提示**：上游在启动流程里提示模型迁移
      （`tui/src/model_migration.rs`）；Compose 没有对应提示。上游 welcome、
      `directory_trust.rs`/`trust_directory.rs` 和 `startup_orchestration.rs` 是终端启动时先确定
      目标项目的 CLI 流程，Compose 的对应能力由 WorkspacePicker/Projects 与
      `TrustProjectSheet` 承担，不计入缺口；添加工作区的信任时机见第 3 节。
- [ ] **slash 命令目录未与上游对齐**：`slash_command.kt` 漏了上游 `/delete`、`/experimental`、
      `/approve`（auto-review denial 的一次重试入口）、`/debug-config`、`/statusline`、`/title`、
      `/rollout`、`/ps` 等命令；已有 Session/Settings/BackgroundTerminals 页面的命令也没有对应
      入口。另有语义差异：Compose `/stop` 调用 `InterruptTurn`，上游 `/stop`（`/clean`）是清理
      background terminals，同名不同动作。Android 扩展的 `/shell`、`/revert`、`/settings`、
      `/approvals` 在上游没有对应命令，终端专有项见第 6 节。

## 5. 暂缓与待定

- [ ] **账户分析仪表盘**：上游 `tui/src/analytics/` 直连 ChatGPT 私有 HTTP 接口
      （`backend-client` 的 analytics 路由），app-server 无对应方法；已有 `account/usage/read`
      的每日用量与 summary。
- [ ] **APK 更新提示**：上游 `tui/src/updates.rs` 面向自更新安装；Android 走应用分发，
      是否在应用内做检查/提示待定。
- [ ] **本地模型 provider（Ollama/LM Studio）**：上游 `tui/src/oss_selection.rs`；手机上
      是否有可用的本地服务端场景待定。

## 6. 终端专有、平台与工具链

以下机制在终端形态下才有直接对应物，或受 Android 平台约束尚未提供。

- [ ] **vim 模态与键位重绑**：上游 `tui/src/bottom_pane/vim_*.rs`、`keymap/`；Compose 输入层
      没有对应实现。
- [ ] **终端按键语义**：Compose 已有硬件快捷键适配（`keymap/key_event_adapter.kt`），但没有
      crossterm 原始键事件、Kitty 键盘协议或 bracketed paste 语义，也没有终端事件到 Compose
      输入层的映射。
- [ ] **光标与 scrollback 重排**：Compose 列表没有终端 scrollback（保持阅读位置、跳转等）。
- [ ] **ANSI/OSC 与终端元信息**：ANSI/OSC 标记、终端标题与调色板、BEL/OSC 9、OSC-52；
      Compose 侧没有通知、剪贴板与标题的等价物。
- [ ] **sixel 内联图片**：Compose 没有对应的内联图片渲染路径。
- [ ] **pager overlay**：上游有通用全屏分页视图；Compose 只有 Ctrl+T 只读历史页
      （`app/history_ui.kt`）。
- [ ] **PTY 终端网格渲染与 daemon 菜单**：随下面的 PTY 支持一起缺失。
- [ ] **IDE context IPC**：上游 `tui/src/ide_context.rs`；没有 IDE 侧协议。
- [ ] **`$EDITOR` → PTY**：系统编辑器 Intent 与 PTY 语义不同。
- [ ] **`/raw`、`/title`、pets**：上游 `tui/src/chatwidget/pets.rs` 等；Compose 侧没有对应的
      Android 表现形式。
- [ ] **PTY / 交互式命令**：`process/spawn|write|resize|kill`；当前
      `json_rpc_app_server_client.kt` 保留 `require(!tty)`，命令只走 `command/exec`
      的非交互流。
- [ ] **LocalDaemon / Remote 后端**：探测与回退、ws 鉴权、remote-workspace 语义、
      按后端隔离的配置（上游 `tui/src/lib.rs` 的 `AppServerTarget`）；当前固定同进程
      Embedded。
- [ ] **CLI 专有机制**：named session lookup（`named_session_lookup.rs`）、跨会话排队
      （`session_queue_commands.rs`）、`CODEX_TUI_RECORD_SESSION` 式 JSONL 录制
      （`session_log.rs`）、`/app`（上游仅 macOS/Windows，需 Android 等价物）、`/ide`、
      `/daemon`、`/keymap`、`/vim`、`/elevate-sandbox`。
- [ ] **设备端编译工具链**：clang/rustc/cmake/ninja/perl 与 JDK、Android 构建工具
      （aapt2/d8/apksigner/Gradle）受 bionic/glibc 限制。
- [ ] **缺失工具**：node/npm（现由 bun 取代）、wget（现由 curl 取代）、
      vi/less/top/watch、nc/ping/traceroute（依赖 PTY 或额外权限）。
- [ ] **登录与凭据**：系统浏览器回跳的自定义 scheme（现由 app-server localhost 回调
      替代）、`auth.json` 的 Keystore 保护（内嵌 Rust 直接读写该文件）。
- [ ] **屏幕阅读器与无障碍语义**：上游 `tui/src/screen_reader.rs`、
      `screen_reader_windows.rs` 会检测屏幕阅读器并调整输出；Compose 侧只有零散的图标
      `contentDescription`，尚未给 transcript、审批表单、状态卡和动态活动行补齐语义角色、
      状态描述与朗读顺序，也未做屏幕阅读器模式的布局降级。

## 7. 账户与登录门控

与上游 TUI 的账户门控逐点对照（`requires_openai_auth`、`has_chatgpt_account`/
`has_codex_backend_auth`、plan 与登录方式）。提交与输入区现按上游 `should_show_login_screen`
语义门控（`requiresSignIn`，`workspace_messages.kt`）；同一链路上仍不一致的门控如下。

- [ ] **`/usage` 与账户页不区分账户类型**：上游 `/usage` 由 `has_codex_backend_auth` 门控，
      否则报 "Sign in with ChatGPT to use /usage."
      （`codex/codex-rs/tui/src/chatwidget/slash_dispatch.rs:1198`），命令列表对它隐藏
      （`codex/codex-rs/tui/src/bottom_pane/slash_commands.rs:78`）。Compose 的 `usage` 命令
      无门控、直接打开账户页（`app.kt:946`），账户页在 `account == null` 时一律渲染「未登录」
      与 ChatGPT/设备码/API key 登录表单（`status/account.kt:151`、`:230`）。对
      `requires_openai_auth = false` 的 provider，上游不显示登录界面
      （`codex/codex-rs/tui/src/lib.rs:2296`），`/status` 也不输出账户行
      （`codex/codex-rs/tui/src/status/card.rs:861`）。
- [ ] **rate limit 轮询/刷新门控偏松**：上游 `should_prefetch_rate_limits() =
      requires_openai_auth && has_chatgpt_account`
      （`codex/codex-rs/tui/src/chatwidget/rate_limits.rs:420`），并连带关掉轮询间隔（`:193`）、
      `/status` 刷新（`codex/codex-rs/tui/src/chatwidget/slash_dispatch.rs:510`）与启动预取
      （`codex/codex-rs/tui/src/app/startup.rs:987`）；限流失败后的输入保持与
      `RefreshRateLimits(Recovery)` 也只对 ChatGPT 账户生效
      （`codex/codex-rs/tui/src/chatwidget/turn_runtime.rs:416`、`:462`）。Compose 轮询只看
      `account == null`（`app.kt:1111`），账户页打开/刷新只看 `signedIn`（`app.kt:1662`、
      `status/account.kt:124`），限流恢复不查账户（`app.kt:1219`、`:1544`）：API key 账户仍会
      轮询、刷新并保持输入，上游不会。
- [ ] **usage / thread usage / Agents 用量门控**：上游用量入口统一要 `has_codex_backend_auth`，
      线程用量与 Agents 概览用量还要求 plan ∈ {Business, EnterpriseCbpUsageBased,
      EnterpriseCbpAutomation}（`codex/codex-rs/tui/src/chatwidget/thread_usage.rs:358`、
      `codex/codex-rs/tui/src/app/agents_overview_usage.rs:45`）。Compose 的 `ReloadUsage`
      无门控（`app.kt:398`），会话状态页的 `readThreadUsage`（`app/session_status.kt:91`）不查
      账户与 plan；Agents 概览没有对应的用量读取（只用 `thread/tokenUsage/updated` 事件，
      `agents_overview.kt`）。
- [ ] **状态页的账户行、plan 行与 token 用量行**：上游账户行只在有账户显示值时输出（API key
      文案 "API key configured (run codex login to use ChatGPT)"，无账户时整行省略，
      `codex/codex-rs/tui/src/status/card.rs:747`、`:861`），plan 只在 ChatGPT 账户行内出现，
      `Token usage` 行对 ChatGPT 订阅者隐藏（`:881`），"ChatGPT usage" 链接只随
      `requires_openai_auth` 显示（`:354`、`:819`）。Compose 会话状态页始终输出 Plan 行
      （`account == null` → 「未登录」，`app/session_status.kt:582`、`:766`）与 token 用量行
      （`app/session_status.kt:426`、`:747`），且没有 ChatGPT usage 链接。
- [ ] **连接器（`/apps`）不按账户门控**：上游 `connectors_enabled = Feature::Apps &&
      has_chatgpt_account`（`codex/codex-rs/tui/src/chatwidget/connectors.rs:151`），门控
      `/apps` 命令、连接器缓存刷新与 `@` 提及；Compose 的 `apps` 命令与 `Surface.Apps` 无账户
      判断（`app.kt:930`、`:1672`）。
- [ ] **登录方式与 Bedrock 入口不完整**：上游按 `allowed_login_methods` 决定展示哪些登录方式
      （`codex/codex-rs/tui/src/onboarding/auth.rs:352`），Bedrock 选项还需
      `should_show_bedrock_setup_wizard` 的全部条件（Embedded + `requires_openai_auth` +
      feature 门控 + `model_provider == "openai"` + 未显式配置 provider + 允许 API 登录，
      `codex/codex-rs/tui/src/lib.rs:2306`）。Compose 账户页固定展示 ChatGPT/设备码/API key
      （`status/account.kt:230`），`configRequirements/read` 只解析审批人与权限配置、未读
      `allowedLoginMethods`（`protocol/protocol/v2/config.kt:183`），`Surface.Bedrock` 是设置页
      常驻入口（`chatwidget/settings_popups.kt:674`），不满足上述条件也可见。
- [ ] **线程标题模型不按账户选择**：上游仅当 `model_provider == "openai" &&
      has_chatgpt_account` 且模型目录含 `THREAD_TITLE_MODEL` 时用专用小模型生成标题，否则用
      当前模型（`codex/codex-rs/tui/src/app/thread_title.rs:89`）；Compose 一律用当前模型
      （`chatwidget.kt:1499`）。
- [ ] **账户切换时的失效范围不全**：上游把每条账户更新当身份边界，`update_account_state`
      （`codex/codex-rs/tui/src/chatwidget/settings.rs:193`）除 headline/backend banner 外还重置
      rate-limit 警告、reset-credit 请求、usage notice 与线程用量等账户级状态；Compose 在
      `AccountUpdated`/`AccountLoginCompleted` 只重置 headline 与 backend banner
      （`app.kt:1552`、`:1565`），`rateLimitWarnings`（`app.kt:273`）、`catalog.threadUsage` 与
      账户页的 `usage`/`usageLoaded` 仍保留上一个账户的数据，切号后先显示旧值直到下一次读取。
