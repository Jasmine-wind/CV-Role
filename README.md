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
- PDFBox
- Apache POI
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

后端会读取仓库根目录 `.env` 或 `backend/.env`。本地可在仓库根目录创建 `.env`：

```properties
DB_PASSWORD=your_postgres_password
JWT_SECRET=change-this-to-a-long-random-secret-at-least-32-chars
DASHSCOPE_API_KEY=your_api_key
OPENAI_MODEL=deepseek-v4-flash
OPENAI_TEMPERATURE=0.2
OPENAI_TIMEOUT_SECONDS=90
OPENAI_MAX_TOKENS=800
```

说明：

- 不要提交 `.env`。
- `DASHSCOPE_API_KEY` 当前被用作 AI 客户端 API Key 环境变量名。
- 当前配置的 AI base URL 在 `backend/src/main/resources/application.yaml` 中。
- `JWT_SECRET` 请使用足够长的随机字符串。

前端默认请求：

```text
http://localhost:8080
```

如需修改后端地址，可在 `web/.env.local` 中配置：

```properties
VITE_API_BASE_URL=http://localhost:8080
```

## PostgreSQL 初始化

1. 创建数据库：

```bash
createdb ai_resume_optimizer
```

2. 确认 `backend/src/main/resources/application.yaml` 中的数据库用户名和本机一致：

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/ai_resume_optimizer
    username: dawn
    password: ${DB_PASSWORD}
```

3. 按顺序执行初始化脚本：

```bash
psql -d ai_resume_optimizer -f backend/src/main/resources/db/init/v0.1.2-users.sql
psql -d ai_resume_optimizer -f backend/src/main/resources/db/init/v0.2.1-resumes.sql
psql -d ai_resume_optimizer -f backend/src/main/resources/db/init/v0.3.1-resume-parse-results.sql
psql -d ai_resume_optimizer -f backend/src/main/resources/db/init/v0.4.1-resume-ai-analyses.sql
psql -d ai_resume_optimizer -f backend/src/main/resources/db/init/v0.5.1-jobs.sql
psql -d ai_resume_optimizer -f backend/src/main/resources/db/init/v0.5.3-job-match-results.sql
```

`v0.5.1-jobs.sql` 会预置 Phase 1 可用岗位数据。

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

## 常见问题

### 前端提示“无法连接后端服务”

确认后端已启动，并检查 `VITE_API_BASE_URL` 是否指向 `http://localhost:8080`。

### 后端启动时报数据库连接失败

确认 PostgreSQL 已启动，数据库 `ai_resume_optimizer` 已创建，`.env` 中的 `DB_PASSWORD` 与本机数据库密码一致。

### AI 分析失败

检查 `.env` 中的 `DASHSCOPE_API_KEY`、`OPENAI_MODEL` 和后端 `application.yaml` 中的 AI base URL 是否匹配当前服务商。

### 上传简历失败

当前仅支持 PDF、DOC、DOCX，单个文件最大 10 MB。

### 看不到岗位数据

确认已经执行 `backend/src/main/resources/db/init/v0.5.1-jobs.sql`。

## 文档

- [开发流程](docs/00-development-workflow.md)
- [项目总览](docs/01-project-overview.md)
- [Phase 1 MVP](docs/phase-1-mvp.md)
- [项目结构规范](docs/project-structure.md)
- [Phase 1 任务清单](docs/tasks/generated-phase-1-task-list.md)
- [迭代日志](docs/iteration-log/)

## 后续规划

Phase 1 完成后，后续阶段可继续补充：

- 数据库迁移工具
- OpenAPI 文档
- 更完整的异常码体系
- 管理后台
- 岗位管理
- 更强的 AI 分析和匹配能力
- Docker Compose 完整开发环境
