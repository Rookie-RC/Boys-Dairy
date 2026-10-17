# Boys Dairy（Daily Calc）

桌面上，它是一个叫「Daily Calc」、图标是计算器的普通工具；点开之后，它要你先报 4 位暗号，因为里面住着一本**私人小本本**：记录每一次「个人修行」的时间、心情和战绩，附带日历、统计和一点毒舌点评。数据只躺在你自己的手机里；整个 App 唯一会联网的地方是可选的 **AI 锐评**，你不点按钮，它一个字节都不会发。

原生 Android 应用（Kotlin + Jetpack Compose）。本仓库此前是一个 React 网页应用，现已被完全取代。

## 功能

- **男生版 / 女生版**：同一个应用，首次解锁后问一次你用哪一版（设置 →「外观」里随时可改）。两版布局、配色、功能和数据完全一样，只换界面文字和一部分 emoji（比如男生版「发射」「贤者时刻」，女生版「放飞」「余韵时刻」）；锁屏始终是中性的伪装文案。
- **伪装**：启动器名称「Daily Calc」+ 计算器图标；锁屏副标题每次随机一句「这只是个计算器」。
- **暗号锁**：4 位 PIN（加盐 SHA-256），首次启动设置并确认；切到后台立即上锁。连续输错 5 次关小黑屋 30 秒，锁定状态持久化保存（杀进程、重启都不会重置），并能识别系统时间被往回拨。
- **首页**：距上次记录的天数（按日历日计算，分 7 档文案）、连续天数提醒、近 7 / 30 天次数与平均愉悦度、每日养生贴士、月历（周一为每周第一天，单人 / 双人用不同颜色标记，左右滑动翻月，一天多条显示角标，点日期查看当天战绩、修改或在这天补一条）。
- **打卡**（底栏中央按钮）：时间（不能选未来）、类型（单人副本 / 双人成行）、时长 1–120 分钟（滑块 + 「−」「＋」微调）、事前 / 事后心情、愉悦度 1–10、笔记（上限 1000 字符）；时长和愉悦度会得到一句吐槽式点评；类型和时长默认沿用上一条；没保存就想走会先确认。
- **战绩**：按时间倒序、按月分组列出所有记录，可按笔记搜索、按单人 / 双人筛选；点卡片即可修改（改完、删完都能撤销）；删除需二次确认，删完还有「撤销」。
- **完整战报**：首页一键进入——可选时间范围的总览、本月 vs 上月、近 12 周柱状图（带平均愉悦度折线）、星期 / 时段分布、心情排行和成就墙，全部在本机计算；保存时解锁新成就会顺带提示。
- **AI 锐评**（可选）：自备 API Key，在设置的 AI 卡片或 AI 页的「谁来锐评」里选服务商——DeepSeek（默认）或 Claude——只需填所选那家的 Key；模型、推理强度和提示词都固定在代码里，没有模型选项。温柔 / 狠狠两档语气，结果可一键复制（剪贴板标记为敏感）。发给 AI 的是统计摘要和最近的精简记录，笔记原文默认不发（需手动打开）；点按钮前可展开预览，原样看到会发出的全部提示词（固定指令 + 数据摘要）。
- **备份**：首页会在该备份时提醒你（可暂缓）；把所有战绩导出为 JSON 文件（系统文件选择器，自己决定存哪），导入时可选「合并」或「覆盖」。备份只含战绩，不含 PIN、API Key 和设置。
- **设置**：主题（跟随系统 / 白天 / 夜猫子）、语言（跟随系统 / 中文 / English）、界面版本（男生版 / 女生版）、改暗号、立刻上锁、导出 / 导入（显示上次导出时间）、清空所有战绩（需再输一次 PIN）、AI 服务商与对应的 API Key、版本号。
- **界面**：新粗野主义 + 搞怪文案；浅色奶油 / 深色暗紫两套主题；文案中英双语，emoji 随处可见。

## 隐私一览

- 日记保存在应用私有目录的 Room（SQLite）数据库里，不上传、不进云备份（`allowBackup="false"`，数据提取规则排除所有域）。
- `INTERNET` 权限只为 AI 锐评而存在，应用里没有其他联网代码；只有你点下锐评按钮，数据才会发给你选的那一家（`api.deepseek.com` 或 `api.anthropic.com`），另一家什么也收不到。
- API Key（每家服务商一把）用 Android Keystore 里的 AES-256/GCM 密钥加密后保存；PIN 只保存加盐哈希。
- Release 版开启 `FLAG_SECURE`：不能截屏 / 录屏，最近任务里不显示内容。
- 男生版 / 女生版只是本机的界面设置：不进备份，也从不发给 AI（两版发给 AI 的提示词完全相同，且不涉及性别）。
- 4 位数字 PIN 的熵很低，它防的是旁人随手翻看，不是专业取证。

完整说明（存了什么、存在哪、哪些会离开设备、已知局限）见 [docs/PRIVACY.md](docs/PRIVACY.md)。

## 快速开始

需要 JDK 17 和 Android SDK 35。macOS 上可以一键装好（用户级安装，不需要 sudo 和 Homebrew）：

```bash
scripts/setup-macos.sh --with-emulator --accept-licenses   # 一次性：JDK 17 + Android SDK + 模拟器（会代你接受 SDK 许可）
./gradlew testDebugUnitTest                                 # 跑单元测试
./gradlew assembleDebug                                     # 构建 → app/build/outputs/apk/debug/app-debug.apk
scripts/emulator.sh --install                               # 启动模拟器，安装并打开应用
```

其他平台、`local.properties`、调试技巧、CI 与发布构建见 [docs/DEVELOPMENT.md](docs/DEVELOPMENT.md)。

技术栈：Kotlin 2.1.0、AGP 8.7.3、Gradle 8.11.1、Compose BOM 2024.12.01（Material 3，未启用动态取色）、Navigation Compose、Room 2.6.1（KSP）、DataStore Preferences。最低支持 Android 8.0（API 26），目标 API 35。AI：Claude 走官方 Anthropic Java SDK，DeepSeek 直接用 OkHttp 调它的 OpenAI 兼容接口；依赖版本见 `gradle/libs.versions.toml`。

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
        │       ├── domain/          # 纯 Kotlin：Entry、日期逻辑、Stats
        │       ├── data/            # Room 实体 / DAO / 仓库、DataStore 设置
        │       │   └── backup/      # JSON 导出 / 导入（编码、合并、引擎、SAF 管理器）
        │       ├── security/        # PinHasher、PinAttemptLimiter、LockManager、持久化锁定记录
        │       ├── ai/              # AI 锐评：提示词、服务编排、AiProvider（固定模型）、各服务商实现、Key 加密存储
        │       └── ui/
        │           ├── AppRoot.kt  SessionStores.kt  MainNavigation.kt  FunBottomBar.kt
        │           ├── theme/       # FunColors / FunShapes / Material3 主题
        │           ├── common/      # Fun* 设计系统组件
        │           └── lock/ home/ add/ history/ insights/ ai/ backup/ settings/
        └── test/                    # JVM 单元测试（约 420 个）
```

## 文档

| 文档 | 内容 |
| --- | --- |
| [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) | 分层、依赖容器、锁与会话生命周期、导航、数据模型、备份格式、AI 请求流程、设计系统 |
| [docs/PRIVACY.md](docs/PRIVACY.md) | 存了什么、存在哪、什么会离开设备、4 位 PIN 的诚实局限 |
| [docs/DEVELOPMENT.md](docs/DEVELOPMENT.md) | 环境搭建、命令、模拟器、调试、CI、加文案、换 AI 模型、发布构建 |
| [AGENTS.md](AGENTS.md) | 给编码 Agent 的项目指南（命令、代码地图、硬性规则） |
| [CLAUDE.md](CLAUDE.md) | Claude Code 专用补充说明 |
| [CHANGELOG.md](CHANGELOG.md) | 版本变更记录 |
