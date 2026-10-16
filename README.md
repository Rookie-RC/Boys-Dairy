# Boys Dairy（Daily Calc）

一款**完全离线**的原生 Android 私人日记应用，使用 Kotlin 与 Jetpack Compose 编写。应用在桌面上显示为「Daily Calc」（伪装名称），打开后需输入 4 位密码才能进入。所有数据只保存在本机，应用不申请任何网络权限。

> 本仓库此前是一个 React Web 应用，现已由原生 Android 应用完全取代。

## 功能

- **首页**：显示距离上次记录已过去的天数（按日历日计算，而非 24 小时取整）以及上次记录的日期；月历视图可翻月，周一为每周第一天，有记录的日期显示彩色圆点（含伴侣记录的日期为红色，否则为蓝色），今天会高亮。
- **新增记录**：时间（默认现在，不允许选择未来）、类型（单人 / 伴侣）、持续时间（1–120 分钟）、事前情绪、事后情绪、愉悦度（1–10）、笔记。
- **历史**：按时间倒序列出所有记录，支持按笔记内容搜索（不区分大小写），每条记录可删除（需二次确认）。
- **设置**：修改密码、主题（跟随系统 / 浅色 / 深色）、语言（跟随系统 / 中文 / English）、版本号。

## 隐私与安全设计

- **纯本地存储**：数据保存在应用私有目录的 Room（SQLite）数据库中，应用不包含任何联网代码，也没有 `INTERNET` 权限。
- **禁止备份与迁移**：`allowBackup="false"`，并通过 `data_extraction_rules.xml` 将所有数据域从云备份和设备间迁移中排除。
- **防截屏、隐藏最近任务预览**：Release 版主界面窗口设置了 `FLAG_SECURE`，无法截屏或录屏，最近任务列表中也不会显示应用内容。Debug 版关闭此项，方便在模拟器上镜像画面调试（见 `tools/device-preview/`）。
- **4 位密码**：首次启动设置并确认密码。密码不会明文保存，而是保存 16 字节随机盐（Base64）和 `SHA-256(盐 + 密码)` 的十六进制摘要（DataStore），校验时使用 `MessageDigest.isEqual` 做恒定时间比较。
- **自动上锁**：应用进入后台（进程生命周期 `ON_STOP`）立即上锁；锁定期间只会组合锁屏界面，主界面（导航、页面、列表）根本不在组合树中。
- **防暴力破解**：连续输错 5 次后锁定 30 秒，并显示倒计时（仅保存在内存中）。
- **应用伪装**：应用名称为「Daily Calc」，图标为计算器。

> 说明：4 位数字密码的熵很低，设计目标是防止旁人随手翻看，而不是抵御获取了设备存储内容的专业攻击者。

## 如何构建

环境要求：

- JDK 17
- Android SDK 35（`platforms;android-35` 与对应 build-tools）

在仓库根目录执行：

```bash
./gradlew assembleDebug
```

生成的 APK 位于：

```
app/build/outputs/apk/debug/app-debug.apk
```

运行单元测试：

```bash
./gradlew testDebugUnitTest
```

首次构建前请在仓库根目录创建 `local.properties`，写入 `sdk.dir=<你的 Android SDK 路径>`（或设置 `ANDROID_HOME` 环境变量）。该文件已被 `.gitignore` 忽略。

主要技术栈：Kotlin 2.1.0、AGP 8.7.3、Compose BOM 2024.12.01（Material 3，未启用动态取色）、Navigation Compose、Room 2.6.1（KSP）、DataStore Preferences。最低支持 Android 8.0（API 26）。

## 项目结构

```
.
├── build.gradle.kts / settings.gradle.kts / gradle/libs.versions.toml   # Gradle 根配置与版本目录
└── app/
    ├── schemas/                      # Room 导出的数据库 schema
    └── src/
        ├── main/
        │   ├── AndroidManifest.xml
        │   ├── res/                  # 字符串（values 为中文，values-en 为英文）、主题、图标、xml 配置
        │   └── java/com/rookierc/boysdairy/
        │       ├── BoysDairyApp.kt   # Application + 手动依赖注入容器 + 进程生命周期上锁
        │       ├── MainActivity.kt   # AppCompatActivity，FLAG_SECURE，edge-to-edge
        │       ├── data/             # Room 实体 / DAO / 数据库、仓库、DataStore 设置
        │       ├── domain/           # 纯 Kotlin 领域模型与日期逻辑（距今天数、月历网格、分组、搜索）
        │       ├── security/         # 密码哈希、尝试次数限制、锁定状态 LockManager
        │       └── ui/
        │           ├── AppRoot.kt        # 锁屏闸门（未解锁只显示锁屏）与主题
        │           ├── MainNavigation.kt # 底部导航与路由
        │           ├── theme/ common/    # Material3 主题与通用组件
        │           ├── lock/ home/ add/ history/ settings/
        └── test/                     # JUnit4 单元测试（密码哈希、日期逻辑、月历网格等）
```

## 语言切换

应用内语言通过 `AppCompatDelegate.setApplicationLocales()` 实现（Android 13+ 与系统的「应用语言」设置同步，旧版本由 AppCompat 自动保存），不保存在 DataStore 中。
