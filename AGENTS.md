# Repository Guidelines

## Source of Truth

优先级（冲突时高者为准）：

1. `docs/PRD.md` — 产品目标、原则与边界
2. `docs/ARCHITECTURE.md` — 当前实现架构与关键设计
3. `docs/OPERATIONS.md` — 部署、HTTPS、备份与运维
4. 当前代码、Flyway 迁移、配置与测试 — 实现事实

## Repository & Code Placement

仓库根目录只做编排，不是 Spring Boot 工程：

```text
backend/  Spring Boot 后端
web/      Vue 3 前端
docs/     产品、架构与运维文档
deploy/   部署配置
scripts/  运维脚本
```

- 后端根包 `com.winter.airesumeoptimizer`：`common/`、`config/`、`security/`、`infra/`、`module/`；业务代码只放 `module/{module}/`，按 controller / service / impl / entity / mapper / dto / vo 分层。
- 数据库变更只用 Flyway 迁移：`backend/src/main/resources/db/migration/`；不改写已发布迁移，不做手工 `db/init` schema 脚本。
- 前端源码只放 `web/src/`，按 `api/`、`views/`、`components/`、`layout/`、`router/`、`stores/`、`types/`、`utils/`、`styles/` 分层。
- 不要在根目录创建 `src/`、`pom.xml`、`.mvn/`、`mvnw` 或应用代码。

## Product Rules

- 不编造用户简历中的经历、技能、数字或成果；AI 输出在解析与技术校验前都是不可信输入。
- SOURCE 是冻结来源；TARGET 是可编辑的岗位版本；Workspace 只写 TARGET。
- 质量问题以提示为主，不阻塞正常使用；只有技术上无法完成操作才 hard fail。
- 不为展示技术引入微服务、消息队列、Kubernetes、爬虫、自动投递或新的技术栈。

## Engineering Rules

- 优先简单、直接的实现；避免无意义的 interface / wrapper / coordinator / state machine。
- 保留 CAS 并发、资源 ownership 校验与 server-authoritative provenance；不削弱 Preview / Export receipt 绑定。
- 查询、文件与存储访问必须校验 current-user 归属；存储统一走 storage abstraction。
- Redis 不是业务事实的唯一来源；日志与错误响应不暴露凭据或 Provider 密钥。
- 修改行为必须同步更新对应测试；不确定的文件先保留并如实报告。

## Repository Hygiene

禁止提交：

- `.env`、密钥、API Key、Token
- 上传的真实简历与用户隐私数据
- 字体文件
- `node_modules`、`target`、`dist`、`coverage`
- Playwright report、`test-results`
- 本地截图与临时 QA 产物

## Verification

按修改范围运行：

```bash
cd backend && ./mvnw test

cd web && npm run test
cd web && npm run lint:check
cd web && npm run type-check
cd web && npm run build

git diff --check
```

部署相关改动还需渲染对应的 Compose 配置。环境性失败要如实报告，不要为通过检查而改动无关业务代码。
