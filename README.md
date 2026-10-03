# Codex Android

在 Android 上运行 [Codex](https://github.com/openai/codex)：用 Compose UI 替换上游的
`codex-tui`，Rust 内核以 JNI 库编入 APK，UI 通过上游 app-server 协议访问同进程内的
`codex-core`，内核 spawn 的命令交给 App 内置的交叉编译工具链执行。

## 目录

```text
app/          Compose UI、协议客户端、Android 运行时
native/       JNI bridge、codex-helper、Rust 交叉编译与补丁
toolchain/    工具链声明、补丁、构建产物
build-logic/  Gradle 约定插件：工具构建、打包、JNI
codex/        openai/codex submodule（只读）
third_party/  上游源码 submodule（只读）
docs/         工具链说明与待办
scripts/      真机冒烟脚本
```

## 构建

宿主机需要 JDK 21、Android SDK 37、NDK r30、Rust 1.95.0 与 1.97.1 的
`aarch64-linux-android` target（1.95.0 另需 `rust-src` 组件）、Go 1.27，以及 `cmake`、
`ninja`、`autoconf`、`bison`、`flex`。首次克隆后执行
`git submodule update --init --recursive`（`llvm`、`binutils` 的历史较大）。

```bash
./gradlew :app:assembleDebug        # 工具链 + codex JNI + APK
```

产物在 `app/build/outputs/apk/debug/app-debug.apk`，包名 `com.cy.codex`。工具链与 JNI
也可以单独构建：

```bash
./gradlew :toolchain:buildToolchain :toolchain:packJniLibs
./gradlew :native:buildJni
```

只改 Kotlin 时可跳过两者（要求产物已存在）：

```bash
./gradlew :app:testDebugUnitTest -PskipToolchainBuild -PskipNativeBuild
```

## 验证

```bash
./gradlew :app:testDebugUnitTest -PskipToolchainBuild -PskipNativeBuild   # JVM
bash scripts/device-smoke-test.sh -PskipNativeBuild                       # 真机（adb）
./gradlew :native:hostSmokeTest                                           # 宿主内核
```

真机测试在真实 App UID 下验证工具链安装、JNI 启动、账户/配置/模型/会话读取、
`command/exec`、apply_patch 与重启后恢复。模型生成和登录需要有效账户及网络连接，离线测试
不能替代。

## 说明

- 只构建 arm64-v8a，`minSdk 36`。
- Android 没有上游的 Linux namespace 沙箱，嵌入运行时使用 `danger-full-access`；命令仍受
  App UID 权限边界限制，没有 root，也读不到其他 App 的私有数据。
- 未登录也能启动。只有需要 OpenAI 认证的 provider 在提交消息时引导到账户页面（ChatGPT
  设备码或 API key 登录）；自带凭据的第三方 provider（`requires_openai_auth = false`）
  不要求登录，与上游 `should_show_login_screen` 一致。宿主机 Codex 凭据不会复制到设备。
- 配置、认证与会话记录在 `files/home/.codex/`（`CODEX_HOME`），不参与 Android 自动备份。
- Android 进程被系统终止后，下次启动重新连接并读取磁盘上的会话；没有后台常驻保证。
- 主会话与历史页的用户消息可“从此处重新编辑”：回退到该轮之前，并把原输入连同附件
  放回草稿；运行中的消息、轮内插话和 review 内部输入不支持单独回退，工作区文件不撤销。
- 运行中发送消息会插话，并显示待接收预览；“排到下一轮”保留可编辑的服务端队列。
  中断时，尚未接收的插话和确认移出队列的输入会并回草稿，保留图片、音频与提及。
- Transcript 显示用户审批决定，以及自动审核明确的拒绝或超时回执；仅关闭审批请求不会
  推断审批结果。回执为当前打开会话的 UI 状态，重新加载会话后不从服务端重建。
- 界面是「导航栏 - 展开菜单 - 正文」三段式，整壳跑在 miuix 的 Scaffold 下：左侧导航栏常驻且只有
  图标（首页、插件、项目，底部为设置），顶端是自绘的 LOGO（`logo.kt`：强调色圆角方块里嵌一个终端
  提示符，不用图标字体，所以跟着主题的强调色走、放大也不糊），占导航栏顶端独立的一块
  （`UiConsts.NavRailHeadHeight`，离窗口顶边一个 `UiConsts.ScreenMargin` 加窗口自身的顶部
  inset，因此不会贴边、也不会落进摄像头挖孔），作为整壳的视觉锚点；导航栏由我们自己的 miuix
  组件 `CodexNavigationRail` 绘制
  （`styles.kt`：沿用 miuix 的 surface 背景、`Role.Tab` 与分隔线，选中态只把图标染成强调色，按下
  时在整块方形上画圆角矩形的按压高亮，但没有 miuix `NavigationRail` 的标签、固定 24dp 内边距与
  内部滚动），图标中心到左边缘的距离等于到上/下边缘的距离；第二段菜单
  （首页＝新建会话与项目列表，插件＝Skill、MCP 等扩展页，设置＝设置分区与账户页）由 `NavMenuPanel`
  绘制，与第三段正文各是一张
  独立的圆角卡片，用的是同一个 `CodexShellCard`（同样的圆角、底色与描边）：两张卡片同高、左缘都
  贴着导航栏，离窗口边缘和彼此的边距都是 `UiConsts.ScreenInset`，都浮在导航栏的底色上；指针停在
  图标上或长按图标时，菜单卡片带悬浮阴影悬在正文卡片之上（尺寸与位置和固定后一样），正文卡片不动；
  指针沿导航栏移到别的图标只是就地换掉菜单内容，移进菜单不会让它消失、菜单可滚动。菜单的行是
  `ui_kit.kt` 里的同一套二级菜单组件：分组小标题（`CodexMenuGroupTitle`）加图标文字选项行
  （`CodexMenuRow`，选中态是浅灰圆角底、图标走强调色），顶上一条搜索框按标题本地过滤
  （`nav_menu.kt` 的 `filterMenuRows`）；首页与项目页的项目、会话行是同一套组件的
  `CodexMenuProjectRow`/`CodexMenuSessionRow`，会话面板弹窗（`SessionMenuPopup`）也用它们，所以
  四个分区的菜单、弹窗与设置页里的分组没有第二套写法。菜单卡片是从导航栏
  右侧抽出来的：从导航栏里滑出来、再滑回去，行和标题不重排。导航栏画在菜单之上，菜单的阴影裁在导航栏
  右侧，不落在导航栏上。菜单已经固定在布局里时，
  指针停留与长按都只给一个名字气泡（miuix `PlainTooltip`，锚在图标右侧）：此时没有可悬浮的菜单，
  长按也不会把固定的卡片收起。点击图标或选中菜单里的一项则「固定」：悬浮的卡片落下去，收掉阴影、
  不改尺寸和位置，与正文卡片一模一样；让开的是正文卡片——它右移一个菜单列，两张卡片并排；再点当前项
  收起菜单卡片（收起后再点同一项只是把卡片放回来，页面留在原来那一项），卡片把让出的那列宽度填回来，
  正文在卡片里居中、宽度不变——正文宽度只由版式决定（窗口减去导航栏与菜单列），所以开关菜单只移动卡片，
  页面里的文字不重排。抽出/收回、落下与卡片避让都走
  `Motion.Panel`，所以开关时两张卡片同一条弹簧、始终隔着一个 `UiConsts.ScreenInset`
  （窗口窄于 `WideContentBreakpoint` 时没有第二列可让，菜单只悬浮在正文之上，固定也不推正文，也不
  落下——与正文同色的卡片落了就没有轮廓可看）。行和标题在固定前后不重排，卡片收起再展开时列表的滚动
  位置也留在原处。指针离开导航栏与菜单的区域、或点击该区域之外，悬浮态消失；长按浮出的菜单点它自己的任何地方
  都固定——点在菜单项上就是选中那一项，点在菜单的留白上就固定到它正在显示的分区——只有滑动菜单
  不固定。菜单标题只有一个大标题，列表在它下面而不是从它下面滚过，所以没有大小标题的收放；列表占满
  卡片（只剩 6dp 内边距和窗口自身的底部 inset 在滚动区内），所以最后一行不会被卡片底部的留白挤掉；
  从菜单打开的页面显示在第三段并在其中直接切换——同一分区的页面就地替换而不是叠进返回栈
  （`CodexApp.openSectionPage`），重新点导航栏回到该分区上次停留的那一页（`CodexApp.sectionPages`），
  左侧两段保持可见，这些页面不再带返回键（返回交给系统手势）。该分区自己的页已经压在栈里时（设置页
  正文里的链接是 push 的），替换把它回到栈顶并丢掉压在它上面的页：`miuix-nav` 以路由做条目标识，
  同一个路由在栈里出现两次会被判为重复而崩（`nav_menu.kt` 的 `sectionPageSwap`）。
- 第三段正文共用一套列表网格（`ui_kit.kt`）：`CodexPage` 是页头（20sp 半粗标题加 13sp 说明）加正文，
  `CodexSection` 是分组小标题加卡片，行是 `CodexRow`/`CodexSwitchRow`/`CodexRadioRow`/
  `CodexNavRow`/`CodexValueRow`（14sp 行标题、12sp 说明），条目网格是 `CodexCardGrid` 加
  `CodexCatalogCard`。对齐只有两条线：`UiConsts.PageGutter`（页头、分组标题、卡片左缘）与
  `UiConsts.RowInset`（行内容）。每个推送页都画在这一套上——设置、账户、记忆、诊断、会话状态、项目与
  环境、Worktree、文件、命令、终端、Review、Diff、Realtime、导入、沙箱、远程控制、用户验证、Agent、
  会话列表——页里只写分组与行，不再自带页头、正文内边距或行样式；正文自己带 `LazyColumn` 的页
  （会话列表、工作目录选择器、历史记录）用 `CodexPage(scroll = false)`，让列表留在有界的正文列里。
  设置页的正文直接显示该分区的可调节项，页内不再重复列一遍分类；技能、插件、App、Hook、MCP、插件分享
  页的条目按参考图排成卡片网格（图标方块、名称、说明、启用开关）。
- 输入框属于首页的会话页而不是外壳：只在这一页显示（读子代理时换成只读栏；被菜单打开的页面覆盖时
  与顶部两个按钮一并收起——它们锚在窗口边缘，会越过正文列伸进留白），宽度即正文列宽；菜单卡片
  固定/收起时它随正文列在卡片里居中（宽度不变），键盘弹出时一起抬起。
- 会话工具（历史、文件、命令、终端、Review、Worktree、Diff、Goal、Realtime）与全部会话/
  已归档/添加工作区是 miuix 的 `OverlayListPopup`（`ListPopupColumn` + 二级菜单的
  `CodexMenuGroupTitle` 与 `CodexMenuRow`），锚定在会话按钮下方、右缘与其对齐，由根 Scaffold 的
  弹出宿主绘制，因此在输入框之上；顶部两个按钮锚定窗口右边缘，距右边缘与距上边缘同为 14dp，
  不跟随正文列。

## 文档

- [docs/TODO.md](docs/TODO.md)：待办与已知功能缺口
- [docs/toolchain.md](docs/toolchain.md)：内置工具与版本
- [native/README.md](native/README.md)：JNI/helper 构建与运行时契约
