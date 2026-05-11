# AI 简历优化与岗位匹配系统

本项目是一个前后端分离的 Phase 1 MVP，用于完成简历上传、文本解析、AI 简历分析、岗位匹配、优化建议和历史记录查看的基础闭环。

项目采用文档驱动方式推进，阶段目标、任务拆分和迭代记录放在 `docs/` 目录下。

## 功能特性

- 用户注册、登录和 JWT 鉴权
- PDF、DOC、DOCX 简历上传
- 本地文件存储
- 简历文本提取和基础结构化解析
- AI 简历分析，输出评分、优势、问题和建议摘要
- 预置岗位列表和岗位详情
- 基于关键词的岗位匹配
- 基于匹配差距的简历优化建议
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

后端默认使用 `dev` profile。公共配置位于：

```text
backend/src/main/resources/application.yaml
```

开发环境配置位于：

```text
backend/src/main/resources/application-dev.yaml
```

测试环境预留配置位于：

```text
backend/src/main/resources/application-test.yaml
```

后端会读取仓库根目录 `.env` 或 `backend/.env`。本地可参考 `.env.example` 在仓库根目录创建 `.env`：

```properties
SERVER_PORT=8080
POSTGRES_DB=ai_resume_optimizer
POSTGRES_USER=dawn
POSTGRES_PASSWORD=postgres
POSTGRES_PORT=5433
DB_URL=jdbc:postgresql://localhost:5432/ai_resume_optimizer
DB_USERNAME=dawn
DB_PASSWORD=postgres
JWT_SECRET=change-this-to-a-long-random-secret-at-least-32-chars
DASHSCOPE_API_KEY=your_api_key
OPENAI_BASE_URL=https://api.deepseek.com
OPENAI_MODEL=deepseek-v4-flash
OPENAI_TEMPERATURE=0.2
OPENAI_TIMEOUT_SECONDS=90
OPENAI_MAX_TOKENS=800
LOCAL_STORAGE_BASE_DIR=uploads
MINIO_ROOT_USER=minioadmin
MINIO_ROOT_PASSWORD=minioadmin
MINIO_API_PORT=9000
MINIO_CONSOLE_PORT=9001
```

说明：

- 不要提交 `.env`。
- `.env.example` 只保留占位符，可以提交。
- `DASHSCOPE_API_KEY` 当前被用作 AI 客户端 API Key 环境变量名。
- AI base URL 可通过 `OPENAI_BASE_URL` 覆盖，默认是 `https://api.deepseek.com`。
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
docker compose up -d postgres minio
```

如果使用 Podman：

```bash
podman compose up -d postgres minio
```

服务端口：

```text
PostgreSQL: localhost:5433
MinIO API:  http://localhost:9000
MinIO 控制台: http://localhost:9001
```

默认 PostgreSQL 配置与 `.env.example` 一致：

```properties
POSTGRES_DB=ai_resume_optimizer
POSTGRES_USER=dawn
POSTGRES_PASSWORD=postgres
```

如果不使用 Compose，也可以手动创建数据库：

```bash
createdb ai_resume_optimizer
```

确认 `.env` 中的数据库连接信息和本机一致：

```properties
DB_URL=jdbc:postgresql://localhost:5432/ai_resume_optimizer
DB_USERNAME=dawn
DB_PASSWORD=postgres
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

## Phase 1 演示流程

1. 注册用户
2. 登录系统
3. 上传 PDF、DOC 或 DOCX 简历
4. 在“我的简历”中查看上传记录
5. 触发简历解析
6. 查看结构化解析结果
7. 触发 AI 简历分析
8. 查看 AI 评分、优势、问题和建议摘要
9. 查看岗位列表
10. 进入岗位详情
11. 选择简历进行岗位匹配
12. 查看匹配分数、命中项、缺失项和优化建议
13. 进入历史记录页面，查看聚合状态

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

检查 `.env` 中的 `DASHSCOPE_API_KEY`、`OPENAI_MODEL` 和后端 `application.yaml` 中的 AI base URL 是否匹配当前服务商。

### AI 输出限制

AI 分析结果只作为简历检查和表达优化参考。涉及教育经历、工作经历、项目经历、技能、证书、奖项和量化指标的内容，必须由用户确认真实后再使用。系统不应代替用户编造不存在的经历或数据。

### 上传简历失败

当前仅支持 PDF、DOC、DOCX，单个文件最大 10 MB。

### 看不到岗位数据

确认后端已正常启动，Flyway 会自动执行 `backend/src/main/resources/db/migration/` 下的迁移脚本并写入预置岗位数据。

## 文档

- [开发流程](docs/00-development-workflow.md)
- [项目总览](docs/01-project-overview.md)
- [Phase 1 MVP](docs/phase-1-mvp.md)
- [Phase 2 工程化](docs/phase-2-engineering.md)
- [项目结构规范](docs/project-structure.md)
- [Phase 1 任务清单](docs/tasks/phase-1-task-list.md)
- [Phase 2 任务清单](docs/tasks/phase-2-task-list.md)
- [迭代日志](docs/iteration-log/)

## 后续规划

Phase 1 MVP 和 Phase 2 工程化增强已完成。后续可进入 Phase 3，重点放在 AI 能力深化：

- AI Prompt 结构化增强
- AI 分析结果质量提升
- 岗位匹配能力增强
- AI 调用失败降级策略
- 更完整的演示数据和案例
