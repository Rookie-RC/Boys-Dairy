# 变更记录

格式参考 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.1.0/)，版本号遵循[语义化版本](https://semver.org/lang/zh-CN/)。

## [Unreleased]

### Added

- **修改战绩**：在战绩列表或日历的当天面板里点一下卡片，就能改这条记录的全部字段。没动时间就原样保留原来的时间戳（精确到毫秒），只改了首尾空白的笔记也原样保留，所以修改后备份合并不会把它当成新的一条。
- **在这天补一条**：日历当天面板里新增按钮，打开打卡表单并把日期预填为那天（时间取现在的时分，不会落到未来）。
- **完整战报**（首页「查看完整战报 📊」）：总览（总次数、单人 / 双人占比、平均时长与愉悦度、平均 / 最长间隔、当前 / 历史最长连续）、近 12 周堆叠柱状图、星期与时段分布、事前 / 事后心情排行，以及 14 个成就的成就墙（已解锁 / 进度）。全部在本机计算，图表自己画，没有新增依赖。
- **成就解锁提示**：保存新战绩或修改后，如果这次保存解锁了成就，保存提示下面会多一行「🏆 叮！解锁成就：……」，并停留得更久。
- **清空所有战绩**（设置 → 数据）：先确认，再输入当前 PIN（输错计入锁定次数）；只删战绩，PIN、设置和 API Key 不受影响。
- **战绩列表**：按单人 / 双人筛选（可与笔记搜索叠加），并按月分组显示「2026 年 10 月 · 8 次」，月份标题滚动时吸顶。
- **月历**：左右滑动翻月；一天有多条时显示数量角标；不能翻到未来月份；不在本月时出现「回到本月」；每个日期格有完整的无障碍描述（日期 + 次数）。
- **底栏**：中央「打卡」按钮下面的文字也能点了（读屏仍只读到一个按钮）。
- **锁屏键盘触感**：按键和输错 PIN 时有轻微的触感反馈（跟随系统的触摸反馈开关，不需要震动权限）。
- **打卡表单**：时长两侧加了「−」「＋」按钮，每次 1 分钟；新增时类型和时长默认沿用最近一条；有未保存的改动时，返回或关闭会先确认；笔记上限 1000 字符，快到上限时显示计数。
- **AI 锐评**（可选）：新增「AI 锐评」tab。用户自备 API Key，温柔 / 狠狠两档语气；回复附实际应答的模型徽章。
- **多个 AI 服务商**：DeepSeek（默认）和 Claude 二选一，可在设置的 AI 卡片或 AI 页的「谁来锐评」里切换；只需填所选服务商的 Key，另一家不会收到任何数据。
  - **模型固定，没有模型选项**：DeepSeek 用 `deepseek-v4-flash`、Claude 用 `claude-haiku-5-5`，推理强度都是 high，模型、强度和提示词都固定在代码里（`AiProvider.spec`）。
  - DeepSeek 直接用 OkHttp 调 OpenAI 兼容的 `chat/completions`（开启思考、忽略 `reasoning_content`、不重试、不跟随重定向）；Claude 走官方 Anthropic Java SDK。
  - Claude 的服务器端拒答回退只发给确认支持它的模型（`ClaudeConfig.FALLBACK_MODELS`），因为 Haiku 5.5 会以 HTTP 400 拒绝它。
- **更细的 AI 错误提示**：新增「余额不足」（`INSUFFICIENT_BALANCE`）和「请求被退回」（`BAD_REQUEST`），报错文案会点出具体是哪家服务商；另有 Key 无效、限流、过载、网络、拒答、服务器错误。
- **发送前预览**：展开即可原样看到本次发出的**全部提示词**——固定的系统指令和你的战绩摘要；笔记原文默认不发送，需手动打开开关。
- **API Key 加密保存**：每个服务商一个槽（`api_key_deepseek`、`api_key_claude`），用 Android Keystore 里的 AES-256/GCM 密钥加密，存放在独立的 DataStore 文件 `secure` 中。
- **统计**（`domain/Stats.kt`）：近 7 / 30 天次数、连续天数、最长间隔、平均间隔、平均时长与愉悦度、心情分布、最忙的星期几和时段；首页和 AI 锐评共用。
- **本地 JSON 备份**：通过系统文件选择器导出 / 导入，导入可选「合并」（跳过重复）或「覆盖」（二次确认，单事务）；只含战绩，不含 PIN、API Key 和设置。
- **首页**：七档「冷却」文案（按日历日计算）、连续天数提醒、近 7 / 30 天与平均愉悦度、每日养生贴士、点日期弹出当天战绩的底部面板。
- **打卡 / 战绩**：时长和愉悦度的吐槽式标签、随机的保存 / 删除提示语、删除后可撤销。
- **底栏**：首页 / 战绩 / 打卡（中央凸起按钮）/ AI 锐评 / 设置。
- **主题化单色图标**：Android 13+ 的主题图标（`<monochrome>` 层）。
- **设计系统**（`ui/common/`）：`FunSurface`、`FunCard`、`ChunkyButton`、`Chips`、`FunDialog`、`FunSnackbar`、`FunTextField` 等。
- **开发工具**：`scripts/setup-macos.sh`（用户级安装 JDK 17 + Android SDK + 模拟器）、`scripts/emulator.sh`（启动模拟器，窗口停靠屏幕右侧，可选安装并打开应用）、GitHub Actions CI（单元测试 + debug 构建 + lint）、PR 模板、`.editorconfig`、`.gitattributes`。
- **文档**：重写 `README.md`；新增 `AGENTS.md`、`CLAUDE.md`、`docs/ARCHITECTURE.md`、`docs/PRIVACY.md`、`docs/DEVELOPMENT.md`、本文件。
- 单元测试增至 368 个（新增：完整战报的统计、成就与新解锁判定、修改 / 补记的表单规则、列表筛选与分组、仓库的 get / update / clearAll）；此前为 287 个，覆盖锁定持久化、统计、备份编码 / 合并 / 引擎、提示词、AI 服务与服务商选择、Key 加密与迁移、两家服务商的请求 / 响应 / 错误映射（用本机 `MockWebServer` 校验实际发出的请求）。

### Changed

- 锁屏输错 PIN 的抖动不会在旋转屏幕或重新上锁时多播一次。
- **界面整体重做**：新粗野主义 + 搞怪文案；浅色（奶油）/ 深色（暗紫）两套主题，未启用动态取色；设置页按外观 / 安全 / 数据 / AI / 关于分组。所有用户可见文案仍中英文 key 对齐。
- **界面打磨**：底栏的间距调整；滑块未激活轨道加深，在浅色和深色主题下都清晰可见（对比度 ≥ 3:1）。
- **PIN 锁定改为持久化**：失败次数和锁定截止时间写入 DataStore，杀进程、重启不再重置 5 次 / 30 秒的限制；识别系统时间被往回拨，此时从现在起重新锁满 30 秒。（原先只保存在内存中。）
- **上锁即清空会话**：每次解锁使用独立的 ViewModelStore，上锁时销毁，未保存的打卡表单、屏幕上的 AI 回复和进行中的 AI 请求都不会留到下次解锁。
- **系统文件选择器的 launcher 注册在锁闸门之上**：选择器会让应用进入后台并上锁，选好的文件会等重新输入 PIN 后再继续导出 / 导入。
- 应用不再是「零网络权限」：新增 `INTERNET`，仅供 AI 锐评使用，没有其他联网代码。
- **API Key 存储**：从单个 Key 改为每个服务商一个槽；此前版本留下的 `claudeApiKeyBlob` 会在首次访问时原样迁移到 Claude 槽。所有槽共用同一个 Keystore 密钥（别名 `boysdairy_claude_key`，为兼容保留）。
- `.gitignore` 补充 APK / AAB / keystore、`.env`、`local.properties`、`.vscode/`、`.claude/worktrees/`。

### Removed

- `tools/device-preview/`（把模拟器画面镜像到浏览器的 Node 小工具）及配套的 `.claude/launch.json`，改用 `scripts/emulator.sh` 直接显示模拟器窗口。
- 「完全离线、不含任何联网代码」这一承诺（见上，改为：只有 AI 锐评会联网，且仅在用户点按钮时）。

### Security

- PIN 错误锁定持久化，并带系统时间回拨保护（见 Changed）。
- API Key 仅以 Keystore 加密后的密文落盘，只放在请求头里，不进请求体、不写日志；`AiRequest.toString()` 不打印 Key 和提示词。
- AI 请求只在用户点击按钮时发送，且只发给所选的那一家服务商；笔记默认不发；预览展示全部提示词；回复只留在内存里。DeepSeek 路径不重试、不跟随重定向。
- 备份文件不含 PIN、API Key、设置；导入前做严格校验并限制文件大小（20 MiB），不合法的文件整体拒绝。
- 云备份与设备迁移依旧关闭（`allowBackup="false"` + `data_extraction_rules.xml`）；`FLAG_SECURE` 仍仅在 release 构建开启。

## [0.1.0] - 2026-10-16

用 Kotlin + Jetpack Compose 重写为原生 Android 应用，取代此前的 React 网页版。

### Added

- 应用伪装：启动器名称「Daily Calc」+ 计算器图标。
- 4 位 PIN 锁屏：首次启动设置并确认；以 16 字节随机盐和 `SHA-256(盐 + PIN)` 保存，校验用恒定时间比较；连续输错 5 次锁定 30 秒（仅内存）；应用进入后台（`ON_STOP`）自动上锁，锁定期间不组合主界面。
- 首页：距上次记录的天数（按日历日）和周一起始的月历，单人 / 伴侣用不同颜色标记。
- 新增记录：时间（不能选未来）、类型、时长、事前 / 事后心情、愉悦度、笔记。
- 历史：按笔记搜索，二次确认后删除。
- 设置：修改 PIN、主题（跟随系统 / 浅色 / 深色）、语言（跟随系统 / 中文 / English，用 AppCompat 的 per-app locale）。
- Room（导出 schema）+ DataStore 存储；约 30 个 JVM 单元测试。
- `tools/device-preview/`：把模拟器画面镜像到浏览器的小工具（后续版本已移除）。

### Removed

- React / Vite 网页版及其 GitHub Pages 部署流程。

### Security

- 无 `INTERNET` 权限；`allowBackup="false"` 并通过 `data_extraction_rules.xml` 排除所有数据域。
- Release 构建开启 `FLAG_SECURE`（禁止截屏 / 录屏，最近任务不显示内容）；debug 构建关闭以便调试。
