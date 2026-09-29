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
  图标（首页、插件、项目，底部为设置），由我们自己的 miuix 组件 `CodexNavigationRail` 绘制
  （`styles.kt`：沿用 miuix 的 surface 背景、`Role.Tab` 与分隔线，选中态只把图标染成强调色，按下
  时在整块方形上画圆角矩形的按压高亮，但没有 miuix `NavigationRail` 的标签、固定 24dp 内边距与
  内部滚动），图标中心到左边缘的距离等于到上/下边缘的距离；第二段菜单（首页＝新建会话与项目列表，
  插件＝Skill、MCP 等扩展页，设置＝设置分区与账户页）由同一个 `NavMenuPanel` 承担悬浮与固化两种
  形态：指针停在图标上或长按图标时，它是悬在正文之上的一张卡片——圆角、与导航栏留出间距、带悬浮
  阴影，正文列不动；指针沿导航栏移到别的图标只是就地换掉菜单内容，移进菜单不会让它消失、菜单可
  滚动。菜单只在开关时动：从导航栏的边缘向右展开、同样收回，卡片连同圆角和阴影一起长出来，而不是
  从自己的中间放大或淡入。导航栏画在菜单之上，菜单的阴影落在正文一侧，不落在导航栏上。菜单已经
  固化在布局里时，指针停留与长按都只给一个名字气泡（miuix `PlainTooltip`，锚在图标右侧）：此时
  没有可悬浮的菜单，长按也不会把固化的列收起。点击图标或选中菜单里的一项则「固化」：卡片的留白
  收掉、直角化、贴住导航栏并占满高度，成为第二段的固定列，正文让出「窗口 - 导航栏 - 菜单」的
  宽度；两种形态是同一个组合，固化不重建列表（滚动位置保留），卡片向外填充与正文右移是同一段
  动画，行和标题始终停在原处（填充量以内容内边距还回去）。再次点击当前项收起。指针离开导航栏与
  菜单的区域、或点击该区域之外，悬浮态消失；长按浮出的菜单点它自己的任何地方都固化——点在菜单项上
  就是选中那一项，点在菜单的留白上就固化到它正在显示的分区——只有滑动菜单不固化。菜单标题只有一个
  大标题，列表在它下面而不是从它下面滚过，所以没有大小标题的收放；从菜单打开的页面显示在第三段并在
  其中直接切换，左侧两段保持可见，这些页面不再带返回键（返回交给系统手势）。
- 输入框属于首页的会话页而不是外壳：只在这一页显示（读子代理时换成只读栏；被菜单打开的页面覆盖时
  与顶部两个按钮一并收起——它们锚在窗口边缘，会越过正文列伸进留白），宽度即正文列宽，但会
  收回菜单收起时让出的两侧留白——始终止于窗口右边距、起于导航栏右缘；菜单填充/收起时随正文列一起
  移动（悬浮态不动正文），键盘弹出时一起抬起。
- 会话工具（历史、文件、命令、终端、Review、Worktree、Diff、Goal、Realtime）与全部会话/
  已归档/添加工作区是 miuix 的 `OverlayListPopup`（`ListPopupColumn` + `SmallTitle` +
  行复用菜单的 `BasicComponent`），锚定在会话按钮下方、右缘与其对齐，由根 Scaffold 的弹出宿主
  绘制，因此在输入框之上；顶部两个按钮锚定窗口右边缘，距右边缘与距上边缘同为 14dp，不跟随正文列。

## 文档

- [docs/TODO.md](docs/TODO.md)：待办与已知功能缺口
- [docs/toolchain.md](docs/toolchain.md)：内置工具与版本
- [native/README.md](native/README.md)：JNI/helper 构建与运行时契约
