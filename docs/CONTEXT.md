# 项目上下文与当前状态

更新基线：V2 Phase 2（核心领域模型）已完成。本文只记录**当前事实、已确认决策和已知差距**；产品目标见 [PRD.md](PRD.md)，实现顺序见 [PLAN.md](PLAN.md)。

## 1. 当前定位

仓库当前是在 V1 模块化单体上完成 Phase 1 用户链路收敛和 Phase 2 正式领域模型迁移的实现。普通用户仍只需选择或上传真实简历、粘贴目标 JD 并开始分析；前端主链路现在以 `OptimizationTask` 为业务身份，不再用 `resumeId + jobDescriptionId` 组织重试、路由和结果读取。

Phase 2 只建立 `ResumeVersion`、`JobTarget`、`OptimizationTask`、输入 / 配置快照及兼容迁移。Evidence / Gap、Workspace、AI Diff / Apply、Typst / PDF、BYOK 等后续能力尚未实现。

## 2. 真实已有能力

| 领域 | 当前实现 |
|---|---|
| 账号 | 注册、登录、JWT、当前用户、受保护前端路由 |
| 简历 | PDF / DOC / DOCX 上传、local / MinIO、读取、删除、文本提取与结构化解析；上传后自动提交后台准备任务 |
| 正式简历版本 | 每次新岗位分析建立独立 `SOURCE` 输入快照版本和由其派生的 `TARGETED` 岗位版本；两个版本初始内容一致，不修改上传简历或解析结果 |
| 正式目标岗位 | `JobTarget` 保存用户归属、原始 JD、标题与来源；当前通过一对一兼容引用继续复用 `job_descriptions` 的解析能力 |
| 正式优化任务 | `OptimizationTask` 关联源版本、岗位版本和 JobTarget，保存 Resume / JD、Prompt、Rules、Provider、Model、Template 快照以及正式状态、异步执行记录和兼容分析结果 |
| 岗位分析 | 首页选择简历、粘贴 JD 并一键启动；后台在匹配前冻结简历输入快照，保存原始 JD，完成解析与现有 AI 匹配 |
| 重试与结果 | 失败重试按 `OptimizationTask` 复用输入和版本；成功任务不可被重试改写；结果页只通过正式任务 ID 读取，旧重试接口保留兼容 |
| 旧后端能力 | `job_descriptions`、`ai_job_match_results`、简历诊断、优化建议、局部改写、聚合报告、预置岗位和旧历史仍存在，供解析 / 匹配 / 兼容读取；不构成默认前端流程 |
| 向量 | pgvector、Embedding、分块、语义相似度、可选 RAG 上下文；不作为用户步骤 |
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
→ 解析兼容 JD、生成兼容匹配结果，并回写任务模型 / Prompt / Model 快照
→ 通过 OptimizationTask 查看结果或在失败后重试
```

岗位库、独立目标岗位管理、旧“匹配与优化”编排、技术分类式 AI 历史、Dashboard 指标 / Stepper 仍不在前端路由和导航中。

## 4. Phase 2 数据迁移

`V19__create_phase2_core_domain.sql` 是加法式迁移：

- 新建 `job_targets`、`resume_versions`、`optimization_tasks`。
- 通过 `(resource_id, user_id)` 复合外键约束正式模型内的用户归属。
- 每份旧简历回填一个来源为 `LEGACY_IMPORT` 的源版本。
- 每条旧 `job_descriptions` 回填一个 JobTarget，并保留 JD 原文。
- 每条旧 `ai_job_match_results` 回填一个岗位派生版本和一个 OptimizationTask，保存当时可获得的 Resume / JD、Prompt、Model 与结果状态。
- 迁移前拒绝跨用户的旧匹配关系；迁移后验证 Resume、JD、Match 是否全部有正式模型对应项，任一缺失即令 Flyway 事务失败。
- 不修改或删除 V1 业务记录。应用回滚时旧版本可忽略新增表继续运行；正式迁移数据保留，恢复 Phase 2 应用后仍可使用。

在无旧数据的全量迁移和带 Resume / Parse / JD / Match 样本的回填迁移上均已用独立临时 PostgreSQL 数据库执行并校验。

## 5. 与后续 V2 的主要差距

当前尚未具备：

- 岗位要求 ↔ 真实经历 ↔ 当前表达的显式证据映射。
- “可通过表达修改解决”与“真实经历未覆盖”的稳定领域模型。
- 可直接编辑的 Workspace、自动保存、Undo / Redo 和恢复点。
- Diff、Apply / Reject 与事实校验组成的完整可控修改体验。
- Structured Resume JSON 驱动的 Typst Preview / PDF Export。
- 每用户 BYOK、Credential 加密、AI Gateway 与自定义 Base URL SSRF 防护。
- 用户数据导出 / 全量删除和长期多 JD 方向洞察。

Phase 3 是下一阶段。不得把当前兼容匹配输出或 `missingSkills` 直接包装成正式 Capability Gap，也不得在 Phase 3 中提前实现 Workspace、AI Rewrite 或 Typst。

## 6. 已确认并保留的决策

- 模块化单体和前后端分离足以支撑 V2；不引入微服务、消息队列或 Kubernetes。
- 上传的原始文件、`resumes` 元数据和 `resume_parse_results` 不因岗位版本派生而改变。
- `ResumeVersion` 的源版本代表本次分析输入快照；岗位版本通过 `source_version_id` 显式派生，后续编辑不得静默污染源版本。
- `OptimizationTask` 是正式业务任务，`async_tasks` 只是可替换的执行记录。
- 任务创建时冻结原始 JD 和当前 Provider / Model / Rules / Template 配置；成功时补齐实际 Job Parse / Match Prompt 与模型快照。
- 当前解析、RAG 与匹配实现继续通过 V1 表运行，但其 ID 只在后端兼容上下文中使用；默认前端只认识正式任务 ID。
- AI 只能优化表达和排序，不能新增未验证技术、经历、日期、公司、成果或数字。
- Redis 失败应尽量降级，不影响 PostgreSQL 中的核心业务事实。
- 文件读取必须经过用户归属检查；迁移和正式服务同样校验资源归属。
- 数据库迁移以 Flyway 为唯一机制。
- 异步任务使用真实阶段 / 状态，不伪造进度；错误信息必须脱敏。

## 7. 当前质量与风险

- V19 是加法式且可应用回滚，但新增表尚未经历生产规模数据量；部署前仍应按备份流程保存数据库，并观察唯一索引和回填事务耗时。
- 当前 AI 匹配结果仍由 `(resume_id, job_description_id)` 唯一记录覆盖；正式任务快照可解释输入和配置，但多次失败重试的每次 AI 输出不形成独立结果版本。Phase 2 不把它扩展成后续 Evidence / Gap 历史模型。
- 旧后端表、接口和服务仍有解析、匹配、历史、删除及兼容重试依赖，当前不能删除。旧前端的直接 JD / Match API 客户端已因无运行时引用而删除。
- 删除旧 JobDescription 或 Resume 会通过外键删除对应正式任务 / 岗位版本；由任务创建但已失去岗位关系的源快照版本可能随 Resume 保留，后续统一数据生命周期阶段需再次核对。
- 首页的进行中任务恢复仍以会话存储的正式任务引用为主；正式任务可跨设备按 ID 查询，但“最近优化”列表尚未加入首页。
- 本机 Java 25 仍需显式启用 annotation processing 才能生成 Lombok 代码；CI 使用 Java 21。
- 外部 reviewer 仍因仓库外 `pi-permission-system` package manifest 缺失无法启动；两个 Task 均已记录失败并由主执行者按相同维度完成代码、迁移、权限、兼容性和测试自查。

## 8. Phase 2 验证

- 后端 `./mvnw -Dmaven.compiler.proc=full test`：331 个测试通过，2 个真实外部服务 smoke test 跳过。
- 新增 / 更新正式模型、版本派生、快照、归属、成功不可重试、兼容失败重试和正式 API 的单元 / Web MVC 测试。
- 前端 `npm run build`：类型检查和生产构建通过。
- 前端 Oxlint / ESLint：0 warning、0 error。
- Flyway V1–V19 空库迁移、V1–V18 样本数据到 V19 回填、迁移后关系 / 快照 / 用户归属查询：通过。
- `git diff --check`：通过。

## 9. 文档与决策优先级

1. `PRD.md`：V2 产品与最高层架构决策。
2. 真实代码、迁移、配置和测试：当前行为事实。
3. `ARCHITECTURE.md`：当前边界与迁移约束。
4. `PLAN.md`：实施顺序和阶段门禁。
5. `CONTEXT.md`：当前状态和已知差距。
6. `OPERATIONS.md`：当前部署运行方式。
