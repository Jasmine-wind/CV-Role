# CV-Role / AI Resume Optimizer

面向真实求职场景的岗位定向简历优化系统。当前已完成 V2 Phase 1：用户选择或上传真实简历、粘贴目标岗位 JD 后即可开始岗位分析，解析与匹配准备由后台完成。

> 当前仅完成信息架构和分析主链路简化；版本、证据映射、Workspace、Diff、PDF 等后续能力尚未实现。真实现状与差距见 [docs/CONTEXT.md](docs/CONTEXT.md)。


## 当前已实现

- 注册、登录、JWT 鉴权和用户资源隔离
- PDF / DOC / DOCX 上传，上传后自动触发文本提取、清洗和结构化准备
- 首页选择简历、粘贴目标岗位 JD 并一键开始岗位分析
- 后台自动保存和解析 JD、准备旧简历并生成匹配分析
- 面向用户语言的已有优势、优先检查项和简历未体现项结果页
- 可恢复的异步任务状态查询；旧诊断、建议、局部改写和报告能力仍保留在后端待后续迁移
- OpenAI-compatible Chat / Embedding 接入，pgvector 语义检索
- PostgreSQL、Redis、MinIO、本地文件存储和 Flyway 迁移
- Vue 3 前端、Docker Compose、Nginx、HTTPS 和运维脚本

当前用户主链路：

```text
注册 / 登录
→ 选择或上传简历（后台自动准备）
→ 粘贴目标岗位 JD
→ 开始分析
→ 查看已有优势、值得优先检查的表达和简历当前未体现的要求
```

一级导航仅保留“首页”和“我的简历”。正式 Evidence / Gap 领域模型及可控修改链路将在后续 Phase 实现。

## 技术栈

| 层 | 当前技术 |
|---|---|
| 后端 | Java 21、Spring Boot 3.5、Spring Security、MyBatis-Plus、Flyway |
| 前端 | Vue 3、TypeScript、Vite、Pinia、Vue Router、Element Plus |
| 数据与存储 | PostgreSQL + pgvector、Redis、MinIO / 本地文件 |
| AI | OpenAI-compatible Chat API、OpenAI-compatible Embedding API |
| 部署 | Docker Compose、Nginx、Let's Encrypt、Shell 运维脚本 |

![当前系统架构](docs/architecture/system-overview.png)

## 仓库结构

```text
backend/    Spring Boot 后端、Flyway、测试
web/        Vue 3 前端
docs/       V2 基线、架构、计划、上下文与运维文档
deploy/     Nginx 配置
scripts/    运维脚本
```

后端业务代码位于 `backend/src/main/java/com/winter/airesumeoptimizer/module/`；基础设施适配位于 `infra/`；前端页面位于 `web/src/views/`。

## 本地运行

### 1. 环境要求

- Java 21
- Node.js `^20.19.0` 或 `>=22.12.0`
- Docker Compose

### 2. 配置并启动依赖

```bash
cp .env.example .env
# 按需填写 AI_API_KEY / EMBEDDING_API_KEY，并检查本地密码

docker compose up -d postgres redis minio
```

示例配置将 PostgreSQL 映射到宿主机 `5433`；后端使用 `.env` 中的 `DB_URL` 连接。

### 3. 启动后端

```bash
cd backend
./mvnw spring-boot:run
```

后端默认地址为 `http://localhost:8080`。Swagger UI：`http://localhost:8080/swagger-ui/index.html`。

### 4. 启动前端

```bash
cd web
npm ci
npm run dev
```

前端默认地址为 `http://localhost:5173`。

## 检查命令

```bash
cd backend && ./mvnw test
cd web && npm run build
```

后端测试使用 PostgreSQL test profile；CI 定义见 `.github/workflows/ci.yml`。

## 生产部署

```bash
cp .env.production.example .env.production
# 替换全部生产密码、域名、JWT 和 API Key

docker compose -f docker-compose.prod.yml --env-file .env.production up -d --build
```

部署、HTTPS、更新、备份和排障见 [docs/OPERATIONS.md](docs/OPERATIONS.md)。

## 文档

| 文档 | 唯一职责 |
|---|---|
| [docs/PRD.md](docs/PRD.md) | V2 最高层产品与架构决策基线 |
| [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) | 当前实现架构、边界和 V2 演进约束 |
| [docs/PLAN.md](docs/PLAN.md) | V2 阶段顺序、门禁和非目标 |
| [docs/CONTEXT.md](docs/CONTEXT.md) | 当前能力、历史决策、已知差距和仓库状态 |
| [docs/OPERATIONS.md](docs/OPERATIONS.md) | 当前生产部署与运维 |

执行规则见 [AGENTS.md](AGENTS.md)。文档冲突时，V2 产品决策以 `docs/PRD.md` 为准，当前实现事实以代码和 `docs/CONTEXT.md` 为准。
