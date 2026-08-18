# 项目上下文与当前状态

更新基线：V2 Phase 3（Evidence Matching 与 Gap Analysis）已完成。本文只记录**当前事实、已确认决策和已知差距**；产品目标见 [PRD.md](PRD.md)，实现顺序见 [PLAN.md](PLAN.md)。

## 1. 当前定位

仓库当前是在 V1 模块化单体上完成 Phase 1 用户链路收敛、Phase 2 正式领域模型迁移和 Phase 3 正式证据分析的实现。普通用户仍只需选择或上传真实简历、粘贴目标 JD 并开始分析；前端主链路以 `OptimizationTask` 为业务身份，结果页展示的是逐条可追溯的正式证据分析，不再是旧匹配输出。

Phase 3 建立了岗位要求 ↔ 简历证据的正式映射与三态结论（已有证据 / 有经历但表达不足 / 当前材料未提供证据）。Workspace、AI Diff / Apply、Typst / PDF、BYOK 等后续能力尚未实现。

## 2. 真实已有能力

| 领域 | 当前实现 |
|---|---|
| 账号 | 注册、登录、JWT、当前用户、受保护前端路由 |
| 简历 | PDF / DOC / DOCX 上传、local / MinIO、读取、删除、文本提取与结构化解析；上传后自动提交后台准备任务 |
| 正式简历版本 | 每次新岗位分析建立独立 `SOURCE` 输入快照版本和由其派生的 `TARGETED` 岗位版本；两个版本初始内容一致，不修改上传简历或解析结果 |
| 正式目标岗位 | `JobTarget` 保存用户归属、原始 JD、标题与来源；当前通过一对一兼容引用继续复用 `job_descriptions` 的解析能力 |
| 正式优化任务 | `OptimizationTask` 关联源版本、岗位版本和 JobTarget，保存 Resume / JD、Prompt、Rules、Provider、Model、Template 快照以及正式状态、异步执行记录和兼容分析结果 |
| 岗位分析 | 首页选择简历、粘贴 JD 并一键启动；后台在证据匹配前冻结简历输入快照，保存原始 JD，完成解析后生成正式证据分析 |
| 正式证据分析 | 每个成功任务一条 `evidence_analyses` 及其 `evidence_requirements` / `requirement_evidences` 行；每条岗位要求判定为 MATCHED / EXPRESSION_GAP / NO_EVIDENCE，证据引用逐字命中冻结简历快照，未命中即丢弃并降级为无证据 |
| 重试与结果 | 失败重试按 `OptimizationTask` 复用输入和版本，并整体替换旧的正式分析行；成功任务不可被重试改写；结果页只通过正式任务 ID 读取，历史任务无正式分析时兼容读取旧匹配结果 |
| 旧后端能力 | `job_descriptions`、`ai_job_match_results`、简历诊断、优化建议、局部改写、聚合报告、预置岗位和旧历史仍存在，供解析与兼容读取；主链路不再写入新的旧匹配行 |
| 向量 | pgvector、Embedding、分块、语义相似度、可选 RAG 上下文；不作为用户步骤，也不进入正式证据匹配主链路 |
| 执行任务 | `async_tasks` 继续承担单进程后台执行与轮询，不再冒充正式优化业务模型；新分析以 `OPTIMIZATION_TASK` 作为 biz / result 类型 |
| 工程 | Flyway、统一异常、校验、日志脱敏、OpenAPI、后端单元 / Web MVC 测试、前端类型检查与构建 |
| 部署 | Docker Compose、Nginx、PostgreSQL、Redis、MinIO、certbot、备份 / 恢复脚本 |

## 3. 当前页面与主流程

当前页面路由包括：Landing、首页、我的简历、按 `optimizationTaskId` 访问的岗位分析结果、登录和注册。一级导航只有：首页、我的简历。

当前权威用户流程：

```text
登录
→ 选择已有简历，或上传简历并由后台自动准备
→ 粘贴真实目标岗位 JD
→ 创建 JobTarget + SOURCE ResumeVersion + TARGETED ResumeVersion + OptimizationTask
→ 后台确保简历可用并冻结任务输入快照
→ 解析兼容 JD，随后由 EvidenceMatchService 生成正式证据分析，并回写任务模型 / Prompt / Model 快照
→ 通过 OptimizationTask 查看逐条可追溯的分析结果，或在失败后重试
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

## 5. 与后续 V2 的主要差距

当前尚未具备：

- 可直接编辑的 Workspace、自动保存、Undo / Redo 和恢复点。
- Diff、Apply / Reject 与事实校验组成的完整可控修改体验；无证据内容仍不得进入自动改写。
- “缺少事实时询问用户确认”的交互（PRD 第 8 节）；当前 NO_EVIDENCE 只做提示，不收集用户确认事实。
- Structured Resume JSON 驱动的 Typst Preview / PDF Export。
- 每用户 BYOK、Credential 加密、AI Gateway 与自定义 Base URL SSRF 防护。
- 用户数据导出 / 全量删除和长期多 JD 方向洞察。

Phase 4 是下一阶段。不得在 Phase 4 中提前实现 AI Rewrite 的无证据写入或 Typst，也不得把 NO_EVIDENCE 直接宣称为用户没有能力。

## 6. 已确认并保留的决策

- 模块化单体和前后端分离足以支撑 V2；不引入微服务、消息队列或 Kubernetes。
- 上传的原始文件、`resumes` 元数据和 `resume_parse_results` 不因岗位版本派生而改变。
- `ResumeVersion` 的源版本代表本次分析输入快照；岗位版本通过 `source_version_id` 显式派生，后续编辑不得静默污染源版本。
- `OptimizationTask` 是正式业务任务，`async_tasks` 只是可替换的执行记录。
- 任务创建时冻结原始 JD 和当前 Provider / Model / Rules / Template 配置；成功时补齐实际 Job Parse 与证据匹配 Prompt 及模型快照。
- 正式分析结果的 Source of Truth 是 `evidence_analyses` 及其子表；不在 `ai_job_match_results` 上叠加第二套业务逻辑，旧表只保留兼容读取。
- 证据匹配输入只使用任务已冻结的简历快照与当次 JD 解析结果；具体匹配实现在 `EvidenceMatchingStrategy` 接口之后，可替换不泄漏到编排。
- “简历没写”不等于“用户没有能力”；NO_EVIDENCE 只代表当前材料未提供证据。
- AI 只能基于已有或用户确认事实判断，不得补造技能、经历、数字或成果；证据引用未命中简历快照即被丢弃并降级。
- 当前解析实现继续通过 V1 表运行，但其 ID 只在后端兼容上下文中使用；默认前端只认识正式任务 ID。
- AI 只能优化表达和排序，不能新增未验证技术、经历、日期、公司、成果或数字。
- Redis 失败应尽量降级，不影响 PostgreSQL 中的核心业务事实。
- 文件读取必须经过用户归属检查；迁移和正式服务同样校验资源归属。
- 数据库迁移以 Flyway 为唯一机制。
- 异步任务使用真实阶段 / 状态，不伪造进度；错误信息必须脱敏。

## 7. 当前质量与风险

- V19 / V20 是加法式且可应用回滚，但新增表尚未经历生产规模数据量；部署前仍应按备份流程保存数据库，并观察唯一索引和事务耗时。
- 推理型模型（如当前配置的 deepseek-v4-pro）的思考 token 计入 max_tokens；证据匹配需要足够的输出额度，否则会以空内容结束。仓库默认已调整为 `OPENAI_MAX_TOKENS` / `AI_MAX_TOKENS` 16000；更换模型或 Provider 时应重新验证。
- 正式证据分析依赖 JD 结构化解析质量；JD 解析失败或信息不足时，证据分析会输出少量要求或整体失败，不会猜测补全。
- 旧后端表、接口和服务仍有解析、历史、删除及兼容重试依赖，当前不能删除；主链路不再产生新的旧匹配行，旧读取接口对旧数据仍有效。
- 删除旧 JobDescription 或 Resume 会通过外键删除对应正式任务 / 岗位版本 / 正式分析；由任务创建但已失去岗位关系的源快照版本可能随 Resume 保留，后续统一数据生命周期阶段需再次核对。
- 首页的进行中任务恢复仍以会话存储的正式任务引用为主；正式任务可跨设备按 ID 查询，但“最近优化”列表尚未加入首页。
- 本机 Java 25 仍需显式启用 annotation processing 才能生成 Lombok 代码；CI 使用 Java 21。
- 本次 reviewer 子代理正常启动并完成审查（无 Critical / Major / Minor 问题）；此前仓库外 `pi-permission-system` 阻断问题未复现，若再次出现仍按同维度自审并记录。

## 8. Phase 3 验证

- 后端 `./mvnw -Dmaven.compiler.proc=full test`：363 个测试通过，3 个真实外部服务 smoke test 默认跳过。
- 新增：证据解析器（含伪造引用丢弃与降级、去重、上限、代码围栏）、匹配策略、Prompt 渲染、证据服务（幂等替换、归属拒绝、快照缺失、结果组装）单测；更新编排、任务 markSuccess 与正式 API（EVIDENCE / LEGACY_COMPAT 双模式）测试。
- 轻量效果验证：真实 AI smoke 覆盖明确匹配、表达不足、无证据、同义表达、多证据与易编造案例，七条要求判定均合理且全部引用通过事实校核；Kafka 等无证据项未被误判为有证据。
- 前端 `npm run build`：类型检查和生产构建通过；Oxlint / ESLint：0 warning、0 error。
- Flyway V1–V20 空库全量迁移、V1–V18 样本数据回填后升级 V20（含归属一致性核对）：在独立临时 PostgreSQL 数据库执行并校验。
- `git diff --check`：通过。

## 9. 文档与决策优先级

1. `PRD.md`：V2 产品与最高层架构决策。
2. 真实代码、迁移、配置和测试：当前行为事实。
3. `ARCHITECTURE.md`：当前边界与迁移约束。
4. `PLAN.md`：实施顺序和阶段门禁。
5. `CONTEXT.md`：当前状态和已知差距。
6. `OPERATIONS.md`：当前部署运行方式。
