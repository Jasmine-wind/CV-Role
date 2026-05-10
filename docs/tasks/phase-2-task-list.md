# Phase 2 Task List — 工程化增强阶段

## 0. 文件用途

本文件用于记录 Phase 2 工程化增强阶段的版本任务和完成情况。

Phase 1 已完成基础 MVP 主流程。Phase 2 围绕可靠性、可维护性、测试、配置、日志、接口文档和本地开发体验进行增强。

使用原则：

1. 每次只推进一个 v1.x 小版本。
2. 每完成一个 v1.x 版本，更新对应迭代日志。
3. 不在 Phase 2 提前进入 RAG、pgvector、多轮对话、微服务拆分等 Phase 3 / Phase 4 内容。

---

## 1. Phase 2 总体目标

在 Phase 1 MVP 已可运行的基础上提升工程质量，完成后应满足：

- 本地开发环境和测试环境配置分离。
- 核心 API 错误返回格式一致、请求参数校验清晰。
- 数据库结构变更可重复执行（Flyway）。
- 核心 Service 具备基础单元测试，关键主流程 API 具备基础集成测试。
- 日志可辅助定位问题且不泄露敏感内容。
- 接口文档可查看、可调试（OpenAPI / Swagger UI）。
- 本地依赖服务可通过 Docker Compose 编排启动。
- 项目具备基础 CI 检查能力（GitHub Actions）。
- README 和开发文档能支撑他人复现运行。

### 范围内

配置分离、参数校验增强、统一异常处理增强、统一错误码增强、Flyway 数据库迁移、日志规范、Service 层测试、关键 API 集成测试、OpenAPI 接口文档、Docker Compose 本地环境、GitHub Actions 基础 CI、README 和开发文档完善。

### 范围外

大规模业务功能扩张、管理后台大功能重写、AI Prompt 深度优化、RAG、pgvector、多轮对话、微服务拆分、消息队列、Kubernetes、复杂监控平台、生产级高可用部署。

---

## 2. Phase 2 版本划分

| 版本 | 主题 | 状态 |
|---|---|---|
| v1.1 | Phase 2 基线检查与配置整理 | 已完成 |
| v1.2 | 统一异常、错误码与参数校验增强 | 已完成 |
| v1.3 | Flyway 数据库迁移管理 | 已完成 |
| v1.4 | 日志规范与敏感信息保护 | 已完成 |
| v1.5 | Service 层单元测试 | 已完成 |
| v1.6 | 关键 API 集成测试 | 已完成 |
| v1.7 | OpenAPI / Knife4j 接口文档 | 已完成 |
| v1.8 | 本地编排、CI 与文档收口 | 已完成 |

---

# v1.1 — Phase 2 基线检查与配置整理

状态：已完成

## v1.1 总结

v1.1 确认了 Phase 1 MVP 的代码、配置、文档和运行方式，建立 Phase 2 工程化增强的起点，重点不是新增功能而是检查现状、清理配置、明确环境边界。

- 已将后端公共配置保留在 `application.yaml`，新增 `application-dev.yaml` 和 `application-test.yaml`。
- 已新增前端 `web/.env.development` 和 `web/.env.production`。
- 已新增根目录 `.env.example`，只含占位符不含真实密钥。
- 已调整 `.gitignore`，不再忽略 `docs/tasks` 任务文档。
- 已确认当前阶段仍使用本地 `uploads/` 文件存储，不要求启动 MinIO。
- 已更新 README 环境配置说明。
- 已运行后端 `./mvnw test` 和前端 `npm run build`。
- 迭代日志：`docs/iteration-log/v1.1-config-baseline.md`。

## v1.1 小任务完成情况

| 小任务 | 状态 |
|---|---|
| 检查后端/前端启动 | 已完成 |
| 检查 PostgreSQL / MinIO 配置 | 已完成 |
| 检查 AI Key 泄露风险 | 已完成 |
| 整理 application.yml / application-dev.yml | 已完成 |
| 规划 application-test.yml | 已完成 |
| 检查前端 .env.development / .env.production | 已完成 |
| 检查 .gitignore 覆盖范围 | 已完成 |
| 更新 README 环境配置说明 | 已完成 |
| 更新 v1.1 迭代日志 | 已完成 |

---

# v1.2 — 统一异常、错误码与参数校验增强

状态：已完成

## v1.2 总结

v1.2 增强了后端错误处理和参数校验能力，使 API 返回更稳定、更可预测，便于前端处理和问题排查。

- 已新增 `ResultCode` 稳定错误码枚举。
- 已增强 `Result<T>`，错误响应包含 `code`、`message`、`data`、`path` 和 `timestamp`。
- 已增强 `BusinessException`，兼容原有数字 code 并支持 `ResultCode`。
- 已增强 `GlobalExceptionHandler`，统一处理参数校验、请求体格式、文件上传、AI 调用、文件存储、权限和未知异常。
- 已新增 `JwtAccessDeniedHandler`（统一 403）和增强 `JwtAuthenticationEntryPoint`（统一 401）。
- 已补充路径参数、分页参数和岗位匹配请求参数校验。
- 已更新前端 `ApiResult<T>` 类型，兼容后端错误响应。
- 已运行后端 `./mvnw test` 和前端 `npm run build`。
- 迭代日志：`docs/iteration-log/v1.2-error-validation.md`。

## v1.2 小任务完成情况

| 小任务 | 状态 |
|---|---|
| 检查现有统一返回结构 | 已完成 |
| 设计错误码枚举 | 已完成 |
| 增强 BusinessException | 已完成 |
| 增强 GlobalExceptionHandler | 已完成 |
| 处理参数校验/认证失败/访问拒绝/资源不存在异常 | 已完成 |
| 处理文件上传/AI 调用异常 | 已完成 |
| 给核心 DTO 增加校验 | 已完成 |
| 前端适配统一错误提示 | 已完成 |
| 更新 v1.2 迭代日志 | 已完成 |

---

# v1.3 — Flyway 数据库迁移管理

状态：已完成

## v1.3 总结

v1.3 引入 Flyway 管理数据库表结构和初始化数据，使其可重复执行、可追踪。

- 已添加 `flyway-core` 和 `flyway-database-postgresql` 依赖。
- 已启用 `baseline-on-migrate`，baseline version 设为 `0`，便于接管已有本地库。
- 已创建 V1～V7 迁移脚本，覆盖 Phase 1 所需全部表和预置岗位数据。
- 已在 `resumes` 表补充 `storage_type` 字段（默认 `LOCAL`），同步实体和上传保存逻辑。
- 已更新旧 `db/init` 脚本避免与实体字段不一致。
- 已更新 README，推荐 Flyway 自动迁移，`db/init` 仅保留为历史参考。
- 已运行后端 `./mvnw test` 验证 Flyway baseline 和迁移。
- 迭代日志：`docs/iteration-log/v1.3-flyway.md`。

## v1.3 小任务完成情况

| 小任务 | 状态 |
|---|---|
| 添加 Flyway 依赖 | 已完成 |
| 补充 resumes 表 storage_type 字段 | 已完成 |
| 创建 V1～V7 迁移脚本 | 已完成 |
| 创建预置岗位数据脚本 | 已完成 |
| 检查已有数据库与 Flyway baseline 策略 | 已完成 |
| 更新 README 数据库初始化说明 | 已完成 |
| 更新 v1.3 迭代日志 | 已完成 |

---

# v1.4 — 日志规范与敏感信息保护

状态：已完成

## v1.4 总结

v1.4 建立了基础日志规范，使日志可辅助定位问题，同时避免泄露简历原文、密码、Token、AI Key 等敏感信息。

- 已新增 `RequestIdFilter`，为每个请求写入 MDC `requestId`，并在响应头返回 `X-Request-Id`。
- 已新增 `LogSanitizer`，对 Token、API Key、密码等敏感字段做基础脱敏，限制错误原因长度。
- 已配置控制台日志格式，统一输出时间、级别、requestId、logger 和消息。
- 已在注册、登录、上传、解析、AI 分析、岗位匹配等核心流程增加必要 `info` 日志。
- 已在文件上传、文本解析、AI 调用、全局异常处理等失败路径增加 `warn` 或 `error` 日志。
- 已避免打印密码、JWT、AI Key、完整简历原文、AI Prompt 和 AI 返回全文。
- 已显式提供 JWT 项目的 `UserDetailsService` 边界，避免 Spring Security 输出自动生成的开发密码。
- 已运行后端 `./mvnw test` 和前端 `npm run build`。
- 迭代日志：`docs/iteration-log/v1.4-logging.md`。

## v1.4 小任务完成情况

| 小任务 | 状态 |
|---|---|
| 检查现有日志使用情况 | 已完成 |
| 设计日志级别约定 | 已完成 |
| 关键流程增加必要日志 | 已完成 |
| 失败路径增加 warn/error 日志 | 已完成 |
| 禁止打印密码/JWT/AI Key | 已完成 |
| 避免完整打印简历原文 | 已完成 |
| 增加 requestId | 已完成 |
| 更新 v1.4 迭代日志 | 已完成 |

---

# v1.5 — Service 层单元测试

状态：已完成

## v1.5 总结

v1.5 为核心业务逻辑补充了 Service 层单元测试，提升修改代码时的信心。

- 已确认项目使用 `spring-boot-starter-test`（JUnit 5、AssertJ、Mockito、Spring Test）。
- 已新增 `AuthServiceImplTest`，覆盖注册成功、用户名重复、登录成功、密码错误。
- 已扩展 `ResumeServiceImplTest`，覆盖上传元数据保存、非法扩展名、超出大小限制、元数据保存失败后文件清理、删除级联清理。
- 已新增 `ResumeTextExtractionServiceImplTest`，覆盖 DOCX 文本提取、空文件类型、文件读取失败包装。
- 已保留已有 `ResumeStructureParseServiceImplTest`、`JobMatchServiceImplTest`、`JobMatchSuggestionServiceImplTest`、`JobMatchResultServiceImplTest` 等测试。
- 测试可通过 Maven 命令执行，不依赖真实外部 AI 服务。
- 迭代日志：`docs/iteration-log/v1.5-service-test.md`。

## v1.5 小任务完成情况

| 小任务 | 状态 |
|---|---|
| 编写 AuthService 注册/登录测试 | 已完成 |
| 编写 ResumeService 文件校验测试 | 已完成 |
| 编写 ResumeTextExtractService 基础测试 | 已完成 |
| 编写 ResumeStructureParseService 基础测试 | 已完成 |
| 编写 JobMatchService 匹配算法测试 | 已完成 |
| 编写优化建议生成测试 | 已完成 |
| 覆盖正常和异常场景 | 已完成 |
| 更新 v1.5 迭代日志 | 已完成 |

---

# v1.6 — 关键 API 集成测试

状态：已完成

## v1.6 总结

v1.6 为 Phase 1 主流程中的关键 API 增加了接口级测试，验证认证、权限边界和核心流程行为。

- 已采用 `@WebMvcTest + MockMvc` 作为接口级测试方式，使用 `test` profile 并通过 mock Service 隔离外部依赖。
- 已新增 `Phase1ApiIntegrationTest`，覆盖认证、当前用户、简历上传/列表/详情、解析触发/结果、AI 分析、岗位列表/详情/匹配、历史记录等接口。
- 已验证受保护接口未登录返回统一 401 错误结构，简历详情跨用户访问返回统一业务错误。
- 已验证 `RequestIdFilter` 在认证失败响应中也能返回 `X-Request-Id`，并将 Filter 顺序提前。
- 已运行后端 `./mvnw test`。
- 迭代日志：`docs/iteration-log/v1.6-api-test.md`。

## v1.6 小任务完成情况

| 小任务 | 状态 |
|---|---|
| 确定 API 集成测试方式 | 已完成 |
| 配置测试环境数据库 | 已完成 |
| 编写认证接口测试 | 已完成 |
| 编写受保护接口未登录访问测试 | 已完成 |
| 编写简历上传权限/列表用户隔离测试 | 已完成 |
| 编写解析/AI 分析接口测试 | 已完成 |
| 编写岗位/匹配/历史记录接口测试 | 已完成 |
| 更新 v1.6 迭代日志 | 已完成 |

---

# v1.7 — OpenAPI / Knife4j 接口文档

状态：已完成

## v1.7 总结

v1.7 引入了接口文档能力，使后端 API 可查看、可调试、可交付。

- 已引入 `springdoc-openapi-starter-webmvc-ui`，提供 OpenAPI JSON 和 Swagger UI。
- 已新增 `OpenApiConfig`，配置接口文档标题、版本和 JWT Bearer 认证方案。
- 已放行 `/v3/api-docs/**`、`/swagger-ui/**` 和 `/swagger-ui.html` 文档访问路径。
- 已为 Auth、User、Resume、Analysis、Job、Job Match、History 等核心 Controller 补充接口分组和说明。
- 已为核心 DTO、VO 和统一响应结构补充字段说明。
- 已更新 README，补充 Swagger UI、OpenAPI JSON 和 JWT 调试方式。
- 已扩展 API 测试验证 `/v3/api-docs` 可匿名访问并覆盖 OpenAPI JWT 配置。
- 已运行后端 `./mvnw test`。
- 迭代日志：`docs/iteration-log/v1.7-api-doc.md`。

## v1.7 小任务完成情况

| 小任务 | 状态 |
|---|---|
| 添加 OpenAPI / Knife4j 依赖 | 已完成 |
| 创建接口文档配置类 | 已完成 |
| 配置 JWT 认证说明 | 已完成 |
| 为各 Controller 补充接口说明 | 已完成 |
| 为核心 DTO / VO 补充字段说明 | 已完成 |
| 更新 README 接口文档访问方式 | 已完成 |
| 更新 v1.7 迭代日志 | 已完成 |

---

# v1.8 — 本地编排、CI 与文档收口

状态：已完成

## v1.8 总结

v1.8 整理了本地开发环境、基础 CI 和 Phase 2 文档，使项目更容易复现和展示。

- 已新增根目录 `docker-compose.yml`，编排 PostgreSQL 和 MinIO 本地依赖服务。
- 已更新 `.env.example`，补充服务端口、数据库、MinIO、后端运行和 AI 配置占位符。
- 已新增 `.github/workflows/ci.yml`，CI 覆盖后端测试和前端构建。
- 已更新 README，补充 Compose 启停、本地依赖服务端口、CI 和任务文档入口。
- 已新增 `docs/codex-context.md`，将上下文切换到 Phase 2 已完成、下一步进入 Phase 3。
- 已运行后端 `./mvnw test`、前端 `npm run build`，并检查 Compose 配置。
- 迭代日志：`docs/iteration-log/v1.8-engineering-summary.md`。

## v1.8 小任务完成情况

| 小任务 | 状态 |
|---|---|
| 创建 docker-compose.yml（PostgreSQL + MinIO） | 已完成 |
| 整理 .env.example | 已完成 |
| 创建 GitHub Actions CI workflow | 已完成 |
| CI 中执行后端构建/测试、前端构建 | 已完成 |
| 检查敏感信息未进入仓库 | 已完成 |
| 更新 README | 已完成 |
| 更新 Phase 2 任务文档和迭代日志 | 已完成 |
| 归档 Phase 2 | 已完成 |

---

## Phase 2 禁止提前实现内容

- RAG、pgvector、向量语义匹配
- 多轮对话、职业顾问聊天
- 微服务拆分、RabbitMQ、Nacos、Gateway
- Kubernetes、Prometheus / Grafana
- 大规模重构现有业务或与 MVP 稳定性无关的大功能扩张

---

## Phase 2 完成后的下一步

进入 **Phase 3 — AI 深度版本**，处理：

- Embedding、pgvector、RAG
- Prompt Template 深化与版本管理
- AI 缓存
- 多轮对话、职业顾问式问答
