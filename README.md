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
- 界面是「导航栏 - 展开菜单 - 正文」三段式：左侧导航栏常驻且只有图标（首页、插件、项目，
  底部为设置），切换导航项会在第二段展开该栏的菜单（首页＝新建会话与项目列表，插件＝Skill、
  MCP 等扩展页，设置＝设置分区与账户页），再次点击当前项收起菜单；从菜单打开的页面显示在第三段
  并在其中直接切换，左侧两段保持可见，这些页面不再带返回键（返回交给系统手势）。会话正文宽度为
  「窗口 - 导航栏 - 展开的菜单」，菜单收起时在剩余空间内居中。
- 底栏（输入框）属于外壳而不是某个页面：任何页面下都在，并随可用宽度伸缩——菜单展开时右移，
  收起时补上菜单让出的部分。菜单标题固定为 Codex，列表滚动时大标题收成小标题。
- 会话工具（历史、文件、命令、终端、Review、Worktree、Diff、Goal、Realtime）与全部会话/
  已归档/添加工作区放在右侧悬浮会话面板里；面板与顶部两个按钮锚定在窗口右边缘，不跟随正文列。

## 文档

- [docs/TODO.md](docs/TODO.md)：待办与已知功能缺口
- [docs/toolchain.md](docs/toolchain.md)：内置工具与版本
- [native/README.md](native/README.md)：JNI/helper 构建与运行时契约
