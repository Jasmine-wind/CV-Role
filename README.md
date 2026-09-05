# CV-Role / AI Resume Optimizer

面向真实求职场景的岗位定向简历优化系统。当前已实现 V2 Phase 1–9：用户选择或上传真实简历、粘贴目标岗位 JD 后即可完成证据分析，在工作区可控编辑，并通过真实 Typst Preview 导出岗位定向 PDF；高级用户可在设置中选择账户级 BYOK。

> Phase 1–9 均已通过独立 Final Gate。Phase 9 完成了只读 Multi-JD Insight、最小 Observability、真实 PostgreSQL/Flyway + MinIO + fake Provider + Playwright E2E，以及非生产 Demo；未创建或批准 Phase 10。真实现状与差距见 [docs/CONTEXT.md](docs/CONTEXT.md)。


## 当前已实现

- 注册、登录、JWT 鉴权和用户资源隔离
- PDF / DOC / DOCX 上传，上传后自动触发文本提取、清洗和结构化准备
- 首页选择简历、粘贴目标岗位 JD 并一键开始岗位分析
- 后台自动保存和解析 JD、准备旧简历并生成正式 Evidence / Gap 分析
- 独立 SOURCE / TARGETED 简历版本、JobTarget、OptimizationTask 和冻结输入 / 配置快照
- 面向用户语言的已有优势、建议完善和当前材料未体现结果页
- 两栏优化工作区、结构化编辑、Undo / Redo、自动保存、并发冲突处置和恢复优化前版本
- 单 Bullet AI Suggest、代码 Diff、Apply / Reject / Regenerate 和事实闭包校验
- Classic / Modern / Minimal 内置模板、真实 Typst PDF Preview、导出前检查与私有 ExportArtifact 生命周期
- Landing、首页、分析、工作区与 AI 设置的统一信息层级，以及可恢复的 Loading / Error / Empty / 保存状态
- 可恢复的异步任务状态查询；旧诊断、建议、局部改写和报告能力仍保留用于兼容
- 可选账户级 OpenAI-compatible BYOK、加密 Credential、任务级 AI Selection Snapshot 与最小 Usage ledger（独立事务、Task attribution、90 天 retention）
- 达到样本门槛后可从首页查看只读的多 JD 岗位方向洞察；不改变单 JD 主链或创建能力事实
- 真实 PostgreSQL/Flyway integration、确定性 fake Provider 与 Playwright Chromium 恢复型 E2E
- 专用、非生产、普通 User 路径的 Demo Compose / 合成 seed / 显式确认 reset 工具
- OpenAI-compatible Chat / Platform-only Embedding 接入，pgvector 语义检索
- PostgreSQL、Redis、MinIO、本地文件存储和 Flyway 迁移
- Vue 3 前端、Docker Compose、Nginx、HTTPS 和运维脚本

当前用户主链路：

```text
注册 / 登录
→ 选择或上传简历（后台自动准备）
→ 粘贴目标岗位 JD
→ 开始分析
→ 查看已有优势、建议完善的材料和当前材料未体现的要求
→ 进入优化工作区人工编辑，或请求单 Bullet AI 建议
→ 查看 Diff 后显式采纳或拒绝，采纳内容自动保存
→ 选择模板预览 PDF 并导出可投递版本
```

一级导航仅保留“首页”和“我的简历”。正式 Evidence / Gap、Workspace、单 Bullet 受约束改写与 Typst Preview / PDF 导出均已完成。

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

后端测试使用 PostgreSQL/Flyway 与独立 MinIO lifecycle profile；CI 还运行 deterministic fake Provider 的 Chromium E2E。Phase 9 Demo 仅可通过 `deploy/demo/` 的独立环境启动，详见 [docs/OPERATIONS.md](docs/OPERATIONS.md)。

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
