# Phase 2 - 工程化增强阶段

## 当前状态

Phase 1 基础可运行 MVP 阶段已完成，主流程已经通过 v0.6 全流程联调确认，可以作为本地 MVP 演示版本使用。

Phase 2 工程化增强阶段已完成，当前已完成：

```text
v1.1 - Phase 2 基线检查与配置整理
v1.2 - 统一异常、错误码与参数校验增强
v1.3 - Flyway 数据库迁移管理
v1.4 - 日志规范与敏感信息保护
v1.5 - Service 层单元测试
v1.6 - 关键 API 集成测试
v1.7 - OpenAPI / Knife4j 接口文档
v1.8 - 本地编排、CI 与文档收口
```

下一步建议进入：

```text
Phase 3 - AI 能力深化阶段
```

本阶段不以新增大业务功能为主，而是在 MVP 已经存在的基础上，提升系统的可靠性、可维护性、可测试性和开发规范程度。当前 Phase 2 已完成收口，可作为后续 Phase 3 的工程基线。

---

## 1. 阶段目标

Phase 2 的目标是在不破坏 Phase 1 主流程的前提下，对项目进行工程化增强。

本阶段完成后，项目应具备以下能力：

- 本地开发环境和测试环境配置分离。
- 核心 API 具备统一、可预测的错误返回格式。
- 请求参数和文件上传参数具备更清晰的校验。
- 数据库结构变更可以通过迁移脚本重复执行。
- 核心业务逻辑具备基础单元测试。
- 关键 API 具备基础集成测试。
- 日志可以帮助定位上传、解析、AI 调用、岗位匹配等问题。
- 日志中不暴露密码、Token、AI Key、完整简历原文等敏感信息。
- 后端 API 有可查看、可调试的接口文档。
- 本地依赖服务可以通过 Docker / Podman 编排启动。
- 项目具备基础 CI 检查能力。
- README 和开发文档可以支撑他人复现项目运行。

---

## 2. 阶段定位

Phase 2 是从“能跑的项目”升级为“更像真实公司项目”的阶段。

Phase 1 的重点是业务闭环：

```text
注册登录 -> 上传简历 -> 解析简历 -> AI 分析 -> 岗位匹配 -> 优化建议 -> 历史记录
```

Phase 2 的重点是工程质量：

```text
配置规范 -> 错误规范 -> 数据库迁移 -> 日志规范 -> 自动化测试 -> 接口文档 -> 本地编排 -> CI
```

本阶段不追求功能数量，而追求：

- 稳定
- 可维护
- 可测试
- 可复现
- 可交付
- 可讲解

---

## 3. 技术范围

### 3.1 后端工程化

- Spring Boot 配置分环境
- Hibernate Validator 参数校验增强
- 统一返回结构增强
- 统一异常处理增强
- 稳定错误码设计
- Flyway 数据库迁移
- SLF4J + Logback 日志规范
- JUnit 5
- Mockito
- Spring Boot Test
- MockMvc 或等价 API 测试方式
- OpenAPI 3
- Knife4j

### 3.2 前端工程化

- Axios 统一错误处理
- 前端环境变量整理
- 接口错误提示适配
- 构建检查
- 可选：ESLint / Prettier 规范整理

### 3.3 本地开发与交付

- Docker 或 Podman
- Docker Compose 或 Podman Compose
- PostgreSQL 编排
- MinIO 编排
- `.env.example`
- GitHub Actions 基础 CI
- README 启动说明

---

## 4. 工程化范围

### 4.1 本阶段必须完成

- 配置分离
- 统一异常处理增强
- 统一错误码增强
- 参数校验增强
- 文件上传校验统一
- Flyway 数据库迁移
- 日志规范
- Service 层基础单元测试
- 关键 API 基础集成测试
- OpenAPI / Knife4j 接口文档
- Docker / Podman 本地依赖服务编排
- GitHub Actions 基础 CI
- README 和工程化文档更新

### 4.2 本阶段尽量完成

- 前端错误提示统一增强
- 前端环境变量说明补充
- 测试数据构造规范
- 开发启动脚本整理
- Phase 2 工程化总结

### 4.3 本阶段不做

- 新增复杂业务模块
- 复杂管理后台重写
- AI Prompt 深度优化
- RAG
- pgvector
- 向量语义匹配
- 多轮对话
- 微服务拆分
- RabbitMQ
- Nacos
- Gateway
- Kubernetes
- Prometheus / Grafana
- 生产级高可用部署
- 大规模架构重写

---

## 5. 总体排期

建议 Phase 2 拆分为 8 个迭代版本。每个版本形成一个可验证的工程化增强点。

| 迭代 | 主题 | 核心产出 |
|---|---|---|
| v1.1 | Phase 2 基线检查与配置整理 | 工程化起点明确，配置文件更清晰 |
| v1.2 | 统一异常、错误码与参数校验增强 | API 错误返回稳定、可预测 |
| v1.3 | Flyway 数据库迁移管理 | 数据库结构可重复初始化和升级 |
| v1.4 | 日志规范与敏感信息保护 | 日志可定位问题且不泄露敏感内容 |
| v1.5 | Service 层单元测试 | 核心业务逻辑具备基础测试 |
| v1.6 | 关键 API 集成测试 | MVP 主流程接口具备基础自动化验证 |
| v1.7 | OpenAPI / Knife4j 接口文档 | 接口可查看、可调试、可交付 |
| v1.8 | 本地编排、CI 与文档收口 | 开发环境更容易复现，Phase 2 可归档 |

---

## 6. 任务拆解与验收标准

### v1.1 - Phase 2 基线检查与配置整理

#### 目标

确认 Phase 1 MVP 的代码、配置、文档和运行方式，建立 Phase 2 工程化增强的起点。

#### 任务拆解

- 检查后端是否可以启动。
- 检查前端是否可以启动。
- 检查 PostgreSQL 配置。
- 检查 MinIO 配置。
- 检查 AI Key 配置是否存在泄露风险。
- 整理 `application.yml`。
- 整理 `application-dev.yml`。
- 规划 `application-test.yml`。
- 检查 `.gitignore` 是否覆盖 `.env`、`target/`、`node_modules/`、`.idea/`。
- 更新 README 中的环境配置说明。
- 更新 `docs/iteration-log/v1.1-config-baseline.md`。

#### 验收标准

- Phase 1 主流程仍可启动和基本运行。
- 后端配置文件职责清晰。
- 开发配置和测试配置有明确边界。
- 敏感信息不硬编码在代码中。
- `.env` 可以说明需要哪些环境变量。
- `.gitignore` 能避免提交本地配置和敏感文件。
- 本轮结果记录到 `docs/iteration-log/v1.1-config-baseline.md`。

---

### v1.2 - 统一异常、错误码与参数校验增强

#### 目标

增强后端错误处理和参数校验能力，使 API 返回更稳定、更可预测，便于前端处理和问题排查。

#### 任务拆解

- 检查现有 `Result<T>`。
- 设计或整理错误码枚举。
- 增强 `BusinessException`。
- 增强 `GlobalExceptionHandler`。
- 处理参数校验异常。
- 处理认证失败异常。
- 处理访问拒绝异常。
- 处理资源不存在异常。
- 处理文件上传异常。
- 处理 AI 调用异常。
- 统一错误返回字段。
- 给注册、登录、上传、解析、AI 分析、岗位匹配等核心 DTO 增加校验。
- 前端适配统一错误提示。
- 更新 `docs/iteration-log/v1.2-error-validation.md`。

#### 建议错误返回结构

| 字段 | 说明 |
|---|---|
| code | 稳定错误码 |
| message | 面向用户或前端的错误信息 |
| path | 请求路径 |
| timestamp | 时间 |
| traceId | 请求追踪 ID，可选 |

#### 验收标准

- 参数错误返回格式一致。
- 业务错误返回格式一致。
- 未登录和无权限错误可被前端识别。
- 文件上传错误提示清晰。
- 核心 API 不直接暴露原始异常堆栈。
- 前端可以基于统一结构显示错误信息。
- 本轮结果记录到 `docs/iteration-log/v1.2-error-validation.md`。

---

### v1.3 - Flyway 数据库迁移管理

#### 目标

引入数据库迁移管理，让数据库表结构和初始化数据可以重复执行、可追踪。

#### 任务拆解

- 添加 Flyway 依赖。
- 创建 `backend/src/main/resources/db/migration/`。
- 创建 `V1__create_users_table.sql`。
- 创建 `V2__create_resumes_table.sql`。
- 创建 `V3__create_resume_parse_results_table.sql`。
- 创建 `V4__create_resume_ai_analyses_table.sql`。
- 创建 `V5__create_jobs_table.sql`。
- 创建 `V6__create_job_match_results_table.sql`。
- 创建预置岗位数据脚本。
- 检查已有数据库与 Flyway baseline 策略。
- 更新 README 中数据库初始化说明。
- 更新 `docs/iteration-log/v1.3-flyway.md`。

#### 验收标准

- 新数据库可以通过 Flyway 自动创建 Phase 1 所需表。
- 预置岗位数据可以初始化。
- Flyway 脚本命名规范。
- 实体字段与数据库字段一致。
- 不再依赖零散手动 SQL 初始化项目。
- 本轮结果记录到 `docs/iteration-log/v1.3-flyway.md`。

---

### v1.4 - 日志规范与敏感信息保护

#### 目标

建立基础日志规范，使日志可以帮助定位问题，同时避免泄露简历原文、密码、Token、AI Key 等敏感信息。

#### 任务拆解

- 检查现有日志使用情况。
- 设计日志级别约定。
- 在注册、登录、上传、解析、AI 分析、岗位匹配关键流程增加必要日志。
- 文件上传失败记录原因。
- 文本解析失败记录原因。
- AI 调用失败记录原因。
- 岗位匹配失败记录原因。
- 禁止打印密码。
- 禁止打印 JWT。
- 禁止打印 AI Key。
- 避免完整打印简历原文。
- 可选：增加 requestId / traceId。
- 更新 `docs/iteration-log/v1.4-logging.md`。

#### 验收标准

- 关键流程有必要日志。
- 错误场景能通过日志定位。
- 日志中不包含密码、Token、AI Key。
- 日志中不完整暴露用户简历原文。
- 日志级别使用合理。
- 本轮结果记录到 `docs/iteration-log/v1.4-logging.md`。

---

### v1.5 - Service 层单元测试

#### 目标

为核心业务逻辑补充 Service 层单元测试，提升修改代码时的信心。

#### 任务拆解

- 引入或确认 JUnit 5。
- 引入或确认 Mockito。
- 编写 AuthService 注册测试。
- 编写 AuthService 登录测试。
- 编写 ResumeService 文件校验测试。
- 编写 ResumeTextExtractService 基础测试。
- 编写 ResumeStructureParseService 基础测试。
- 编写 JobMatchService 匹配算法测试。
- 编写优化建议生成测试。
- 测试正常场景。
- 测试异常场景。
- 更新 `docs/iteration-log/v1.5-service-test.md`。

#### 验收标准

- 核心 Service 至少有基础单元测试。
- 岗位匹配算法有可重复测试样例。
- 文件校验逻辑有测试覆盖。
- 测试可以通过 Maven 命令执行。
- 测试不依赖真实外部 AI 服务。
- 本轮结果记录到 `docs/iteration-log/v1.5-service-test.md`。

---

### v1.6 - 关键 API 集成测试

#### 目标

为 Phase 1 主流程中的关键 API 增加集成测试或接口级测试，验证认证、权限边界和核心流程行为。

#### 任务拆解

- 确定 API 集成测试方式。
- 配置测试环境数据库。
- 编写认证接口测试。
- 编写受保护接口未登录访问测试。
- 编写简历上传权限测试。
- 编写简历列表用户隔离测试。
- 编写解析接口用户隔离测试。
- 编写 AI 分析接口基本测试。
- 编写岗位接口基本测试。
- 编写岗位匹配接口基本测试。
- 编写历史记录接口测试。
- 更新 `docs/iteration-log/v1.6-api-test.md`。

#### 验收标准

- 关键 API 有基础自动化验证。
- 未登录访问受保护接口会失败。
- 用户不能访问其他用户资源。
- 测试环境与开发环境配置分离。
- 测试不依赖真实 AI 服务返回。
- 本轮结果记录到 `docs/iteration-log/v1.6-api-test.md`。

---

### v1.7 - OpenAPI / Knife4j 接口文档

#### 目标

引入接口文档能力，使后端 API 可以被查看、调试和交付。

#### 任务拆解

- 添加 OpenAPI / Knife4j 依赖。
- 创建接口文档配置类。
- 配置文档标题和版本。
- 配置 JWT 认证说明。
- 为 AuthController 补充接口说明。
- 为 UserController 补充接口说明。
- 为 ResumeController 补充接口说明。
- 为 AnalysisController 补充接口说明。
- 为 JobController 补充接口说明。
- 为 HistoryController 补充接口说明。
- 为核心 DTO / VO 补充字段说明。
- 更新 README 中接口文档访问方式。
- 更新 `docs/iteration-log/v1.7-api-doc.md`。

#### 验收标准

- 可以访问接口文档页面。
- 核心接口分组清晰。
- 接口请求参数说明清晰。
- 接口返回结构说明清晰。
- 文档中说明 JWT 使用方式。
- 本轮结果记录到 `docs/iteration-log/v1.7-api-doc.md`。

---

### v1.8 - 本地编排、CI 与文档收口

#### 目标

整理本地开发环境、基础 CI 和 Phase 2 文档，使项目更容易复现、更适合展示。

#### 任务拆解

- 创建或整理根目录 `docker-compose.yml`。
- 编排 PostgreSQL。
- 编排 MinIO。
- 配置必要环境变量示例。
- 编写本地一键启动说明。
- 创建 GitHub Actions workflow。
- CI 中执行后端构建。
- CI 中执行后端测试。
- CI 中执行前端构建。
- 检查敏感信息未进入仓库。
- 更新 README。
- 更新 `docs/tasks/phase-2-task-list.md`。
- 更新 `docs/iteration-log/v1.8-engineering-summary.md`。
- 归档 Phase 2。

#### 验收标准

- 本地依赖服务可以通过编排方式启动。
- README 可以说明本地环境启动方式。
- CI 可以执行基础构建和测试。
- Phase 2 相关迭代日志完整。
- 项目具备更强的工程化展示价值。
- 本轮结果记录到 `docs/iteration-log/v1.8-engineering-summary.md`。

---

## 7. Phase 2 总体验收标准

Phase 2 完成时，必须满足：

- Phase 1 MVP 主流程仍可正常运行。
- 本地开发环境和测试环境配置已分离。
- 核心 API 错误格式一致。
- 请求参数校验更加明确。
- 文件上传限制具备统一校验。
- 数据库迁移脚本可重复执行。
- 核心 Service 具备基础单元测试。
- 关键 API 具备基础集成测试。
- 日志能帮助定位核心流程问题。
- 日志不泄露敏感信息。
- 接口文档可访问。
- 本地依赖服务可编排启动。
- CI 可以执行基础检查。
- README 和迭代日志已更新。

---

## 8. Phase 2 开发顺序

严格按以下顺序推进：

1. v1.1 Phase 2 基线检查与配置整理
2. v1.2 统一异常、错误码与参数校验增强
3. v1.3 Flyway 数据库迁移管理
4. v1.4 日志规范与敏感信息保护
5. v1.5 Service 层单元测试
6. v1.6 关键 API 集成测试
7. v1.7 OpenAPI / Knife4j 接口文档
8. v1.8 本地编排、CI 与文档收口

任何与工程化稳定性无关的新功能，应延后到 Phase 3 或后续阶段。
