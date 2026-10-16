# AGENTS.md

给编码 Agent（任何厂商）的项目指南。面向人类的介绍见 [README.md](README.md)。

## 项目概览

Boys Dairy 是一个私人、幽默风格的原生 Android 日记应用：Kotlin 2.1 + Jetpack Compose + Material3，Room + DataStore，单模块 `:app`，包名 `com.rookierc.boysdairy`，minSdk 26 / target 35。启动器名称是「Daily Calc」、图标是计算器（伪装），进入需要 4 位 PIN。用户可见文案默认中文，另有英文。可选功能「AI 锐评」会在用户点按钮时联网，把数据发给用户选的 AI 服务商（DeepSeek 默认，或 Claude）。

深入阅读：[docs/ARCHITECTURE.md](docs/ARCHITECTURE.md)、[docs/PRIVACY.md](docs/PRIVACY.md)、[docs/DEVELOPMENT.md](docs/DEVELOPMENT.md)。

## 命令

先设环境变量（macOS，本机路径）：

```bash
export JAVA_HOME="$HOME/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home"
export ANDROID_HOME="$HOME/Library/Android/sdk"
```

```bash
./gradlew testDebugUnitTest                                       # JVM 单元测试
./gradlew testDebugUnitTest --tests 'com.rookierc.boysdairy.security.*'   # 只跑一部分
./gradlew assembleDebug                                           # → app/build/outputs/apk/debug/app-debug.apk
./gradlew lintDebug                                               # Android Lint
./gradlew --no-daemon testDebugUnitTest assembleDebug lintDebug   # 与 CI 完全一致；交付前跑一遍
```

- 同一个工作区同一时间只跑一个 Gradle（别的 Agent 正在构建时不要再开）。
- 测试报告：`app/build/reports/tests/testDebugUnitTest/index.html`；lint 报告：`app/build/reports/lint-results-debug.html`。
- 没有 instrumented 测试（`androidTest`）；UI 靠模拟器人工验证。

## 工具链

- JDK 17（Temurin），位于 `~/Library/Java/JavaVirtualMachines/temurin-17.jdk`。别的 JDK 版本不保证能构建。
- Android SDK 位于 `~/Library/Android/sdk`；路径写在已被 git 忽略的 `local.properties`（`sdk.dir=...`）。
- 这台 Intel Mac 上 Homebrew 不可用（没有预编译包，要从源码编译）。不要 `brew install`；缺工具链就跑 `scripts/setup-macos.sh`（用户级安装，不需要 sudo；`--with-emulator` 同时装模拟器，`--accept-licenses` 代为接受 SDK 许可，`--help` 看全部选项）。
- 模拟器 AVD 名为 `boysdairy`（Pixel 6，API 35 google_apis，720×1560 @ 320dpi），用 `scripts/emulator.sh` 启动。
- Gradle wrapper 8.11.1，首次构建需要联网下载依赖。

## 代码地图

`app/src/main/java/com/rookierc/boysdairy/`：

| 包 / 文件 | 职责 |
| --- | --- |
| `BoysDairyApp.kt` | `Application` + `AppContainer`（手动依赖注入，进程内单例）；在 `ProcessLifecycleOwner` `ON_STOP` 时调用 `lockManager.lock()` |
| `MainActivity.kt` | `FLAG_SECURE`（由 `BuildConfig.SECURE_SCREEN` 控制）、edge-to-edge |
| `domain/` | 纯 Kotlin，零 Android 依赖：`Entry`、`Mood`、`EntryType`、`DateLogic`（距今天数、月历网格、分组、搜索）、`Stats`（`computeStats`） |
| `data/` | Room（`AppDatabase` / `EntryDao` / `EntryEntity`）、`EntryRepository`、`SettingsRepository`（DataStore `settings`：主题、PIN 哈希、所选 AI 服务商 `aiProvider`） |
| `data/backup/` | JSON 备份：`BackupCodec`（格式）、`BackupMerge`（去重）、`BackupEngine`（流读写，可纯 JVM 测试）、`BackupManager`（SAF Uri） |
| `security/` | `PinHasher`（加盐 SHA-256）、`PinAttemptLimiter` + `DataStorePinLockoutStore`（持久化锁定）、`LockManager`（锁状态） |
| `ai/` | AI 锐评。`AiProvider.kt`：服务商枚举 + `AiProvider.spec`（**固定的**模型、推理强度、展示用主机名）；`AiPromptBuilder`（纯函数，决定发什么）；`AiAnalysisService`（编排）；`ApiKeyStore`（每个服务商一个 Key 槽，Keystore 加密，DataStore `secure`）；`DeepSeekTextGenerator`（OkHttp）与 `ClaudeTextGenerator`（Anthropic Java SDK）及各自的配置常量；`AiModels.kt`（`AiRequest` / `AiResult` / `AiError`）。**唯一允许联网的包** |
| `ui/AppRoot.kt` `ui/SessionStores.kt` | 锁闸门与每次解锁独立的 ViewModelStore |
| `ui/MainNavigation.kt` `ui/FunBottomBar.kt` | 路由与底栏（首页 / 战绩 / 中央打卡 / AI 锐评 / 设置） |
| `ui/theme/` | `FunColors`、`FunShapes`、Material3 主题（浅色奶油 / 深色暗紫） |
| `ui/common/` | `Fun*` 设计系统组件：`FunSurface`、`FunCard`、`ChunkyButton`、`Chips`、`FunDialog`、`FunSnackbar`、`FunTextField`、`Headers`、`EntryCard` 等 |
| `ui/lock/ home/ add/ history/ ai/ backup/ settings/` | 各屏幕及其 ViewModel |

测试在 `app/src/test/java/...`，目录结构与主代码镜像。

## 硬性规则

**文案**
- 所有用户可见字符串都放进 `res/values/strings.xml`（中文，默认）**和** `res/values-en/strings.xml`（英文），key 完全一致（包括 `plurals`、`string-array`）。不要在 Kotlin 里写死用户可见文字；纯装饰 emoji 用 `EmojiText`。
- 同步检查：`diff <(grep -o 'name="[^"]*"' app/src/main/res/values/strings.xml | sort) <(grep -o 'name="[^"]*"' app/src/main/res/values-en/strings.xml | sort)` 应无输出。
- `app_name` 必须保持「Daily Calc」（伪装）。

**隐私与安全**
- `ai/` 之外不得出现任何网络调用；不要新增权限、遥测、崩溃上报、第三方 SDK。`INTERNET` 权限只为 AI 锐评存在。
- 没有用户的显式点击就不能发请求：预览只读本地，只有 `AiViewModel.analyze()` 这条路径才会真正发送。
- 不记录日志、不持久化日记内容或 API Key 的明文：不要写 `Log.*` / `println`，异常信息里不要带日记内容或 Key，API Key 只允许以 Keystore 加密后的形式落盘，且只放在请求头里。
- 备份文件只含战绩；永远不要把 PIN 哈希 / 盐、API Key、设置写进去。
- AI 服务商的模型和推理强度固定在 `AiProvider.spec`，这是产品决定：不要加用户可选的模型 / 参数界面，也不要把模型 id 写到别处。
- 持久化键不能改名：`AiProvider.id`（`deepseek` / `claude`）、DataStore 键 `aiProvider` 和 `api_key_<id>`、Keystore 别名 `boysdairy_claude_key`（最初为 Claude 创建，现在所有服务商共用）。改了会让用户已存的选择和 Key 失效。
- 每个服务商实现都要：Key 只放请求头；不跟随重定向；不打印请求 / 响应体；错误映射到 `AiError`；`previewPayload()` 展示的两段提示词必须与 `analyze()` 实际发送的一致。新增服务商的步骤见 [docs/DEVELOPMENT.md](docs/DEVELOPMENT.md)。
- 保留 `FLAG_SECURE` 仅 release 生效的逻辑（`BuildConfig.SECURE_SCREEN`：release = true，debug = false）。不要在 release 里关掉它，也不要让 debug 的放宽泄漏到 release。
- 保持 `allowBackup="false"` 与 `data_extraction_rules.xml` 排除所有域。
- PIN 比较用恒定时间（`MessageDigest.isEqual`）；不要降低 `PinAttemptLimiter` 的限制或把锁定状态改回只存内存。

**架构**
- 依赖方向：`ui → (security, ai, data/backup) → data → domain`，`domain` 不依赖任何其他层。
- 解锁后的界面里创建的 ViewModel 必须取自会话 store（在 `MainNavigation` 下用 `viewModel(factory = ...)`），这样上锁时会被清空。需要跨越上锁存活的东西（系统文件选择器 launcher、`BackupViewModel`）放在 `AppRoot` 的锁闸门之上，别随手加新的。
- 数据库结构变更：升 `@Database` 的 `version`，写 `Migration`，提交新生成的 `app/schemas/.../<N>.json`；不要用 `fallbackToDestructiveMigration`（会清空用户日记）。
- 改了备份格式要升 `BackupCodec.FORMAT_VERSION` 并保持能读旧版本。

**测试**
- 为 `domain` / `data` / `security` / `ai` 的逻辑新增或保留单元测试（JVM，JUnit4）。需要时间或存储就像现有代码那样注入（`now` / `clock` 参数、`PinLockoutStore`、`keyProvider` 等），不要在测试里依赖真实时钟或网络；网络行为用本机 `MockWebServer` 验证。
- 单元测试里用真正的 `org.json`（`android.jar` 里的是会抛异常的桩），已在 `libs.versions.toml` 里配置。

**设计系统**
- 新界面优先用 `ui/common/` 的 `Fun*` 组件和 `FunTheme.colors` 色板，不要硬编码颜色。可读性规则：粉 / 柠檬 / 薄荷 / 单人蓝 / 危险红底上的字用 `onAccent`，葡萄紫底上用 `onGrape`，其余用 `textPrimary`（目标对比度 ≥ 4.5:1）。
- 浅色和深色主题都要看一遍。

## 文案基调

- 幽默、有点贱但不冒犯：自嘲、玩梗、夸张，不羞辱用户或伴侣，不评价身体，不低俗露骨（用「发射」「战绩」「贤者时刻」这类委婉说法）。
- 可以大方用 emoji；英文版保持同样的语气，不是机械直译。
- 涉及健康的玩笑要带上「非医嘱」之类的免责，严肃场景（报错、隐私、覆盖导入确认）先把话说清楚再玩梗。
- 数量词文案用 `plurals`；中文只有 `other`。

## 在模拟器上验证

```bash
scripts/emulator.sh --install     # 启动 AVD（窗口停靠在屏幕右侧），等待开机，安装并打开 debug 版
scripts/emulator.sh --headless    # 无窗口；--no-position 不改窗口位置；--avd NAME 换 AVD
~/Library/Android/sdk/platform-tools/adb exec-out screencap -p > /tmp/shot.png   # 截图
```

- `adb screencap` 只在 debug 构建上有效；release 构建开了 `FLAG_SECURE`，截出来是黑屏。验证用 debug 版。
- 已有模拟器在运行时脚本会复用它。UI 改动请至少看：锁屏 → 解锁 → 首页 → 打卡 → 战绩 → AI 锐评 → 设置，并切一次深色主题和英文。

## 提交约定

- 只在用户要求时才提交 / 推送，不要自行切分支。
- 提交信息：英文祈使句主题行（如 `Add backup merge mode`，建议 ≤ 72 字符），正文可选，说明「为什么」。由 Agent 完成的提交末尾保留 `Co-Authored-By:` 署名行。
- 不要提交 `local.properties`、APK / AAB、keystore（`*.jks` `*.keystore`）、`.env`——它们已在 `.gitignore` 里。
- 改动涉及隐私 / 联网 / 备份格式时，同步更新 `docs/PRIVACY.md` 和 `README.md` 的隐私一览；用户可见的改动写进 `CHANGELOG.md` 的 `[Unreleased]`。
- PR 按 `.github/pull_request_template.md` 填写（单元测试、模拟器验证、是否涉及隐私数据或网络）。
