# 开发指南

给人和 Agent 共用的操作手册。项目规则与代码地图见 [AGENTS.md](../AGENTS.md)，设计见 [ARCHITECTURE.md](ARCHITECTURE.md)。

## 环境要求

| 项 | 版本 |
| --- | --- |
| JDK | 17（Temurin） |
| Android SDK | `platforms;android-35`、`build-tools;35.0.0`、`platform-tools`（跑模拟器还要 `emulator` 和一个 API 35 系统镜像） |
| Gradle | wrapper 8.11.1（`./gradlew` 自动下载，首次构建需要联网） |
| AGP / Kotlin | 8.7.3 / 2.1.0 |

## macOS 一键搭建

```bash
scripts/setup-macos.sh --with-emulator --accept-licenses
```

用户级安装，不需要 sudo，也不用 Homebrew（Intel Mac 上 Homebrew 没有预编译包，要从源码编译，实际不可用）。脚本幂等，已装好的步骤会跳过：

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

**其他平台**没有脚本：自己装 JDK 17 和上表的 SDK 包即可。

## local.properties

仓库根目录的 `local.properties` 只有一行，告诉 Gradle SDK 在哪（已被 `.gitignore` 忽略，不要提交）：

```properties
sdk.dir=/Users/你/Library/Android/sdk
```

也可以不建这个文件，改设 `ANDROID_HOME` 环境变量。

## 常用命令

| 目的 | 命令 |
| --- | --- |
| 单元测试 | `./gradlew testDebugUnitTest` |
| 只跑一部分 | `./gradlew testDebugUnitTest --tests 'com.rookierc.boysdairy.ai.*'` |
| 构建 debug APK | `./gradlew assembleDebug`（产物 `app/build/outputs/apk/debug/app-debug.apk`） |
| Lint | `./gradlew lintDebug`（报告 `app/build/reports/lint-results-debug.html`） |
| 装到已连接设备 / 模拟器 | `./gradlew installDebug` |
| 与 CI 完全一致 | `./gradlew --no-daemon testDebugUnitTest assembleDebug lintDebug` |

单元测试报告在 `app/build/reports/tests/testDebugUnitTest/index.html`。同一个检出目录里同一时间只跑一个 Gradle。

## 模拟器

```bash
scripts/emulator.sh --install
```

启动 AVD、等待开机、`./gradlew installDebug`、再打开应用。选项：

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
4. 代码里用 `stringResource(R.string.xxx)`；数量词用 `quantityString(R.plurals.xxx, n, n)`（`ui/common/Formatting.kt`）；数组用 `stringArrayResource`。
5. 检查两个文件 key 一致（应无输出）：

   ```bash
   diff <(grep -o 'name="[^"]*"' app/src/main/res/values/strings.xml | sort) \
        <(grep -o 'name="[^"]*"' app/src/main/res/values-en/strings.xml | sort)
   ```

6. 在模拟器里切到两种语言各看一眼，注意长英文会不会撑破布局。

## 更换 / 升级 AI 模型

用户没有模型选项，模型和推理强度固定在代码里，换模型就是改一个地方：

1. 改 `ai/AiProvider.kt` 里 `AiProvider.spec`：对应服务商的 `model`（需要时还有 `effort`）。这是唯一来源，别处不要写模型 id。
2. 先读新模型的官方文档：不同模型对 thinking、推理强度可选值、采样参数、prefill 的限制不一样。Claude 用官方 Anthropic Java SDK，**不要凭记忆猜 SDK 的 API**，以文档和编译结果为准（`ClaudeTextGenerator` 的 KDoc 记录了当前的约束）；DeepSeek 看其官方 API 文档（OpenAI 兼容接口，`reasoning_effort` 只接受 `low` / `high` / `max`，`AiEffort.MEDIUM` 会按 `high` 发送）。
3. 换 Claude 模型时看 `ClaudeConfig.FALLBACK_MODELS`：只有列在里面的模型才会带服务器端拒答回退（`fallbacks: "default"` + beta 头 `FALLBACK_BETA`），目前只有 `claude-opus-5-5`；不支持它的模型收到会返回 HTTP 400（Haiku 5.5 就是）。除非确认新模型支持，否则不要把它加进去。
4. 更新测试里写死的 id：`ai/AiProviderTest`（`deepSeekSpecIsFixed`、`claudeSpecIsFixed`）、`ai/AiAnalysisServiceTest`（断言各服务商实际用的模型），其余 `DeepSeekTextGeneratorTest`、`ClaudeTextGeneratorHttpTest`、`ClaudeParamsTest`、`ClaudeMappingTest` 里的 id 多半只是构造请求 / 响应用的样例，`grep` 一下再决定要不要改。
5. 跑 `./gradlew testDebugUnitTest --tests 'com.rookierc.boysdairy.ai.*'`：HTTP 测试用本机 `MockWebServer` 校验实际发出的路径、头和请求体，不联网。
6. 用你自己的 Key 在模拟器上真跑一次 AI 页（Key 只输入到应用里，不要写进仓库、日志或聊天）。
7. 在 `CHANGELOG.md` 的 `[Unreleased]` 记一笔；如果请求地址变了，同步 `docs/PRIVACY.md`。

`AiProviderSpec.endpointHost` 只是展示给用户看的主机名（界面和隐私文案），真正的地址在 `DeepSeekConfig.BASE_URL`（DeepSeek）或 SDK 默认值（Claude）。改地址时三处要一起改。

## 新增一个 AI 服务商

1. 在 `AiProvider` 里加一个枚举项，给一个**永不改名**的 `id`（它是持久化键：所选服务商和 `api_key_<id>` Key 槽都用它），并在 `spec` 里补上模型、推理强度、主机名。
2. 写一个 `AiTextGenerator` 实现（输入 `AiRequest`，输出 `AiResult`）：Key 只放请求头，不跟随重定向，不打印请求 / 响应体，把服务商的错误映射成 `AiError`；在 `AppContainer.aiAnalysisService` 的 `generators` 映射里注册。
3. 补 `ui/common/AiProviderUi.kt` 的 `nameRes()` / `taglineRes()` / `emoji()`，以及中英两份 `ai_provider_<id>`、`ai_provider_<id>_tagline` 文案。
4. 测试：用 `MockWebServer` 验证实际请求和各类响应、状态码映射；`AiProviderTest` 里补固定值断言。
5. 更新 `docs/PRIVACY.md` 的服务商表、`docs/ARCHITECTURE.md`、`README.md` 和 `CHANGELOG.md`。

## 数据库 schema 变更

改 `EntryEntity` 或加表：升 `AppDatabase` 的 `version`，写 `Migration` 并注册，构建后把新生成的 `app/schemas/com.rookierc.boysdairy.data.AppDatabase/<N>.json` 一并提交；不要用 `fallbackToDestructiveMigration`。若字段会进备份，同时评估是否要升 `BackupCodec.FORMAT_VERSION`。

## Release 构建

```bash
./gradlew assembleRelease     # → app/build/outputs/apk/release/app-release-unsigned.apk
```

- 仓库里**没有签名配置**，产物是未签名 APK。要装到真机需要自己签名，keystore 放在仓库之外，绝不提交（`*.jks`、`*.keystore` 已被忽略）：

  ```bash
  BT="$HOME/Library/Android/sdk/build-tools/35.0.0"
  "$BT/zipalign" -f -p 4 app/build/outputs/apk/release/app-release-unsigned.apk /tmp/aligned.apk
  "$BT/apksigner" sign --ks ~/path/to/release.jks --out /tmp/boysdairy-release.apk /tmp/aligned.apk
  ```

- Release 版 `BuildConfig.SECURE_SCREEN = true`，即开启 `FLAG_SECURE`：不能截屏、录屏，也不能用 `adb screencap` 验证界面；要看界面请用 debug 版。
- **R8 / 代码压缩尚未开启**（`isMinifyEnabled = false`，`proguard-rules.pro` 只有占位注释），APK 偏大、类名未混淆。开启前要按 Anthropic SDK（及其依赖）的文档补 keep 规则，并在 release 构建上用两家服务商各完整跑一遍 AI 流程；这一步还没有验证过。
- 版本号在 `app/build.gradle.kts` 的 `versionCode` / `versionName`（设置页显示的版本取自 `BuildConfig.VERSION_NAME`）；发版时同步更新 `CHANGELOG.md`。
