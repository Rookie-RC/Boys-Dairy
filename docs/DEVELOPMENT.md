# 开发指南

给人和 Agent 共用的操作手册：上手、命令、调试、CI，以及常见改动的步骤。面向用户的介绍见 [README.md](../README.md)，项目规则与代码地图见 [AGENTS.md](../AGENTS.md)，设计见 [ARCHITECTURE.md](ARCHITECTURE.md)。

## 概览

Boys Dairy 是一个原生 Android 应用（启动器名称「Daily Calc」，计算器图标伪装），单模块 `:app`，包名 `com.rookierc.boysdairy`，最低支持 Android 8.0（API 26），目标 / 编译 API 35。本仓库此前是一个 React 网页应用，0.1.0 起已被这个原生应用完全取代（网页版及其 GitHub Pages 部署流程已删除）。

技术栈：Kotlin 2.1.0、AGP 8.7.3、Gradle 8.11.1、Compose BOM 2024.12.01（Material 3，未启用动态取色）、Navigation Compose、Room 2.6.1（KSP）、DataStore Preferences、androidx.biometric（可选的指纹解锁）。AI：Claude 走官方 Anthropic Java SDK，DeepSeek 直接用 OkHttp + `org.json` 调它的 OpenAI 兼容接口。依赖版本见 `gradle/libs.versions.toml`。

### 文档索引

| 文档 | 内容 |
| --- | --- |
| [README.md](../README.md) | 面向用户：是什么、能做什么、隐私一览、安装、常见问题 |
| [ARCHITECTURE.md](ARCHITECTURE.md) | 分层、依赖容器、锁与会话生命周期、导航、数据模型、备份格式、AI 请求流程、设计系统 |
| [PRIVACY.md](PRIVACY.md) | 存了什么、存在哪、什么会离开设备、4 位 PIN 的诚实局限 |
| DEVELOPMENT.md（本文） | 环境搭建、命令、项目结构、模拟器、调试、CI、加文案、换 AI 模型、装到手机 / 发布构建 |
| [AGENTS.md](../AGENTS.md) | 给编码 Agent 的项目指南（命令、代码地图、硬性规则） |
| [CLAUDE.md](../CLAUDE.md) | Claude Code 专用补充说明 |
| [CHANGELOG.md](../CHANGELOG.md) | 版本变更记录 |

## 快速开始

需要 JDK 17 和 Android SDK 35。macOS 上可以一键装好（用户级安装，不需要 sudo 和 Homebrew）：

```bash
scripts/setup-macos.sh --with-emulator --accept-licenses   # 一次性：JDK 17 + Android SDK + 模拟器（会代你接受 SDK 许可）
./gradlew testDebugUnitTest                                 # 跑单元测试
./gradlew assembleDebug                                     # 构建 → app/build/outputs/apk/debug/app-debug.apk
scripts/emulator.sh --install                               # 启动模拟器，安装并打开应用
```

脚本做了什么、其他平台怎么装，见下面的「环境搭建」；更多命令见「常用命令」；想装到自己手机上日常用，见「[装到自己手机上](#装到自己手机上)」。

## 环境搭建

### 环境要求

| 项 | 版本 |
| --- | --- |
| JDK | 17（Temurin） |
| Android SDK | `platforms;android-35`、`build-tools;35.0.0`、`platform-tools`（跑模拟器还要 `emulator` 和一个 API 35 系统镜像） |
| Gradle | wrapper 8.11.1（`./gradlew` 自动下载，首次构建需要联网） |
| AGP / Kotlin | 8.7.3 / 2.1.0 |

### macOS 一键搭建

快速开始第一行的 `scripts/setup-macos.sh` 是用户级安装，不需要 sudo，也不用 Homebrew（Intel Mac 上 Homebrew 没有预编译包，要从源码编译，实际不可用）。脚本幂等，已装好的步骤会跳过：

- Temurin JDK 17 → `~/Library/Java/JavaVirtualMachines/temurin-17.jdk`（从 Adoptium API 下载，校验 SHA-256）。
- Android 命令行工具 → `~/Library/Android/sdk/cmdline-tools/latest`（默认从 dl.google.com 下载；设 `CMDLINE_TOOLS_SHA256` 可校验，`CMDLINE_TOOLS_URL` 可换地址）。
- SDK 包：`platform-tools`、`platforms;android-35`、`build-tools;35.0.0`；加 `--with-emulator` 还会装 `emulator` 和 `system-images;android-35;google_apis;<x86_64|arm64-v8a>`，并创建 AVD `boysdairy`（Pixel 6，720×1560 @ 320dpi，2048 MB 内存）。
- 在仓库根目录写 `local.properties`（已存在则不动）。

`--accept-licenses` 表示**由脚本代你接受 Android SDK 许可**；不加这个参数时会在终端里逐项询问（没有终端会报错退出）。环境变量 `ANDROID_HOME` / `ANDROID_SDK_ROOT`、`ANDROID_AVD_HOME` 可改默认位置。`--help` 看全部选项。

装完把脚本最后打印的几行加进 `~/.zshrc`：

```bash
export JAVA_HOME="$HOME/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home"
export ANDROID_HOME="$HOME/Library/Android/sdk"
export PATH="$PATH:$ANDROID_HOME/platform-tools"
```

### 其他平台

没有脚本：自己装 JDK 17 和上表的 SDK 包即可。

### local.properties

仓库根目录的 `local.properties` 只有一行，告诉 Gradle SDK 在哪（已被 `.gitignore` 忽略，不要提交）：

```properties
sdk.dir=/Users/你/Library/Android/sdk
```

也可以不建这个文件，改设 `ANDROID_HOME` 环境变量。

## 常用命令

跑全部单元测试和构建 debug APK 的命令见「快速开始」，其余常用的：

| 目的 | 命令 |
| --- | --- |
| 只跑一部分测试 | `./gradlew testDebugUnitTest --tests 'com.rookierc.boysdairy.ai.*'` |
| Lint | `./gradlew lintDebug`（报告 `app/build/reports/lint-results-debug.html`） |
| 装到已连接设备 / 模拟器 | `./gradlew installDebug`（debug 版，只用于开发） |
| 与 CI 完全一致（交付前跑一遍） | `./gradlew --no-daemon testDebugUnitTest assembleDebug lintDebug` |
| 构建 release APK | 见「[装到自己手机上](#装到自己手机上)」 |

单元测试报告在 `app/build/reports/tests/testDebugUnitTest/index.html`。同一个检出目录里同一时间只跑一个 Gradle。

## 项目结构

```
.
├── README.md  AGENTS.md  CLAUDE.md  CHANGELOG.md
├── docs/                            # ARCHITECTURE / PRIVACY / DEVELOPMENT
├── scripts/
│   ├── setup-macos.sh               # 安装 JDK 17 + Android SDK（+ 模拟器），写 local.properties
│   └── emulator.sh                  # 启动模拟器，可选安装并打开应用
├── .github/
│   ├── workflows/android.yml        # CI：单元测试 + debug 构建 + lint
│   └── pull_request_template.md
├── build.gradle.kts  settings.gradle.kts  gradle.properties
├── gradle/libs.versions.toml        # 版本目录
└── app/
    ├── build.gradle.kts  proguard-rules.pro
    ├── schemas/                     # Room 导出的数据库 schema
    └── src/
        ├── main/
        │   ├── AndroidManifest.xml
        │   ├── res/                 # values（中文，默认）/ values-en / values-night / xml / drawable / mipmap
        │   └── java/com/rookierc/boysdairy/
        │       ├── BoysDairyApp.kt  # Application + AppContainer（手动依赖注入）+ 进程级自动上锁
        │       ├── MainActivity.kt  # FLAG_SECURE（仅 release）、edge-to-edge
        │       ├── domain/          # 纯 Kotlin：Entry、日期逻辑、统计、完整战报、今日宜忌、单人关键词、备份提醒规则
        │       ├── data/            # Room 实体 / DAO / 仓库、DataStore 设置、男 / 女生版
        │       │   └── backup/      # JSON 导出 / 导入（编码、合并、引擎、SAF 管理器）
        │       ├── security/        # PinHasher、PinAttemptLimiter、LockManager、持久化锁定记录、指纹解锁
        │       ├── ai/              # AI 锐评：提示词、服务编排、AiProvider（固定模型）、各服务商实现、Key 加密存储
        │       └── ui/
        │           ├── AppRoot.kt  SessionStores.kt  MainNavigation.kt  FunBottomBar.kt
        │           ├── theme/       # FunColors / FunShapes / Material3 主题
        │           ├── common/      # Fun* 设计系统组件、按男 / 女生版取文案与 emoji
        │           └── lock/ edition/ home/ add/ history/ insights/ ai/ backup/ settings/
        └── test/                    # JVM 单元测试，目录与 main 镜像
```

每个包的职责更细的说明见 [AGENTS.md](../AGENTS.md) 的「代码地图」。

## 模拟器

快速开始最后一行的 `scripts/emulator.sh --install` 会启动 AVD、等待开机、`./gradlew installDebug`、再打开应用。选项：

| 选项 | 作用 |
| --- | --- |
| `--avd NAME` | 换 AVD（默认 `boysdairy`） |
| `--install` | 开机后安装 debug 版并启动 `com.rookierc.boysdairy/.MainActivity` |
| `--headless` | 无窗口（`-no-window`） |
| `--no-position` | 不修改 AVD 的窗口位置 |
| `-h`, `--help` | 帮助 |

- 默认把模拟器窗口停在屏幕**右侧**：用 `osascript` 向 Finder 读屏幕宽度，往 AVD 的 `emulator-user.ini` 写 `window.x = 屏幕宽 − 430`、`window.y = 25`、`window.scale = 0.53`。
- 已经有模拟器在运行就直接复用，不会再起一个；开机超时 300 秒；模拟器输出写到 `${TMPDIR:-/tmp}/boysdairy-emulator.log`。
- `--install` 会把 `ANDROID_SERIAL` 设为该模拟器，所以同时连着真机也不会装错地方。
- 脚本缺 AVD / SDK 时提示运行 `scripts/setup-macos.sh --with-emulator`。关闭模拟器：`adb emu kill`。

## 调试技巧

`adb` 在 `~/Library/Android/sdk/platform-tools/adb`（加进 `PATH` 后直接用 `adb`）。

```bash
adb exec-out screencap -p > /tmp/shot.png        # 截图
adb shell input tap 360 800                      # 点击（坐标以 720×1560 为准）
adb shell pm clear com.rookierc.boysdairy        # 清空应用数据（PIN、日记都没了，回到首次设置）
adb shell run-as com.rookierc.boysdairy ls files/datastore databases   # 看应用私有文件
adb exec-out run-as com.rookierc.boysdairy cat databases/boysdairy.db > /tmp/boysdairy.db   # 拉数据库
```

- **`screencap` 和 `run-as` 都只在 debug 构建上可用**：release 开了 `FLAG_SECURE`，截图是黑屏；release 也不可调试，`run-as` 会被拒绝。
- Room 默认 WAL 模式，想要一致的快照请把 `boysdairy.db-wal` 和 `boysdairy.db-shm` 一起拉下来。
- 应用本身不打日志（有意为之，避免泄漏日记内容），`adb logcat` 里只有系统和库的输出；调试时临时加的日志不要提交。
- 想测英文：设置 → 语言 → English；想测深色：设置 → 主题 → 夜猫子模式。
- 测锁定：在锁屏连续输错 5 次，会看到 30 秒倒计时；杀掉应用再打开，倒计时仍在。

## CI

`.github/workflows/android.yml`（Android CI）：

- 触发：任意分支 push、所有 pull request；同一 ref 上新的运行会取消旧的。
- `ubuntu-latest`，超时 30 分钟，权限 `contents: read`，不需要任何 secret。
- 步骤：checkout → JDK 17（Temurin）→ `gradle/actions/setup-gradle@v4`（缓存）→ `./gradlew --no-daemon testDebugUnitTest assembleDebug lintDebug`。
- 产物：`app-debug-apk`（debug APK，保留 14 天）；`reports`（单元测试报告 / 结果与 lint 报告，无论成败都上传，保留 14 天）。
- 没有发布、签名、部署步骤。

PR 模板 `.github/pull_request_template.md` 要求填：变更说明、截图（UI 变化）、是否跑了单元测试和模拟器、是否涉及隐私数据或网络。

## 新增一条字符串

1. 在 `app/src/main/res/values/strings.xml`（中文，默认）加 `<string name="...">`，放在相应的注释分组下；key 以屏幕或功能为前缀（`home_`、`ai_`、`settings_`、`backup_` ……）。
2. 在 `app/src/main/res/values-en/strings.xml` 加**同名** key 和英文文案（语气一致，不是直译）。`plurals`、`string-array` 也一样；中文 `plurals` 只写 `other`。
3. 带参数的用位置占位符（`%1$s`、`%2$d`），两种语言都写。
4. 代码里用 `stringResource(R.string.xxx)`；数量词用 `quantityString(R.plurals.xxx, n, n)`（`ui/common/Formatting.kt`）；数组用 `stringArrayResource`。这几个都要从 `com.rookierc.boysdairy.ui.common` 导入（按男 / 女生版取文案），不要导入 `androidx.compose.ui.res` 的同名函数；Composable 之外（比如保存回调里拼提示）用 `Resources.editionString` 等扩展并传入捕获的 edition。`EditionResourcesGuardTest` 会拦住漏网的。
5. 这条文案在女生版要换说法时：再加一条 `<同名>_girl`（中英都加，占位符保持一致），并在 `ui/common/GirlVariants.kt` 对应的表里登记 `R.string.xxx to R.string.xxx_girl`。不需要换就什么都不用做，两版共用。`app_name` 和锁屏的 `lock_*` 不做变体。`GirlVariantsTest` 检查命名、登记和占位符。
6. 检查两个文件 key 一致（应无输出）：

   ```bash
   diff <(grep -o 'name="[^"]*"' app/src/main/res/values/strings.xml | sort) \
        <(grep -o 'name="[^"]*"' app/src/main/res/values-en/strings.xml | sort)
   ```

7. 在模拟器里切到两种语言各看一眼，注意长英文会不会撑破布局；有女生版变体的，在设置 →「界面版本」切过去也看一眼。

## 更换 / 升级 AI 模型

用户没有模型选项，模型和推理强度固定在代码里，换模型就是改一个地方：

1. 改 `ai/AiProvider.kt` 里 `AiProvider.spec`：对应服务商的 `model`（需要时还有 `effort`）。这是唯一来源，别处不要写模型 id。
2. 先读新模型的官方文档：不同模型对 thinking、推理强度可选值、采样参数、prefill 的限制不一样。Claude 用官方 Anthropic Java SDK，**不要凭记忆猜 SDK 的 API**，以文档和编译结果为准（`ClaudeTextGenerator` 的 KDoc 记录了当前的约束）；DeepSeek 看其官方 API 文档（OpenAI 兼容接口，`reasoning_effort` 只接受 `low` / `high` / `max`，`AiEffort.MEDIUM` 会按 `high` 发送）。
3. 换 Claude 模型时看 `ClaudeConfig.FALLBACK_MODELS`：只有列在里面的模型才会带服务器端拒答回退（`fallbacks: "default"` + beta 头 `FALLBACK_BETA`），目前只有 `claude-opus-5-5`；不支持它的模型收到会返回 HTTP 400（Haiku 5.5 就是）。除非确认新模型支持，否则不要把它加进去。
4. 更新测试里写死的 id：`ai/AiProviderTest`（`deepSeekSpecIsFixed`、`claudeSpecIsFixed`）、`ai/AiAnalysisServiceTest`（断言各服务商实际用的模型），其余 `DeepSeekTextGeneratorTest`、`ClaudeTextGeneratorHttpTest`、`ClaudeParamsTest`、`ClaudeMappingTest` 里的 id 多半只是构造请求 / 响应用的样例，`grep` 一下再决定要不要改。
5. 跑 `./gradlew testDebugUnitTest --tests 'com.rookierc.boysdairy.ai.*'`：HTTP 测试用本机 `MockWebServer` 校验实际发出的路径、头和请求体，不联网。
6. 用你自己的 Key 在模拟器上真跑一次 AI 页（Key 只输入到应用里，不要写进仓库、日志或聊天）。
7. 在 `CHANGELOG.md` 的 `[Unreleased]` 记一笔；如果请求地址变了，同步 `docs/PRIVACY.md`。

`AiProviderSpec.endpointHost` 只是请求主机名的记录（隐私文档引用、`AiProviderTest` 校验，界面不显示），真正的地址在 `DeepSeekConfig.BASE_URL`（DeepSeek）或 SDK 默认值（Claude）。改地址时三处要一起改。

## 新增一个 AI 服务商

1. 在 `AiProvider` 里加一个枚举项，给一个**永不改名**的 `id`（它是持久化键：所选服务商和 `api_key_<id>` Key 槽都用它），并在 `spec` 里补上模型、推理强度、主机名。
2. 写一个 `AiTextGenerator` 实现（输入 `AiRequest`，输出 `AiResult`）：Key 只放请求头，不跟随重定向，不打印请求 / 响应体，把服务商的错误映射成 `AiError`；在 `AppContainer.aiAnalysisService` 的 `generators` 映射里注册。
3. 补 `ui/common/AiProviderUi.kt` 的 `nameRes()` / `taglineRes()` / `emoji()`，以及中英两份 `ai_provider_<id>`、`ai_provider_<id>_tagline` 文案。
4. 测试：用 `MockWebServer` 验证实际请求和各类响应、状态码映射；`AiProviderTest` 里补固定值断言。
5. 更新 `docs/PRIVACY.md` 的服务商表、`docs/ARCHITECTURE.md`、`README.md`（功能、隐私一览和常见问题里提到服务商的地方；README 面向普通用户，用大白话写，不写类名、主机名这类技术细节）和 `CHANGELOG.md`。

## 数据库 schema 变更

改 `EntryEntity` 或加表：升 `AppDatabase` 的 `version`，写 `Migration` 并注册，构建后把新生成的 `app/schemas/com.rookierc.boysdairy.data.AppDatabase/<N>.json` 一并提交；不要用 `fallbackToDestructiveMigration`。若字段会进备份，同时评估是否要升 `BackupCodec.FORMAT_VERSION`。

## 装到自己手机上

README 的「安装」一节指向这里。仓库**没有发布渠道**：没有上架应用商店，也没有现成的安装包；CI 只产出用于验证的 debug APK（见上文「CI」），不适合日常使用。想日常用，就自己构建并签名 release 版。

**别拿 debug 版记真事**：debug 版 `BuildConfig.SECURE_SCREEN = false`（能截屏 / 录屏，最近任务里能看到内容），而且可调试，连上电脑就能用 `adb run-as` 读走应用私有数据。`./gradlew installDebug` 只用于开发。

### 构建、签名、安装

```bash
./gradlew assembleRelease     # → app/build/outputs/apk/release/app-release-unsigned.apk
```

仓库里**没有签名配置**，产物是未签名 APK，要自己签名后才能安装。keystore 放在仓库之外，绝不提交（`*.jks`、`*.keystore` 已被忽略）：

```bash
# 只需一次：用 JDK 自带的 keytool 生成自己的 keystore（会提示设置密码）
keytool -genkeypair -v -keystore ~/path/to/release.jks -alias boysdairy -keyalg RSA -keysize 2048 -validity 10000

BT="$HOME/Library/Android/sdk/build-tools/35.0.0"
"$BT/zipalign" -f -p 4 app/build/outputs/apk/release/app-release-unsigned.apk /tmp/aligned.apk
"$BT/apksigner" sign --ks ~/path/to/release.jks --out /tmp/boysdairy-release.apk /tmp/aligned.apk
adb install /tmp/boysdairy-release.apk     # 手机需开启 USB 调试；也可以把 APK 拷到手机上点开安装
```

### 升级与签名

- debug 和 release 用同一个 `applicationId`（`com.rookierc.boysdairy`），但签名不同。以后每次升级都要用**同一个 keystore** 签名，再 `adb install -r` 覆盖安装；签名对不上（包括手机上装的是 debug 版）时系统会拒绝覆盖，只能卸载重装，而卸载会删除全部日记、PIN 和 API Key（云备份与设备迁移是关着的，系统不会替你留一份）。keystore 和它的密码自己备份好。
- 升级或换签名前，先在应用里导出一次备份（设置 →「数据」），装好后再导入。

### release 构建须知

- Release 版 `BuildConfig.SECURE_SCREEN = true`，即开启 `FLAG_SECURE`：不能截屏、录屏，也不能用 `adb screencap` 验证界面；要看界面请用 debug 版。
- **R8 / 代码压缩尚未开启**（`isMinifyEnabled = false`，`proguard-rules.pro` 只有占位注释），APK 偏大、类名未混淆。开启前要按 Anthropic SDK（及其依赖）的文档补 keep 规则，并在 release 构建上用两家服务商各完整跑一遍 AI 流程；这一步还没有验证过。
- 版本号在 `app/build.gradle.kts` 的 `versionCode` / `versionName`（设置页显示的版本取自 `BuildConfig.VERSION_NAME`）；发版时同步更新 `CHANGELOG.md`。
