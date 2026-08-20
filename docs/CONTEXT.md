# 项目上下文与当前状态

本文是仓库唯一的项目级 Context Source of Truth，只记录**当前实现事实、已确认约束、未完成能力和仍有效风险**。产品决策以 [PRD.md](PRD.md) 为准，架构边界见 [ARCHITECTURE.md](ARCHITECTURE.md)，阶段顺序和 Gate 见 [PLAN.md](PLAN.md)；当前行为最终以代码、Flyway 迁移、配置和测试为准。

## 1. 当前阶段

| Phase | 状态 | 当前结果 |
|---|---|---|
| Phase 1 | 已完成 | 首页收敛为选择 / 上传简历、粘贴 JD、一键分析；解析和分析由后台编排 |
| Phase 2 | 已完成 | 建立 `ResumeVersion`、`JobTarget`、`OptimizationTask`，主链路改用正式任务身份和冻结快照 |
| Phase 3 | 已完成，Gate 已通过 | 建立正式 Evidence Matching / Gap Analysis，三态为 MATCHED / PARTIAL_EVIDENCE / NO_EVIDENCE |
| Phase 4 | 已完成，Gate 已通过 | 建立两栏 Workspace、结构化编辑、Undo / Redo、自动保存、乐观并发和恢复优化前版本 |
| Phase 5 | 已完成，Gate 已通过 | 建立单 Bullet AI Suggest、代码 Diff、Apply / Reject / Regenerate；真实性与严格 Parser Blocker 修复已通过独立复审 |
| Phase 6 | 尚未开始 | Typst Preview、PDF 与导出物尚未实现 |

当前停止在 Phase 5。Phase 6 尚未开始；不得把 Phase 6 或后续 P0 / P1 / P2 能力描述为已实现，也不得在没有明确指令时提前进入 Phase 6。

## 2. 当前系统与主流程

当前系统是 Vue 3 SPA + Spring Boot 模块化单体，使用 PostgreSQL / Flyway / pgvector、Redis、Local / MinIO，以及 OpenAI-compatible Chat / Embedding 接口。PostgreSQL 是业务事实来源；Redis 只保存可重建内容；文件访问统一经过存储抽象和用户归属校验。

当前前端路由只有 Landing、首页、我的简历、岗位分析结果、优化工作区、登录和注册；一级导航只有首页和我的简历。岗位库、独立目标岗位管理、旧匹配编排和技术分类式 AI 历史不在默认用户流中。

```text
登录
→ 选择已有简历，或上传后由后台自动准备
→ 粘贴目标岗位 JD
→ 创建 JobTarget + SOURCE / TARGETED ResumeVersion + OptimizationTask
→ 冻结 Resume / JD 输入并完成岗位解析
→ 生成正式 Evidence / Gap 分析
→ 通过 optimizationTaskId 查看结果并进入 Workspace
→ 人工编辑 TARGET，或对单个 Bullet 请求受约束 AI 建议
→ 查看 Diff，显式 Apply / Reject / Regenerate
→ Apply 后复用 Undo / Auto Save / expectedRevision CAS
```

失败分析按同一 `OptimizationTask` 重试并复用冻结输入；成功任务不可被重试改写。历史任务缺少正式证据分析时只兼容读取旧结果，不允许启用 Phase 5 岗位定向改写。

## 3. 当前领域与数据事实

- `ResumeVersion`：每次新分析创建独立 `SOURCE` 输入快照和由其派生的 `TARGETED` 岗位版本；Workspace 只写 TARGET，不修改上传简历、解析结果、SOURCE 或任务冻结快照。
- `JobTarget`：保存用户归属、原始 JD、标题和来源；当前仍通过兼容引用复用 `job_descriptions` 的解析能力。
- `OptimizationTask`：正式业务身份和前端路由身份，保存版本关系、输入快照及 Prompt / Rules / Provider / Model / Template 配置快照；`async_tasks` 只承担执行状态和轮询。
- 正式证据分析：每个任务最多一条 `evidence_analyses`，子表为 `evidence_requirements` 和 `requirement_evidences`；正式主链路不再向 `ai_job_match_results` 写新结果。
- Workspace 文档：`RESUME_DOCUMENT_V1` 是唯一规范编辑结构，持久化在 `resume_versions.structured_content`，不存在第二套 Workspace 内容字段。
- 内容并发：`resume_versions.content_revision` 是服务端乐观并发版本；保存和恢复都必须携带 `expectedRevision` 并通过单条条件更新递增。冲突保留本地草稿，不允许无条件覆盖。

当前正式迁移为：

- V19：加法式建立 Phase 2 正式领域并回填可验证的 V1 数据，保留旧表。
- V20：加法式建立 Phase 3 正式证据表，不用无可追溯引用的旧匹配结果伪造正式 Evidence。
- V20.1：将正式语义收敛为 MATCHED / PARTIAL_EVIDENCE / NO_EVIDENCE 和 SUFFICIENT / PARTIAL；旧 Phase 3 派生分析失效后可用冻结输入重试，V1 历史不变。
- V21：只增加 `content_revision BIGINT NOT NULL DEFAULT 0`，不增加第二个内容字段。

## 4. 必须保持的设计约束

### 用户归属与数据边界

- 保持前后端分离和模块化单体；未经新的产品基线，不引入微服务、消息队列或 Kubernetes。
- 所有资源读取、写入和文件访问都必须校验 `current_user + resource_id`；正式关系继续使用服务校验和复合所有权外键。
- 数据库结构只由 `backend/src/main/resources/db/migration/` 下的 Flyway 迁移维护；不得修改已发布迁移改变生产状态。
- 原始 Resume、原始 JD、任务输入和配置快照必须可追溯；岗位版本不得静默污染源版本。
- Redis 不能成为唯一业务事实来源；存储访问必须经过 `FileStorageService` 抽象；日志和客户端错误不得泄露凭据或原始供应商秘密。
- 异步任务必须使用真实状态 / 阶段，不伪造进度百分比；失败必须保留已保存的 Resume / JD 和冻结输入，不能要求用户重做前置步骤。

### Evidence 与真实性

- MATCHED 表示冻结材料足以支持完整要求；PARTIAL_EVIDENCE 表示存在直接相关但不完整的材料证据；NO_EVIDENCE 只表示当前材料未找到证据，不代表用户没有该能力。
- Requirement 必须来自冻结 JD；MATCHED / PARTIAL_EVIDENCE 必须保留逐字命中冻结 SOURCE ResumeVersion 且与要求相关的 Evidence；无有效 Evidence 时必须降级为 NO_EVIDENCE，且 NO_EVIDENCE 不保存 Evidence。
- PARTIAL_EVIDENCE 不授权 AI 补全技能、经历、数字或成果。新增事实只能来自用户补充 / 确认或独立事实来源；该确认交互当前尚未实现。
- Resume 和 JD 都是不可信输入，不能覆盖平台指令、Schema、权限或真实性约束；AI 输出必须经过受控 DTO / Schema 解析和代码校验后才能进入业务流程。

### Workspace 与 Phase 5

- `optimizationTaskId` 是 Workspace 唯一入口；服务端负责解析 Task → SOURCE / TARGET / JobTarget / Resume / User 完整链路，前端不能指定可写 ResumeVersion。
- TARGET 是唯一可编辑版本；SOURCE、`resume_input_snapshot` 和 Evidence 始终只读。TARGET 编辑后不实时重算分析，左栏明确展示分析时的 SOURCE Evidence。
- Undo / Redo 只属于当前页面会话；刷新后只恢复最后成功保存的服务端内容；localStorage / sessionStorage 不是正式简历内容恢复源。
- AI Suggest 只处理用户明确选中的单个 Bullet。平台策略进入 SYSTEM，简历、JD、Evidence 和本次要求进入标记为不可信的 USER 数据区；Prompt 使用单遍模板替换。
- Suggestion 只存在于当前前端会话，服务端只读生成且没有服务端 Apply。Suggest / Reject / Regenerate 不修改 TARGET 或 revision。
- 事实闭包只以当前 Bullet 原文为基线，不跨 Bullet 或从 SOURCE / Evidence 搬运新事实。新增或升级技术、实体、数字、量化、责任级别、成果、因果、范围或时间必须拒绝；无法可靠判断时 fail closed。
- 候选绑定 requestId、baseRevision、草稿变更序号、bulletId 和原文哈希。人工编辑、Undo / Redo、Restore、revision 变化、冲突、任务切换、Regenerate 替代或乱序响应都会使候选失效。
- Apply 必须由用户显式触发并再次验证候选，只替换对应 Bullet，形成一个 Undo 节点，然后进入既有 dirty → Auto Save → CAS；不得绕过 Phase 4 并发协议。

## 5. 尚未实现

- 缺少事实时向用户询问并记录真实补充 / 确认。
- 用户 Profile / Rules 与平台策略的完整分层；Phase 5 只有平台默认策略和本次自定义要求。
- Typst Preview、PDF、ExportArtifact 和导出前检查。
- 每用户 BYOK、Credential 加密、统一 AI Gateway、自定义 Base URL SSRF 防护和 Usage 记录。
- 用户数据导出 / 全量删除、最近优化列表和长期多 JD 求职方向洞察。

以上能力必须继续按 `PLAN.md` 顺序推进；本次上下文整理不进入 Phase 6。

## 6. 当前技术债与遗留风险

- `requirement_evidences.source_resume_version_id` 的数据库外键只直接约束同用户，没有直接约束为所属任务的 SOURCE；正式服务当前固定写入并校验任务 SOURCE，后续如补强数据库约束必须使用新迁移。
- V19 / V20 的新增表尚未经过生产规模数据验证；V20.1 会原位改变正式枚举和列语义，旧 Phase 3 应用不能运行在迁移后 Schema 上，部署必须同步升级应用与 Flyway 并遵循备份流程。
- Workspace CAS 已有单元和 Web MVC 覆盖，但仍缺真实 PostgreSQL 多线程争用与事务故障注入集成测试。
- Workspace 转换器对未知 / 错误类型、超限内容和无法完整转换的旧快照会整体 fail closed；少量旧数据可能需要重新解析，不能用不完整投影覆盖 TARGET。
- 初始 Workspace 元素 ID 按位置派生，只对同一冻结快照的重复转换稳定，并非语义或内容寻址 ID；Restore 会恢复基线位置 ID。当前 Phase 5 还绑定 revision、草稿序号和原文哈希，未来功能不得只凭元素 ID 判断候选仍有效。
- 正式 Evidence 目前只保存 SOURCE 版本、section label 和逐字 quote，没有 Workspace 元素 ID 或字符范围。当前单 Bullet 手动选择绕开了该缺口，但可靠的“查看原文”、从建议跳转到编辑位置和更细来源追踪仍缺正式锚点模型。
- 正式证据分析依赖 JD 结构化解析质量；解析失败或信息不足时只能返回少量要求或整体失败，不能猜测补全。推理型模型还需要足够输出 token，切换模型或 Provider 时必须重新验证额度。
- 旧表、接口和服务仍被解析、历史读取、删除和兼容重试依赖，当前不能直接删除。删除 Resume / JobDescription 的级联影响和可能残留的源快照仍需在统一数据生命周期阶段核对。
- RewriteFactValidator 已从有限危险词拒绝收紧为保守的可证明安全子集：完整 Latin token、数字—单位—对象关系、否定极性、能力程度、责任层级、成果 / 因果、时间 / 范围、Unicode 控制字符及未知中文事实片段无法由当前 Bullet 原文确定支持时一律拒绝。该实现有意允许误拒，用户仍可手工编辑；不得用第二次 LLM、Embedding 或相似度判断放宽真实性门禁。
- AI Suggest 是同步请求，当前无限流；服务端 AI 默认超时 30 秒、前端请求超时 65 秒。生成窗口内的并发编辑通过候选失效和 CAS 防止落库覆盖，但后续运维仍可评估频控。
- 首页进行中任务恢复主要依赖会话中保存的任务引用，尚无“最近优化”列表；正式任务本身可按 ID 跨设备查询。
- 前端主 chunk 超过 Vite 500 kB 提示阈值，主要来自 Element Plus 全量引入；不影响当前正确性，但属于后续体验 / 性能优化项。
- 本机使用 Java 25 时需要显式开启 annotation processing 才能生成 Lombok 代码；CI 的标准运行环境是 Java 21。

## 7. 文档与事实优先级

1. [PRD.md](PRD.md)：V2 产品和最高层架构决策。
2. 代码、Flyway 迁移、配置和测试：当前行为事实。
3. [ARCHITECTURE.md](ARCHITECTURE.md)：当前实现边界和演进约束。
4. [PLAN.md](PLAN.md)：阶段顺序、Gate 和非目标。
5. 本文：当前状态、已确认约束、差距和风险。
6. [OPERATIONS.md](OPERATIONS.md)：部署与运行方式。

阶段 Gate 的详细完成记录保留在 `PLAN.md` 和 Git 历史中，不在本文重复维护逐轮测试数量、审查过程或迭代日志。
