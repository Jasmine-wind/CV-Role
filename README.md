# AI 简历优化与岗位匹配系统

<p>
  <img alt="Java 21" src="https://img.shields.io/badge/Java-21-007396">
  <img alt="Spring Boot 3.5" src="https://img.shields.io/badge/Spring_Boot-3.5-6DB33F">
  <img alt="Vue 3" src="https://img.shields.io/badge/Vue-3-42B883">
  <img alt="PostgreSQL pgvector" src="https://img.shields.io/badge/PostgreSQL-pgvector-4169E1">
  <img alt="AI Powered" src="https://img.shields.io/badge/AI-OpenAI--compatible-111827">
</p>

一个面向求职场景的 AI 简历优化与岗位匹配系统。系统围绕“简历内容 + 目标岗位 JD”建立分析闭环，帮助用户完成简历解析、AI 诊断、岗位匹配、优化建议、局部改写、报告回看和历史追踪。

本项目采用 Spring Boot + Vue 3 前后端分离架构，覆盖 JWT 鉴权、文件存储抽象、异步任务、AI 调用、Embedding、Redis、MinIO 和 Docker Compose 部署，适合作为 Java 后端 / AI 应用方向的项目展示。

![AI 简历优化与岗位匹配工作台](docs/screenshots/readme-cover.png)

## 项目亮点

- 面向真实求职流程：从上传简历、解析岗位到优化报告，形成完整业务闭环。
- 简历与岗位 JD 联合分析：不只做单次简历评分，而是围绕目标岗位识别优势、差距和风险。
- AI 输出可追溯：匹配、建议、改写和报告均保留结构化结果与历史记录。
- 控制 AI 边界：AI 只做分析和表达优化，不自动编造经历，不直接写回原始简历。
- 工程链路完整：前后端分离、JWT 鉴权、异步任务、文件存储抽象、缓存和对象存储均已接入。
- 可部署可运维：提供 Docker Compose 生产编排、Nginx、HTTPS、备份和日志运维说明。

## 核心流程

```text
注册登录
  -> 上传简历
  -> 简历解析
  -> AI 简历诊断
  -> 目标岗位解析
  -> 匹配分析
  -> 岗位优化建议
  -> 局部改写
  -> 优化报告
  -> AI 历史回看
```

## 功能概览

| 模块 | 能力 | 状态 |
|---|---|---|
| 用户与鉴权 | 注册、登录、JWT 鉴权、当前用户识别 | 已完成 |
| 简历上传与解析 | PDF / DOC / DOCX 上传、文本提取、结构化解析、完整原文查看 | 已完成 |
| AI 简历诊断 | 优势、问题、建议和下一步方向 | 已完成 |
| 目标岗位 | 用户粘贴 JD、岗位画像、核心技能、职责和经验信号 | 已完成 |
| 匹配分析 | 匹配分数、强弱匹配、缺失技能、风险提醒和依据 | 已完成 |
| 优化建议 | 按优先级输出可执行修改方向 | 已完成 |
| 局部改写 | 基于建议改写真实片段，支持采纳状态管理 | 已完成 |
| AI 历史 | 按结果类型回看用户可读报告 | 已完成 |
| 部署运维 | Docker Compose、Nginx、HTTPS、Redis、MinIO、备份脚本 | 已完成 |

## 技术栈

| 方向 | 技术 |
|---|---|
| 后端 | Java 21、Spring Boot 3.5、Spring Security、JWT、MyBatis-Plus、Flyway |
| 前端 | Vue 3、TypeScript、Vite、Pinia、Vue Router、Element Plus、Axios、SCSS |
| 数据与存储 | PostgreSQL、pgvector、Redis、MinIO、本地文件存储 |
| AI 能力 | OpenAI-compatible Chat API、SiliconFlow OpenAI-compatible Embeddings API |
| 部署运维 | Docker Compose、Nginx、Let's Encrypt HTTPS、Shell 运维脚本 |

## 系统架构

![系统总体架构](docs/architecture/a11cd1e7-642f-4119-917d-0b9c052d15c4.png)

说明：

- 前端通过同域 `/api` 或本地代理访问后端。
- 后端统一适配 AI Chat API 和 Embedding API，前端不接触真实密钥。
- Redis 只缓存可重新生成内容，不替代数据库核心状态。
- MinIO / 本地文件存储通过后端存储抽象切换，文件访问仍经过权限校验。

## 项目结构

```text
.
├── backend/                 # Spring Boot 后端
│   ├── src/main/java/       # 业务代码
│   └── src/main/resources/  # 配置、Flyway 迁移、Mapper
├── web/                     # Vue 3 + Vite 前端
│   └── src/                 # 页面、组件、API、状态和样式
├── docs/                    # 项目文档、展示材料、迭代日志
├── deploy/                  # Nginx 与部署配置
├── scripts/                 # 运维脚本
├── docker-compose.yml       # 本地依赖服务
└── docker-compose.prod.yml  # 生产部署编排
```

后端业务代码集中在 `backend/src/main/java/com/winter/airesumeoptimizer/module/`，按 `auth`、`user`、`resume`、`job`、`analysis`、`history` 等模块组织。

## 快速启动

### 1. 启动依赖服务

```bash
docker compose up -d postgres redis minio
```

本地开发依赖包括 PostgreSQL、Redis 和 MinIO。也可以只使用本地文件存储，具体配置见 `.env.example`。

### 2. 配置环境变量

复制示例配置：

```bash
cp .env.example .env
```

至少检查以下变量：

```properties
DB_URL=jdbc:postgresql://localhost:5433/ai_resume_optimizer
DB_USERNAME=dawn
DB_PASSWORD=change-me-local-postgres-password
JWT_SECRET=change-me-to-a-long-random-secret

AI_API_KEY=your-ai-api-key
AI_BASE_URL=https://api.deepseek.com
AI_MODEL=deepseek-chat

EMBEDDING_API_KEY=your-siliconflow-api-key
```

完整配置说明见 [.env.example](.env.example) 和 [部署与运维文档](docs/ai-resume-deployment-ops-guide.md)。

### 3. 启动后端

```bash
cd backend
./mvnw spring-boot:run
```

后端默认地址：

```text
http://localhost:8080
```

### 4. 启动前端

```bash
cd web
npm install
npm run dev
```

前端默认地址：

```text
http://localhost:5173
```

## API 文档

后端启动后访问：

```text
Swagger UI: http://localhost:8080/swagger-ui/index.html
OpenAPI JSON: http://localhost:8080/v3/api-docs
```

需要登录的接口使用 `bearerAuth`。调试时先调用 `/api/auth/login` 获取 token，再在 Swagger UI 的 `Authorize` 中填写。

## 部署说明

生产部署使用 `docker-compose.prod.yml`，包含 Nginx、Backend、PostgreSQL + pgvector、Redis、MinIO 和 certbot。

```bash
docker compose -f docker-compose.prod.yml --env-file .env up -d --build
```

完整部署、HTTPS、日志、备份、更新和排障流程见 [线上部署与运维指南](docs/ai-resume-deployment-ops-guide.md)。

## 项目展示

项目展示流程、公开示例数据和讲解要点见 [项目展示指南](docs/demo-guide.md)。

本仓库不提交真实个人简历附件、页面截图和架构图图片；截图与架构图可作为本地展示素材保留，文档中已配置本地引用路径。

## 文档导航

| 文档 | 内容 |
|---|---|
| [docs/README.md](docs/README.md) | 文档总入口 |
| [docs/demo-guide.md](docs/demo-guide.md) | 展示流程、公开示例数据、架构讲解 |
| [docs/project-final-summary.md](docs/project-final-summary.md) | 项目最终总结、阶段成果、边界说明 |
| [docs/ai-resume-deployment-ops-guide.md](docs/ai-resume-deployment-ops-guide.md) | 生产部署、HTTPS、运维、备份和排障 |
| [docs/project-structure.md](docs/project-structure.md) | 目录结构和代码放置规范 |

## 项目边界

- AI 只做简历诊断、岗位匹配、优化建议、局部改写和报告聚合展示。
- AI 不自动编造教育经历、工作经历、项目经历、技能、证书、奖项或量化指标。
- 局部改写不会自动写回原始简历，是否采纳由用户确认。
- 岗位优化报告聚合已有结果，不为了补依据重复调用 AI。
- 所有建议都需要用户结合真实经历确认后再使用。
