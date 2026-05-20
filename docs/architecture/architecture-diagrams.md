# AI 简历优化与岗位匹配系统架构图

本文档用于沉淀 Phase 4 收口后的架构图、模块图、主流程图、异步任务流程图和部署图。当前图表以 Mermaid 作为源格式，方便在 Markdown、README、答辩文档和后续项目包装中复用。

说明：

- 图中只展示当前项目已经实现或明确预留的能力。
- Redis、MinIO 在当前阶段是部署依赖和配置预留，不表示后端主流程已经依赖 Redis 或上传链路已经切换到 MinIO。
- 当前部署图表达单机部署准备形态，不表示已经完成生产级高可用、HTTPS 或应用容器化。

## 1. 系统总体架构图

```mermaid
flowchart LR
    User[用户浏览器] --> Nginx[Nginx 反向代理]
    Nginx --> Web[Vue 3 前端静态页面]
    Nginx -->|/api| Backend[Spring Boot 后端]

    Backend --> DB[(PostgreSQL)]
    DB --> PgVector[pgvector 向量能力]
    Backend --> Storage[Local 文件存储目录]
    Backend --> AI[AI Chat API]
    Backend --> Embedding[Embedding API]

    Compose[Docker / Podman Compose] -.管理依赖.-> DB
    Compose -.预留.-> Redis[(Redis)]
    Compose -.预留.-> MinIO[(MinIO)]

    Backend -.当前未依赖.-> Redis
    Backend -.对象存储预留.-> MinIO
```

要点：

- 前端通过 Nginx 托管静态文件。
- `/api/` 请求由 Nginx 反向代理到后端。
- 当前后端主流程依赖 PostgreSQL、本地文件存储和外部 AI / Embedding 服务。
- Redis 和 MinIO 当前为部署预留，不作为已完成业务依赖夸大描述。

## 2. 后端模块图

```mermaid
flowchart TB
    Backend[com.winter.airesumeoptimizer]

    Backend --> Common[common<br/>结果封装 / 异常 / 日志脱敏 / 工具]
    Backend --> Config[config<br/>OpenAPI / Security / Async / Storage 配置]
    Backend --> Security[security<br/>JWT / 当前用户 / 权限失败处理]
    Backend --> Infra[infra<br/>AI Client / Embedding Client / Prompt / Storage]
    Backend --> Module[module<br/>业务模块]

    Module --> Auth[auth<br/>注册 / 登录]
    Module --> User[user<br/>用户信息]
    Module --> Resume[resume<br/>上传 / 解析 / 展示 / 删除]
    Module --> Job[job<br/>岗位库 / 目标岗位 / JD 解析]
    Module --> Analysis[analysis<br/>诊断 / 匹配 / 建议 / 改写 / 报告]
    Module --> EmbeddingModule[embedding<br/>简历向量 / 岗位向量]
    Module --> History[history<br/>历史记录 / AI 结果回看]
    Module --> Task[task<br/>异步任务记录 / 状态查询]

    Resume --> Infra
    Job --> Infra
    Analysis --> Infra
    EmbeddingModule --> Infra
    Auth --> Security
```

要点：

- 业务代码集中在 `module/` 下。
- `infra/` 只承接外部服务适配，不承载业务主流程规则。
- `common/`、`config/`、`security/` 保持基础能力边界。

## 3. 主业务流程图

```mermaid
flowchart TD
    Start([开始]) --> Login[注册 / 登录]
    Login --> Upload[上传简历]
    Upload --> Parse[简历解析]
    Parse --> Diagnosis[简历诊断]
    Diagnosis --> InputJD[用户粘贴目标岗位 JD]
    InputJD --> ParseJD[目标岗位解析]
    ParseJD --> Match[简历与目标岗位匹配分析]
    Match --> Suggestion[岗位优化建议]
    Suggestion --> Rewrite[局部改写用户选中片段]
    Rewrite --> History[AI 结果回看]
    History --> End([结束])
```

业务边界：

- 简历诊断只分析简历自身质量。
- 目标岗位解析只解析用户粘贴 JD。
- 匹配分析只判断简历与目标岗位是否匹配。
- 岗位优化建议只给策略建议和修改方向。
- 局部改写只改写用户选中的简历片段。
- AI 结果回看只查询历史结果，不触发新的 AI 生成。

## 4. 异步任务流程图

```mermaid
sequenceDiagram
    participant User as 用户
    participant Web as Vue 前端
    participant API as Spring Boot API
    participant Task as AsyncTaskService
    participant Worker as 后台线程池
    participant AI as AI / Embedding 服务
    participant DB as PostgreSQL

    User->>Web: 点击解析 / 诊断 / 向量生成
    Web->>API: 提交任务请求
    API->>Task: 创建任务记录
    Task->>DB: 写入 PENDING
    Task->>Worker: 提交后台执行
    API-->>Web: 返回 taskId

    Web->>API: 轮询任务状态
    Worker->>DB: 更新 RUNNING 和进度
    Worker->>AI: 调用 AI 或 Embedding
    AI-->>Worker: 返回结果或错误

    alt 执行成功
        Worker->>DB: 写入业务结果
        Worker->>DB: 更新 SUCCESS / 100
        Web->>API: 获取结果
        API-->>Web: 返回结果
    else 执行失败
        Worker->>DB: 写入 FAILED 和失败原因
        API-->>Web: 返回失败状态
    end
```

要点：

- 当前异步化仍在单体应用内完成，不引入消息队列。
- 前端通过轮询任务状态展示进度和错误原因。
- 失败信息应脱敏，不向前端暴露密钥、请求头或服务端内部路径。

## 5. 部署图

```mermaid
flowchart TB
    Browser[浏览器] --> Nginx[Nginx<br/>80 / 443]

    Nginx --> Static[前端静态资源<br/>web/dist]
    Nginx -->|/api| Backend[Spring Boot 后端<br/>127.0.0.1:8080]

    Backend --> Postgres[(PostgreSQL<br/>业务数据 / pgvector)]
    Backend --> Uploads[服务器本地上传目录<br/>APP_STORAGE_LOCAL_BASE_DIR]
    Backend --> AI[AI Chat API]
    Backend --> Embed[Embedding API]

    Compose[Compose 依赖服务] --> Postgres
    Compose --> Redis[(Redis 预留)]
    Compose --> MinIO[(MinIO 预留)]

    Admin[维护人员] --> Env[服务器 .env]
    Env --> Backend
    Admin --> Backup[数据库和上传目录备份]
    Backup --> Postgres
    Backup --> Uploads
```

部署边界：

- 后端端口不建议直接暴露公网。
- 前端生产构建建议使用 `VITE_API_BASE_URL=/api`。
- 上传文件目录必须持久化并纳入备份。
- 当前 Compose 不包含后端 / 前端应用镜像。


