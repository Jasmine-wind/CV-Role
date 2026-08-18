# 架构基线

本文记录**当前仓库真实实现**及其向 V2 演进时必须保持的边界。V2 产品决策以 [PRD.md](PRD.md) 为准；本文不另行设计产品。

## 1. 当前系统

```text
Vue 3 SPA
   │ /api + JWT
   ▼
Spring Boot 单体应用
   ├─ PostgreSQL + Flyway + pgvector
   ├─ Redis（非关键、可重建缓存）
   ├─ Local / MinIO（简历文件）
   ├─ OpenAI-compatible Chat API
   └─ OpenAI-compatible Embedding API
```

生产环境由 `docker-compose.prod.yml` 编排 Nginx、backend、PostgreSQL、Redis、MinIO 和 certbot。Nginx 托管前端并反向代理 `/api`；后端不直接暴露公网端口。

## 2. 仓库与模块职责

```text
backend/  后端应用、数据库迁移和测试
web/      前端应用
docs/     长期产品、架构、计划、上下文与运维文档
deploy/   Nginx 配置
scripts/  生产运维脚本
```

后端根包：`com.winter.airesumeoptimizer`。

| 区域 | 职责 |
|---|---|
| `common/` | 统一响应、异常、日志等跨模块能力 |
| `config/` | Spring、安全、缓存、异步执行器配置 |
| `security/` | JWT、认证上下文、401 / 403 处理 |
| `infra/` | AI、Embedding、Redis、文件存储等外部适配 |
| `module/` | 业务模块与用例编排 |

当前业务模块：

- `auth` / `user`：账号、登录和当前用户。
- `resume`：简历文件、解析、质量检查与展示模型。
- `job`：预置岗位、用户目标 JD、岗位解析和匹配。
- `analysis`：诊断、AI 匹配、优化建议、局部改写和聚合报告。
- `embedding`：文本分块、向量生成、相似度与 RAG 上下文。
- `history`：旧历史聚合与 AI 结果回看。
- `task`：单进程异步任务记录、归属校验和状态查询。

前端按 `api/`、`components/`、`layout/`、`router/`、`stores/`、`types/`、`utils/`、`views/` 分层。Phase 1 后的页面路由只包括 Landing、首页、我的简历、岗位分析结果、登录和注册；一级导航只有首页和我的简历。

## 3. 当前主链路

```text
注册 / 登录
→ 选择已有简历，或上传后由 ResumeIntakeService 自动提交准备任务
→ 在首页粘贴目标岗位 JD
→ JobAnalysisService 保存原始 JD，并在一个后台任务内确保简历可用、解析 JD、生成匹配分析
→ 失败时复用已保存的简历与 JD 重试，不重复创建目标岗位
→ 岗位分析结果页展示已有优势、表达检查项和简历当前未体现的要求
```

Phase 1 的 `ResumeIntakeService` 与 `JobAnalysisService` 是默认用户流的两个深模块 seam：前者负责上传、默认准备和准备失败恢复，后者负责首次分析与复用已保存输入的重试；调用方不需要编排 Parse、Embedding、Prompt 或供应商步骤。正式 `ResumeVersion`、`OptimizationTask`、Evidence / Gap 模型尚未建立，因此结果页不会把当前匹配输出宣称为稳定的能力缺口判断。

重要边界：

- 岗位库、目标岗位管理、技术分类式 AI 历史和旧匹配编排页面已退出前端路由；对应后端表和服务暂时保留，供后续数据模型迁移与兼容读取，不能恢复为第二套用户主流程。
- AI 输出通过受控 DTO / Schema 解析后持久化；不能把模型原文直接当可信业务数据。
- 优化报告聚合已有结果，不为补全报告再次调用 AI。
- Redis 只缓存可重新生成内容，不承担唯一业务状态。
- 文件访问经过后端鉴权；MinIO bucket 不作为公开下载入口。
- 业务数据库结构只由 `db/migration/` 下的 Flyway 迁移维护。
- 异步任务是真实状态机，不伪造进度百分比。

## 4. 数据与外部系统

- PostgreSQL 是账号、简历元数据、解析结果、岗位、分析结果、向量和任务的事实来源。
- pgvector 当前用于简历 / JD 分块语义检索；向量不可用时部分 AI 链路可以降级。
- 简历原文件由 `FileStorageService` 抽象访问，本地开发默认 local，生产默认 MinIO。
- Chat 与 Embedding 都通过后端兼容客户端调用，密钥不得进入前端、日志或 Git。
- `application.yaml` 提供公共默认值，`application-local/dev/test/prod.yaml` 负责环境差异。

## 5. 后续 V2 目标架构边界

Phase 1 已完成用户链路收敛，但 V2 不推翻前后端分离和模块化单体基础。后续能力应围绕 PRD 冻结链路建立：

```text
Resume / ResumeVersion
JobTarget
OptimizationTask
Evidence Mapping / Gap Analysis
Workspace / Editor / Diff
AI Gateway / Provider Credential
Typst Preview / ExportArtifact
```

迁移时遵守：

1. **先收敛用户链路，再迁移内部模型**；不得先把 Provider、Prompt、Embedding、Task 暴露给普通用户。
2. **原始简历、原始 JD 与用户修改可追溯**；岗位版本不得静默污染原简历。
3. **事实约束先于生成能力**；Expression Gap 与 Capability Gap 必须基于可验证证据区分。
4. **所有资源显式归属用户**；查询至少包含 `current_user + resource_id`。
5. **OptimizationTask 保存输入和配置快照**；后续配置变化不得改写历史解释。
6. **Structured Resume JSON 是编辑和导出的业务数据源**；Typst 只是输出基础设施。
7. **保持可回滚迁移**；在替代链路验收前，不删除仍服务当前生产流量的接口或数据。
8. **不拆微服务、不引入消息队列或 Kubernetes**，除非未来有独立决策基线替代 PRD。

## 6. 安全与可靠性

当前必须继续保持：JWT 鉴权、参数校验、资源归属检查、日志脱敏、上传限制、私有对象存储、环境变量注入。

V2 新增 Provider / BYOK 时还必须具备：服务端加密、掩码显示、替换与删除、SSRF 防护、Redirect / Timeout / Response Size 限制和统一错误映射。

Resume 与 JD 均视为不可信数据，不能覆盖平台指令、Schema 或真实性约束。

## 7. 验证边界

- 后端：`cd backend && ./mvnw test`
- 前端：`cd web && npm run build`
- 部署配置：`docker compose config`（本地）和 `docker compose -f docker-compose.prod.yml --env-file <env> config`（生产模板）

当前 CI 执行后端测试与前端构建。V2 每个阶段必须在不破坏现有主链路的前提下增加针对新模型和新用户流的验证。
