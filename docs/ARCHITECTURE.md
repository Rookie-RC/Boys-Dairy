# 架构

单模块 `:app`，包名 `com.rookierc.boysdairy`。隐私相关的存储与外发细节只在 [PRIVACY.md](PRIVACY.md) 里写，这里不重复。

## 分层

```
+------------------------------ ui ------------------------------+
| AppRoot (lock gate) -> MainNavigation -> screens + ViewModels  |
| theme/   common/ (Fun* design system)                          |
+---------+------------------+---------------------+-------------+
          |                  |                     |
    +-----v------+     +-----v------+       +------v-------+
    | security   |     | ai         |       | data/backup  |
    | PinHasher  |     | PromptBld  |       | Codec, Merge |
    | Limiter    |     | Service    |       | Engine       |
    | LockManager|     | ApiKeyStore|       | Manager      |
    +-----+------+     | Generators |       +------+-------+
          |            +-----+------+              |
          +------------------+---------------------+
                             |
                      +------v-------+
                      | data         |  Room, DataStore, repositories
                      +------+-------+
                             |
                      +------v-------+
                      | domain       |  pure Kotlin: Entry, DateLogic, Stats
                      +--------------+
```

- 依赖只向下：`domain` 不依赖任何其他层；`data` 只依赖 `domain`；`security`、`ai`、`data/backup` 依赖 `data` / `domain`；`ui` 依赖所有。
- `ai/` 是唯一包含联网代码的包，其余各层完全离线。
- 手动依赖注入，没有 Hilt / Koin。

## 依赖容器

`BoysDairyApp.kt` 里的 `AppContainer`，每个进程一个实例，由 `BoysDairyApp.container` 持有。ViewModel 工厂通过 `CreationExtras.appContainer()` 取依赖（`viewModelFactory { initializer { ... } }`）。

| 成员 | 说明 |
| --- | --- |
| `entryRepository` | 懒加载；首次使用才打开 Room 数据库 |
| `settingsRepository` | DataStore `settings` |
| `lockManager` | 带 `PinAttemptLimiter(DataStorePinLockoutStore, System::currentTimeMillis)`，用应用级 `CoroutineScope` |
| `backupManager` | `ContentResolver` + `entryRepository` |
| `apiKeyStore` | Keystore 加密的 API Key，每个服务商一个槽 |
| `aiAnalysisService` | `entryRepository` + `settingsRepository` + `apiKeyStore` + `generators`（`DEEPSEEK → DeepSeekTextGenerator()`，`CLAUDE → ClaudeTextGenerator()`） |


## 锁与会话生命周期

`LockState`：`Loading → NeedsSetup | Locked ⇄ Unlocked`。

```
进程启动     LockManager.state = Loading
              └─ 读取持久化的锁定记录 + 是否已有 PIN → NeedsSetup（首次）或 Locked
LockScreen   setupPin(pin)（输入两次）/ unlock(pin) → Unlocked
Unlocked ──ProcessLifecycleOwner ON_STOP──► Locked
```

- `BoysDairyApp` 订阅 `ProcessLifecycleOwner`，整个应用离开前台时调用 `lockManager.lock()`（该回调自带约 700 ms 防抖，旋转屏幕不会上锁）。进程被杀后重启一律从 `Locked` 开始。
- `BoysDairyRoot`（`ui/AppRoot.kt`）是锁闸门：状态不是 `Unlocked` 时只组合 `LockScreen`，导航图、屏幕、列表根本不在组合树里。
- **会话 store**：解锁后的内容放在 `UnlockedSession` 里，它给内容提供一个 `SessionViewModelStoreOwner`。里面所有 ViewModel 都存在 `SessionStoreHolder.store`（一个 Activity 级 ViewModel 持有）。上锁 → `UnlockedSession` 离开组合 → `holder.end()` 清空 store 并换新的。所以上锁后，未保存的打卡表单、屏幕上的 AI 结果、进行中的 AI 请求都会消失；而配置变更（旋转、切语言）不会触发 `end()`。
- **要跨越上锁存活的东西放在闸门之上**：系统文件选择器 launcher（`CreateDocument` / `OpenDocument`）和 Activity 级的 `BackupViewModel`。原因：打开系统选择器会让应用进入后台、触发上锁；如果 launcher 注册在解锁后的界面里，结果回来时它已被注销。回来后 `BackupFlowHost` 只在解锁状态下组合，所以选好的文件会等用户重新输入 PIN 之后才开始导出 / 导入。导出成功后 `BackupViewModel` 把当前时间写进 `SettingsRepository.lastExportAt`（写失败不影响导出结果），首页的备份提醒（`domain/BackupReminder.kt` 的纯函数 `shouldRemindBackup`）和设置页的「上次导出」都读它。
- **指纹解锁**（可选）：`security/BiometricUnlock` 管开关（DataStore `biometricUnlock`）和 Keystore 密钥 `boysdairy_biometric_key`（`KeystoreBiometricKeys`：AES-256/GCM，`setUserAuthenticationRequired(true)` + 每次使用都要强生物识别 + `setInvalidatedByBiometricEnrollment(true)`）。锁屏（`LockViewModel`）在 `Locked` 时准备一个 `ENCRYPT_MODE` 的 Cipher 放进 `BiometricPrompt.CryptoObject`，成功后用回调里的 Cipher 做一次 `doFinal`（`verify`），通过了才调 `LockManager.unlockWithBiometric()`（只把 `Locked` 变成 `Unlocked`，不碰 `PinAttemptLimiter`）。Cipher 初始化抛 `KeyPermanentlyInvalidatedException` 等 → 自动关闭开关、删除密钥、锁屏给中性提示。什么时候自动弹框由纯状态机 `ui/lock/BiometricAutoPrompt` 决定：锁屏每「出现」一次（变成 `Locked`，或 Activity 回到 STARTED）弹一次，取消后不再自动弹，配置变更不算新的出现。设备能力到界面状态的映射是 `security/BiometricPolicy` 里的纯函数。弹框的系统 UI 在 `ui/common/BiometricPromptLauncher`。
- 输错 PIN 的计数与锁定见 `PinAttemptLimiter`：连续错 5 次 → 锁定 30 秒 → 之后重新给 5 次（不递增）。状态写进 DataStore（失败次数、截止时间、最后更新时间，均为墙钟毫秒），内存里留一份，`LockManager.lockoutRemainingMillis()` 是非挂起读取。读到「最后更新时间在未来」说明系统时间被往回拨过，此时从现在起锁满一个周期；剩余时间最多显示一个周期。改密码时先用 `verifyPin` 验证当前 PIN，同样计入次数。

## 导航

`MainNavigation`（`ui/MainNavigation.kt`），起点 `home`。

```
BoysDairyRoot
 ├─ Loading ............ 空白
 ├─ NeedsSetup / Locked  LockScreen
 └─ Unlocked
      ├─ 版本读取中 ........ 空白（不把老用户误当成新用户）
      ├─ 版本未选 .......... EditionChooserScreen（男生版 / 女生版，写入 SettingsRepository.edition）
      └─ 已选 .............. LocalEdition provides 所选版本 ⟶ MainNavigation (NavHost)
           ├─ home  history  ai  settings    底栏四个 tab（popUpTo 起点 + saveState/restoreState）
           ├─ add?entryId={entryId}&date={epochDay}
           │                                 打卡表单；全屏，无底栏。无参数 = 新增（底栏中央按钮），
           │                                 entryId = 修改那条（战绩卡片 / 日历面板里点卡片），
           │                                 date = 新增并把日期预填为那天（日历面板「在这天补一条」）
           ├─ insights                       首页「查看完整战报」；全屏，无底栏
           └─ change_pin                     设置 → 改暗号；全屏，无底栏
```

- **男生版 / 女生版**：`Edition`（`data/Edition.kt`）存在 DataStore `settings` 的 `edition`。`RootViewModel.edition` 是 `EditionState`（`Loading` / `Unset` / `Chosen`）。在设置里切换版本要先过 `FunConfirmDialog`（`ui/settings/EditionSwitch` 持有待确认的版本，确认后才写入），之后只改变 `LocalEdition` 的值，不换分支，所以导航栈和各页状态都保留。锁屏在 provider 之外，始终显示基础文案。代码里永远写基础资源 id，`ui/common/EditionResources.kt` 的同名 `stringResource` / `stringArrayResource` / `pluralStringResource` 按 `LocalEdition` 查 `GirlVariants` 换成 `<name>_girl`，没登记的就两版共用；两版不同的装饰 emoji 集中在 `EditionEmoji`。`EditionResourcesGuardTest` 扫源码，防止有人绕过它直接用 androidx 的版本或 `getString`。
- 路由字符串用 `Routes.addEntry(entryId, date)` 拼；两个参数都是可选的 `LongType`，缺省值见 `AddEntryViewModel.NO_ENTRY_ID` / `NO_EPOCH_DAY`。`AddEntryViewModel` 只从 `SavedStateHandle` 读这两个参数，从不把表单内容写进去。
- 修改模式：加载完成前不能保存；那条已被删掉 → 关闭表单并提示。没改时间就**原样保留原 timestamp（毫秒不变）**，只改了首尾空白的笔记也原样保留，这样备份合并的去重键 `(timestamp, type, durationMinutes, note)` 不会因为一次无关修改而变化；时长超过 120 的旧数据不动就原样保存。这些规则是 `ui/add/EntryFormLogic.kt` 里的纯函数，有单元测试。
- 新增模式：类型和时长默认取最近一条战绩的（用户先动过就不覆盖）。表单有改动时，返回键 / 关闭按钮先弹「放弃确认」。笔记上限 1000 字符（更长的旧笔记不会被截断，只是不能再变长）。
- 关闭表单只在它仍在栈顶时才 `popBackStack`，连点两次不会把下面的 tab 也弹掉。
- 修改模式下还能「删掉这条」。保存修改和删除后的 snackbar 带「撤销」：ViewModel 交出一个只捕获仓库和条目的 `EntryUndo`（`suspend () -> Unit`），由 `MainNavigation` 在自己的 `rememberCoroutineScope()` 里执行——表单和它的 ViewModel 那时已经销毁，而这个 scope 属于解锁会话，上锁即取消。撤销修改 = 按加载时的原样 `update` 回去（id、时间戳不变）；撤销删除 = 以新 id 重新插入。
- 表单的快捷时间（刚刚 / 30 分钟前 / 1 小时前 / 2 小时前）按「真实的现在 − N 分钟」换算成本地时间再截到分钟，跨午夜和夏令时都正确（`EntryFormLogic`）。

- `FunBottomBar`：4 个 emoji tab（首页 🏠 / 战绩 📼 / AI 锐评 🤖 / 设置 ⚙️）+ 凸起的粉色圆形打卡按钮；凸起部分算在栏自身高度内，内容不会被盖住。按钮下方的「打卡」文字也可点，但对读屏不可见（只有圆形按钮这一个「新增」控件）。
- 保存打卡后，`AddEntryViewModel.save` 用同一个「现在」在保存前后各算一次 `computeAchievements`，`newlyUnlocked` 得出这次解锁的成就，附在保存提示里（读取失败就当没有，不影响保存）。
- `AI` 页没配 Key 时有「去设置 Key」：置位 `openKeyDialogRequest` 并切到设置 tab，设置页消费这个请求并弹出 Key 对话框。
- 全局 `FunSnackbarController`（`LocalSnackbar`）在导航层，所以删除后的「撤销」提示在切 tab 后仍在。
- 备份流程的对话框由 `BackupFlowHost` 在导航层渲染。

## 数据模型

Room 数据库 `boysdairy.db`，`version = 2`，`exportSchema = true`（schema 在 `app/schemas/`，`1.json`、`2.json` 都要留着），一张表 `entries`：

| 列 | 类型 | 说明 |
| --- | --- | --- |
| `id` | INTEGER PK, autoGenerate | 行 id，不进备份 |
| `timestamp` | INTEGER | epoch 毫秒；按设备时区取日历日 |
| `type` | TEXT | `SOLO` / `PARTNER` |
| `durationMinutes` | INTEGER | 界面里 1–120 |
| `moodBefore` | TEXT | `Mood` 枚举名；界面可选 ANXIOUS / BORED / EXCITED / STRESSED / NEUTRAL |
| `moodAfter` | TEXT | `Mood` 枚举名；界面可选 RELAXED / GUILTY / EMPTY / ENERGETIC / NEUTRAL |
| `pleasure` | INTEGER | 1–10 |
| `note` | TEXT | 笔记，可为空串 |
| `keyword` | TEXT，可空 | 单人记录的「今日老师」（女生版叫「今日灵感」）关键词；版本 2 新增。去首尾空白，空串存 `NULL`，双人记录永远 `NULL`，界面限 30 字符（更长的旧值不截断） |

- **迁移**：`AppDatabase.MIGRATIONS` 注册在建库处。`1 → 2` 是 ``ALTER TABLE `entries` ADD COLUMN `keyword` TEXT``（可空、无默认值，与 `2.json` 一致），老数据的 keyword 为 `NULL`。`AppDatabaseMigrationTest` 校验迁移 SQL 并比对 `1.json` / `2.json`。不准用 `fallbackToDestructiveMigration`。
- 枚举按名字存字符串，不依赖序号；读取时遇到未知名字退回默认值（`SOLO` / `NEUTRAL`）。`EntryEntity` ⇄ `domain.Entry` 的转换在 `EntryEntity.kt`。
- `EntryRepository`：`entries`（Flow，新到旧，同一时刻按 id 新到旧）、`get(id)`、`add`、`update`（id 必须 > 0；行已不存在时什么也不做）、`delete`、`clearAll`（设置里的「清空所有战绩」）、`snapshot`（一次性读，新到旧）、`addAll`（备份合并）、`replaceAll`（`@Transaction`：先清空再插入，失败整体回滚）。
- `domain/Stats.kt` 的 `computeStats()` 一次性算出 `EntryStats`（总数 / 单人 / 双人、近 7 / 30 天、距上次天数、连续天数、最长间隔、平均间隔、平均时长 / 愉悦度、事前事后心情分布、最忙的星期几和时段）。首页和 AI 提示词共用它。「天」一律按日历日算。
- `domain/Insights.kt` 给「完整战报」页补充：`weeklyCounts`（近 N 个周一起始的周，空周也在）、`weekdayCounts` / `hourBucketCounts`（全部键都在）、`longestStreakDays`（历史最长连续）、`busiestKeys`（并列最多的都算），以及成就 `Achievement` / `computeAchievements`（每个成就有 `target`，进度封顶）/ `newlyUnlocked`。成就的名字、描述和 emoji 在 `ui/common/Labels.kt`（`Achievement.labels(edition)`），战报页和保存提示共用。战报页的时间范围是 `InsightsPeriod` + `entriesInPeriod`（「近 N 天」按日历日、含今天，与 `last30DaysCount` 同口径），只作用于总览、星期 / 时段分布和心情；近 12 周柱状图、成就墙和 `monthOverMonth`（本月 vs 上月同期，上月没有这一天时取月末）始终基于全部战绩。不改 `EntryStats`，所以 AI 提示词不受影响。
- `domain/SoloKeyword.kt`：关键词的规范化（`normalizeKeyword`）、表单里「最近用过」的 chip（`recentKeywords`，最多 5 个，忽略大小写去重、按最近使用排序）和战报的「老师排行榜」（`topKeywords`，所选范围内单人记录 Top 5，次数相同按最近使用）。
- `domain/TodayAdvice.kt`：首页「今日宜忌」。`todayAdvice(entries, now, zone)` 按顺序判定、命中即停：今天已有记录（或最近记录在未来）→ 忌；截至昨天连续 ≥ 3 天 → 忌（带天数）；能算出典型间隔（最近至多 10 个有记录的日历日，相邻间隔的中位数，偶数个取中间两个的平均四舍五入，至少 1 天；少于 3 个日子算不出）时，距上次 < 典型间隔 → 不宜，否则 → 宜；算不出时昨天有记录 → 不宜，否则 / 没有记录 → 平。文案刻意不出现倒数天数。卡片上的两条宜、两条忌和一句正经话由 `pickDistinctByDay` / `pickByDay` 按 epochDay 挑选，同一天不变。由 `HomeViewModel` 计算（跨午夜、回前台时重算），只在内存里，不进备份，也不进 AI 提示词。
- `domain/DateLogic.kt` 另有战绩列表和月历用的 `filterEntries(entries, query, type)`（笔记或关键词搜索 + 类型筛选）、`groupEntriesByMonth`（保持输入顺序）、`buildDaySummaries`（每天的类型 + 条数，月历角标用；`buildDayMarkers` 由它派生）。
- 其他存储（DataStore、Keystore）见 [PRIVACY.md](PRIVACY.md#存储位置)。语言不存 DataStore，由 AppCompat 的 per-app locale 管理。

## 备份格式

`data/backup/BackupCodec`，`formatVersion = 2`，UTF-8 JSON（缩进 2 空格），文件名建议 `boysdairy-backup-YYYYMMDD.json`，MIME `application/json`：

```json
{
  "app": "boysdairy",
  "formatVersion": 2,
  "exportedAt": 1792185600000,
  "entries": [
    {
      "timestamp": 1792180000000,
      "type": "SOLO",
      "durationMinutes": 12,
      "moodBefore": "STRESSED",
      "moodAfter": "RELAXED",
      "pleasure": 7,
      "note": "...",
      "keyword": "..."
    }
  ]
}
```

- 只有战绩：没有行 id、PIN、API Key、设置。
- 版本历史：1 没有 `keyword`；2 加上它，只在记录有关键词时才写。两个版本都能读；`keyword` 缺失或为 `null` 都表示没有，读入时和表单一样规范化（去首尾空白、空串 = 没有、双人记录丢弃、不截断）。
- 读取（`decode`）：`app` 必须是 `boysdairy`；`formatVersion` 大于当前版本 → `UNSUPPORTED_VERSION`；不是 JSON / 缺字段 / 类型不对 / 未知枚举名 / `formatVersion < 1` → `INVALID_FORMAT`；`entries` 为空 → `EMPTY`。多余的未知字段被忽略。任何一条记录不合法，整份备份都会被拒绝，不会写入部分数据。
- 数值会被夹到合法范围：`pleasure` 1–10，`durationMinutes` 1–600（比界面的 120 宽松）。文件上限 20 MiB。
- 导出：没有战绩时直接返回 `EMPTY`，不会打开（清空）目标文件；写入使用 `"wt"` 模式。
- **合并（MERGE）**：若 `(timestamp, type, durationMinutes, note)` 完全相同，视为同一条，跳过（`keyword` 和心情、愉悦度一样不参与比较，所以旧备份仍能和后来补了关键词的记录去重；跳过时以设备上那条为准，不会把文件里的关键词补上去）；文件内部重复的也只保留第一条。其余按原顺序追加，分配新 id。结果是 `Imported(added, skippedDuplicates)`。
- **覆盖（REPLACE）**：在一个 Room 事务里清空再插入；界面上要经过两层确认（选「覆盖」→ 再确认「真的要覆盖吗？」）。
- UI 状态机在 `BackupViewModel`：`Idle → ExportPicked | ChooseImportMode → (ConfirmReplace) → Working → Done`。错误统一为 `BackupError`（`IO` / `INVALID_FORMAT` / `UNSUPPORTED_VERSION` / `EMPTY`），对话框里显示本地化文案；异常信息不含日记内容。

## AI 请求流程

```
SettingsScreen / AiScreen --选择--> SettingsRepository.aiProvider (默认 DEEPSEEK)

AiScreen -> AiViewModel (会话 store) -> AiAnalysisService -> generators[provider] -> 服务商 API
                                            |
      AiPromptBuilder (纯函数)  ApiKeyStore.load(provider)  EntryRepository.snapshot()  AiProvider.spec
```

**服务商与固定参数**（`ai/AiProvider.kt`）：`AiProvider` 枚举有 `DEEPSEEK("deepseek")` 和 `CLAUDE("claude")`，`DEFAULT = DEEPSEEK`；`id` 是持久化键，发布后不能改。模型、推理强度都固定在 `AiProvider.spec`（`AiProviderSpec`：`model`、`effort`、`endpointHost`），**用户没有模型选项，只选服务商、填 Key**。当前值：

| 服务商 | 模型 | 推理强度 | 实现 | 请求 |
| --- | --- | --- | --- | --- |
| DeepSeek（默认） | `deepseek-v4-flash` | high | `DeepSeekTextGenerator`（OkHttp） | `POST https://api.deepseek.com/chat/completions`（OpenAI 兼容） |
| Claude | `claude-haiku-5-5` | high | `ClaudeTextGenerator`（官方 Anthropic Java SDK，beta messages） | SDK 默认地址 `api.anthropic.com` |

`endpointHost` 只用来在界面里告诉用户数据发往哪里；真正的请求地址在各自的实现里（`DeepSeekConfig.BASE_URL`；Claude 用 SDK 默认），改地址时两处要一起改。

**一次锐评：**

1. **选择服务商**：设置页 AI 卡片里的两张卡片，或 AI 页的「谁来锐评」快捷切换，都写同一个设置（`SettingsRepository.setAiProvider`）。`aiSelectionFlow(settings, apiKeyStore)` 把「所选服务商」和「它有没有 Key」合成 `AiSelection`，供两个页面共用。
2. **预览（不联网）**：展开「看看会发送什么」时，`AiAnalysisService.previewPayload(options)` 返回 `AiPreview(system, user)`，界面把两段都原样显示：① 固定指令（系统提示词，随语气和语言变化），② 你的战绩摘要（用户消息）。它与服务商和 Key 无关，没有战绩时返回 `null`。
3. **发送**：用户点下大按钮 → `AiViewModel.analyze(languageTag)` → `AiAnalysisService.analyze(options)`：没有战绩 → `NO_DATA`；读取所选服务商，取它的 Key，没有 → `NO_API_KEY`；否则用同一套 `AiPromptBuilder` 提示词和 `provider.spec` 组装 `AiRequest(apiKey, model, effort, system, user)`，交给 `generators[provider].generate(request)`。`AiRequest.toString()` 不会打印 Key 和提示词。
4. **提示词内容**由 `AiPromptBuilder` 决定，所有服务商一样：统计摘要 + 最近至多 `MAX_RECENT_ENTRIES`（30）条精简记录；只有 `AiOptions.includeNotes` 为真（默认关）才附上至多 `MAX_NOTES`（20）条笔记，每条压成一行并截到 `MAX_NOTE_CHARS`（200）字符。具体字段见 [PRIVACY.md](PRIVACY.md#会离开设备的东西)。
5. **结果**：`AiResult.Success(text, model)`（`model` 是实际应答的模型，界面用贴纸徽章显示）或 `AiResult.Failure(AiError)`。`AiPhase.Failed` 还记着当时问的是哪个服务商，所以即使之后切换了服务商，报错文案点的名也是对的。
6. **取消与上锁**：请求跑在 IO 调度器上且可中断；点「算了，不问了」或上锁（会话 store 被清空，`viewModelScope` 取消）都会中止进行中的请求。

**DeepSeek 实现**（`ai/DeepSeekTextGenerator.kt`）：

- 一次阻塞、非流式的 `POST`，请求头 `Authorization: Bearer <key>`；请求体：`model`、`messages`（一条 system、一条 user）、`thinking: {"type":"enabled"}`、`reasoning_effort`（`low` / `high` / `max`，DeepSeek 没有 medium，`AiEffort.MEDIUM` 按 `high` 发）、`max_tokens`（32000）、`stream: false`；不发 temperature / top_p（思考模式下无效）。
- 一个懒创建的 `OkHttpClient`：连接 15 s、读取 180 s、整次调用 200 s 超时（思考型模型可能要几分钟）；**不跟随重定向**（请求带着日记摘要和 Key，不能被转去别处）；**不重试**。
- 响应：先看 `finish_reason`（`content_filter` → `REFUSED`，`insufficient_system_resource` → `OVERLOADED`），再取 `message.content`（去空白；空 → `UNKNOWN`；因 `length` 截断但有内容仍算成功）。`reasoning_content`（思维链）从不读取、不显示。应答模型取响应里的 `model`，缺省用请求的模型。
- HTTP 状态 → `AiError`：401 / 403 → `INVALID_API_KEY`；402 → `INSUFFICIENT_BALANCE`；400 / 404 / 422 → `BAD_REQUEST`；429 → `RATE_LIMITED`；503 → `OVERLOADED`；其他 5xx → `SERVER`；`IOException` → `NETWORK`；其余 → `UNKNOWN`。

**Claude 实现**（`ai/ClaudeTextGenerator.kt`）：

- 每次调用建一个 SDK 客户端（用完关闭），一次阻塞、非流式的 `client.beta().messages().create(...)`；参数由纯函数 `buildParams` 生成：`model`、`maxTokens`（16000）、`outputConfig.effort`、`system`、一条 user 消息。不发 `thinking`（自适应思考默认开启），没有 temperature / top_p / prefill。超时 120 s，SDK 自带重试 `MAX_RETRIES = 1`。
- **服务器端拒答回退**（`fallbacks: "default"` + beta 头 `ClaudeConfig.FALLBACK_BETA`）只发给 `ClaudeConfig.FALLBACK_MODELS` 里的模型（目前只有 `claude-opus-5-5`），因为 Haiku 5.5 收到这个请求会返回 HTTP 400。所以按当前的固定模型，实际不会发送回退。
- 响应：`stop_reason == refusal` → `REFUSED`（哪怕带了文字）；拼接所有 text 块，不含 thinking 块；空 → `UNKNOWN`。
- 异常 → `AiError`：401 / 403 → `INVALID_API_KEY`；402 → `INSUFFICIENT_BALANCE`；400 / 404 / 422 → `BAD_REQUEST`；429 → `RATE_LIMITED`；`overloaded_error` 或 529 → `OVERLOADED`；其他 5xx → `SERVER`；IO 类异常 → `NETWORK`；其余 → `UNKNOWN`。

**统一的错误**：`AiError` = `NO_API_KEY`、`NO_DATA`、`INVALID_API_KEY`、`INSUFFICIENT_BALANCE`、`BAD_REQUEST`、`RATE_LIMITED`、`OVERLOADED`、`NETWORK`、`REFUSED`、`SERVER`、`UNKNOWN`，一一对应 `ai_error_*` 文案。

**API Key 存储**（`ApiKeyStore`）：每个服务商一个槽，DataStore `secure` 里的 `api_key_deepseek` / `api_key_claude`，值是 Base64(`iv ‖ 密文 ‖ tag`)；所有槽共用一把 Keystore 里的 AES-256/GCM 密钥（别名 `boysdairy_claude_key`，最初为 Claude 创建，为兼容保留）。UI 层只能问「有没有 Key」，拿不到明文。升级前留下的单个 `claudeApiKeyBlob` 在首次访问时被原样搬到 Claude 槽（Claude 槽已有 Key 则以后者为准）并删除。所选服务商本身存在 DataStore `settings` 的 `aiProvider`。

**测试缝**：`AiAnalysisService` 的 internal 构造函数接 lambda 和 `generators` 映射，`AiTextGenerator` 是接口可换假实现；`ApiKeyStore` 的 internal 构造函数接 DataStore 和 `ApiKeyCrypto`（`keyProvider` 让 JVM 测试用软件密钥代替 Keystore）；`DeepSeekTextGenerator(baseUrl, clientFactory)` 和 `ClaudeTextGenerator(useRefusalFallback, clientFactory)` 让测试把客户端指向本机 `MockWebServer`。

## 主题与设计系统

- `ui/theme/Color.kt`：`FunColors` 色板（`ink` 描边、`shadow` 硬阴影、`background` / `surface`、`pink` / `lemon` / `mint` / `grape` / `solo` / `danger` 及对应的 tint、`onAccent` / `onGrape`、`disabled`），有 `LightFunColors`（奶油底）和 `DarkFunColors`（暗紫底）两套，通过 `FunTheme.colors` 读取。`EntryType.accent()` / `tint()`：单人 = 电光蓝，双人 = 热粉。
- `ui/theme/Theme.kt`：`BoysDairyTheme(darkTheme)` 同时提供 `FunColors` 和由同一色板派生的 Material3 `ColorScheme`（不启用动态取色）；Material3 只用于行为复杂的控件（日期 / 时间选择器、底部面板、输入）。`FunShapes` 是各组件共用的圆角；`Type.kt` 把标题字重推到 Bold / ExtraBold。
- `ui/common/`：
  - `FunSurface`：基石，圆角 + 实线描边 + 无模糊的硬偏移阴影，按下时「压」到阴影上。
  - 在它之上：`FunCard`、`ChunkyButton`（及 `ChunkyButtonSlot`、`ChunkyIconButton`）、`Chips`（`EmojiChip` / `ChoiceCard` / `StickerBadge`）、`FunDialog` / `FunConfirmDialog`、`FunSnackbar`、`FunTextField`、`Headers`（`ScreenTitle` / `SectionHeader` / `FunTopBar`）、`EntryCard`。
  - 杂项：`EmojiText`、`EmptyState`、`Labels`（枚举 → 文案 / emoji）、`Formatting`、`rememberToday()`（回到前台和跨过午夜时刷新「今天」）。
- 主题模式（跟随系统 / 浅色 / 深色）存在 `SettingsRepository`，`RootViewModel` 读到之前不渲染，避免首帧闪错主题；状态栏 / 导航栏透明，由 `SystemBarsEffect` 跟随应用内主题。
- 对话框继承窗口的 `FLAG_SECURE`，所以 release 里对话框同样不能截屏。
