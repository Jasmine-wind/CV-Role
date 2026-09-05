# CV Role

基于真实简历证据的岗位定向简历优化系统。

CV Role 接收用户已有简历和目标岗位 JD，将岗位要求与简历中的真实材料建立可追溯关系，在不编造经历、技能、数字或成果的前提下，完成岗位版本编辑、AI 单条建议、PDF 预览与导出。

## 核心流程

```mermaid
flowchart LR
  A[上传简历] --> B[内容确认]
  B --> C[目标岗位 JD]
  C --> D[岗位要求]
  D --> E[Evidence 核对]
  E --> F[Workspace 编辑]
  F --> G[AI 单条建议]
  G --> H[Diff / Apply]
  H --> I[PDF Preview]
  I --> J[Export]
```

完成的岗位任务会进入“最近优化”，用户可以重新打开历史岗位版本继续工作。

## 为什么不是全文 AI 简历生成器

- **全文生成 → 单 Bullet Suggest**：建议限定在用户明确选择的一条内容。
- **直接覆盖 → Diff + Explicit Apply**：用户先审阅变化，再明确采纳。
- **只靠 Prompt 约束 → 代码 Fact Validator**：候选内容还要通过事实闭包校验。
- **修改原简历 → SOURCE / TARGETED 分离**：岗位版本不会静默污染来源简历。
- **前端草稿直接导出 → Saved Revision + Preview Receipt**：预览和导出只消费服务端已保存版本。
- **简单生成 PDF → PDF Quality Gate**：导出前检查联系方式、排版边界、页结构和可读性。

## 当前能力

- 注册、登录、JWT 鉴权与用户资源隔离
- PDF / DOC / DOCX 简历上传、内容准备与必要的人工确认
- 岗位要求与冻结简历材料之间的 Evidence / Gap 分析
- Resume Review、SOURCE / TARGETED 版本、岗位任务与冻结输入快照
- 工作区结构化编辑、自动保存、Undo / Redo 与 CAS 并发冲突处理
- 单 Bullet AI 建议、Diff、Apply / Reject / Regenerate 与事实闭包校验
- 最近优化列表：可继续已完成任务，或重新分析失败任务
- Typst PDF Preview、导出前检查、私有导出物生命周期与历史记录


## 技术栈

| 层 | 当前技术 |
|---|---|
| 后端 | Java 21、Spring Boot 3.5、Spring Security、MyBatis-Plus、Flyway |
| 前端 | Vue 3、TypeScript、Vite、Pinia、Vue Router、Element Plus |
| 数据与存储 | PostgreSQL + pgvector、Redis、MinIO / 本地文件 |
| AI | OpenAI-compatible Chat API、OpenAI-compatible Embedding API |
| 渲染 | Typst CLI（PDF Preview / Export，镜像内置） |
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
- Typst CLI（PDF 预览 / 导出需要；本地开发需自行安装并保证 `typst` 在 PATH，容器镜像已内置）

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

后端测试使用 PostgreSQL/Flyway 与独立 MinIO lifecycle profile；CI 还运行 deterministic fake Provider 的 Chromium E2E。部署、备份和运行约束见 [docs/OPERATIONS.md](docs/OPERATIONS.md)。

## 生产部署

```bash
cp .env.production.example .env
# 替换全部生产密码、域名、JWT 和 API Key；生产 Compose 要求这些变量显式存在。

docker compose -f docker-compose.prod.yml --env-file .env config
docker compose -f docker-compose.prod.yml --env-file .env up -d --build
```

部署、HTTPS、更新、备份和排障见 [docs/OPERATIONS.md](docs/OPERATIONS.md)。

## 文档

| 文档 | 唯一职责 |
|---|---|
| [docs/PRD.md](docs/PRD.md) | V2 最高层产品与架构决策基线 |
| [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) | 当前实现架构、边界和 V2 演进约束 |
| [docs/PLAN.md](docs/PLAN.md) | V2 阶段顺序、门禁和非目标 |
| [docs/CONTEXT.md](docs/CONTEXT.md) | 当前状态、实现约束、已知差距和遗留风险 |
| [docs/OPERATIONS.md](docs/OPERATIONS.md) | 当前生产部署与运维 |

执行规则见 [AGENTS.md](AGENTS.md)。文档冲突时，V2 产品决策以 `docs/PRD.md` 为准，当前实现事实以代码和 `docs/CONTEXT.md` 为准。
