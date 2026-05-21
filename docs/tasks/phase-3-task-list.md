# Phase 3 Task List - AI 能力增强阶段

## 0. 文件用途

本文件用于拆解 Phase 3 的具体开发任务。

`docs/phase-3-ai.md` 负责说明第三阶段的总体目标、功能范围、技术范围和阶段边界。  
本文件负责说明 Phase 3 每个版本下面具体怎么拆小任务。

使用原则：

1. 每次只做一个小任务。
2. 当前正在开发的 v2.x 版本详细展开。
3. 已完成版本压缩成总结。
4. 未开始版本可以先保留大纲，进入对应版本前再继续细分。
5. 完成一个 v2.x 版本后，更新对应迭代日志。
6. 不允许跳过当前任务直接实现后续版本功能。
7. AI 输出必须遵守事实一致性原则，不得编造用户不存在的经历、技能、证书、奖项或量化指标。
8. 所有 AI 输出应尽量结构化，关键结果必须保存模型名称、Prompt 版本和生成时间。

---

## 1. Phase 3 总版本划分

| 版本 | 主题 | 状态 |
|---|---|---|
| v2.1 | AI 能力基线检查与提示词规范 | 已完成 |
| v2.2 | 岗位描述输入与结构化解析 | 已完成 |
| v2.3 | 简历与岗位 AI 匹配分析 | 已完成 |
| v2.4 | AI 简历优化建议增强 | 已完成 |
| v2.5 | AI 局部改写建议 | 已完成 |
| v2.5.5 | 产品信息架构与模块边界收敛 | 已完成 |
| v2.6 | AI 结果持久化与历史回看 | 已完成 |
| v2.7 | AI 输出评估流程 | 已完成 |
| v2.8 | Embedding / pgvector / RAG 初步增强 | 已完成 |
| v2.9 | 简历文本解析质量优化 | 已完成 |
| v2.10 | 岗位优化报告与演示闭环 | 当前版本 |

---

## 2. Phase 3 总目标

在 Phase 1 MVP 和 Phase 2 工程化基础上，增强系统的 AI 能力，使系统能够围绕“简历内容 + 岗位描述”完成更深入的分析、匹配、优化和局部改写。

Phase 3 完成后应满足：

- 用户可以提交岗位描述。
- 系统可以解析岗位描述并抽取结构化岗位要求。
- 系统可以基于简历和岗位描述生成结构化匹配反馈。
- 系统可以生成具体、有依据的简历优化建议。
- 系统可以给出局部改写建议。
- AI 输出不会编造用户不存在的经历。
- AI 输出可以持久化和回看。
- AI 输出记录模型名称和 Prompt 版本。
- 系统具备基础 AI 输出评估流程。
- 可选完成基础 Embedding / pgvector / RAG 增强。

---

## 3. Phase 3 禁止提前实现内容

在 Phase 3 中不允许提前实现：

- 全自动职位投递。
- 模型微调。
- 企业级 Prompt 实验平台。
- 大规模招聘网站爬取。
- 自动伪造简历经历。
- 自动生成不存在的证书、奖项、量化指标。
- 复杂 A/B 测试平台。
- 多租户企业后台。
- 微服务拆分。
- RabbitMQ / Nacos / Gateway。
- Kubernetes。
- 复杂监控平台。
- 生产级高可用部署。

---

# v2.1 - AI 能力基线检查与提示词规范

状态：已完成

## v2.1 总结

v2.1 已完成 AI 能力基线检查与提示词规范收口：

- 已确认当前 AI 调用入口位于 `infra/ai/`，核心为 `AiClientService`、`AiClientProperties` 和 `OpenAiCompatibleAiClientService`。
- 已确认 AI 配置通过 `application.yaml` 读取环境变量，模型名称由 `OPENAI_MODEL` 注入，不在业务代码中硬编码。
- 已确认当前唯一业务 AI 能力是“简历 AI 分析”，由 `ResumeAnalysisServiceImpl` 编排。
- 已确认 Prompt 构建和输出解析已拆分到独立 Service，Prompt 版本为 `resume_analysis_v1`。
- 已明确 `infra/ai/`、Prompt 构建、输出解析、业务 Service、Entity / Mapper 和 VO 的分层职责。
- 已更新 `docs/project-structure.md`，补充 AI 分层和后续扩展放置规则。
- 已整理 Prompt 命名、版本、输入字段、输出字段、JSON 约束和安全约束规范。
- 已整理 AI 输出解析流程，明确非 JSON、字段缺失、评分越界、多余字段和脏数据入库策略。
- 已在现有简历分析 Prompt 中补充不得代填量化指标、证书、奖项或项目结果的约束。
- 已在 AI 输出解析中限制单条列表文本长度，避免超长自由文本直接进入结构化结果。
- 已在简历 AI 分析展示和历史 AI 分析摘要中增加“AI 建议需用户确认”的提示。
- 已在 README 中补充 AI 输出限制说明。
- 已确认当前日志不完整打印简历原文，只记录必要状态、模型和脱敏错误原因。
- 已完成 v2.1 相关单元测试、前端构建和文档静态核对。
- 迭代日志已更新：`docs/iteration-log/v2.1-ai-baseline.md`。

说明：

- v2.1 不新增岗位描述解析、AI 匹配、优化建议增强或局部改写。
- v2.1 不引入复杂 Prompt 管理后台、Prompt A/B 测试平台、RAG、pgvector 或微服务拆分。
- 当前 Prompt 和解析服务仍保留在 `module/analysis` 内；后续出现跨业务重复逻辑时再小步抽象。
- 当前只保存结构化 AI 结果和错误信息，不保存原始 AI 输出。

## v2.1 小任务完成情况

| 小任务 | 状态 |
|---|---|
| v2.1.1 现有 AI 能力检查 | 已完成 |
| v2.1.2 AI 分层结构整理 | 已完成 |
| v2.1.3 Prompt 模板规范 | 已完成 |
| v2.1.4 AI 输出结构与解析规范 | 已完成 |
| v2.1.5 AI 安全规则落地 | 已完成 |
| v2.1.6 联调、审查与日志 | 已完成 |

---

# v2.2 - 岗位描述输入与结构化解析

状态：已完成

## v2.2 总结

v2.2 已完成岗位描述输入与结构化解析闭环：

- 已新增 `job_descriptions` 表迁移脚本 `V8__create_job_descriptions_table.sql`。
- 已新增 `JobDescription` 实体和 `JobDescriptionMapper`。
- 已实现 `POST /api/job-descriptions` 岗位描述提交接口。
- 已实现 `GET /api/job-descriptions` 当前用户岗位描述列表接口。
- 已实现 `GET /api/job-descriptions/{id}` 岗位描述详情接口。
- 已将 `/api/job-descriptions/**` 加入认证保护。
- 已校验标题不能为空、岗位描述原文不能为空，且限制标题和原文最大长度。
- 已保证用户只能查看和解析自己的岗位描述。
- 已创建 `job_description_parse_v1` Prompt，输入为岗位描述原文。
- 已明确 AI 输出字段：`jobTitle`、`requiredSkills`、`bonusSkills`、`experienceSignals`、`responsibilities`、`keywords`、`summary`。
- 已要求 Prompt 不编造岗位描述中不存在的职位、技能、经验、职责或招聘条件。
- 已新增岗位描述结构化解析 Service 和输出 Parser。
- 已实现 AI 调用、JSON 解析、结构化结果保存、模型名称保存和 Prompt 版本保存。
- 已实现 `POST /api/job-descriptions/{id}/parse` 岗位描述解析接口。
- 已处理 AI 调用失败和 AI 返回格式错误，失败时保存 `FAILED` 状态和错误信息，并清空结构化结果。
- 已新增前端岗位描述列表页、输入页和详情页。
- 已接入 `/job-descriptions`、`/job-descriptions/new` 和 `/job-descriptions/:id` 路由，并使用登录守卫保护。
- 已支持前端查看已提交岗位描述、提交岗位描述、触发解析、查看解析状态和结构化解析结果。
- 已展示职位名称、岗位摘要、必备技能、加分技能、经验信号、职责内容和关键词。
- 已修复岗位描述详情页首次进入不加载数据的问题。
- 已将岗位描述解析前端请求超时时间调整为 120 秒。
- 已完成后端相关测试、前端构建和手动联调，用户确认可成功解析。
- 迭代日志已更新：`docs/iteration-log/v2.2-job-description-parse.md`。

说明：

- v2.2 不做简历与岗位 AI 匹配。
- v2.2 不做岗位描述删除功能或复杂岗位管理后台。
- 当前岗位描述解析结果以 JSON 字符串保存在 `structured_content`，后续 v2.3 可直接读取。
- 当前失败状态保存错误信息，不保存原始 AI 输出。

## v2.2 小任务完成情况

| 小任务 | 状态 |
|---|---|
| v2.2.1 岗位描述表设计 | 已完成 |
| v2.2.2 岗位描述提交接口 | 已完成 |
| v2.2.3 岗位描述解析 Prompt | 已完成 |
| v2.2.4 岗位描述结构化解析服务 | 已完成 |
| v2.2.5 岗位描述解析接口 | 已完成 |
| v2.2.6 前端岗位描述输入与解析展示 | 已完成 |
| v2.2.7 v2.2 联调、审查与日志 | 已完成 |

---

# v2.3 - 简历与岗位 AI 匹配分析

状态：已完成

## v2.3 总结

v2.3 已完成简历与岗位描述的 AI 匹配分析闭环：

- 已新增 `ai_job_match_results` 表迁移脚本 `V9__create_ai_job_match_results_table.sql`。
- 已新增 `AiJobMatchResult` 实体和 `AiJobMatchResultMapper`。
- 已建立 `resume_id`、`job_description_id` 与匹配结果的关联关系，并对同一简历和同一岗位描述设置唯一约束。
- 已明确 AI 匹配结果字段：总体分数、强匹配项、弱匹配项、缺失技能、表达较弱经历、匹配依据、风险提示、模型名称、Prompt 版本和错误信息。
- 已创建 `ai_job_match_v1` Prompt，输入为简历结构化解析结果和岗位描述结构化解析结果。
- 已要求 Prompt 只基于输入内容分析，不编造用户不存在的经历、技能、项目、证书、奖项或量化指标。
- 已新增 AI 匹配 Prompt 构建、输出解析和业务编排服务。
- 已实现 AI 输出 JSON 解析、分数截断、字段兜底和失败状态保存。
- 已实现 `POST /api/resumes/{resumeId}/ai-job-matches`，用于触发当前用户简历与岗位描述的 AI 匹配。
- 已实现 `GET /api/resumes/{resumeId}/ai-job-matches`，支持查询简历下的匹配结果列表，也支持通过 `jobDescriptionId` 查询指定结果。
- 已保证用户只能匹配和查看自己的简历、岗位描述和 AI 匹配结果。
- 已新增前端 AI 匹配页面 `/ai-job-matches`，并通过登录守卫保护。
- 已支持前端选择简历和已解析岗位描述，触发 AI 匹配并展示结构化结果。
- 已展示总体分数、强匹配项、弱匹配项、缺失技能、表达较弱经历、匹配依据、风险提示、模型名称、Prompt 版本和更新时间。
- 已接入首页、岗位描述列表页和岗位描述详情页的 AI 匹配入口。
- 已将 AI 匹配请求超时时间设置为 120 秒，并将默认 `OPENAI_MAX_TOKENS` 调整为 4000。
- 已增强 OpenAI-compatible 客户端对空响应、截断响应和非 JSON 响应的错误提示。
- 已优化 AI 匹配 Prompt 输入长度，降低输出超限和非 JSON 结果概率。
- 已在历史记录中接入 AI 岗位匹配结果，最近匹配可以显示并跳转到 AI 匹配页面。
- 已修复简历删除时未清理 AI 匹配结果导致的删除失败问题。
- 已新增岗位描述删除接口和前端删除入口，删除岗位描述时同步清理关联 AI 匹配结果。
- 已完成后端相关测试、前端构建和本地联调，用户确认 AI 匹配可以成功解析并展示。
- 迭代日志已更新：`docs/iteration-log/v2.3-ai-match.md`。

说明：

- v2.3 不生成最终简历优化方案。
- v2.3 不生成局部改写内容。
- v2.3 不做向量语义匹配、RAG、pgvector 或多轮对话。
- 当前 AI 匹配结果以结构化 JSON 字符串保存，后续 v2.4 可基于成功匹配结果生成优化建议。
- 当前失败状态保存错误信息，不保存原始 AI 输出。

## v2.3 小任务完成情况

| 小任务 | 状态 |
|---|---|
| v2.3.1 AI 匹配结果表设计 | 已完成 |
| v2.3.2 AI 匹配 Prompt 设计 | 已完成 |
| v2.3.3 AI 匹配分析服务 | 已完成 |
| v2.3.4 AI 匹配触发接口 | 已完成 |
| v2.3.5 AI 匹配结果查询接口 | 已完成 |
| v2.3.6 前端 AI 匹配结果展示 | 已完成 |
| v2.3.7 v2.3 联调、审查与日志 | 已完成 |

---

# v2.4 - AI 简历优化建议增强

状态：已完成

## v2.4 总结

v2.4 已完成基于 AI 匹配结果的简历优化建议闭环：

- 已新增 `ai_resume_suggestions` 表迁移脚本 `V10__create_ai_resume_suggestions_table.sql`。
- 已新增 `AiResumeSuggestion` 实体和 `AiResumeSuggestionMapper`。
- 已建立 `resume_id`、`job_description_id`、`ai_job_match_result_id` 与优化建议结果的关联关系，并对同一 AI 匹配结果设置唯一约束。
- 已明确优化建议状态、结构化建议 JSON、模型名称、Prompt 版本、错误信息和更新时间字段。
- 已创建 `resume_suggestion_v1` Prompt，输入为简历结构化解析结果、岗位描述结构化解析结果和 AI 匹配结果。
- Prompt 要求只基于输入内容提出建议，不编造用户不存在的经历、技能、证书、奖项或量化指标。
- 已新增优化建议 Prompt 构建、输出解析和业务编排服务。
- 已实现建议类型、优先级、问题、建议内容、依据、注意事项和关联项的结构化解析与校验。
- 已实现 `POST /api/resumes/{resumeId}/ai-suggestions`，用于基于成功 AI 匹配结果触发优化建议生成。
- 已实现 `GET /api/resumes/{resumeId}/ai-suggestions`，支持列表查询，也支持通过 `jobDescriptionId` 或 `aiJobMatchResultId` 查询指定建议结果。
- 已保证用户只能基于自己的简历、岗位描述和 AI 匹配结果生成或查看优化建议。
- 已明确重复生成策略：同一 `ai_job_match_result_id` 再次生成时覆盖旧建议结果。
- AI 调用失败或输出解析失败时保存 `FAILED` 状态和错误信息，不保存脏建议。
- 已新增前端优化建议 API 和类型定义。
- 已在 AI 匹配页面增加优化建议生成入口和结果展示区域。
- 前端已展示建议状态、建议数量、建议类型、优先级、目标简历部分、问题、建议内容、依据、注意事项、模型名称和生成时间。
- 前端已按高优先级、技能缺口、经历表达、优势与综合建议分组展示。
- 已增加“AI 建议需用户确认，不应直接伪造经历”的前端提示。
- 已完成后端相关测试、前端构建和本地手动联调，用户确认优化建议可以成功生成并展示。
- 迭代日志已更新：`docs/iteration-log/v2.4-ai-suggestions.md`。

说明：

- v2.4 不生成完整定制版简历。
- v2.4 不生成局部改写文本，局部改写进入 v2.5。
- v2.4 不做 RAG、pgvector 或多轮对话。
- 当前建议结果以结构化 JSON 字符串保存在 `suggestions` 字段。
- 当前失败状态保存错误信息，不保存原始 AI 输出。

## v2.4 小任务完成情况

| 小任务 | 状态 |
|---|---|
| v2.4.1 优化建议结果结构设计 | 已完成 |
| v2.4.2 优化建议 Prompt 设计 | 已完成 |
| v2.4.3 优化建议生成服务 | 已完成 |
| v2.4.4 优化建议触发接口 | 已完成 |
| v2.4.5 优化建议查询接口 | 已完成 |
| v2.4.6 前端优化建议展示 | 已完成 |
| v2.4.7 v2.4 联调、审查与日志 | 已完成 |

---

# v2.5 - AI 局部改写建议

状态：已完成

## v2.5 总结

v2.5 已完成 AI 局部改写建议闭环：

- 已新增 `ai_rewrite_suggestions` 表迁移脚本、实体和 Mapper。
- 已建立局部改写建议与简历、岗位描述、AI 匹配结果、AI 优化建议的可选关联关系。
- 已明确改写对象类型、目标简历部分、原文、改写建议、改写理由、注意事项、生成状态、采纳状态、模型名称、Prompt 版本和错误信息字段。
- 已创建 `rewrite_suggestion_v1` Prompt，输入为原文片段、改写对象类型、目标简历部分，以及可选岗位描述结构化结果、AI 匹配结果和 AI 优化建议。
- Prompt 明确要求只基于用户已有内容进行表达优化，不编造项目经历、技术栈、证书、奖项或虚假量化指标。
- 已新增局部改写 Prompt 构建、输出解析和业务编排服务。
- 已实现 AI 输出 JSON 解析与校验，格式异常时保存 `FAILED` 状态，不保存脏改写正文。
- 已实现 `POST /api/resumes/{resumeId}/rewrite-suggestions`，用于生成局部改写建议。
- 已实现 `GET /api/resumes/{resumeId}/rewrite-suggestions`，支持按简历查询局部改写建议，并支持按改写类型和采纳状态筛选。
- 已实现 `PATCH /api/rewrite-suggestions/{rewriteId}/accept-status`，用于记录用户采纳或拒绝改写建议。
- 已保证用户只能生成、查看和更新自己的局部改写建议。
- 已新增前端局部改写 API 和类型定义。
- 已在 AI 匹配页面增加局部改写弹窗、原文与改写建议对比展示、改写理由、注意事项、历史改写建议列表和采纳/拒绝按钮。
- 已修复采纳/拒绝接口的 `PATCH` CORS 配置问题，并将 `/api/rewrite-suggestions/**` 纳入鉴权路径。
- 已完成后端相关测试、前端构建和本地手动联调，用户确认局部改写生成、查询、采纳和拒绝流程可用。
- 迭代日志已更新：`docs/iteration-log/v2.5-rewrite.md`。

说明：

- v2.5 不自动修改原始简历文件。
- v2.5 不生成完整定制版简历。
- v2.5 不导出 PDF 或 DOCX。
- v2.5 不做 RAG、pgvector 或多轮对话。
- 当前采纳状态只记录用户决策，不会自动覆盖简历内容。
- 当前事实一致性主要依赖 Prompt 约束、输出结构校验和用户确认，后续可结合评估流程继续增强。

## v2.5 小任务完成情况

| 小任务 | 状态 |
|---|---|
| v2.5.1 局部改写结果表设计 | 已完成 |
| v2.5.2 局部改写 Prompt 设计 | 已完成 |
| v2.5.3 局部改写服务 | 已完成 |
| v2.5.4 局部改写接口 | 已完成 |
| v2.5.5 前端原文与改写建议对比展示 | 已完成 |
| v2.5.6 用户确认状态记录 | 已完成 |
| v2.5.7 v2.5 联调、审查与日志 | 已完成 |

---

# v2.5.5 - 产品信息架构与模块边界收敛

状态：已完成

## v2.5.5 总结

v2.5.5 已完成产品结构收敛：

- 已梳理当前普通用户侧、管理员侧、前端页面、主要后端接口、首页入口、简历入口、岗位入口和 AI 入口。
- 已新增并更新 `docs/product-structure.md`，记录模块边界、岗位边界、AI 功能边界、用户主流程、导航结构、页面命名和 v2.6 AI 历史聚合范围。
- 已新增并更新 `docs/page-list.md`，记录当前页面、推荐页面名称、页面类型、主导航建议、详情页/结果区块、页面跳转关系和管理员专属页面边界。
- 已将 AI 能力边界收敛为：简历诊断、目标岗位解析、匹配分析、岗位优化建议、AI 局部改写、AI 历史。
- 已将岗位相关概念收敛为：岗位库、目标岗位、岗位管理；普通用户侧不再使用“岗位管理”命名。
- 已将普通用户主流程收敛为：工作台 -> 我的简历 -> 目标岗位 -> 匹配与优化 -> AI 历史。
- 已完成前端轻量入口调整，将页面标题和关键按钮文案统一到新的产品语义，保留原有路由路径。
- 已确认 v2.6 AI 历史聚合类型：`RESUME_DIAGNOSIS`、`TARGET_JOB_PARSE`、`MATCH_ANALYSIS`、`JOB_OPTIMIZATION_SUGGESTION`、`LOCAL_REWRITE`。
- 已创建迭代日志：`docs/iteration-log/v2.5.5-product-structure.md`。
- 已执行 `cd web && npm run build`，前端构建通过。

说明：

- v2.5.5 不新增 AI 生成能力。
- v2.5.5 不实现 AI 历史接口或 AI 历史页面。
- v2.5.5 不修改数据库结构。
- v2.5.5 不重构后端接口路径。
- v2.5.5 不做大规模 UI 重写，只做前端入口、页面标题和按钮文案的轻量调整。

## v2.5.5 小任务完成情况

| 小任务 | 状态 |
|---|---|
| v2.5.5.1 当前功能模块梳理 | 已完成 |
| v2.5.5.2 AI 功能边界重新定义 | 已完成 |
| v2.5.5.3 岗位相关功能边界收敛 | 已完成 |
| v2.5.5.4 用户主流程重新设计 | 已完成 |
| v2.5.5.5 导航结构重新设计 | 已完成 |
| v2.5.5.6 页面命名与页面清单更新 | 已完成 |
| v2.5.5.7 前端入口轻量调整 | 已完成 |
| v2.5.5.8 v2.6 AI 历史聚合范围确认 | 已完成 |
| v2.5.5.9 文档更新与迭代日志 | 已完成 |

---

# v2.6 - AI 结果持久化与历史回看

状态：已完成

## v2.6 总结

v2.6 已完成 AI 结果持久化与历史回看闭环。系统现在可以统一回看简历诊断、目标岗位解析、匹配分析、岗位优化建议和局部改写结果，并支持按类型、简历、目标岗位和状态筛选。

本版本保持边界：不新增 AI 生成能力，不引入评估流程，不做 Embedding、pgvector、RAG、多轮对话或完整简历生成。

完成内容：

- 梳理并统一五类 AI 结果元数据：模型名称、Prompt 版本、状态、错误信息、创建时间和更新时间。
- 明确 AI 历史聚合类型：`RESUME_DIAGNOSIS`、`TARGET_JOB_PARSE`、`MATCH_ANALYSIS`、`JOB_OPTIMIZATION_SUGGESTION`、`LOCAL_REWRITE`。
- 采用聚合查询方案，不新建 `ai_result_records` 表，不重复存储大段 AI 输出内容。
- 新增 `/api/ai-results` 列表接口，支持分页和按类型、简历、目标岗位、状态筛选。
- 新增 `/api/ai-results/{resultType}/{recordId}` 详情接口，按 `resultType + recordId` 查询来源表详情。
- 将 `/api/ai-results/**` 纳入鉴权，保证用户只能查看自己的 AI 结果。
- 将前端 `/history` 升级为 AI 历史页面，支持列表、筛选、分页、详情抽屉和业务跳转。
- 前后端均避免展示 API Key、完整 Prompt 等敏感信息。

## v2.6 小任务完成情况

| 小任务 | 状态 | 说明 |
|---|---|---|
| v2.6.1 AI 结果元数据统一 | 已完成 | 已确认五类 AI 结果均可追踪模型、Prompt、状态、错误信息和时间，并补齐详情 VO 的 `createdAt`。 |
| v2.6.2 AI 历史记录聚合查询设计 | 已完成 | 已明确采用聚合查询方案，不新建统一流水表，保留旧 `/api/history` 简历维度历史接口。 |
| v2.6.3 AI 结果列表接口 | 已完成 | 已实现 `GET /api/ai-results`，聚合五类 AI 结果并支持分页和筛选。 |
| v2.6.4 AI 结果详情接口 | 已完成 | 已实现 `GET /api/ai-results/{resultType}/{recordId}`，支持五类 AI 结果详情回看。 |
| v2.6.5 前端 AI 历史页面 | 已完成 | 已将 `/history` 升级为 AI 历史页面，接入列表和详情接口。 |
| v2.6.6 v2.6 联调、审查与日志 | 已完成 | 已完成本地联调确认，并更新任务清单与迭代日志。 |


# v2.7 - AI 输出评估流程

状态：已完成

v2.7 已建立轻量 AI 输出评估流程：准备评估目录、样例简历、样例岗位描述、输出记录、人工评分表、评估报告和 Prompt 修改规则。该版本只做文档化评估闭环，不新增 AI 能力，不修改业务接口，不实现自动化评测平台、模型微调、Embedding、pgvector 或 RAG。

核心产物：

- `docs/evaluation/README.md`
- `docs/evaluation/evaluation-notes.md`
- `docs/evaluation/resumes/`
- `docs/evaluation/job-descriptions/`
- `docs/evaluation/outputs/`
- `docs/evaluation/reports/evaluation-report-v1.md`
- `docs/iteration-log/v2.7-ai-evaluation.md`

v2.7 小任务完成情况：

| 任务 | 状态 | 结果 |
|---|---|---|
| v2.7.1 评估集目录设计 | 已完成 | 建立 `docs/evaluation/` 目录、命名规则、隐私边界和可提交内容规则 |
| v2.7.2 样例简历准备 | 已完成 | 新增 3 份虚构样例简历，覆盖 Java 后端、AI 应用开发、数据分析或算法方向 |
| v2.7.3 样例岗位描述准备 | 已完成 | 新增 3 份虚构样例岗位描述，覆盖不同岗位方向、技能要求和匹配关注点 |
| v2.7.4 评估维度与评分表设计 | 已完成 | 建立 7 个评估维度、1 到 5 分规则、扣分规则、严重问题判定和报告模板 |
| v2.7.5 Prompt 输出结果记录 | 已完成 | 保存 5 份输出记录，覆盖岗位解析、简历分析、匹配分析、优化建议和局部改写 |
| v2.7.6 评估问题汇总与 Prompt 修改规则 | 已完成 | 完成 5 条输出记录人工评分，标记局部改写 JSON 结构问题，形成 Prompt 修改规则 |
| v2.7.7 v2.7 日志与总结 | 已完成 | 压缩任务清单，更新 v2.7 迭代日志，准备进入 v2.8 |

评估结论：

- 优化建议输出整体最好，具备目标章节、问题、建议、依据和 caution。
- 简历分析、岗位解析、匹配分析整体可用，但仍可增强逐条依据和必备/加分技能区分。
- 局部改写的主要风险是结构稳定性，应优先强化 `rewrite_suggestion_v1` 的 JSON-only 输出约束。
- 当前输出记录主要用于跑通评估流程；如后续替换为真实联调输出，需要重新评分。

下一步进入 `v2.8 - Embedding / pgvector / RAG 初步增强`。

# v2.8 - Embedding / pgvector / RAG 初步增强

状态：已完成

v2.8 已完成基础 Embedding / pgvector / 语义相似度 / 简单 RAG 闭环：系统可以接入 OpenAI-compatible Embedding 服务，使用 pgvector 保存简历和岗位描述向量，基于成功向量查询 Top-K 语义相似片段，并将简单 RAG 上下文可选接入 AI 岗位匹配和 AI 简历优化建议。该版本只做基础语义增强，不做复杂推荐系统、企业级知识库、多轮对话、Reranker、模型微调或微服务拆分。

核心产物：

- `docker-compose.yml`
- `backend/src/main/resources/db/migration/V12__enable_pgvector_extension.sql`
- `backend/src/main/resources/db/migration/V13__create_embedding_tables.sql`
- `backend/src/main/java/com/winter/airesumeoptimizer/infra/ai/EmbeddingClientService.java`
- `backend/src/main/java/com/winter/airesumeoptimizer/infra/ai/OpenAiCompatibleEmbeddingClientService.java`
- `backend/src/main/java/com/winter/airesumeoptimizer/infra/ai/EmbeddingClientProperties.java`
- `backend/src/main/java/com/winter/airesumeoptimizer/module/embedding/`
- `docs/iteration-log/v2.8-vector-rag.md`

v2.8 小任务完成情况：

| 任务 | 状态 | 结果 |
|---|---|---|
| v2.8.1 pgvector 可行性检查 | 已完成 | 明确原 `postgres:16-alpine` 不包含 pgvector，后续需使用 `pgvector/pgvector:pg16`，并确认 Flyway 可管理 `CREATE EXTENSION vector` |
| v2.8.2 向量表设计 | 已完成 | 新增 pgvector 扩展迁移和 `resume_embeddings`、`job_description_embeddings`，向量字段使用非固定维度 `vector` 并记录实际维度 |
| v2.8.3 Embedding 模型接入 | 已完成 | 接入 OpenAI-compatible `/embeddings` 客户端，当前默认 SiliconFlow 模型 `Qwen/Qwen3-Embedding-0.6B`，默认维度 `1024`，配置通过环境变量读取 |
| v2.8.4 简历向量生成 | 已完成 | 支持已解析简历文本切片、生成向量、保存到 `resume_embeddings`，并提供生成和查询接口 |
| v2.8.5 岗位描述向量生成 | 已完成 | 支持已解析岗位描述文本切片、生成向量、保存到 `job_description_embeddings`，并提供生成和查询接口 |
| v2.8.6 基础语义相似度查询 | 已完成 | 使用 pgvector 余弦距离 `<=>` 查询简历与岗位描述 Top-K 相似片段，返回片段和相似度分数 |
| v2.8.7 简单 RAG 检索增强 | 已完成 | 基于语义相似片段组装受限长度 RAG 上下文，并可选接入 AI 匹配和优化建议 Prompt |
| v2.8.8 v2.8 联调、审查与日志 | 已完成 | 用户通过接口文档完成 pgvector、Embedding、向量生成、语义查询、RAG 接入和异常场景验收，已更新任务清单和迭代日志 |

联调验收结论：

- pgvector 扩展可用，后端 Flyway 能完成 V12 / V13 迁移。
- Embedding 服务可调用，当前默认 SiliconFlow 模型为 `Qwen/Qwen3-Embedding-0.6B`，默认维度为 `1024`。
- 简历向量和岗位描述向量均可生成并查询。
- 语义相似度接口可返回 Top-K 相似片段、简历片段、岗位描述片段和相似度分数。
- AI 岗位匹配和 AI 简历优化建议可选接入 RAG 上下文；无向量或无可用片段时不阻断原流程。
- 权限隔离、无向量、维度不一致、Top-K 限制等异常场景已按预期处理。
- `.env.example`、README、文档和日志不写入真实 API Key；运行日志不输出完整 Prompt、API Key 或 Token。

当前边界：

- 不引入 Reranker。
- 不接入外部知识库。
- 不做多轮问答。
- 不做复杂推荐系统。
- 不做企业级向量数据库平台。
- 不做模型微调或微服务拆分。

下一步进入 `v2.9 - 简历文本解析质量优化`，先补强简历文本提取和结构化解析质量。



# v2.9 - 简历文本解析质量优化

状态：已完成

## v2.9 总结

v2.9 已完成简历解析质量闭环：文本质量检查、文本清洗、章节识别、基础结构化解析、解析质量校验、AI 辅助章节归类、AI 结构化补全校验、AI 展示优化、前端解析结果收敛和轻量回归样例均已落地。

核心结果：

- 后端解析结果已返回原文、清洗文本、章节识别、质量状态、警告、解析模式、AI 状态和调试信息。
- DOCX 提取已支持段落、表格、页眉、页脚、文本框 / shape 文本，并通过去重和顺序修复降低复杂模板错位。
- 结构化结果已演进为 `parseMeta` + `rawSections` + `structuredData` 双层结构，同时保留旧字段兼容层。
- `structuredData` 已将 skills 改为标签集合，将 work / internship / campus 统一到 experiences，并用 achievements 承接旧 awards。
- AI 状态已明确区分 `USED`、`SKIPPED`、`FALLBACK`、`DISABLED`；缓存只服务真实 AI 调用。
- 新增 `displayModel` / `aiDisplayModel` / `ruleDisplayModel`，AI 只整理展示模型，不修改 `structuredData` 原始事实；失败时降级为规则展示模型。
- 前端解析结果页优先展示 `displayModel`，旧数据缺少 displayModel 时使用规则 adapter；原始文本、rawSections、blocks 和 AI 调试信息默认折叠。
- v2.9.18 已补充图标字体、顶部左右分栏混排、GitHub、GPA、CET、排名、技能侧栏、SRTP 项目和获奖格式的规则解析增强。
- 已建立 `docs/evaluation/parse/regression/` 回归资料，当前样例覆盖 Java 简历、复杂 DOCX/PDF、双层结构和图标字体多列样例。

说明：

- v2.9 不做 OCR、扫描版 PDF 全量识别、模型微调、企业级简历解析平台或 AI 主流程改造。
- v2.9 不新增岗位匹配算法、不新增 Embedding / RAG 能力、不自动编造缺失简历字段。
- 当前用户手动修正仅完成方案和前端确认状态，尚未实现编辑保存和下游 AI 结果重算；AI 章节归类和 AI 展示优化缓存仍为进程内缓存。

## v2.9 小任务完成情况

| 小任务 | 状态 |
|---|---|
| v2.9.1 当前解析问题样例收集 | 已完成 |
| v2.9.2 文本提取结果质量检查 | 已完成 |
| v2.9.3 文本清洗与章节识别优化 | 已完成 |
| v2.9.4 基础结构化字段解析优化 | 已完成 |
| v2.9.5 解析结果校验与错误提示 | 已完成 |
| v2.9.6 前端解析结果预览与用户修正方案 | 已完成 |
| v2.9.7 解析质量评估样例与报告 | 已完成 |
| v2.9.9 非 AI 解析基础修复 | 已完成 |
| v2.9.10 AI 辅助章节归类 | 已完成 |
| v2.9.11 AI 结构化补全与 Schema 校验 | 已完成 |
| v2.9.12 前端解析结果收敛与用户确认方案 | 已完成 |
| v2.9.13 解析质量评估与回归样例 | 已合并收口 |
| v2.9.14 DOCX 顺序修复、AI 性能优化与章节展示收敛 | 已完成 |
| v2.9.17 解析结果结构解耦、AI 状态语义修复与低耦合字段重构 | 已完成 |
| v2.9.18 图标字体、多列布局与顶部混排信息清洗优化 | 已完成 |
| v2.9.19 AI 展示优化 displayModel 与规则降级 | 已完成 |

---

# v2.10 - 岗位优化报告与演示闭环

状态：已完成

## v2.10 总结

v2.10 已完成岗位优化报告与演示闭环：系统可以基于一份已解析简历和一个已解析目标岗位，聚合已有 AI 匹配结果、岗位优化建议和局部改写建议，生成可查看、可解释、可演示的岗位优化报告。

核心结果：

- 新增 `JobOptimizationReportVO`、`JobOptimizationReportService` 和 `JobOptimizationReportController`。
- 新增接口 `GET /api/resumes/{resumeId}/job-optimization-report?jobDescriptionId={jobDescriptionId}`。
- 报告聚合简历名称、目标岗位、匹配分数、匹配等级、强匹配项、弱匹配项、缺失技能、风险提示、优化建议、局部改写、下一步清单、模型信息和 warning。
- 报告只复用已有 AI 结果，不重新调用 AI，不新增数据库表，不反向修改历史 AI 结果。
- 报告支持用户权限隔离；未登录、跨用户访问、无匹配结果和失败匹配结果均有明确错误或提示。
- 前端“匹配与优化”页新增“查看优化报告”入口，报告结构化展示，不直接堆叠 JSON。
- 优化建议按高 / 中 / 低优先级展示，局部改写按已采纳 / 待确认区分。
- 报告复用匹配依据、匹配原因、建议依据、注意事项、改写理由；缺少依据时生成 warning，不编造缺失依据。
- README、项目主线文档、演示流程文档和虚构演示样例已补充。
- 产品结构文档已将岗位优化报告纳入当前普通用户主流程。

说明：

- v2.10 不生成完整定制版简历，不导出 DOCX / PDF，不自动写回用户原始简历。
- v2.10 不新增岗位匹配算法、不新增 AI 模型能力、不新增 RAG / Embedding 能力、不做多轮对话或部署。
- 当前报告仍是当前页面内轻量展示，尚未做独立报告详情页、打印视图或导出功能。

## v2.10 小任务完成情况

| 小任务 | 状态 |
|---|---|
| v2.10.1 岗位优化报告结构设计 | 已完成 |
| v2.10.2 岗位优化报告聚合服务 | 已完成 |
| v2.10.3 岗位优化报告接口 | 已完成 |
| v2.10.4 前端岗位优化报告展示 | 已完成 |
| v2.10.5 AI 结果依据展示增强 | 已完成 |
| v2.10.6 README 展示入口、演示流程文档与项目主线文档 | 已完成 |
| v2.10.7 联调、审查与日志 | 已完成 |

---
