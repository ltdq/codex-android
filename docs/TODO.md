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

- [ ] **安全缓冲重试**：上游给「换更快的模型重试 / 继续等待 / 了解更多」
      （`tui/src/chatwidget/safety_buffering.rs`）；Kotlin 只报一条诊断。
- [ ] **实时语音（realtime）**：目前是壳，无 WebRTC/录音/字幕，`Realtime*` 通知全部丢弃
      （`chatwidget/realtime.kt`）；上游 `tui/src/chatwidget/realtime.rs`、
      `realtime_split_flap.rs`、`realtime_settings.rs`。含麦克风/扬声器电平条、静音提示、
      连接阶段、voice 选择与设置页。
- [ ] **goal 持久状态指示**：上游 footer 常驻 Active/Paused/Blocked/UsageLimited/
      BudgetLimited/Complete 与用量，恢复会话时提示「Resume paused goal?」
      （`tui/src/chatwidget/goal_status.rs`、`goal_menu.rs`）；Kotlin 只在 GoalSheet 打开时可见。
- [ ] **三段式导航的区段切换没有过渡动画**：切换导航区（`app.kt` 的 `openSection`）与正文换页
      （`surface.kt` 的 `ShellPageTransition`）都是直接跳变，miuix 的
      `NavigationRail`/`NavigationRailItem` 只做自身展开动画；菜单卡片的抽出与正文卡片的
      避让已有动效（`nav_rail.kt` 的 `NavMenuPanel`，`app.kt` 的 `menuSlide` 与 `menuRoom`）。
- [ ] **窄窗口没有自己的版式**：`wide = maxWidth >= UiConsts.WideContentBreakpoint`
      （`app.kt`）只看窗口宽度，776dp 的手机竖屏也走三列平板版式；菜单固定又是默认
      （`KeyMenuExpanded` 默认 true），此状态下正文列 460dp（`nav_rail.kt` 的
      `pageColumnStart` 与 `pageColumnWidth`），菜单卡片收起只把卡片放宽到 684dp，正文列不变，
      `chatwidget/rendering.kt` 的 `panelMax` 仍只有 432dp，够不到状态卡与 diff 并排所需的
      700dp，diff 卡因此从 `UiConsts.DiffPaneWidth`（520dp）掉到 432dp。

## 2. Transcript 渲染

- [ ] **Mermaid 图**：完成的 mermaid fence 渲染成图，失败回退代码块
      （上游 `tui/src/markdown_render/mermaid.rs`）；Kotlin 当普通代码块。
- [ ] **inline visualization**：`::codex-inline-vis{…}` 指令（上游
      `tui/src/inline_visualization.rs`）；Kotlin 只处理 `:codex-file-citation{…}`。
- [ ] **数学排版**：上游对受支持的 TeX 子集做有界排版（`tui/src/markdown_render/math/`）；
      Kotlin 显示为 mono 斜体源码。
- [ ] **后台线程的 hook 结果回放**：`HookCompleted` 只落到当时的会话态，后台子线程的
      hook 运行不会回放进它的 transcript；hook 标识靠 `hooks/list` 元数据按
      (event, displayOrder, sourcePath) join，会话中途改 hook 配置会静默错配。
- [ ] **unified exec 等待/交互 cell**：上游区分「Waited for background terminal」与
      「Interacted with background terminal」（`tui/src/history_cell/exec.rs`）；Kotlin 把
      stdin 混进命令输出，等待不可见。
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
      `TrustProjectSheet` 承担，不计入缺口。
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

## 7. 桌面设置页

设置侧栏按桌面应用的四组十九页重建：导航栏是主页/定时任务/自定义/项目，设置固定在底部；
设置菜单是 个人（常规、导入、外观、语音、配置、个性化、Mini 与虚拟宠物、键盘快捷键）、
集成（插件、电脑操控、应用快照、浏览器）、编码（钩子、连接、代码审查、Git、环境、Worktrees）、
已归档（已归档的聊天）。每页的标题、分组标题、行标题与说明逐字取自桌面截图；本端没有对应能力
的行照常列出但置灰（`app/.../chatwidget/settings_general.kt` 起的一族 `settings_*.kt`）。
下面只记这些置灰行与它们缺的能力。

- [ ] **常规**：`默认文件打开位置`、`集成终端 Shell`、`语言`、`Confirm before closing a window`、
      `默认采用完整视图`、`底部面板`、`默认终端位置`、`压缩本地保存的聊天历史记录`、
      `打开源许可证`、`插件`、`纯文本编辑器`、`显示上下文窗口使用情况`、`发送快捷键`、
      `跟进处理方式`、`弹出窗口快捷键`、`默认使用独立聊天`、`Enable dot notifications`、
      `通知提示音`、`彩纸礼炮` 全部置灰：桌面应用的窗口、标签页、集成终端、听写与插件开关
      在本端没有对应物。`轮次完成通知` 只表达得出「从不 / 仅在未聚焦时」两态
      ——`AgentNotification.TurnComplete`（`chatwidget/notifications.kt`）是布尔，
      桌面的第三态「始终」没有落点。
- [ ] **语音**：只有 `语音会话` 一行通向本端 Realtime 页（`chatwidget/realtime.kt` 仍是壳，
      无 WebRTC、录音与字幕，见第 1 节）；`麦克风`、`语言`、`听写快捷键`、`最近录音`、
      `听写词典` 全部置灰。
- [ ] **外观**：`主题`、`强调色`、`背景`、`前景`、`字体`、`界面字号`、`代码字体大小`、
      `分别设置浅色和深色模式`、`界面字体样式`、`内容字体`、`代码字体`、`半透明侧边栏`、
      `对比度`、`差异标记`、`使用指针光标` 全部置灰；本端只有 System/Light/Dark 与减弱动效
      （`theme/theme.kt` 的 `Appearance`）。桌面的 `减少动态效果` 是三态（系统/开启/关闭），
      `Appearance.reduceMotion` 是布尔，只有两态。
- [ ] **配置**：`用户配置` 与 `打开 config.toml`（设备上没有能打开它的编辑器）、
      `Codex 依赖项`、`重置并安装工作空间` 置灰。`可用推理强度` 在桌面是多选（模型控件里显示
      哪些档位），本端是单选并写入 `model_reasoning_effort`（会话默认档）。
      `网页搜索` 写 `web_search`，该键不在 `ConfigSnapshot`（`protocol/protocol/v2/config.kt`）
      里，且本仓 `codex/` submodule 未检出，键名与取值无法在本仓校验。
- [ ] **个性化**：`Codex 指令` 置灰——本端没有 AGENTS.md 编辑器，`/init` 只是命令。
      截图的 `Codex 记忆` 分组标题行上还挂着主机下拉 `本地主机`，本端没有主机概念，
      `CodexSection` 也只有标题，没有挂在标题上的控件位；分组说明行按导入页的做法放进卡片首行。
- [ ] **项目**：截图的项目页是 `名称` / `已更新 ↓` 两列表格，且整个侧栏收起；本端仍是
      `修改的项目` 页加侧栏项目树（`Surface.Projects`、`nav_menu.kt` 的 `addProjectTree`）。
- [ ] **Mini 与虚拟宠物、电脑操控、应用快照、浏览器、代码审查**：截图没有覆盖这五页的内容，
      页面只有一行置灰说明；`代码审查` 另有一行通向本端 Review 页。
- [ ] **插件**：桌面的页签（插件 / MCP / 技能）、搜索框与 `浏览目录`、`添加` 在本端没有对应；
      本端列五行分别通向 Plugins、Skills、McpServers、Apps、PluginShares 页。
- [ ] **连接**：`添加` 与 SSH 连接列表置灰（本端没有 SSH 连接管理）；`远程控制` 一行通向本端
      RemoteControl 页。
- [ ] **Git**：`分支前缀`、`Pull Request 合并方法`、`始终强制推送`、`创建草稿 Pull Request`、
      `审查结果呈现方式`、`准备就绪时自动合并`、监控指引、`提交说明`、`Pull Request 说明`
      全部置灰——`ConfigSnapshot` 没有这些键。
- [ ] **Worktrees**：`工作树根目录`、`创建工作树前始终获取上游更新`、`自动删除旧工作树`、
      `自动删除限制` 全部置灰。
- [ ] **已归档的聊天**：截图只有空态；本端的归档列表是 `Surface.Archived` 页，设置页留一行入口。
- [ ] **定时任务**：导航栏的时钟项（`Surface.Scheduled`）只有桌面截图里的空态、
      `新建任务` 按钮与 `即将执行` 分组，本端没有任何调度能力，app-server 也没有对应方法
      （`app/scheduled.kt`）。
- [ ] **本端自有的入口**：主页侧栏的 `当前任务` 组（历史、文件、命令、终端、审查、Worktree、
      Diff、Goal、Realtime）与 `最近` 分组标题上的「全部会话」动作在桌面截图里没有对应物；
      配置页尾部保留 `配置来源`、`账户与数据`、`关于` 三组，环境页保留可写根目录，
      常规页保留自动回顾，外观页保留显示启动提示，个性化页保留记忆内容入口，
      钩子页保留 `查看与信任钩子`（0 个钩子）、Worktrees 页保留 `Worktree`、
      已归档的聊天页保留 `已归档会话`，各自通向本端同名页。
      这些是本端独有能力，从菜单去掉就没有别的入口。
- [ ] **自定义**：截图的自定义侧栏只有 `插件`、`技能` 与 `已安装` 分组；本端列五行
      （插件、Skill、MCP 服务器、App、插件分享），因为每项在本端都是独立页面，
      桌面把它们收在插件页的分段控件里（`destination_catalog.kt` 的 `CustomizeMenu`）。
