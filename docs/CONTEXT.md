# 项目上下文与当前状态

更新基线：V2 Phase 4（Optimization Workspace 与结构化简历编辑）已完成并通过 Gate。本文只记录**当前事实、已确认决策和已知差距**；产品目标见 [PRD.md](PRD.md)，实现顺序见 [PLAN.md](PLAN.md)。

## 1. 当前定位

仓库当前是在 V1 模块化单体上完成 Phase 1 用户链路收敛、Phase 2 正式领域模型迁移、Phase 3 正式证据分析与 Phase 4 优化工作区的实现。普通用户仍只需选择或上传真实简历、粘贴目标 JD 并开始分析；分析成功后可进入两栏优化工作区，左栏只读展示分析时的三态结论与 SOURCE Evidence，右栏编辑当前任务的 TARGET 岗位版本，编辑自动保存并可恢复本次优化前版本。正式三态已收紧到当前冻结材料能够证明的范围，Phase 3 Gate 已通过；Phase 4 Gate 已通过。

Phase 4 建立了以 optimizationTaskId 为唯一入口的 Workspace：服务端解析并校验 Task → SOURCE / TARGET / JobTarget / Resume / User 版本链，只有 TARGET 可编辑；结构化简历文档（RESUME_DOCUMENT_V1）落在既有 `resume_versions.structured_content`，未新增第二套内容字段；自动保存以 `content_revision` 乐观并发控制，SOURCE、resume_input_snapshot 与证据分析全程只读。AI Diff / Apply、Typst / PDF、BYOK 等后续能力尚未实现。

## 2. 真实已有能力

| 领域 | 当前实现 |
|---|---|
| 账号 | 注册、登录、JWT、当前用户、受保护前端路由 |
| 简历 | PDF / DOC / DOCX 上传、local / MinIO、读取、删除、文本提取与结构化解析；上传后自动提交后台准备任务 |
| 正式简历版本 | 每次新岗位分析建立独立 `SOURCE` 输入快照版本和由其派生的 `TARGETED` 岗位版本；两个版本初始内容一致，不修改上传简历或解析结果 |
| 正式目标岗位 | `JobTarget` 保存用户归属、原始 JD、标题与来源；当前通过一对一兼容引用继续复用 `job_descriptions` 的解析能力 |
| 正式优化任务 | `OptimizationTask` 关联源版本、岗位版本和 JobTarget，保存 Resume / JD、Prompt、Rules、Provider、Model、Template 快照以及正式状态、异步执行记录和兼容分析结果 |
| 岗位分析 | 首页选择简历、粘贴 JD 并一键启动；后台在证据匹配前冻结简历输入快照，保存原始 JD，完成解析后生成正式证据分析 |
| 正式证据分析 | 每个成功任务一条 `evidence_analyses` 及其 `evidence_requirements` / `requirement_evidences` 行；要求必须来自冻结 JD，证据必须逐字命中冻结 ResumeVersion 并与要求相关；MATCHED / PARTIAL_EVIDENCE 必须有 Evidence，NO_EVIDENCE 不保存 Evidence，三态由保留证据及其支持程度推导 |
| 重试与结果 | 失败重试按 `OptimizationTask` 复用已冻结输入和版本，并整体替换旧的正式分析行；正式结果与任务 SUCCESS 在同一事务提交，并发提交通过条件更新只允许一个执行占用；成功任务不可被重试改写；历史任务无正式分析时兼容读取旧匹配结果 |
| 优化工作区 | 以 `optimizationTaskId` 为唯一入口的两栏工作区：左栏只读展示分析时三态结论与 SOURCE Evidence，右栏编辑 TARGET 岗位版本的结构化简历文档；支持基础信息 / Section / Entry / Bullet 编辑、Bullet 增删、Section 排序、会话内 Undo / Redo、debounce 自动保存与恢复优化前版本 |
| 内容并发控制 | `resume_versions.content_revision`（V21，NOT NULL DEFAULT 0）承担乐观并发；保存携带 expectedRevision，仅版本一致时单条条件 UPDATE 写入并递增；冲突返回服务端当前版本并保留本地草稿；恢复优化前版本基于任务冻结的 resume_input_snapshot 确定性重生成并作为新 revision 写入 |
| 旧后端能力 | `job_descriptions`、`ai_job_match_results`、简历诊断、优化建议、局部改写、聚合报告、预置岗位和旧历史仍存在；`ai_job_match_results` 的公开写入口已停用，仅保留历史读取，正式主链路也不再写入新的旧匹配行 |
| 向量 | pgvector、Embedding、分块、语义相似度、可选 RAG 上下文；不作为用户步骤，也不进入正式证据匹配主链路 |
| 执行任务 | `async_tasks` 继续承担单进程后台执行与轮询，不再冒充正式优化业务模型；新分析以 `OPTIMIZATION_TASK` 作为 biz / result 类型 |
| 工程 | Flyway、统一异常、校验、日志脱敏、OpenAPI、后端单元 / Web MVC 测试、前端类型检查与构建 |
| 部署 | Docker Compose、Nginx、PostgreSQL、Redis、MinIO、certbot、备份 / 恢复脚本 |

## 3. 当前页面与主流程

当前页面路由包括：Landing、首页、我的简历、按 `optimizationTaskId` 访问的岗位分析结果、按 `optimizationTaskId` 访问的优化工作区、登录和注册。一级导航只有：首页、我的简历。

当前权威用户流程：

```text
登录
→ 选择已有简历，或上传简历并由后台自动准备
→ 粘贴真实目标岗位 JD
→ 创建 JobTarget + SOURCE ResumeVersion + TARGETED ResumeVersion + OptimizationTask
→ 后台确保简历可用并冻结任务输入快照
→ 解析兼容 JD，随后由 EvidenceMatchService 生成正式证据分析，并回写任务模型 / Prompt / Model 快照
→ 通过 OptimizationTask 查看逐条可追溯的分析结果，或在失败后重试
→ 任务成功后进入优化工作区：编辑 TARGET 岗位版本、自动保存、必要时恢复本次优化前版本
```

岗位库、独立目标岗位管理、旧“匹配与优化”编排、技术分类式 AI 历史、Dashboard 指标 / Stepper 仍不在前端路由和导航中。

## 4. Phase 2 / Phase 3 数据迁移

`V19__create_phase2_core_domain.sql` 是加法式迁移：

- 新建 `job_targets`、`resume_versions`、`optimization_tasks`。
- 通过 `(resource_id, user_id)` 复合外键约束正式模型内的用户归属。
- 每份旧简历回填一个来源为 `LEGACY_IMPORT` 的源版本。
- 每条旧 `job_descriptions` 回填一个 JobTarget，并保留 JD 原文。
- 每条旧 `ai_job_match_results` 回填一个岗位派生版本和一个 OptimizationTask，保存当时可获得的 Resume / JD、Prompt、Model 与结果状态。
- 迁移前拒绝跨用户的旧匹配关系；迁移后验证 Resume、JD、Match 是否全部有正式模型对应项，任一缺失即令 Flyway 事务失败。
- 不修改或删除 V1 业务记录。应用回滚时旧版本可忽略新增表继续运行；正式迁移数据保留，恢复 Phase 2 应用后仍可使用。

在无旧数据的全量迁移和带 Resume / Parse / JD / Match 样本的回填迁移上均已用独立临时 PostgreSQL 数据库执行并校验。

`V20__create_phase3_evidence_gap_domain.sql` 同样是加法式迁移：新建 `evidence_analyses`、`evidence_requirements`、`requirement_evidences` 三张表，并为 `optimization_tasks` 补充 `(id, user_id)` 唯一约束以支持复合所有权外键；不回填、不修改、不删除任何 V1 数据，应用回滚后新表闲置即可。正式证据分析不从旧匹配数据回填：旧输出不含可追溯的简历原文引用，回填会违反真实性边界。

`V20_1__rename_expression_gap_to_partial_evidence.sql` 原位把正式状态收敛为 MATCHED / PARTIAL_EVIDENCE / NO_EVIDENCE，把单条 Evidence 支持程度收敛为 SUFFICIENT / PARTIAL，不保留 EXPRESSION_GAP 或 expression_status 双模型。旧语义生成的 Phase 3 分析属于可重算派生数据，会被删除并将对应正式任务 / 执行记录标为可重试失败；冻结 Resume / JD 输入继续保留。V1 历史表和兼容读取不受影响。

`V21__add_resume_version_content_revision.sql` 同样是加法式迁移：只给 `resume_versions` 补充 `content_revision BIGINT NOT NULL DEFAULT 0` 及非负 CHECK，不新增内容字段、不回填、不修改任何已有数据；应用回滚后新列闲置即可。revision 为 0 表示版本从未被工作区编辑（内容仍是冻结解析快照或其未动副本），大于 0 表示 TARGET 已持久化 RESUME_DOCUMENT_V1 编辑文档。曾存在的未提交草稿迁移（额外增加 workspace_content 列）因违反“不新增第二套正式内容字段”的冻结约束被替换，当时未在任何数据库执行。

## 5. 与后续 V2 的主要差距

当前尚未具备：

- Diff、Apply / Reject 与事实校验组成的完整可控修改体验，以及受约束的上下文 AI Suggest / Rewrite；无证据内容仍不得进入自动改写。
- “缺少事实时询问用户确认”的交互（PRD 第 8 节）；当前 NO_EVIDENCE 只做提示，不收集用户确认事实。
- Structured Resume JSON 驱动的 Typst Preview / PDF Export。
- 每用户 BYOK、Credential 加密、AI Gateway 与自定义 Base URL SSRF 防护。
- 用户数据导出 / 全量删除和长期多 JD 方向洞察。

Phase 5 是计划中的下一阶段，Phase 4 Gate 已通过。不得在 Phase 5 中实现无证据写入，也不得把 NO_EVIDENCE 直接宣称为用户没有能力。

## 6. 已确认并保留的决策

Phase 3 统一语言：

- **MATCHED**：当前冻结材料存在足够证据支持完整岗位要求。前端称“已有优势”。
- **PARTIAL_EVIDENCE**：当前冻结材料存在直接相关证据，但不足以完整支持岗位要求。前端称“建议完善”。不得解释为“用户确实有该经历但没写清楚”。
- **NO_EVIDENCE**：当前冻结材料未找到支持证据。前端称“当前材料未体现”。不得解释为用户没有相应能力。
- **Evidence Support Level**：单条 Evidence 对 Requirement 的材料支持程度，只允许 SUFFICIENT / PARTIAL。避免使用 expression status、ADEQUATE / WEAK 或 Expression Gap。

Phase 4 统一语言与边界：

- **Workspace 身份**：optimizationTaskId 是唯一入口；SOURCE / TARGET / JobTarget / Evidence 全部由服务端从任务解析，前端不指定可写 ResumeVersion。
- **结构化简历文档**：RESUME_DOCUMENT_V1 是 TARGET 唯一规范编辑文档与持久化内容 Source of Truth，落在 `resume_versions.structured_content`；不新增第二套内容字段。元素身份与顺序稳定；解析结构 → 编辑结构的转换确定、完整，无法安全转换时 fail closed，不静默截断 / 丢字段 / 补内容 / 重排。
- **只读冻结面**：SOURCE、resume_input_snapshot 与证据分析不被 Workspace 修改；Phase 3 Evidence 始终引用分析时 SOURCE，TARGET 编辑不触发重算，建议区明确展示的是“分析时材料”。
- **乐观并发**：保存必须携带 expectedRevision，仅版本一致时原子写入并递增；冲突保留本地草稿并停止盲目重试，不提供绕过 revision 的无条件强写；用户显式覆盖时基于重新获取的最新服务端 revision 发起新的条件保存。
- **恢复优化前版本**：显式确认后基于任务冻结快照确定性重生成，作为新 revision 写入，遵循相同并发规则。
- **会话边界**：Undo / Redo 只属当前页面编辑会话；刷新 / 重新进入只恢复最后成功持久化的服务端内容；localStorage / sessionStorage 不作为正式简历恢复源。

- 模块化单体和前后端分离足以支撑 V2；不引入微服务、消息队列或 Kubernetes。
- 上传的原始文件、`resumes` 元数据和 `resume_parse_results` 不因岗位版本派生而改变。
- `ResumeVersion` 的源版本代表本次分析输入快照；岗位版本通过 `source_version_id` 显式派生，后续编辑不得静默污染源版本。
- `OptimizationTask` 是正式业务任务，`async_tasks` 只是可替换的执行记录。
- 任务创建时冻结原始 JD 和当前 Provider / Model / Rules / Template 配置；成功时补齐实际 Job Parse 与证据匹配 Prompt 及模型快照。
- 正式分析结果的 Source of Truth 是 `evidence_analyses` 及其子表；不在 `ai_job_match_results` 上叠加第二套业务逻辑，旧表只保留兼容读取。
- 证据匹配输入只使用任务已冻结的简历快照与当次 JD 解析结果；具体匹配实现在 `EvidenceMatchingStrategy` 接口之后，可替换不泄漏到编排。
- “简历没写”不等于“用户没有能力”；NO_EVIDENCE 只代表当前材料未提供证据。
- PARTIAL_EVIDENCE 不能授权任何后续 AI 增加 Evidence 中不存在的能力、技术、数字或成果；真正判断“有经历但没有写出来”必须由用户补充 / 确认或独立事实来源支持，本 Phase 不实现该交互。
- AI 只能基于已有或用户确认事实判断，不得补造技能、经历、数字或成果；证据引用未命中简历快照即被丢弃并降级。
- 当前解析实现继续通过 V1 表运行，但其 ID 只在后端兼容上下文中使用；默认前端只认识正式任务 ID。
- AI 只能优化表达和排序，不能新增未验证技术、经历、日期、公司、成果或数字。
- Redis 失败应尽量降级，不影响 PostgreSQL 中的核心业务事实。
- 文件读取必须经过用户归属检查；迁移和正式服务同样校验资源归属。
- 数据库迁移以 Flyway 为唯一机制。
- 异步任务使用真实阶段 / 状态，不伪造进度；错误信息必须脱敏。

## 7. 当前质量与风险

- V20 的复合外键能阻止跨用户关系，但 `requirement_evidences.source_resume_version_id` 在数据库层只约束同用户，未直接约束为该任务的 SOURCE；正式服务已校验任务版本链并固定写入任务 SOURCE，仍应在后续迁移决策中评估是否补充更强数据库约束。

- V19 / V20 是加法式且可应用回滚，但新增表尚未经历生产规模数据量；部署前仍应按备份流程保存数据库，并观察唯一索引和事务耗时。
- V20.1 是有意的单模型语义迁移：列名与 CHECK 被原位重命名，旧 Phase 3 应用不能继续运行在迁移后的模式上；部署必须同步升级应用与 Flyway。旧 Phase 3 结果可由冻结输入重算，V1 历史读取仍可回滚使用。
- 推理型模型（如当前配置的 deepseek-v4-pro）的思考 token 计入 max_tokens；证据匹配需要足够的输出额度，否则会以空内容结束。仓库默认已调整为 `OPENAI_MAX_TOKENS` / `AI_MAX_TOKENS` 16000；更换模型或 Provider 时应重新验证。
- 正式证据分析依赖 JD 结构化解析质量；JD 解析失败或信息不足时，证据分析会输出少量要求或整体失败，不会猜测补全。
- 旧后端表、接口和服务仍有解析、历史、删除及兼容重试依赖，当前不能删除；主链路不再产生新的旧匹配行，旧读取接口对旧数据仍有效。
- 删除旧 JobDescription 或 Resume 会通过外键删除对应正式任务 / 岗位版本 / 正式分析；由任务创建但已失去岗位关系的源快照版本可能随 Resume 保留，后续统一数据生命周期阶段需再次核对。
- 首页的进行中任务恢复仍以会话存储的正式任务引用为主；正式任务可跨设备按 ID 查询，但“最近优化”列表尚未加入首页。
- 本机 Java 25 仍需显式启用 annotation processing 才能生成 Lombok 代码；CI 使用 Java 21。
- 当前 Phase 3 已约束要求与证据来源、正式结果事务、重试并发和旧写入口，并通过 PARTIAL_EVIDENCE 语义移除对用户现实能力的推断；Phase 3 Gate 已通过。
- Workspace 并发争用目前由单条条件 UPDATE 的服务端语义加单元 / Web MVC 测试验证，尚缺真实 PostgreSQL 多线程并发保存集成测试；前端冲突处置（保留草稿、显式覆盖、采用服务端版本）可兼容该窗口，仍作为遗留测试风险保留。
- 工作区初始化优先使用冻结快照的 `rawSections` 完整文本与顺序，并用 `rawText` 补回未进入章节的原始行；历史快照缺少章节时保守退回完整 `rawText`，再无原始文本时才使用 structuredData、合法旧版顶层字段或 displayModel。未知或错误类型字段、超限内容和无法完整转换的内容会整体拒绝，不会用不完整投影覆盖 TARGET；少量不符合当前解析 Schema 的旧快照可能需要重新解析。
- 前端主 chunk 体积已超过 Vite 500 kB 提示阈值（element-plus 全量引入）；不影响正确性，后续视觉 / 体验阶段可评估按需引入。

## 8. Phase 4 验证

- 后端 `./mvnw -Dmaven.compiler.proc=full test`：独立 Gate 修复后全量运行 421 个测试，0 失败、0 错误，3 个真实外部服务 smoke test 默认跳过。
- 新增：转换器确定性、rawSections / rawText 完整性与上限 fail-closed 测试（含扩展联系方式保留、未知 / 错误类型拒绝、超限拒绝而非截断）；Workspace 服务的完整 Task → SOURCE / TARGET / JobTarget / Resume / User 链、共享 TARGET fail-closed、同简历双任务独立 TARGET、SOURCE / 快照不变、同 revision 竞争、恢复确定性与冲突；API 集成测试覆盖 workspace 读取 / 保存 / 恢复 / 参数校验 / 匿名拒绝。
- 前端 `npm test`：Workspace 状态机 10 个高风险场景测试通过，覆盖旧响应、新草稿重试、覆盖后二次冲突与 revision 重取失败、Undo / Redo、max-wait、Task 会话隔离、恢复成功 / 冲突 / 在途编辑；`npm run build`、只读 `oxlint`、`eslint` 均通过，未用自动修复命令改写无关文件。
- 迁移验证：独立临时 PostgreSQL / pgvector 中，V1–V21 空库全量迁移成功；另以已有 SOURCE / TARGET 内容的 V20.1 数据执行 V21 升级，内容逐字不变且 revision 均为 0。`content_revision` 为 NOT NULL DEFAULT 0 并有非负 CHECK，`workspace_content` 不存在。
- 独立 reviewer 对后端与前端切片分别审查：冻结约束逐项核对通过；提出的保存竞态守卫、beforeunload 兼容、debounce max-wait、转换侧超限 fail-closed 等问题已修复并复验。
- `git diff --check` 通过；工作区无遗留日志与构建产物。
- 尚缺真实 PostgreSQL 下的多线程并发保存与事务故障注入集成测试；当前由条件更新语义、事务边界和单元 / Web MVC 测试共同验证，仍作为遗留测试风险保留（与 Phase 3 相同处置）。

Phase 3 验证（历史基线）：

- 后端 `./mvnw -Dmaven.compiler.proc=full test`：Phase 3 提交范围内运行 375 个测试，0 失败、0 错误，3 个真实外部服务 smoke test 默认跳过。
- 新增 / 加强：MATCHED 充分证据、PARTIAL_EVIDENCE 部分证据、非法支持程度保守降级、NO_EVIDENCE 中性文案与无改写授权、伪造 / 部分 / 无关引用拒绝、重复要求 / 证据、错误版本链、跨用户、重试快照、并发占用、异常输出、正式 API 新字段及 V1 历史兼容测试。
- 前端 `npm run build`：类型检查和生产构建通过；未用自动修复型 lint 命令改写无关文件。
- V1–V20.1 空库迁移以及旧 EXPRESSION_GAP / WEAK 正式样本升级：在独立临时 PostgreSQL / pgvector 数据库执行并校验；旧正式分析被安全失效、冻结输入保留供重试，新 PARTIAL_EVIDENCE / PARTIAL 样本可落库，V1 历史表不变。
- 生产 Compose 配置只读渲染成功；`git diff --check` 通过。
- 尚缺真实 PostgreSQL 下的并发争用和事务故障注入集成测试；当前由条件更新、事务边界和单元测试共同验证，仍作为遗留测试风险保留。

## 9. 文档与决策优先级

1. `PRD.md`：V2 产品与最高层架构决策。
2. 真实代码、迁移、配置和测试：当前行为事实。
3. `ARCHITECTURE.md`：当前边界与迁移约束。
4. `PLAN.md`：实施顺序和阶段门禁。
5. `CONTEXT.md`：当前状态和已知差距。
6. `OPERATIONS.md`：当前部署运行方式。
