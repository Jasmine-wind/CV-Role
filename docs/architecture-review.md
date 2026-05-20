# 架构现状审查

本文档记录 Phase 4 v3.1 架构现状审查结果。v3.1 只做审查和边界记录，不进行大规模代码移动，不修改业务逻辑，不修改数据库结构。

---

## v3.1.1 当前后端模块结构审查

状态：已完成

审查范围：

```text
backend/src/main/java/com/winter/airesumeoptimizer/
```

重点审查：

- 后端根包结构。
- `common`、`config`、`security`、`infra`、`module` 职责边界。
- 业务模块组织方式。
- Controller / Service / Mapper / DTO / VO / Entity 边界。
- 体量偏大的类和后续需要拆分的方向。

### 1. 当前后端目录结构

当前后端根包为：

```text
com.winter.airesumeoptimizer
├── AiResumeOptimizerApplication.java
├── common
├── config
├── security
├── infra
└── module
```

整体结论：

- 根目录没有发现业务包直接平铺在 `com.winter.airesumeoptimizer` 下。
- 业务代码集中在 `module/` 下，符合当前项目结构约束。
- 基础设施代码集中在 `infra/` 下，主要包含 AI Client、Embedding Client、Prompt 模板加载和文件存储。
- 安全代码集中在 `security/` 下，配置代码集中在 `config/` 下。

### 2. 顶层目录职责

| 目录 | 当前职责 | 结论 |
|---|---|---|
| `common` | 统一返回、业务异常、全局异常处理、请求日志与脱敏 | 职责基本清晰，未发现业务模块逻辑下沉到 common |
| `config` | OpenAPI、Spring Security 配置 | 职责清晰，配置类数量少 |
| `security` | JWT、认证用户、认证失败和权限失败处理 | 职责清晰，未与业务模块混放 |
| `infra` | AI 调用、Embedding 调用、Prompt 模板加载、文件存储适配 | 职责基本清晰，适合作为后续外部服务适配边界 |
| `module` | auth、user、resume、job、analysis、history、embedding 等业务模块 | 领域模块完整，但 resume / analysis / history 体量偏大 |

### 3. 业务模块结构

当前业务模块：

| 模块 | 文件数量 | 主要职责 | 结论 |
|---|---:|---|---|
| `auth` | 6 | 注册、登录、认证返回 | 结构轻量清晰 |
| `user` | 6 | 当前用户信息 | 结构轻量清晰 |
| `resume` | 78 | 简历上传、解析、清洗、结构化、展示模型、解析质量 | 模块边界正确，但内部解析链路类多且部分实现类过大 |
| `job` | 35 | 岗位库、目标岗位、目标岗位解析、基础匹配 | 结构基本清晰，需要持续区分岗位库和目标岗位 |
| `analysis` | 61 | 简历诊断、匹配分析、岗位优化建议、局部改写、岗位优化报告 | 领域聚合合理，但 Controller 和部分 Service 仍偏重 |
| `history` | 16 | 基础历史和 AI 结果回看 | 职责清晰，但聚合查询逻辑较重 |
| `embedding` | 25 | 简历 / 目标岗位向量、语义匹配、RAG 上下文 | 可作为 AI 增强模块保留，后续需注意不要扩成主流程依赖 |

### 4. Controller 边界

检查结果：

- Controller 返回类型基本为 `Result<...VO>` 或 `Result<List<...VO>>`。
- 未发现关键 Controller 直接返回 Entity。
- Controller 没有直接操作 Mapper。
- 大多数 Controller 只做参数接收、当前用户读取、Service 调用和 Result 包装。

需要关注：

| 类 | 问题 | 后续建议 |
|---|---|---|
| `module/analysis/controller/ResumeAnalysisController.java` | 约 427 行，包含较多 Entity -> VO 组装方法，承载简历诊断、匹配分析、建议、改写多个入口 | v3.2 可考虑拆出 VO assembler，或按功能拆成简历诊断、匹配分析、建议、改写 Controller |
| `module/analysis/controller/AiRewriteSuggestionController.java` | 与 `ResumeAnalysisController` 中存在相似的改写 VO 组装逻辑 | 后续可统一改写 VO 组装边界 |

当前不建议在 v3.1.1 直接拆 Controller，避免影响接口行为和前端联调。

### 5. Service 边界

当前 Service 已按接口和 `impl/` 分层，所有业务模块都有明确 service 包。

体量偏大的实现类：

| 类 | 行数 | 当前职责 | 风险 |
|---|---:|---|---|
| `ResumeDisplayModelServiceImpl` | 约 1233 | 简历展示模型组装、字段整理、复杂展示映射 | 展示模型规则集中，后续修改成本高 |
| `ResumeStructureParseServiceImpl` | 约 1209 | 简历结构化解析主规则 | 解析规则复杂，继续扩展会影响可读性 |
| `ResumeStructuredResultAssembler` | 约 1128 | 结构化解析结果组装 | 组装规则偏多，适合拆出子 assembler |
| `ResumeServiceImpl` | 约 1099 | 上传、列表、详情、解析编排、删除级联、展示模型调用 | 承担业务编排和多表级联，职责偏重 |
| `ProjectSourceTextExtractor` | 约 971 | 项目经历源文本提取 | 算法类体量偏大，但边界相对独立 |
| `ResumeTextCleanServiceImpl` | 约 713 | 文本清洗、编号噪声、兼容汉字归一化 | 边界正确，但规则持续增加会变重 |
| `AiHistoryServiceImpl` | 约 648 | 多类 AI 结果聚合、分页、详情 | 聚合查询逻辑偏重 |
| `HistoryServiceImpl` | 约 402 | 基础历史聚合 | 与 AI 历史存在一定功能相邻 |
| `JobOptimizationReportServiceImpl` | 约 495 | 报告聚合和展示字段组装 | 当前边界正确，后续可拆 assembler |
| `AiRewriteSuggestionServiceImpl` | 约 430 | 局部改写编排、上下文、保存、采纳状态 | 职责较多但仍在局部改写领域内 |

结论：

- 当前主要风险不是包放错，而是部分 Service 变成“流程编排 + 查询 + VO/展示组装 + 规则处理”的大类。
- v3.2 不宜一次性大拆，应优先拆低风险的 assembler / helper，再考虑服务边界调整。

### 6. Mapper 边界

检查结果：

- Mapper 均位于各模块 `mapper/` 下。
- Mapper 基本继承 MyBatis-Plus `BaseMapper`。
- 少量向量相关 Mapper 使用注解 SQL 更新向量字段，属于基础设施能力与业务模块交叉点。
- 未发现 Mapper 调用 AI、调用 Service 或承载复杂业务流程。

需要关注：

| 类 | 问题 | 后续建议 |
|---|---|---|
| `ResumeEmbeddingMapper`、`JobDescriptionEmbeddingMapper` | 包含 pgvector 相关注解 SQL | 可接受；后续如果向量 SQL 增多，可考虑 XML 或 infra adapter 边界 |

### 7. DTO / VO / Entity 边界

检查结果：

- DTO / VO / Entity 基本按模块分目录放置。
- 未发现 DTO / VO 文件名重复。
- 关键 Controller 返回 VO，不直接返回 Entity。
- Entity 主要在 Service 内部和 Mapper 层使用。

需要关注：

| 类或模块 | 问题 | 后续建议 |
|---|---|---|
| `ResumeAnalysisController` | Entity -> VO 组装集中在 Controller 私有方法中 | 后续可下沉到 assembler，降低 Controller 体量 |
| `history` 模块 VO | VO 数量较多，承担基础历史和 AI 历史两套展示模型 | 保持现状，后续如继续扩展筛选和详情，可考虑分包或分 service |
| `resume` 模块 DTO | 解析链路 DTO 很多，但多数是内部结构化模型，不全是请求 DTO | 后续可考虑区分 `dto` 与 `model` / `internal`，但当前不强制移动 |

### 8. 当前架构问题清单

| 优先级 | 位置 | 问题 | 影响 | 建议阶段 |
|---|---|---|---|---|
| 高 | `ResumeServiceImpl` | 简历上传、解析编排、删除级联、展示模型查询集中在一个实现类 | 后续改动容易互相影响 | v3.2 |
| 高 | `ResumeStructureParseServiceImpl`、`ResumeStructuredResultAssembler`、`ResumeDisplayModelServiceImpl` | 简历解析和展示模型类体量过大 | 解析质量继续优化时维护成本高 | v3.2 |
| 中 | `ResumeAnalysisController` | Controller 中存在较多 VO 组装和多个 AI 功能入口 | Controller 可读性下降，重复转换逻辑增加 | v3.2 |
| 中 | `AiHistoryServiceImpl`、`HistoryServiceImpl` | 基础历史和 AI 结果回看逻辑相邻但分散 | 后续历史筛选和详情扩展时可能重复 | v3.2 或 v3.5 |
| 中 | `JobOptimizationReportServiceImpl` | 报告聚合和展示组装在同一 Service | 当前可接受，后续报告字段增加会变重 | v3.2 |
| 低 | `ResumeEmbeddingMapper`、`JobDescriptionEmbeddingMapper` | 向量 SQL 写在 Mapper 注解中 | 当前量少可接受，后续复杂 SQL 可读性下降 | v3.3 或 v3.8 |
| 低 | `resume/dto` | 内部解析模型和请求 DTO 都在 dto 下 | 命名还能理解，但职责语义不够细 | v3.2 |

### 9. 当前不建议立即修改的问题

| 问题 | 原因 | 建议 |
|---|---|---|
| 立即拆分 `resume` 模块目录 | 简历解析链路复杂，直接移动文件容易影响测试和已有导入 | v3.2 先拆低风险 assembler / helper，再逐步整理包 |
| 立即拆分 `analysis` 模块为多个新模块 | 当前分析、建议、改写和报告共享实体和上下文，强拆会增加跨模块依赖 | 先在模块内拆 Controller / assembler |
| 立即把向量能力移入 infra | 当前 embedding 已作为业务增强模块存在，贸然移动会影响接口和测试 | v3.3 或 v3.8 再评估 |
| 立即新增统一历史表 | v3.1 只做审查，不改数据库结构 | v3.5 状态机或任务结果治理时再评估 |

### 10. v3.2 建议调整顺序

1. 为 `ResumeAnalysisController` 增加模块内 VO assembler，先减少 Controller 体量。
2. 为 `JobOptimizationReportServiceImpl` 拆出报告 assembler，保持报告“不重新调用 AI”的边界。
3. 在 `resume` 模块内拆分 `ResumeServiceImpl` 的删除级联、解析结果保存、展示模型组装调用边界。
4. 对 `ResumeStructuredResultAssembler` 和 `ResumeDisplayModelServiceImpl` 做内部 helper 拆分，不改变外部接口。
5. 梳理 `history` 与 `ai-history` 的边界，保留兼容接口，避免前端路径变化。

### 11. v3.1.1 结论

当前后端总体包结构符合项目规范，业务代码没有脱离 `module/`，基础设施和安全配置也没有明显越界。

本轮发现的主要问题是：核心业务模块已经进入“类体量偏大、编排逻辑偏集中”的阶段。Phase 4 后续应优先做模块内低风险拆分和 assembler/helper 提取，而不是大规模包迁移或微服务拆分。

---

## v3.1.2 当前前端模块结构审查

状态：已完成

审查范围：

```text
web/src/
```

重点审查：

- 页面、路由、API、Store、组件目录结构。
- 页面是否按产品模块组织。
- API 调用是否统一放在 API 层。
- 页面中是否直接写重复请求逻辑。
- 普通用户页面和管理员页面是否混合。
- 体量偏大的页面和后续拆分方向。

### 1. 当前前端目录结构

当前前端源码结构：

```text
web/src
├── api
├── assets
├── components
│   ├── business
│   ├── common
│   └── layout
├── layout
├── router
├── stores
├── styles
├── types
├── utils
└── views
```

整体结论：

- 目录结构符合当前项目规范。
- 页面集中在 `views/`，接口函数集中在 `api/`，类型集中在 `types/`，全局样式集中在 `styles/`。
- `components/` 下已有 `business/common/layout` 目录，但目前基本为空，说明页面内部仍承担大量展示和交互逻辑。
- 未发现普通用户页面和管理员页面混合；当前没有管理员前端页面。

### 2. 页面结构

当前页面文件：

| 页面 | 行数 | 当前职责 | 结论 |
|---|---:|---|---|
| `views/HomeView.vue` | 约 295 | 工作台、主流程状态、下一步推荐、快捷入口、最近 AI 结果 | 职责清晰，体量可接受 |
| `views/resume/ResumeView.vue` | 约 2148 | 简历上传、列表、解析、解析结果展示、简历诊断、质量提示 | 体量过大，后续应优先拆分 |
| `views/job/AiJobMatchView.vue` | 约 2038 | 匹配分析、岗位优化建议、局部改写、岗位优化报告 | 体量过大，承载多个 AI 子流程 |
| `views/history/HistoryView.vue` | 约 639 | AI 结果列表、筛选、详情弹窗、跳转 | 体量中等，后续可拆筛选和详情组件 |
| `views/job/JobDetailView.vue` | 约 516 | 岗位库详情、基础匹配 | 可接受，但属于辅助参考入口 |
| `views/job/JobDescriptionDetailView.vue` | 约 373 | 目标岗位详情、解析、删除、进入匹配 | 可接受 |
| `views/job/JobDescriptionListView.vue` | 约 202 | 目标岗位列表、删除、跳转 | 清晰 |
| `views/job/JobDescriptionCreateView.vue` | 约 155 | 新增目标岗位表单 | 清晰 |
| `views/job/JobListView.vue` | 约 120 | 岗位库列表 | 清晰 |
| `views/auth/LoginView.vue`、`RegisterView.vue` | 约 89 / 114 | 登录注册 | 清晰 |

### 3. 路由结构

当前路由集中在 `web/src/router/index.ts`。

路由语义：

| 路由 | 页面 | 当前语义 |
|---|---|---|
| `/` | 工作台 | 登录后主流程入口 |
| `/resumes` | 我的简历 | 简历资产入口 |
| `/jobs`、`/jobs/:id` | 岗位库 / 岗位详情 | 系统预置岗位参考 |
| `/job-descriptions`、`/job-descriptions/new`、`/job-descriptions/:id` | 目标岗位 | 用户粘贴 JD 主流程 |
| `/ai-job-matches` | 匹配与优化 | 匹配分析、建议、改写、报告 |
| `/history` | AI 历史 | AI 结果回看 |
| `/login`、`/register` | 登录注册 | 游客入口 |

结论：

- 路由路径保持稳定，符合“不随意修改已有接口路径 / 页面路径”的原则。
- 普通用户主流程语义已经收敛。
- `meta.title` 已使用工作台、目标岗位、匹配与优化等当前产品名。

### 4. API 层结构

当前 API 文件：

| 文件 | 职责 |
|---|---|
| `request.ts` | Axios 实例、Token 注入、错误处理 |
| `auth.ts`、`user.ts` | 认证和当前用户 |
| `resume.ts` | 简历上传、列表、详情、解析、诊断、删除 |
| `job.ts` | 岗位库和基础匹配 |
| `job-description.ts` | 目标岗位提交、列表、详情、解析、删除 |
| `ai-job-match.ts` | 匹配分析 |
| `ai-resume-suggestion.ts` | 岗位优化建议 |
| `ai-rewrite-suggestion.ts` | 局部改写 |
| `job-optimization-report.ts` | 岗位优化报告 |
| `history.ts` | 基础历史和 AI 结果回看 |

检查结果：

- 页面通过 `@/api/...` 调用接口，未发现页面直接使用 `axios` 或 `fetch`。
- `history.ts` 中 AI 结果接口使用 `/api/ai-results`，与后端 `AiHistoryController` 一致。
- 旧基础历史和 AI 结果回看共用 `history.ts`，当前可接受；后续如继续扩展，可拆成 `history.ts` 和 `ai-history.ts`。

### 5. Store 和状态管理

当前 Store：

| 文件 | 职责 | 结论 |
|---|---|---|
| `stores/auth.ts` | Token、当前用户、登录、登出、拉取当前用户 | 职责清晰 |
| `stores/counter.ts` | Vite / Pinia 示例计数器 | 当前业务无使用价值，后续可删除 |

结论：

- 当前全局状态很少，主要集中在认证状态。
- 简历、目标岗位、匹配分析等页面状态都留在页面内，符合 MVP 阶段简单实现。
- 由于 `ResumeView.vue` 和 `AiJobMatchView.vue` 体量过大，后续可优先拆组件，而不是急着上全局 Store。

### 6. 组件结构

当前 `components/business`、`components/common`、`components/layout` 目录基本为空。

结论：

- 前端目前是“页面优先”的结构，短期开发快，但页面复杂度已经集中到两个大页面。
- v3.2 / 后续前端整理时应优先从大页面中抽取稳定展示组件，而不是新建复杂状态管理。

建议优先抽取：

| 来源页面 | 建议组件 | 说明 |
|---|---|---|
| `ResumeView.vue` | `ResumeUploadPanel`、`ResumeParseResultPanel`、`ResumeDiagnosisPanel`、`ResumeParseDebugPanel` | 降低简历页体量，保持上传、解析、诊断分区 |
| `AiJobMatchView.vue` | `MatchAnalysisPanel`、`JobSuggestionPanel`、`RewriteSuggestionPanel`、`JobOptimizationReportPanel` | 对应匹配、建议、改写、报告四个子流程 |
| `HistoryView.vue` | `AiResultFilterBar`、`AiResultDetailDialog` | 保持历史页查询和详情展示清晰 |

### 7. 当前架构问题清单

| 优先级 | 位置 | 问题 | 影响 | 建议阶段 |
|---|---|---|---|---|
| 高 | `views/resume/ResumeView.vue` | 单文件约 2148 行，承载上传、解析、诊断、展示、调试多类逻辑 | 后续解析展示继续增强时维护成本高 | v3.2 或前端专项 |
| 高 | `views/job/AiJobMatchView.vue` | 单文件约 2038 行，承载匹配、建议、改写、报告多个子流程 | 页面状态复杂，容易产生交互回归 | v3.2 或前端专项 |
| 中 | `components/` | 目录存在但基本未沉淀复用组件 | 页面重复展示和状态逻辑难复用 | v3.2 |
| 中 | `views/history/HistoryView.vue` | 筛选、列表、详情、跳转集中在一个页面 | 后续筛选条件和详情类型增加时会变重 | v3.2 或 v3.5 |
| 低 | `stores/counter.ts` | 示例 Store 未承载业务 | 对运行影响小，但会干扰结构审查 | 后续清理 |
| 低 | `api/history.ts` | 基础历史与 AI 结果回看放在同一 API 文件 | 当前可接受，后续可按业务语义拆分 | v3.2 |

### 8. 当前不建议立即修改的问题

| 问题 | 原因 | 建议 |
|---|---|---|
| 立即重写 `ResumeView.vue` | 页面承载简历主流程，风险高 | 先抽稳定展示组件，不改接口调用顺序 |
| 立即重写 `AiJobMatchView.vue` | 页面承载 Phase 3 核心 AI 闭环，联动状态多 | 先按匹配、建议、改写、报告拆展示组件 |
| 立即引入复杂全局 Store | 当前全局状态不复杂，大量状态是页面局部流程态 | 先组件化，再判断是否需要 Pinia 模块 |
| 立即新增管理员前端结构 | 当前无管理员功能，也不属于 Phase 4 当前任务 | 保持普通用户主流程优先 |

### 9. v3.2 建议调整顺序

1. 先从 `AiJobMatchView.vue` 抽出报告展示组件，因为报告已经是聚合展示，不应触发新 AI，边界稳定。
2. 从 `AiJobMatchView.vue` 抽出匹配分析、岗位优化建议、局部改写三个展示区块，先不移动 API 调用。
3. 从 `ResumeView.vue` 抽出解析结果展示和简历诊断展示组件，保留上传和解析触发逻辑在页面内。
4. 清理未使用的 `stores/counter.ts`。
5. 根据历史页后续扩展情况，再拆 `HistoryView.vue` 的筛选栏和详情弹窗。

### 10. v3.1.2 结论

当前前端目录结构符合项目规范，API 层、类型层和页面层基本清晰，也没有管理员页面混入普通用户流程。

本轮发现的主要问题是页面级文件体量过大，尤其是 `ResumeView.vue` 和 `AiJobMatchView.vue`。后续前端优化应优先做低风险组件抽取，保持路由和接口路径不变，不做大规模 UI 重写。

---

## v3.1.3 长耗时任务现状审查

状态：已完成

审查范围：

```text
backend/src/main/java/com/winter/airesumeoptimizer/module/
backend/src/main/java/com/winter/airesumeoptimizer/infra/ai/
backend/src/main/resources/application.yaml
backend/src/main/resources/db/migration/
```

重点审查：

- 简历解析、简历诊断、目标岗位解析、匹配分析、岗位优化建议、局部改写、Embedding 向量生成的执行方式。
- 当前是否存在异步执行模型。
- 当前是否已有状态字段。
- 哪些任务后续适合异步化、轮询和统一任务状态治理。

### 1. 总体现状

当前触发型任务均为请求内同步执行。

检查结果：

- 未发现 `@Async`、`TaskExecutor`、线程池、`CompletableFuture` 或定时任务执行模型。
- AI 调用通过 `AiClientService.complete(...)` 同步执行。
- Embedding 调用通过 `EmbeddingClientService.embed(...)` 同步逐 chunk 执行。
- `application.yaml` 中 AI completion 默认超时为 `90s`，Embedding 默认超时为 `30s`。
- 多数结果表已经有 `PENDING / SUCCESS / FAILED` 语义字段，但当前请求会等待执行结束后返回最终状态。

当前配置：

| 配置 | 当前默认值 | 说明 |
|---|---:|---|
| `app.ai.openai-compatible.timeout-seconds` | `90` | AI completion 请求超时 |
| `app.ai.openai-compatible.max-tokens` | `4000` | AI completion 输出上限 |
| `app.ai.embedding-compatible.timeout` | `30` | 单次 Embedding 请求超时 |
| `spring.servlet.multipart.max-file-size` | `10MB` | 上传文件大小限制 |
| `app.resume.parse.ai-structured-parse-enabled` | `false` | AI 结构化补全默认关闭 |
| `app.resume.parse.ai-section-classify-enabled` | `true` | AI 章节归类默认开启 |

### 2. 任务执行方式

| 任务 | 触发入口 | 当前执行方式 | 当前状态字段 | 失败处理 | 后续建议 |
|---|---|---|---|---|---|
| 简历上传 | `POST /api/resumes` | 同步上传并保存文件元数据 | `resumes.upload_status` | 上传失败直接抛错或返回失败 | 保持同步 |
| 简历解析 | `POST /api/resumes/{id}/parse` | 同步执行文本提取、清洗、AI 章节归类、规则解析、可选 AI 结构化补全、质量检查和保存 | `resume_parse_results.parse_status` | 保存 `FAILED` 和 `error_message` | 建议异步化 |
| 简历诊断 | `POST /api/resumes/{id}/ai-analysis` | 同步调用 AI completion，解析并保存结果 | `resume_ai_analyses.analysis_status` | 保存 `FAILED` 和 `error_message` | 建议异步化 |
| 目标岗位解析 | `POST /api/job-descriptions/{id}/parse` | 同步调用 AI completion，解析 JD 并保存结构化结果 | `job_descriptions.parse_status` | 保存 `FAILED` 和 `error_message` | 建议异步化 |
| 匹配分析 | `POST /api/resumes/{id}/ai-job-matches` | 同步构建 RAG 上下文、调用 AI completion、解析并保存结果 | `ai_job_match_results.match_status` | 保存 `FAILED` 和 `error_message` | 建议异步化 |
| 岗位优化建议 | `POST /api/resumes/{id}/ai-suggestions` | 同步依赖成功匹配结果，调用 AI completion 并保存建议 | `ai_resume_suggestions.suggestion_status` | 保存 `FAILED` 和 `error_message` | 建议异步化 |
| 局部改写 | `POST /api/resumes/{id}/rewrite-suggestions` | 同步调用 AI completion，保存改写建议和采纳状态 | `ai_rewrite_suggestions.rewrite_status`、`accept_status` | 保存 `FAILED` 和 `error_message` | 可先保持同步，后续按耗时再异步化 |
| 简历向量生成 | `POST /api/resumes/{resumeId}/embeddings` | 同步分块逐个调用 Embedding，逐 chunk 写入成功或失败状态 | `resume_embeddings.embedding_status` | 单 chunk 失败不阻断全部，最终可能部分成功 | 建议优先异步化 |
| 目标岗位向量生成 | `POST /api/job-descriptions/{jobDescriptionId}/embeddings` | 同步分块逐个调用 Embedding，逐 chunk 写入成功或失败状态 | `job_description_embeddings.embedding_status` | 单 chunk 失败不阻断全部，最终可能部分成功 | 建议优先异步化 |
| 语义匹配 | `GET /api/resumes/{resumeId}/semantic-match` 或对应当前路由 | 同步读取已生成向量并做数据库相似度查询 | 无独立任务状态 | 缺少向量时返回业务错误 | 保持同步 |
| 岗位优化报告 | `GET /api/resumes/{id}/job-optimization-report` | 同步聚合已有结果，不调用 AI | 无独立任务状态 | 缺少必要匹配结果时返回错误或 warning | 保持同步 |
| AI 结果回看 | `GET /api/ai-results` | 同步聚合查询已有结果 | 读取各结果状态 | 查询失败直接返回错误 | 保持同步 |

### 3. 已有状态基础

当前已有状态字段可以作为后续异步化基础：

| 表 | 状态字段 | 当前状态集合 |
|---|---|---|
| `resume_parse_results` | `parse_status` | `PENDING`、`PROCESSING`、`SUCCESS`、`FAILED` |
| `resume_ai_analyses` | `analysis_status` | `PENDING`、`SUCCESS`、`FAILED` |
| `job_descriptions` | `parse_status` | `PENDING`、`SUCCESS`、`FAILED` |
| `ai_job_match_results` | `match_status` | `PENDING`、`SUCCESS`、`FAILED` |
| `ai_resume_suggestions` | `suggestion_status` | `PENDING`、`SUCCESS`、`FAILED` |
| `ai_rewrite_suggestions` | `rewrite_status` | `PENDING`、`SUCCESS`、`FAILED` |
| `ai_rewrite_suggestions` | `accept_status` | `PENDING`、`ACCEPTED`、`REJECTED` |
| `resume_embeddings` | `embedding_status` | `PENDING`、`SUCCESS`、`FAILED` |
| `job_description_embeddings` | `embedding_status` | `PENDING`、`SUCCESS`、`FAILED` |

注意：

- `resume_parse_results` 已包含 `PROCESSING`，但当前同步实现中没有真正的后台 processing 生命周期。
- Embedding service 在应用层聚合出 `PARTIAL_SUCCESS`、`NOT_GENERATED`，但数据库约束只保存 chunk 级 `PENDING / SUCCESS / FAILED`。
- 当前没有统一 `task_id`、`task_type`、`progress`、`started_at`、`finished_at`、`retry_count`。

### 4. 失败场景

| 任务 | 主要失败场景 | 当前表现 |
|---|---|---|
| 简历解析 | 文件无法读取、文本为空、文本质量失败、AI 章节归类失败、AI 结构化补全失败、JSON 序列化失败 | 能保存解析失败或 AI fallback 信息，部分 AI 失败会降级继续 |
| 简历诊断 | 未解析、解析失败、AI 超时、AI 返回格式不正确、JSON 解析失败 | 保存失败分析记录 |
| 目标岗位解析 | JD 为空、AI 超时、AI 返回格式不正确、结构化结果序列化失败 | 保存失败状态 |
| 匹配分析 | 简历未解析、目标岗位未解析、RAG 上下文失败、AI 超时、AI 输出格式错误 | 保存失败匹配结果 |
| 岗位优化建议 | 匹配分析未成功、AI 超时、AI 输出格式错误 | 保存失败建议结果 |
| 局部改写 | 原文为空、关联目标岗位 / 匹配 / 建议状态不成功、AI 超时、AI 输出格式错误 | 先创建记录，再保存成功或失败状态 |
| Embedding | API Key 未配置、base-url 未配置、文本过长、单 chunk 超时、向量维度不一致 | chunk 级失败，整体可能部分成功 |

### 5. 异步化优先级

| 优先级 | 任务 | 原因 | 建议方式 |
|---|---|---|---|
| P0 | Embedding 向量生成 | 多 chunk 串行调用外部服务，耗时和失败概率最高；已天然具备 chunk 状态 | 优先改为单体内异步任务，前端轮询 summary |
| P0 | 简历解析 | 文件解析、规则解析和可选 AI 解析链路长；用户等待体验不稳定 | 使用 `resume_parse_results.parse_status=PROCESSING`，前端轮询解析结果 |
| P1 | 匹配分析 | 依赖简历和目标岗位，调用 AI completion，90 秒超时风险明显 | 复用 `ai_job_match_results.match_status`，触发后返回记录 ID |
| P1 | 岗位优化建议 | 依赖匹配结果，调用 AI completion，适合结果轮询 | 复用 `suggestion_status`，触发后返回记录 ID |
| P1 | 简历诊断 | AI completion 任务，适合统一 AI 任务模型 | 复用 `analysis_status`，触发后返回记录 ID |
| P2 | 目标岗位解析 | 通常输入较短，但仍依赖 AI completion | 可在匹配分析前一起纳入异步模型 |
| P2 | 局部改写 | 输入短，用户可能期望即时结果 | 暂可同步，若真实耗时明显再纳入异步 |
| 不建议异步 | 报告、历史、语义匹配查询 | 都是查询 / 聚合，不应制造任务复杂度 | 保持同步 |

### 6. 前端轮询建议

后续 v3.4 / v3.5 如落地异步化，建议保持接口路径尽量稳定：

- 触发接口返回已有结果 VO 或轻量 trigger VO，其中包含 `recordId`、`status`、`message`。
- 查询接口继续使用现有详情 / 列表接口读取状态。
- 前端只对 `PENDING / PROCESSING` 状态做轮询。
- 轮询间隔建议从 `2s` 开始，最长不超过 `60-90s`，超时后提示用户稍后从 AI 结果回看进入。
- 不在 Phase 4 初期引入 WebSocket 或 SSE，除非后续有明确体验瓶颈。

### 7. 是否需要 RabbitMQ

当前不建议引入 RabbitMQ。

原因：

- 当前项目是实习展示型单体系统，业务量和并发没有证明需要消息队列。
- 多数任务已经有结果表状态字段，可先用单体内线程池和数据库状态轮询解决体验问题。
- 引入消息队列会增加部署、失败重试、幂等和运维复杂度，超出当前 Phase 4 的必要范围。

建议：

- v3.4 优先设计单体内异步执行模型。
- v3.5 再补任务状态机、幂等键、重试和超时策略。
- RabbitMQ 仅作为未来扩展，不进入当前实现范围。

### 8. v3.4 / v3.5 建议准备项

| 后续阶段 | 建议准备 |
|---|---|
| v3.4 长耗时任务异步化设计 | 定义 `TaskExecutor` 配置、任务提交返回格式、轮询策略、超时策略 |
| v3.5 解析与 AI 任务状态机落地 | 明确统一状态枚举、错误信息规范、任务幂等策略、失败重试边界 |

建议优先统一的任务元数据：

```text
taskType
recordId
ownerUserId
status
progress
errorMessage
startedAt
finishedAt
durationMs
retryCount
```

v3.5 是否新增统一任务表需要再评估。短期可以复用现有业务结果表；如果任务类型继续增加，再考虑新增轻量 `task_runs` 表。

### 9. v3.1.3 结论

当前系统已经有较完整的结果状态字段，但执行模型仍是同步请求内完成。最需要优先异步化的是 Embedding 向量生成和简历解析，其次是匹配分析、岗位优化建议和简历诊断。

Phase 4 当前不应直接引入消息队列。更合理的路径是先用单体内异步线程池 + 现有结果表状态 + 前端轮询完成体验和可靠性收敛。

---

## v3.1.4 存储、安全与部署现状审查

状态：已完成

审查范围：

```text
backend/src/main/java/com/winter/airesumeoptimizer/infra/storage/
backend/src/main/java/com/winter/airesumeoptimizer/security/
backend/src/main/java/com/winter/airesumeoptimizer/config/
backend/src/main/resources/application*.yaml
docker-compose.yml
.env.example
README.md
deploy/
```

重点审查：

- 简历文件存储方式和后续对象存储切换基础。
- 上传限制、文件访问权限和文件删除一致性。
- JWT、AI Key、Embedding Key、数据库密码等敏感配置来源。
- CORS、日志脱敏、部署配置和环境隔离现状。

### 1. 文件存储现状

当前已经具备 `FileStorageService` 抽象，核心方法为 `store`、`open`、`delete`。

| 项目 | 当前状态 | 结论 |
|---|---|---|
| 存储抽象 | 已有 `FileStorageService` 接口 | 符合后续本地存储 / 对象存储切换方向 |
| 本地实现 | 已有 `LocalFileStorageService` | 当前实际使用本地文件系统 |
| 本地路径 | `app.storage.local.base-dir` 可配置，默认 `./data/uploads` | 满足开发环境配置需求 |
| 对象 Key | 使用业务目录 + UUID + 原扩展名 | 能避免直接暴露原始文件名 |
| 路径安全 | 目录禁止 `..` 和绝对路径，目标路径必须位于 base dir 下 | 已有路径穿越防护 |
| MinIO 支持 | `.env.example` 和 `docker-compose.yml` 有 MinIO 变量与服务，但后端没有 MinIO 实现 | 属于后续 v3.3 任务，不应在 v3.1 强行补实现 |

当前简历上传保存到 `resumes/{userId}` 目录下，数据库记录中保存 `storage_type=LOCAL` 和 `object_key`。这说明业务层已经不直接依赖绝对路径，后续切换 MinIO 的主要工作在存储实现和配置选择，而不是重写上传主流程。

### 2. 上传与文件访问安全

| 检查项 | 当前状态 | 风险判断 |
|---|---|---|
| 上传大小 | Spring Multipart 限制 `10MB / 12MB`，业务配置 `app.resume.upload.max-file-size-bytes=10485760` | 已有限制 |
| 文件扩展名 | 只允许 `pdf`、`doc`、`docx` | 已有限制 |
| PDF Content-Type | PDF 要求 `application/pdf` | 已有限制 |
| DOC / DOCX Content-Type | 当前主要依赖扩展名 | 中低风险，后续可增加 MIME / 文件头校验 |
| 文件读取权限 | 读取解析前通过 `getOwnedResume(userId, resumeId)` 校验归属 | 已有用户隔离 |
| 文件删除权限 | 删除前通过用户归属校验 | 已有用户隔离 |
| 上传失败清理 | 元数据保存失败时会删除已存文件 | 已有补偿 |
| 删除一致性 | 当前先删除物理文件，再删除数据库记录 | 存在一致性风险，若 DB 删除失败可能留下缺失文件记录 |

结论：当前上传和访问权限已满足 MVP 和实习展示项目基础安全要求。后续重点不是重写文件链路，而是补 DOC/DOCX 更严格校验、删除顺序或补偿策略，以及对象存储实现。

### 3. 认证授权与 CORS

| 检查项 | 当前状态 | 结论 |
|---|---|---|
| JWT Secret | 通过 `${jwt.secret}` 注入，主配置无硬编码默认值 | 符合敏感配置要求 |
| 密码加密 | 使用 `BCryptPasswordEncoder` | 符合基础安全要求 |
| Session | Spring Security 配置为无状态 | 符合 JWT 模式 |
| CSRF / Basic / Form Login | 均关闭 | 符合前后端分离 API 模式 |
| 认证入口 | 注册、登录和 Swagger 放行 | 可接受 |
| 业务接口保护 | `/api/users/me`、`/api/resumes/**`、`/api/job-descriptions/**`、`/api/rewrite-suggestions/**`、`/api/ai-results/**`、`/api/history/**` 已要求认证 | 主流程接口已保护 |
| 默认策略 | `anyRequest().permitAll()` | 风险偏高，新增接口容易默认公开 |
| CORS | 允许 localhost / 127.0.0.1 的 5173-5175 端口 | 适合本地开发，不适合部署环境 |

需要在 v3.6 优先调整默认授权策略：从“未声明则放行”收敛为“未声明则认证或拒绝”，再明确标注哪些公开接口确实允许匿名访问。CORS 后续应改为按环境变量配置。

### 4. 敏感配置现状

| 配置 | 当前来源 | 结论 |
|---|---|---|
| 数据库密码 | `application-dev.yaml` 使用 `${DB_PASSWORD}` | 主开发配置未硬编码真实密码 |
| JWT Secret | `application.yaml` 使用 `${jwt.secret}` | 主配置未硬编码真实密钥 |
| AI API Key | `app.ai.openai-compatible.api-key` 使用环境变量 | 未硬编码真实 Key |
| Embedding API Key | `app.ai.embedding-compatible.api-key` 使用环境变量 | 未硬编码真实 Key |
| 测试配置 | `application-test.yaml` 有测试默认值 | 测试环境可接受 |
| 示例配置 | `.env.example` 使用占位符和本地默认值 | 可接受，但需要提醒部署时替换 |

当前 `.env.example` 和 `docker-compose.yml` 已使用明显的本地占位值替代易被照抄的默认密码。这些配置仍只适合本地快速启动，不能作为生产部署配置。后续 v3.7 需要整理生产 Profile 和部署环境变量清单。

### 5. 日志与错误信息

当前已有 `LogSanitizer`，能对 Bearer Token、api-key、token、secret、password 等关键字段做脱敏，并限制日志片段长度。`GlobalExceptionHandler` 对文件存储、AI 调用和未处理异常返回较泛化的错误信息，避免把内部异常直接暴露给前端。

需要继续关注：

| 位置 | 问题 | 建议阶段 |
|---|---|---|
| 部分 Service 直接记录 `exception.getMessage()` | 如果三方 SDK 异常包含敏感片段，仍有泄露可能 | v3.6 |
| AI / Embedding 失败原因保存到业务表 | 便于排查，但需要控制错误信息长度和脱敏 | v3.5 / v3.6 |
| 用户可见错误 | 当前多数已做业务化包装，但仍需统一错误码和文案 | v3.6 |

### 6. 部署配置现状

| 项目 | 当前状态 | 结论 |
|---|---|---|
| `docker-compose.yml` | 提供 PostgreSQL / pgvector 和 MinIO 本地依赖 | 适合本地开发 |
| `deploy/` | 当前为空 | 尚未形成部署配置 |
| Profile | 有 `application.yaml`、`application-dev.yaml`、`application-test.yaml` | 缺少生产 Profile |
| 反向代理 | 未发现 Nginx 或同类配置 | 后续 v3.7 / v3.8 补充 |
| 后端镜像 | 未发现 Dockerfile | 后续部署阶段补充 |
| 前端构建部署 | 未发现部署脚本或静态资源部署说明 | 后续部署阶段补充 |

结论：当前部署能力仍处于本地开发依赖层级，不具备完整生产部署闭环。Phase 4 后续应先补环境隔离、生产配置草案和部署检查清单，再考虑正式服务器上线。

### 7. 当前问题清单

| 优先级 | 位置 | 问题 | 影响 | 建议阶段 |
|---|---|---|---|---|
| 高 | `SecurityConfig` | `anyRequest().permitAll()` 默认放行未显式声明的接口 | 新增接口可能意外公开 | v3.6 |
| 高 | 配置 / 部署 | 缺少生产 Profile、部署环境变量清单和部署检查清单 | 难以安全部署 | v3.7 |
| 中 | 存储 | MinIO 已出现在 compose / env 示例中，但后端没有对象存储实现 | 配置和能力不一致 | v3.3 |
| 中 | 简历删除 | 先删物理文件再删数据库记录 | DB 删除失败时可能产生缺失文件记录 | v3.3 |
| 中 | CORS | 允许源硬编码本地端口 | 部署环境需要改代码或重新打包 | v3.6 / v3.7 |
| 中低 | 上传校验 | DOC / DOCX 当前主要依赖扩展名 | 文件类型校验不够严格 | v3.6 |
| 低 | `deploy/` | 目录存在但没有部署文件 | 后续交付材料不完整 | v3.7 / v3.8 |

### 8. v3.1.4 结论

当前系统已经具备基础的本地文件存储抽象、上传大小限制、用户文件访问校验、JWT 认证、敏感配置环境变量化和日志脱敏能力。

上线前更需要处理的是默认授权策略、生产环境配置、CORS 环境化、MinIO 实现边界、文件删除一致性和部署文档。v3.1.4 不直接修改代码，以上问题进入 Phase 4 后续版本逐步处理。

---

## v3.1.5 v3.1 架构审查总结

状态：已完成

### 1. 当前架构优点

| 维度 | 结论 |
|---|---|
| 后端结构 | 根包下 `common / config / security / infra / module` 边界清晰，业务代码集中在 `module/` |
| 领域模块 | 用户、简历、岗位、分析、历史、向量等模块已形成基本边界 |
| 前端结构 | 页面、API、类型、路由、Store、组件目录符合当前项目结构约束 |
| 业务主线 | 已围绕“简历资产 + 用户粘贴目标岗位 JD”收敛，不再扩张到岗位爬取 |
| AI 边界 | 诊断、解析、匹配、建议、改写、历史回看职责基本可区分 |
| 存储边界 | 已有 `FileStorageService` 抽象，本地存储可配置 |
| 安全基础 | JWT、BCrypt、用户数据归属校验、日志脱敏已有基础 |
| 状态基础 | 多数 AI / 解析结果表已有成功失败状态字段，可支撑后续异步化 |

### 2. 当前架构问题

| 优先级 | 问题 | 主要影响 | 后续版本 |
|---|---|---|---|
| P0 | `ResumeView.vue`、`AiJobMatchView.vue` 体量过大 | 前端核心页面维护和交互回归风险高 | v3.2 |
| P0 | `ResumeServiceImpl`、简历解析 / 展示相关 Service 体量过大 | 后端核心流程继续演进成本高 | v3.2 |
| P0 | Embedding、简历解析和 AI 分析仍是同步请求内执行 | 用户等待时间长，超时和失败体验不稳定 | v3.4 / v3.5 |
| P1 | 默认授权策略过宽 | 新增接口可能意外公开 | v3.6 |
| P1 | 缺少生产 Profile 和部署检查清单 | 无法稳定进入部署阶段 | v3.7 |
| P1 | MinIO 配置存在但后端实现缺失 | 存储演进边界不完整 | v3.3 |
| P2 | 历史、报告、VO 组装存在局部重复 | 后续扩展时容易变重 | v3.2 / v3.5 |
| P2 | 部分日志和错误信息仍需统一脱敏规范 | 排查和安全之间的边界还需收敛 | v3.6 |

### 3. Phase 4 调整优先级

1. v3.2 先做低风险结构整理：拆 assembler / helper / 稳定组件，不改接口路径，不改数据库结构。
2. v3.3 完善文件存储抽象：明确本地存储实现边界，补对象存储切换方案，处理删除一致性。
3. v3.4 设计单体内异步执行模型：优先解决长耗时任务同步等待问题，不直接引入消息队列。
4. v3.5 落地解析与 AI 任务状态机：复用现有状态字段，必要时再评估轻量任务表。
5. v3.6 做安全加固：收紧默认授权、上传校验、日志脱敏和 AI 数据隐私边界。
6. v3.7 / v3.8 完成环境隔离、部署配置、运维文档和阶段复盘。

### 4. 不做微服务拆分的原因

当前项目是实习展示型单体应用，核心目标是体现 Java 后端能力、AI 应用能力、工程化能力和业务建模能力。当前没有明确并发瓶颈、团队协作规模或独立部署需求能证明微服务拆分的收益大于成本。

继续保持单体架构更合理：

- 可以优先解决真实存在的类体量、异步任务、安全配置和部署问题。
- 避免引入注册中心、网关、服务间调用、链路追踪和分布式事务等额外复杂度。
- 更适合展示“边界清晰的单体系统”，而不是为了架构名词牺牲稳定性。

### 5. 进入 v3.2 的条件

v3.1 已完成以下交付：

- 后端模块结构审查。
- 前端模块结构审查。
- 长耗时任务现状审查。
- 存储、安全与部署现状审查。
- Phase 4 后续问题优先级确认。
- v3.1 迭代日志落地。

可以进入 v3.2。v3.2 应继续遵守小步低风险原则，优先整理模块内部边界，不做大规模迁移，不改变接口路径，不修改数据库结构。
