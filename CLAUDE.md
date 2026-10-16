@AGENTS.md

# Claude Code 补充说明

## 协作方式（本仓库惯例）

- **主 Agent 负责统筹与验收，实现交给子 Agent**：主 Agent 拆任务、写清楚简报（目标、已排除的做法、相关文件），子 Agent 写代码；主 Agent 审 diff、自己编译、跑测试、在模拟器上验证，再交付。
- 子 Agent 不提交、不切分支，只改简报里列出的文件。
- 同一个检出目录里同一时间只有一个 Agent 跑 Gradle；有别的 Agent 在构建时，其余 Agent 只读代码或写文档，不要启动 Gradle。需要并行写代码时用 `isolation: "worktree"`（工作树放在 `.claude/worktrees/`，已被 git 忽略）。
- 不要为总结或汇报新建 `.md` 文件，结果直接写在回复里；`docs/` 下的文档只在行为真的变化时更新。

## AI 服务商相关

- 动 `ai/` 之前先读对应服务商的官方文档。Claude 走官方 Anthropic Java SDK：**不要凭记忆猜 SDK 的类名和方法**，以文档和编译结果为准，涉及 Claude API 的任务可以用 `claude-api` skill。DeepSeek 没有用 SDK，是 `OkHttp` + `org.json` 直接调 OpenAI 兼容接口，同样以其官方 API 文档（api-docs.deepseek.com）为准。
- 模型 id 和推理强度只写在 `AiProvider.spec`（`ai/AiProvider.kt`）；`ClaudeConfig` / `DeepSeekConfig` 只放回退 beta 头、`FALLBACK_MODELS`、max tokens、超时、重试、URL 这类调参常量。用户没有模型选项，别加。换模型的步骤见 [docs/DEVELOPMENT.md](docs/DEVELOPMENT.md)。
- 改完 AI 请求的构造方式，要跑通 `ai/` 下的单元测试：`DeepSeekTextGeneratorTest`、`ClaudeTextGeneratorHttpTest` 用本机 `MockWebServer` 校验实际发出的请求（路径、头、请求体）和各类响应，`ClaudeParamsTest` 校验 Claude 的参数规则，都不会联网。
- AI 相关的任何改动都要对照 [docs/PRIVACY.md](docs/PRIVACY.md)：发了什么、发给谁、是不是用户显式触发的、预览是否仍与实际发送一致。
