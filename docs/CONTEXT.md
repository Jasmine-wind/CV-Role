# 项目上下文与当前状态

更新基线：V2 Phase 1（信息架构与主链路简化）已完成。本文只记录**当前事实、已确认决策和已知差距**；产品目标见 [PRD.md](PRD.md)，实现顺序见 [PLAN.md](PLAN.md)。

## 1. 当前定位

仓库当前是一个在 V1 模块化单体上完成 Phase 1 用户链路收敛的实现。普通用户只需选择或上传真实简历、粘贴目标 JD 并开始分析；简历准备、JD 保存与解析、岗位匹配由后台编排，不再要求用户理解或手动执行内部步骤。

Phase 1 只简化信息架构和首次分析链路。`ResumeVersion`、`OptimizationTask`、Evidence / Gap、Workspace、Diff、Typst / PDF、BYOK 等后续能力尚未实现。

## 2. 真实已有能力

| 领域 | 当前实现 |
|---|---|
| 账号 | 注册、登录、JWT、当前用户、受保护前端路由 |
| 简历 | PDF / DOC / DOCX 上传、local / MinIO、读取、删除、文本提取与结构化解析；上传后自动提交后台准备任务 |
| 解析质量 | 清洗、章节识别、质量提示、规则 / AI 辅助；默认用户流不暴露解析模式 |
| 岗位分析 | 首页选择简历、粘贴 JD 并一键启动；后台保存原始 JD、确保简历可用、解析 JD、生成匹配结果 |
| 分析结果 | 以已有优势、值得优先检查的表达和简历当前未体现的要求展示；不把当前匹配结果冒充 Phase 3 的正式 Evidence / Gap 模型 |
| 旧后端能力 | 简历诊断、优化建议、局部改写、聚合报告、预置岗位和历史查询仍存在，供后续迁移与兼容读取；不再有对应默认前端入口 |
| 向量 | pgvector、Embedding、分块、语义相似度、可选 RAG 上下文；不作为用户步骤 |
| 任务 | 单进程线程池、任务表、用户归属校验、真实阶段和前端轮询；首页可从本地任务引用恢复轮询，失败重试复用已保存的简历与 JD |
| 工程 | Flyway、统一异常、校验、日志脱敏、OpenAPI、后端单元 / 集成测试、前端构建 CI |
| 部署 | Docker Compose、Nginx、PostgreSQL、Redis、MinIO、certbot、备份 / 恢复脚本 |

## 3. 当前页面与主流程

当前页面路由包括：Landing、首页、我的简历、岗位分析结果、登录和注册。一级导航只有：首页、我的简历。

当前权威用户流程：

```text
登录
→ 选择已有简历，或上传简历并由后台自动准备
→ 粘贴真实目标岗位 JD
→ 开始分析
→ 后台确保简历可用、保存并解析 JD、生成岗位匹配
→ 查看已有优势、表达检查项和简历当前未体现的要求
```

岗位库、独立目标岗位管理、旧“匹配与优化”编排、技术分类式 AI 历史、Dashboard 指标 / Stepper 已从前端路由和导航删除。对应后端数据与服务尚未迁移或删除，不构成第二套用户主流程。

## 4. 与后续 V2 的主要差距

当前尚未具备：

- `ResumeVersion`、岗位定向版本、可复现的 `OptimizationTask` 输入快照。
- 岗位要求 ↔ 真实经历 ↔ 当前表达的显式证据映射。
- “可通过表达修改解决”与“真实经历未覆盖”的稳定领域模型。
- 可直接编辑的 Workspace、自动保存、Undo / Redo 和恢复点。
- Diff、Apply / Reject 与事实校验组成的完整可控修改体验。
- Structured Resume JSON 驱动的 Typst Preview / PDF Export。
- 每用户 BYOK、Credential 加密、AI Gateway 与自定义 Base URL SSRF 防护。
- 用户数据导出 / 全量删除和长期多 JD 方向洞察。

Phase 2 是下一阶段。不得把 Phase 1 的异步任务或现有 `job_descriptions` / `ai_job_match_results` 表直接包装成最终 `OptimizationTask` 模型，也不得提前进入 Phase 3。

## 5. 已确认并保留的决策

- 模块化单体和前后端分离足以支撑 V2；不引入微服务、消息队列或 Kubernetes。
- 用户粘贴的真实 JD 是主输入；自动采集和招聘网站爬虫不进入范围。
- AI 只能优化表达和排序，不能新增未验证技术、经历、日期、公司、成果或数字。
- 报告与历史查询不触发新的 AI 生成。
- 外部 AI / Embedding、Redis 和文件系统通过后端适配；业务层不直接处理密钥或供应商 HTTP 细节。
- Redis 失败应尽量降级，不影响数据库中的核心业务事实。
- 文件存储由接口抽象，读取必须经过资源归属检查。
- 数据库迁移以 Flyway 为唯一机制。
- 异步任务使用真实阶段 / 状态，不伪造进度；错误信息必须脱敏。
- `ResumeIntakeService` 封装上传、默认准备与准备失败恢复；`JobAnalysisService` 封装从原始 JD 到匹配结果的首次编排及复用已保存输入的重试。页面不得重新拆散这些步骤。

## 6. 当前质量与风险

- 后端测试依赖 PostgreSQL；本地环境配置错误会导致 context test 失败。
- CI 使用 Java 21 和 PostgreSQL 执行 `./mvnw test`；本机 Java 25 需要显式启用 annotation processing 才能生成 Lombok 代码。
- Phase 1 的岗位分析任务会持久化原始 JD 和真实任务状态；失败重试复用已保存的 JD，不要求重新粘贴，但完整跨设备“最近优化”恢复要等 Phase 2 的正式任务模型。
- 上传自动准备与岗位分析均可确保旧简历可用；极端情况下，同一简历在多个页面或标签页同时启动准备仍可能产生重复解析工作，但数据库结果按简历覆盖且不产生第二份业务记录。
- 现有匹配输出只能说明“简历中已有 / 较弱 / 未体现”，不能在 Phase 3 前稳定断言用户真实能力缺口。
- 旧后端接口和表仍需 Phase 2 迁移设计后再决定兼容读取与删除，当前不可直接清表。
- 生产 HTTPS 模板切换和证书操作仍需按 [OPERATIONS.md](OPERATIONS.md) 人工执行。

## 7. Phase 1 验证

- 新增后台简历 intake 与岗位分析编排的单元测试和 Web MVC 接口测试。
- 后端 `./mvnw -Dmaven.compiler.proc=full test`：320 个测试通过，2 个真实外部服务 smoke test 跳过。
- 前端 `npm run build`：通过；ESLint 与 Oxlint：0 warning、0 error。
- Markdown 本地链接、退役前端路由引用和 `git diff --check`：通过。

## 8. 文档与决策优先级

1. `PRD.md`：V2 产品与最高层架构决策。
2. 真实代码、迁移、配置和测试：当前行为事实。
3. `ARCHITECTURE.md`：当前边界与迁移约束。
4. `PLAN.md`：实施顺序和阶段门禁。
5. `CONTEXT.md`：当前状态和已知差距。
6. `OPERATIONS.md`：当前部署运行方式。
