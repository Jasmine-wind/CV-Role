# Phase 4 Task List - 架构演进阶段

## 0. 文件用途

本文件用于拆解 Phase 4 的具体开发任务。

`docs/phase-4-architecture.md` 负责说明第四阶段的总体目标、范围、架构方向和退出标准。  
本文件负责说明 Phase 4 每个版本下具体怎么拆小任务、怎么验收、哪些内容不能提前做。

使用原则：

1. 每次只做一个小任务。
2. 当前正在开发的 v3.x 版本详细展开。
3. 已完成版本压缩成总结。
4. 未开始版本先保留任务大纲，进入对应版本前再继续细分。
5. 完成一个 v3.x 版本后，更新对应迭代日志。
6. 不允许跳过当前任务直接实现后续版本功能。
7. Phase 4 的重点是架构演进、稳定性、异步化、安全和部署准备，不是继续堆 AI 功能。
8. Phase 4 不做微服务拆分，除非后续已经有明确瓶颈和必要性。

前置边界状态：

- v2.11 已完成进入 Phase 4 前的主流程与边界收敛。
- Phase 4 默认沿用“简历资产 + 用户粘贴目标岗位 JD”的主线。
- 岗位优化报告只聚合已有结果，不作为新的 AI 生成功能。

---

## 1. Phase 4 总版本划分

| 版本 | 主题 | 状态 |
|---|---|---|
| v3.1 | 架构现状审查与领域边界收敛 | 已完成 |
| v3.2 | 面向领域的包结构整理 | 已完成 |
| v3.3 | 文件存储抽象与存储策略演进 | 已完成 |
| v3.4 | 长耗时任务异步化设计 | 已完成 |
| v3.5 | 解析与 AI 任务状态机落地 | 已完成 |
| v3.6 | 安全加固与敏感信息治理 | 已完成 |
| v3.7 | 可部署配置与环境隔离 | 已完成 |
| v3.8 | 运维文档、架构复盘与阶段收口 | 已完成 |


---

## 2. Phase 4 总目标

将项目从“功能可用的应用”演进为“结构清晰、边界稳定、可维护、可部署、可排查”的系统。

Phase 4 完成后应满足：

- 核心模块具备稳定边界。
- 业务代码按领域职责组织，而不是随功能堆叠。
- 文件存储通过接口访问，便于本地存储和对象存储切换。
- 长耗时解析和 AI 任务具备明确执行模型。
- 解析、AI 分析、匹配、优化、改写、向量生成等任务具备状态追踪能力。
- 安全假设已经记录，并在代码中落实。
- 本地环境、开发环境和部署环境配置边界清晰。
- 敏感配置不硬编码、不提交。
- 运维和部署文档清晰。
- 不引入没有明确必要性的微服务和分布式复杂度。

---

## 3. Phase 4 禁止提前实现内容

在 Phase 4 中不允许提前实现：

- Spring Cloud 微服务拆分。
- Nacos 服务注册与发现。
- OpenFeign 服务间调用。
- Spring Cloud Gateway。
- Kubernetes。
- 复杂服务治理。
- 复杂多租户 SaaS。
- 商业化支付系统。
- 没有瓶颈依据的消息队列强行改造。
- 全量重写已有稳定功能。
- 为了“架构高级”而牺牲项目稳定性。

说明：

- Phase 4 可以为未来微服务预留边界。
- Phase 4 不直接拆微服务。
- 如果需要引入 RabbitMQ，也必须先证明同步任务已经影响体验或可靠性。
- 默认优先使用单体内异步任务，而不是上来就引入消息队列。

---

# v3.1 - 架构现状审查与领域边界收敛

状态：已完成

## v3.1 目标

在正式进行架构演进前，先审查当前项目的模块结构、包结构、领域边界、任务执行方式、存储方式、安全假设和部署配置，形成清晰的架构问题清单与后续调整优先级。

v3.1 不做大规模代码重构，不改数据库结构，不新增业务功能，主要完成架构现状审查和演进方案确认。

## v3.1 总结

v3.1 已完成架构现状审查与领域边界收敛：

- 已梳理后端 `common / config / security / infra / module` 顶层职责，确认业务代码集中在 `module/` 下。
- 已确认当前后端主要问题不是包结构错位，而是 `ResumeServiceImpl`、简历解析 / 展示相关 Service、`ResumeAnalysisController` 等类体量偏大。
- 已梳理前端页面、路由、API、Store 和组件结构，确认 `ResumeView.vue` 与 `AiJobMatchView.vue` 是后续前端组件化拆分重点。
- 已梳理简历解析、简历诊断、目标岗位解析、匹配分析、优化建议、局部改写和 Embedding 的执行方式。
- 已确认当前长耗时任务仍以同步请求内执行为主，后续优先采用单体内异步线程池 + 状态轮询，不直接引入 RabbitMQ。
- 已审查文件存储、上传限制、用户文件访问权限、JWT、AI Key、Embedding Key、数据库密码、日志脱敏、CORS 和部署配置。
- 已确认系统已有 `FileStorageService` 抽象、本地存储实现、基础上传限制、JWT 认证、BCrypt 和日志脱敏基础。
- 已记录后续高优先级问题：默认授权策略偏宽、生产部署配置不完整、CORS 未环境化、MinIO 配置与实现不一致、文件删除一致性需要优化。
- 已形成 Phase 4 后续调整顺序：v3.2 先做低风险结构整理，v3.3 完善存储，v3.4 / v3.5 处理异步任务和状态机，v3.6 进行安全加固，v3.7 / v3.8 完成部署与运维收口。
- 已明确 Phase 4 继续保持单体架构，不做微服务拆分。
- 详细审查结果已记录到 `docs/architecture-review.md`。
- 迭代日志已更新：`docs/iteration-log/v3.1-architecture-review.md`。

说明：

- v3.1 只做审查和边界确认。
- v3.1 不修改后端业务代码、前端代码或数据库结构。
- v3.1 不新增 AI 能力，不改接口路径，不做微服务拆分。

## v3.1 小任务完成情况

| 小任务 | 状态 |
|---|---|
| v3.1.1 当前后端模块结构审查 | 已完成 |
| v3.1.2 当前前端模块结构审查 | 已完成 |
| v3.1.3 长耗时任务现状审查 | 已完成 |
| v3.1.4 存储、安全与部署现状审查 | 已完成 |
| v3.1.5 v3.1 架构审查日志 | 已完成 |

---

# v3.2 - 面向领域的包结构整理

状态：已完成

## v3.2 目标

在不改变核心业务行为的前提下，整理后端代码包结构，使用户、简历、解析、岗位、AI、历史、存储、安全等职责边界更加清晰。

v3.2 是小步代码结构整理版本，不做功能重写，不改接口语义，不改数据库结构。

---

## v3.2 总结

v3.2 已完成面向领域的包结构整理：

- 已扫描当前后端包结构，确认 `common / config / security / infra / module` 顶层边界保持不变。
- 已确认业务模块继续集中在 `module/auth`、`module/user`、`module/resume`、`module/job`、`module/analysis`、`module/history`、`module/embedding`。
- 已将 AI 输出解析实现类按子领域移动到 `module/analysis/diagnosis`、`match`、`suggestion`、`rewrite` 下，接口和业务入口保持不变。
- 已新增 `AnalysisVoAssembler`，将分析模块 Entity / JSON 到 VO 的转换从 Controller 中移出。
- 已复用 `AnalysisVoAssembler` 消除局部改写采纳状态接口中的重复 VO 组装逻辑。
- 已将 Embedding Client 接口、配置和 OpenAI-compatible 实现从 `infra.ai` 移动到 `infra.embedding`。
- 已确认报告接口只聚合已有结果，不重新调用 AI。
- 已确认历史接口只查询已有结果，不触发新的 AI 生成。
- 已更新 `docs/project-structure.md`，补充 `infra.embedding` 的基础设施边界。
- 已完成相关单元测试和后端编译检查。
- 迭代日志已更新：`docs/iteration-log/v3.2-package-structure.md`。

说明：

- v3.2 不改接口路径。
- v3.2 不改数据库结构。
- v3.2 不新增 AI 能力。
- v3.2 不拆微服务。
- 本轮未修改前端页面、路由或 API 调用，前端主流程由用户手动验收。
- 高风险大类如 `ResumeServiceImpl`、简历解析 / 展示相关实现类仍保留原位，后续继续小步整理。

## v3.2 小任务完成情况

| 小任务 | 状态 |
|---|---|
| v3.2.1 目标包结构确认 | 已完成 |
| v3.2.2 领域模块边界整理 | 已完成 |
| v3.2.3 Controller / Service 职责整理 | 已完成 |
| v3.2.4 基础设施层职责整理 | 已完成 |
| v3.2.5 v3.2 联调、审查与日志 | 已完成 |

---

# v3.3 - 文件存储抽象与存储策略演进

状态：已完成

## v3.3 目标

完善文件存储抽象，使简历文件访问不依赖具体本地路径，便于后续从本地存储切换到 MinIO 或其他对象存储。

v3.3 不要求立刻正式接入 MinIO，也不迁移历史文件。本版本重点是把“文件怎么存、怎么读、怎么删除、怎么生成访问引用”抽象清楚，避免业务代码直接依赖本地磁盘路径。

---

## v3.3 总结

v3.3 已完成文件存储抽象与存储策略演进：

- 已审查简历上传、读取、解析、删除、配置、数据库字段和 MinIO 预留状态。
- 已将 `FileStorageService` 统一为 `StoreFileCommand` 输入、`storageKey` 语义、文件流读取、存在性判断、元信息读取和删除能力。
- 已保持数据库 `objectKey` 字段兼容历史数据，但业务语义收敛为内部 `storageKey`，不作为前端可见字段。
- 已拆出 `LocalStoragePathResolver` 和 `SafeFilenameGenerator`，本地实现集中处理路径生成、安全文件名和路径穿越防护。
- 已支持 `app.storage.type=local`、`app.storage.local.base-dir`、`APP_STORAGE_LOCAL_BASE_DIR`，并兼容旧的 `LOCAL_STORAGE_BASE_DIR`。
- 已预留 MinIO 配置属性和切换方案，当前不引入 MinIO SDK、不默认启用对象存储。
- 已移除上传响应中的内部 `objectKey`，前端不再接收服务器真实路径或内部存储引用。
- 已补充 PDF / DOC / DOCX 扩展名与 content-type 一致性校验。
- 已确认 Controller 不直接操作文件系统，简历解析通过 `FileStorageService.loadAsStream` 读取文件流。
- 已确认用户只能访问自己的简历，越权解析和越权删除不会读取或删除文件。
- 已修复删除链路遗漏的 AI 优化建议和局部改写建议清理问题，避免生成过建议的简历因外键引用删除失败。
- 已完成后端测试、后端编译、前端构建和用户手动验收。
- 已更新 `docs/phase-4-architecture.md` 和 `docs/iteration-log/v3.3-file-storage.md`。

说明：

- v3.3 不改接口路径。
- v3.3 不改数据库结构。
- v3.3 不新增 AI 能力。
- v3.3 不拆微服务。
- MinIO 当前只是配置和设计预留，不代表已经可用。
- 文件物理删除失败后的补偿清理仍保留为后续一致性任务。

## v3.3 小任务完成情况

| 小任务 | 状态 |
|---|---|
| v3.3.1 当前文件存储实现审查 | 已完成 |
| v3.3.2 FileStorageService 接口规范 | 已完成 |
| v3.3.3 本地文件存储实现整理 | 已完成 |
| v3.3.4 MinIO 预留配置与实现方案 | 已完成 |
| v3.3.5 文件访问安全校验 | 已完成 |
| v3.3.6 v3.3 联调、审查与日志 | 已完成 |

---

# v3.4 - 长耗时任务异步化设计

状态：已完成

## v3.4 总结

v3.4 已完成单体内异步任务基础设施建设，目标是为后续解析、AI 诊断、岗位解析、匹配分析、优化建议和 Embedding 等长耗时任务提供统一的“任务记录 + 状态查询 + 前端轮询 + 失败记录”基础能力。

本版本保持单体架构，不引入 MQ，不拆微服务，不改变已有 AI 输出语义，不删除旧同步接口。当前业务按钮仍保留同步流程，完整“提交任务 -> 返回 taskId -> 后台执行 -> 轮询状态 -> 拉取结果”的业务异步闭环进入 v3.5 落地。

已完成内容：

- 已梳理长耗时接口和异步化优先级，明确报告聚合、历史查询等只读接口不进入异步系统。
- 已新增单体内可配置线程池 `applicationTaskExecutor` 和 `app.async` 配置。
- 已新增 `async_tasks` 表和 `module/task`，支持任务创建、运行中、进度更新、成功、失败和按用户归属查询。
- 已新增 `GET /api/tasks/{taskId}`，并纳入登录后访问范围。
- 已新增前端任务状态 API、轮询工具和简历页任务状态面板。
- 已新增统一异步任务错误码和失败处理器，后端记录详细异常，前端只展示友好错误信息。
- 已完成后端测试、前端构建和手动验收收口。

当前不足：

- 旧同步接口仍会阻塞 Web 请求线程。
- 业务提交接口尚未返回 `taskId`，具体 worker 尚未接入线程池和失败处理器。
- 前端已有轮询工具和简历页面板，但还没有全局任务 Store。
- Embedding 已有 chunk 级状态，但缺少任务级聚合状态。

## v3.4 小任务完成情况

| 小任务 | 状态 |
|---|---|
| v3.4.1 长耗时任务异步化方案设计 | 已完成 |
| v3.4.2 单体内线程池配置 | 已完成 |
| v3.4.3 任务状态字段与状态流转设计 | 已完成 |
| v3.4.4 前端轮询策略设计 | 已完成 |
| v3.4.5 异步任务错误处理策略 | 已完成 |
| v3.4.6 v3.4 文档与验证 | 已完成 |

## v3.4 验证结果

- `./mvnw test -Dtest=AsyncTaskConfigTest`：1 个测试通过。
- `./mvnw test -Dtest=AsyncTaskServiceImplTest,AsyncTaskConfigTest`：7 个测试通过。
- `./mvnw test -Dtest=AsyncTaskFailureHandlerImplTest,AsyncTaskServiceImplTest,AsyncTaskConfigTest`：9 个测试通过。
- `./mvnw test -DskipTests`：后端主代码和测试代码编译通过。
- `npm run build`：前端类型检查和 Vite 构建通过。
- 手动验收已完成：现有同步上传、解析、诊断主流程不被破坏，任务状态查询和前端轮询基础能力可用。

---

# v3.5 - 解析与 AI 任务状态机落地

状态：已完成

## v3.5 总结

v3.5 已在 v3.4 异步任务基础设施之上完成首批业务异步闭环，将简历解析、简历诊断和简历向量生成接入“提交任务 -> 返回 taskId -> 后台执行 -> 前端轮询 -> 成功刷新 / 失败提示”的流程。

本版本继续保持单体架构，不引入 MQ，不拆微服务，不改变 AI 输出语义，并保留旧同步接口。前端优先在 `/resumes` 页面完成任务状态面板、轮询、平滑进度、失败原因展示和行级按钮禁用。

已完成内容：

- 已确认首批异步任务为 `RESUME_PARSE`、`RESUME_DIAGNOSIS`、`RESUME_EMBEDDING`。
- 已新增简历解析、简历诊断、简历向量生成三个异步提交接口。
- 已新增 `ResumeAsyncTaskService`，复用原解析、诊断和向量生成逻辑，通过 `applicationTaskExecutor` 后台执行。
- 已补齐同一用户、同一业务对象的运行中任务防重复提交策略。
- 已补充向量任务失败原因透传，Embedding 服务不可用、未解析、解析未成功等场景可显示明确原因。
- 已优化前端任务进度展示，运行中从 0 平滑递增，成功后再切到 100% 并刷新结果。
- 已完成后端测试、前端构建和手动验收收口。

当前不足：

- 目标岗位解析、匹配分析、岗位优化建议、岗位向量生成和局部改写仍保留同步流程。
- 前端任务展示先落在 `/resumes` 页面，尚未抽成全局任务中心。
- 单体内线程池任务不支持后端进程重启后的自动恢复。

## v3.5 小任务完成情况

| 小任务 | 状态 |
|---|---|
| v3.5.1 选择首批异步化任务 | 已完成 |
| v3.5.2 简历解析任务状态化 | 已完成 |
| v3.5.3 AI 分析任务状态化 | 已完成 |
| v3.5.4 向量生成任务状态化 | 已完成 |
| v3.5.5 前端任务状态展示 | 已完成 |
| v3.5.6 v3.5 联调、审查与日志 | 已完成 |

## v3.5 验证结果

- `./mvnw test -Dtest=ResumeAsyncTaskServiceImplTest,AsyncTaskServiceImplTest,AsyncTaskFailureHandlerImplTest,AsyncTaskConfigTest`：后端异步任务测试通过。
- `./mvnw test -Dtest=ResumeAsyncTaskServiceImplTest,AsyncTaskFailureHandlerImplTest,ResumeEmbeddingServiceImplTest,OpenAiCompatibleEmbeddingClientServiceTest`：向量任务失败原因测试通过。
- `./mvnw test -DskipTests`：后端主代码和测试代码编译通过。
- `./mvnw test -Dtest=Phase1ApiIntegrationTest`：Phase 1 API 集成测试通过。
- `npm run build`：前端类型检查和 Vite 构建通过。
- 手动验收已完成：简历解析、简历诊断、简历向量生成均可提交任务并展示状态，失败场景可显示原因。

---

# v3.6 - 安全加固与敏感信息治理

状态：已完成

## v3.6 总结

v3.6 已完成认证授权、用户数据访问、文件上传与访问、敏感配置、日志错误信息和 AI 输入输出隐私边界的安全收口。该版本不新增业务功能，不引入复杂权限系统，不改变 AI 输出语义，重点是把演示项目上线前容易暴露风险的边界补齐。

已完成内容：

- 已梳理安全风险清单，明确立即修复项和后续部署阶段处理项。
- 已将默认授权策略从 `anyRequest().permitAll()` 收敛为 `anyRequest().authenticated()`，仅保留注册、登录、Swagger 和预置岗位库公开访问。
- 已检查简历、目标岗位、匹配分析、优化建议、局部改写、报告、历史、异步任务和 Embedding 的用户归属校验。
- 已加固上传文件内容签名校验，PDF / DOC / DOCX 均需扩展名、MIME 和真实文件内容匹配。
- 已补齐 storageKey 反斜杠、空字节和路径穿越拦截，前端不暴露服务器真实路径或内部 `objectKey`。
- 已治理 `.env.example`、`docker-compose.yml` 和 README 中容易被照抄的本地默认密码，真实密钥保持通过环境变量或 `.env` 注入。
- 已扩展 `LogSanitizer`，覆盖 Token、API Key、secret、password、手机号、邮箱和本地路径脱敏。
- 已规范业务异常、异步任务失败原因和 AI / Embedding 错误日志，避免前端或日志暴露堆栈、本地路径和完整敏感信息。
- 已记录 AI / Embedding 输入输出边界，明确不会发送 Token、API Key、请求头、服务器路径、数据库密码或 MinIO Secret。
- 已完成后端测试、前端构建和手动安全验收收口。

当前不足：

- CORS、生产 Profile 和部署环境变量拆分留到 v3.7 处理。
- 前端 token 仍存储在 `localStorage`，适合当前展示项目，生产化需后续评估更严格方案。
- 历史已保存的错误信息未在本阶段回刷清洗。
- DOC/DOCX 深度恶意内容扫描、模型侧数据保留策略和用户数据保留期限属于后续上线专项。

## v3.6 小任务完成情况

| 小任务 | 状态 |
|---|---|
| v3.6.1 安全清单整理 | 已完成 |
| v3.6.2 用户数据访问控制检查 | 已完成 |
| v3.6.3 文件上传与文件访问安全加固 | 已完成 |
| v3.6.4 敏感配置治理 | 已完成 |
| v3.6.5 日志脱敏与错误信息规范 | 已完成 |
| v3.6.6 AI 输入输出隐私边界说明 | 已完成 |
| v3.6.7 v3.6 联调、审查与日志 | 已完成 |

## v3.6 验证结果

- `./mvnw test -Dtest=Phase1ApiIntegrationTest`：权限与主流程集成测试通过。
- `./mvnw test -Dtest=ResumeServiceImplTest,LocalFileStorageServiceTest`：文件上传、签名校验、越权访问和路径穿越测试通过。
- `./mvnw test -Dtest=LogSanitizerTest,Phase1ApiIntegrationTest,ResumeAiStructuredParserImplTest,ResumeAiSectionClassifierImplTest,ResumeEmbeddingServiceImplTest,JobDescriptionEmbeddingServiceImplTest,AsyncTaskFailureHandlerImplTest`：日志脱敏、AI 解析和任务失败处理测试通过。
- `./mvnw test -Dtest=Phase1ApiIntegrationTest,ResumeServiceImplTest,LogSanitizerTest`：v3.6 收口回归通过。
- `npm run build`：前端类型检查和 Vite 构建通过。
- `git diff --check`：通过。
- 手动验收已完成：认证授权、文件上传、配置、日志错误和 AI 隐私边界均通过。

---

# v3.7 - 可部署配置与环境隔离

状态：已完成

## v3.7 总结

v3.7 已完成本地、开发、生产环境配置边界整理，补齐环境变量模板、生产 Profile、依赖 Compose、Nginx 反向代理草案和部署检查清单。该版本不新增业务功能，不改核心业务逻辑，重点是把“本地能跑”和“服务器可部署”区分清楚，避免本地路径、本地默认密码、调试日志和开发 Profile 被带入生产环境。

已完成内容：

- 已将默认 Profile 收敛为 `local`，并补齐 `application-local.yaml`、`application-prod.yaml`。
- 已整理 `.env.example` 和 `web/.env.example`，覆盖数据库、JWT、AI、Embedding、异步任务、文件存储、MinIO、Redis、日志和前端 API 地址。
- 已将生产配置关键项改为通过环境变量注入，避免在 `application-prod.yaml` 中写入真实密钥或本机路径。
- 已补充 Redis 依赖服务到 `docker-compose.yml`，并明确当前 Compose 只编排 PostgreSQL、Redis、MinIO 等依赖，不包含后端 / 前端应用镜像。
- 已新增 `deploy/nginx/ai-resume.conf` 和 `deploy/nginx/README.md`，形成 Nginx 反向代理草案。
- 已新增 `docs/deployment.md`，整理服务器准备、环境变量、构建、启动、部署后验收、安全检查和回滚清单。
- 已同步 README、架构审查文档和 v3.7 迭代日志。
- 已完成后端生产 Profile 打包、前端构建、Compose 配置展开和 Nginx 草案内容检查。

当前不足：

- 未进行真实服务器部署，v3.7 只完成可部署配置准备。
- 当前环境未安装 `nginx`，未执行 `nginx -t`。
- 仓库暂无后端 / 前端应用 Dockerfile，Compose 仍只用于依赖服务。
- 应用容器镜像、systemd 服务、HTTPS、文件日志、MinIO 实现、Redis 实际接入和 CORS 配置化留到后续阶段。

## v3.7 小任务完成情况

| 小任务 | 状态 |
|---|---|
| v3.7.1 Profile 设计 | 已完成 |
| v3.7.2 环境变量清单整理 | 已完成 |
| v3.7.3 application-local / application-prod 配置整理 | 已完成 |
| v3.7.4 Docker Compose 部署配置整理 | 已完成 |
| v3.7.5 Nginx 反向代理配置草案 | 已完成 |
| v3.7.6 部署检查清单 | 已完成 |
| v3.7.7 v3.7 联调、审查与日志 | 已完成 |

## v3.7 验证结果

- `./mvnw test -DskipTests`：后端主代码和测试代码编译通过。
- `./mvnw test -Dtest=Phase1ApiIntegrationTest`：主流程集成测试通过。
- `SPRING_PROFILES_ACTIVE=prod ./mvnw -q -DskipTests package`：生产 Profile 打包通过。
- `npm run build`：前端类型检查和 Vite 构建通过。
- `docker compose config`：Compose 配置解析通过。
- `docker compose --env-file .env.example config`：示例环境变量下 Compose 配置解析通过。
- `rg -n "server_name|location /api/|proxy_pass|try_files|client_max_body_size" deploy/nginx/ai-resume.conf`：Nginx 草案关键配置存在。
- `git diff --check`：通过。

---

# v3.8 - 运维文档、架构复盘与阶段收口

状态：已完成

## v3.8 总结

v3.8 已完成 Phase 4 的最终收口，重点沉淀部署、运维、排查、架构图和 Phase 5 进入判断。该版本不新增业务功能，不进行真实服务器部署，不虚构生产级高可用能力，只把 Phase 4 已完成的架构演进和部署准备整理成后续可复用的文档资产。

已完成内容：

- 已补齐 `docs/deployment.md`，覆盖部署目标、环境变量、数据库、后端、前端、Compose、Nginx、文件存储、AI / Embedding、验证和回滚。
- 已新增 `docs/ops.md`，覆盖服务组成、日志查看、服务启停、数据库备份恢复、上传文件备份、配置修改和日常巡检。
- 已新增 `docs/troubleshooting.md`，覆盖启动失败、数据库连接、前端请求、文件上传、AI、Embedding、异步任务和 Nginx 常见问题。
- 已新增 `docs/architecture/architecture-diagrams.md`，用 Mermaid 整理系统总体架构图、后端模块图、主业务流程图、异步任务流程图和部署图，并预留 GPT Image 生成提示词。
- 已将 Phase 5 进入判断合并到 `docs/phase-5-productization-deployment.md`、本任务清单和 v3.8 迭代日志，不再保留单独的进入条件文件。
- 已压缩整理 `docs/iteration-log/v3.8-phase-4-summary.md`，形成 v3.8 和 Phase 4 最终收口日志。

当前不足：

- 尚未进行真实服务器部署。
- 尚未配置正式域名和 HTTPS。
- 当前环境未执行 `nginx -t`。
- 仓库暂无后端 / 前端应用 Dockerfile。
- Compose 仍只编排 PostgreSQL、Redis、MinIO 等依赖服务，不包含后端 / 前端应用镜像。
- MinIO、Redis、文件日志、CORS 配置化、systemd 服务和备份脚本仍留到 Phase 5 正式部署任务。
- 前端 UI / UX 深度产品化、截图包装和面试讲解材料仍留到 Phase 5。

## v3.8 小任务完成情况

| 小任务 | 状态 |
|---|---|
| v3.8.2 部署文档初版 | 已完成 |
| v3.8.3 运维文档初版 | 已完成 |
| v3.8.4 常见问题排查 | 已完成 |
| v3.8.5 架构图和模块图整理 | 已完成 |
| v3.8.6 Phase 5 进入条件确认 | 已完成 |
| v3.8.7 v3.8 阶段日志 | 已完成 |

## v3.8 验证结果

- `rg` 检查部署文档关键章节：通过。
- `rg` 检查运维文档关键章节：通过。
- `rg` 检查常见问题排查文档关键章节：通过。
- `rg` 检查架构图文档关键图表和 GPT Image 提示词：通过。
- `rg` 检查 Phase 5 进入判断已合并到现有文档：通过。
- `git diff --check`：通过。

## Phase 5 进入判断

允许进入 Phase 5。

该结论只表示可以进入“产品化优化、正式部署和项目包装阶段”，不表示已经完成正式服务器上线、HTTPS、应用容器化或生产级运维。

---

# Phase 4 完成后的下一步

Phase 4 完成后，可以进入：

```text
Phase 5 - 产品化优化、部署上线与项目包装阶段
```

Phase 5 再开始重点处理：

- 产品体验复盘。
- 前端 UI/UX 深度优化。
- 核心页面视觉重构。
- 服务器正式部署。
- 域名和 HTTPS。
- 数据备份和运维。
- README、演示文档和项目截图。
- 简历包装与面试讲解材料。

---
