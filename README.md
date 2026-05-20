# AI 简历优化与岗位匹配系统

一句话介绍：本项目是一个面向求职场景的 AI 简历优化与岗位匹配系统，帮助用户围绕目标岗位完成简历解析、AI 诊断、匹配分析、优化建议、局部改写和岗位优化报告查看。

项目采用前后端分离结构和文档驱动方式推进，阶段目标、任务拆分和迭代记录放在 `docs/` 目录下。

## 项目核心价值

- 把“简历内容”和“目标岗位要求”放到同一个分析闭环中，而不是只做单次简历评分。
- 将 AI 输出拆成可核查的匹配项、缺口、建议、改写和报告，避免直接生成不可追溯的大段结论。
- 保留用户确认环节，AI 只做分析和表达优化，不替用户编造经历、技能、证书、奖项或量化指标。
- 为本地演示和后续部署准备清晰主线：上传简历 -> 解析简历 -> 诊断简历 -> 解析岗位 -> 匹配岗位 -> 生成建议 -> 局部改写 -> 查看岗位优化报告 -> 回看历史。

## 核心功能流程

```text
注册 / 登录
  -> 上传简历
  -> 简历文本提取与结构化解析
  -> AI 简历诊断
  -> 新增并解析目标岗位
  -> AI 岗位匹配
  -> 岗位优化建议
  -> 局部改写建议
  -> 岗位优化报告
  -> AI 历史回看
```

## 当前已完成能力概览

- 用户注册、登录和 JWT 鉴权。
- PDF、DOC、DOCX 简历上传、本地文件存储和文本提取。
- 简历结构化解析，支持解析模式、解析质量提示、解析结果展示和回归样例。
- AI 简历分析，输出评分、优势、问题和建议摘要。
- 目标岗位提交、岗位解析和岗位信息查看。
- AI 岗位匹配，输出匹配分数、强匹配项、弱匹配项、缺失技能、风险提示和依据。
- 岗位优化建议，按优先级聚合可执行修改建议。
- AI 局部改写，支持采纳 / 拒绝状态管理。
- 岗位优化报告，聚合匹配、建议、改写、下一步清单、模型信息和缺失依据 warning。
- AI 历史记录，支持回看简历分析、岗位匹配、优化建议和局部改写结果。

## AI 能力说明

- AI 能力用于简历诊断、岗位匹配、优化建议、局部改写和报告聚合展示。
- 岗位优化报告只聚合已有 AI 结果，不为了补依据重新调用 AI，不编造缺失依据。
- 局部改写只优化表达，不应写入用户没有真实经历过的项目、技能、证书、奖项或量化指标。
- 所有建议都需要用户结合真实经历确认后再使用。

## 演示入口

- [项目主线说明](docs/project-storyline.md)
- [完整演示流程](docs/demo/demo-flow.md)
- [虚构 Java 后端简历样例](docs/demo/demo-resume-java-backend.md)
- [虚构 Java 后端岗位样例](docs/demo/demo-job-java-backend.md)

## 功能特性

- 用户注册、登录和 JWT 鉴权
- PDF、DOC、DOCX 简历上传
- 本地文件存储
- 简历文本提取和基础结构化解析
- AI 简历分析，输出评分、优势、问题和建议摘要
- 目标岗位提交、解析和详情查看
- AI 岗位匹配
- 基于匹配差距的岗位优化建议
- AI 局部改写建议
- 岗位优化报告
- 用户维度历史记录，聚合上传、解析、AI 分析和岗位匹配状态

## 技术栈

后端：

- Java 21
- Spring Boot 3.5.12
- Spring Web
- Spring Security
- JWT
- MyBatis-Plus
- PostgreSQL
- Flyway
- PDFBox
- Apache POI
- OpenAPI / Swagger UI
- OpenAI-compatible API 调用适配
- Maven Wrapper

前端：

- Vue 3
- TypeScript
- Vite
- Pinia
- Vue Router
- Axios
- Element Plus

## 项目结构

```text
.
├── backend/                  # Spring Boot 后端项目
│   ├── src/main/java/
│   ├── src/main/resources/
│   │   ├── application.yaml
│   │   └── db/init/          # 数据库初始化 SQL
│   └── pom.xml
├── web/                      # Vue 3 + Vite 前端项目
│   ├── src/
│   └── package.json
├── docs/                     # 项目文档、阶段任务、迭代日志
├── deploy/                   # 部署相关文件，当前阶段可为空
├── AGENTS.md
└── README.md
```

后端业务模块位于：

```text
backend/src/main/java/com/winter/airesumeoptimizer/module/
```

当前主要模块：

- `auth`：注册、登录
- `user`：当前用户信息
- `resume`：简历上传、解析、删除
- `analysis`：AI 简历分析
- `job`：岗位和岗位匹配
- `history`：历史记录聚合查询

## 本地环境要求

- JDK 21+
- Node.js 20.19+ 或 22.12+
- PostgreSQL 14+
- 可用的 OpenAI-compatible 模型服务 API Key

## 环境变量

后端默认使用 `local` profile，也可以通过 `SPRING_PROFILES_ACTIVE` 切换到 `dev`、`prod` 或 `test`。公共配置位于：

```text
backend/src/main/resources/application.yaml
```

本地开发配置位于：

```text
backend/src/main/resources/application-local.yaml
```

开发测试环境配置位于：

```text
backend/src/main/resources/application-dev.yaml
```

生产部署配置位于：

```text
backend/src/main/resources/application-prod.yaml
```

测试环境预留配置位于：

```text
backend/src/main/resources/application-test.yaml
```

后端会读取仓库根目录 `.env` 或 `backend/.env`。本地可参考 `.env.example` 在仓库根目录创建 `.env`，并替换数据库密码、`JWT_SECRET`、AI API Key、Embedding API Key 和 MinIO 密钥。

说明：

- 不要提交 `.env`。
- `.env.example` 只保留本地示例值和占位符，可以提交。
- 示例密码仅用于本地开发，不要用于共享环境或生产部署。
- `AI_API_KEY` 是推荐的 AI 客户端 API Key 环境变量名，旧的 `DASHSCOPE_API_KEY` 仍作为兼容 fallback。
- AI base URL 推荐通过 `AI_BASE_URL` 覆盖，旧的 `OPENAI_BASE_URL` 仍作为兼容 fallback，默认是 `https://api.deepseek.com`。
- AI 模型、温度、超时和输出长度推荐使用 `AI_MODEL`、`AI_TEMPERATURE`、`AI_TIMEOUT_SECONDS`、`AI_MAX_TOKENS`。
- Embedding 使用独立的 `EMBEDDING_*` 配置；当前默认模型为 `Qwen3-Embedding-0.6B`，默认向量维度为 `1024`。
- `EMBEDDING_BASE_URL` 不绑定具体服务商，需要按实际 OpenAI-compatible Embedding 服务地址配置，例如本地服务 `http://localhost:8000/v1` 或第三方平台提供的 base-url。
- `EMBEDDING_API_KEY` 需要用户在本地 `.env` 中自行配置，不要提交真实密钥。
- 后续如果切换到 Qwen3-Embedding-4B 或 Qwen3-Embedding-8B，需要同步确认向量维度、历史向量数据兼容性和相似度查询策略。
- `JWT_SECRET` 请使用足够长的随机字符串。
- 当前 Phase 1 / Phase 2 仍使用本地文件存储，`LOCAL_STORAGE_BASE_DIR` 默认是 `uploads`。
- `MINIO_*` 当前用于本地依赖服务编排预留，现有上传链路仍走本地文件存储。

前端开发环境配置位于：

```text
web/.env.development
```

生产构建默认配置位于：

```text
web/.env.production
```

开发环境默认请求：

```text
http://localhost:8080
```

如需在本机覆盖后端地址，可在 `web/.env.local` 中配置：

```properties
VITE_API_BASE_URL=http://localhost:8080
```

## PostgreSQL 初始化与迁移

推荐先使用 Compose 启动本地依赖服务：

```bash
docker compose up -d postgres redis minio
```

如果使用 Podman：

```bash
podman compose up -d postgres redis minio
```

服务端口：

```text
PostgreSQL: localhost:5433
Redis:      localhost:6379
MinIO API:  http://localhost:9000
MinIO 控制台: http://localhost:9001
```

当前 `docker-compose.yml` 只编排本地依赖服务，不包含后端和前端应用镜像。后端仍通过 `cd backend && ./mvnw spring-boot:run` 启动，前端仍通过 `cd web && npm run dev` 启动；应用容器化和反向代理配置会在后续部署任务中继续整理。

Nginx 反向代理草案位于 `deploy/nginx/ai-resume.conf`。该文件仅用于后续部署准备，需要替换域名、前端静态文件路径并单独配置 HTTPS 后再用于真实服务器。

默认 PostgreSQL 配置与 `.env.example` 一致：

```properties
POSTGRES_DB=ai_resume_optimizer
POSTGRES_USER=dawn
POSTGRES_PASSWORD=change-me-local-postgres-password
```

如果不使用 Compose，也可以手动创建数据库：

```bash
createdb ai_resume_optimizer
```

确认 `.env` 中的数据库连接信息和本机一致：

```properties
DB_URL=jdbc:postgresql://localhost:5432/ai_resume_optimizer
DB_USERNAME=dawn
DB_PASSWORD=change-me-local-postgres-password
```

启动后端后，Flyway 会自动执行迁移脚本：

```bash
cd backend
./mvnw spring-boot:run
```

Flyway 脚本位于：

```text
backend/src/main/resources/db/migration/
```

当前迁移会创建 Phase 1 所需表，并预置 Phase 1 岗位数据。`backend/src/main/resources/db/init/` 下的 SQL 保留为历史初始化脚本，不再作为推荐初始化方式。

已有本地数据库如果已经手动建过表，当前配置会通过 `baseline-on-migrate` 接管，并继续执行后续迁移。

## pgvector 本地环境结论

v2.8.1 检查时，Compose 中的 PostgreSQL 镜像为：

```text
postgres:16-alpine
```

本地检查结果：

- PostgreSQL 版本：16.13。
- 当前镜像未安装 `vector` 扩展。
- `CREATE EXTENSION IF NOT EXISTS vector;` 会失败，错误原因是缺少 `vector.control`。
- 因此当前本地数据库不能直接使用 pgvector。

v2.8.2 已将 Compose PostgreSQL 镜像调整为：

```text
pgvector/pgvector:pg16
```

本地如果已经创建过旧容器，需要重新创建 PostgreSQL 容器后，Flyway 才能执行扩展初始化和向量表迁移。

后续迁移由 Flyway 执行扩展初始化：

```sql
CREATE EXTENSION IF NOT EXISTS vector;
```

当前默认 Embedding 模型为 `Qwen3-Embedding-0.6B`，默认向量维度为 `1024`。`EMBEDDING_BASE_URL` 仍由实际 OpenAI-compatible Embedding 服务决定，不在项目中写死；`EMBEDDING_API_KEY` 需要用户在本地 `.env` 中配置。当前 v2.8.3 只完成 Embedding 客户端和环境配置，不生成向量。当前向量表使用不固定维度的 `vector` 字段，并用 `embedding_dimension` 记录实际维度；pgvector 不可用时，只能作为开发过渡临时保存 Embedding JSON 字符串，不能作为最终方案。

停止本地依赖服务：

```bash
docker compose down
```

如需同时删除本地数据卷：

```bash
docker compose down -v
```

## 对象存储

当前 Phase 1 使用本地文件存储，上传文件默认保存到：

```text
backend/uploads/
```

因此本阶段不需要启动 MinIO。

如果后续切换到 MinIO，应在 `deploy/` 中补充对象存储配置，并同步修改后端存储实现和 README。

## 后端启动

进入后端目录：

```bash
cd backend
```

运行测试：

```bash
./mvnw test
```

启动后端：

```bash
./mvnw spring-boot:run
```

默认服务地址：

```text
http://localhost:8080
```

## 接口文档

后端启动后可访问接口文档：

```text
Swagger UI: http://localhost:8080/swagger-ui/index.html
OpenAPI JSON: http://localhost:8080/v3/api-docs
```

需要登录的接口使用 `bearerAuth` 认证方案。调试时先调用 `/api/auth/login` 获取返回的 `token`，再在 Swagger UI 右上角 `Authorize` 中填写该 token。

## 前端启动

进入前端目录：

```bash
cd web
```

安装依赖：

```bash
npm install
```

启动开发服务：

```bash
npm run dev
```

默认访问地址：

```text
http://localhost:5173
```

构建检查：

```bash
npm run build
```

## 演示流程

完整演示流程见 [docs/demo/demo-flow.md](docs/demo/demo-flow.md)。

推荐主线：

1. 注册并登录演示用户。
2. 上传虚构 Java 后端简历。
3. 解析简历并查看结构化结果。
4. 触发 AI 简历诊断。
5. 新增并解析虚构 Java 后端岗位。
6. 触发 AI 岗位匹配。
7. 生成岗位优化建议。
8. 对关键项目片段生成局部改写。
9. 查看岗位优化报告。
10. 进入 AI 历史记录回看完整过程。

## 测试账号

当前项目不预置测试账号。首次本地运行时请通过注册页面创建演示用户。

## CI

项目提供基础 GitHub Actions workflow：

```text
.github/workflows/ci.yml
```

CI 会执行：

- 后端 `./mvnw test`
- 前端 `npm ci`
- 前端 `npm run build`

后端 CI 会启动 PostgreSQL 服务容器，真实 AI smoke test 仍按环境变量条件跳过。

## 常见问题

### 前端提示“无法连接后端服务”

确认后端已启动，并检查 `VITE_API_BASE_URL` 是否指向 `http://localhost:8080`。

### 后端启动时报数据库连接失败

确认 PostgreSQL 已启动，数据库 `ai_resume_optimizer` 已创建，`.env` 中的 `DB_PASSWORD` 与本机数据库密码一致。

### AI 分析失败

检查 `.env` 中的 `AI_API_KEY`、`AI_BASE_URL`、`AI_MODEL` 是否匹配当前服务商；旧变量 `DASHSCOPE_API_KEY`、`OPENAI_BASE_URL`、`OPENAI_MODEL` 仍可兼容，但新环境优先使用 `AI_*`。

### AI 输出限制

AI 分析结果只作为简历检查和表达优化参考。涉及教育经历、工作经历、项目经历、技能、证书、奖项和量化指标的内容，必须由用户确认真实后再使用。系统不应代替用户编造不存在的经历或数据。

### 上传简历失败

当前仅支持 PDF、DOC、DOCX，单个文件最大 10 MB。

### 看不到岗位数据

确认后端已正常启动，Flyway 会自动执行 `backend/src/main/resources/db/migration/` 下的迁移脚本并写入预置岗位数据。

## 文档

- [项目总览](docs/01-project-overview.md)
- [项目主线说明](docs/project-storyline.md)
- [完整演示流程](docs/demo/demo-flow.md)
- [Phase 1 MVP](docs/phase-1-mvp.md)
- [Phase 2 工程化](docs/phase-2-engineering.md)
- [Phase 3 AI 能力深化](docs/phase-3-ai.md)
- [项目结构规范](docs/project-structure.md)
- [Phase 1 任务清单](docs/tasks/phase-1-task-list.md)
- [Phase 2 任务清单](docs/tasks/phase-2-task-list.md)
- [Phase 3 任务清单](docs/tasks/phase-3-task-list.md)
- [迭代日志](docs/iteration-log/)

## 后续规划

Phase 1 MVP 和 Phase 2 工程化增强已完成。Phase 3 已围绕 AI 简历分析、岗位解析、岗位匹配、优化建议、局部改写、历史记录、解析质量和岗位优化报告持续增强。

后续规划：

- Phase 4：服务拆分边界、工程质量、可观测性和更清晰的架构治理。
- Phase 5：产品化、部署、演示材料、上线配置和用户体验收口。
