# Phase 5 - 产品化增强、正式部署与项目包装阶段

状态：进行中

## 当前状态

Phase 4 已完成，项目已经具备较完整的主业务流程、架构演进基础、文件存储抽象方案、异步任务模型、安全治理、环境隔离、部署准备和阶段收口文档。

Phase 5 不再以继续堆叠大功能为主，而是将项目从“功能可用的开发项目”进一步打磨为“可部署、可演示、可包装、可用于求职展示的完整作品”。

前端 UI/UX 深度优化由独立任务文档维护，本文件只保留前端相关的部署、构建、截图和演示要求，不展开前端页面重构任务。

---

## 1. Phase 5 目标

Phase 5 的核心目标是完成产品化增强、正式 Docker 部署和项目展示包装。

完成后项目应具备：

- Redis 正式接入至少一个真实业务缓存场景。
- MinIO 正式接入文件上传、读取、删除流程。
- 项目可通过 Docker Compose 在服务器部署。
- PostgreSQL、Redis、MinIO、后端、前端、Nginx 均可容器化运行。
- 可通过域名和 HTTPS 访问系统。
- 生产环境配置、日志、备份、安全复查有明确方案。
- README、演示文档、截图素材、简历项目描述和面试讲解材料完整。
- 项目可以作为 Java 后端 / AI 应用方向实习作品展示。

---

## 2. 阶段定位

Phase 5 是从“工程项目”走向“作品项目”的阶段。

前四个阶段侧重：

```text
Phase 1：基础功能闭环
Phase 2：工程化增强和 AI 能力扩展
Phase 3：解析、Embedding、RAG、AI 结果治理等能力完善
Phase 4：架构演进、部署准备与安全治理
```

Phase 5 侧重：

```text
Redis / MinIO 增强
Docker Compose 正式部署
域名和 HTTPS
备份、日志、运维
README 和演示材料
简历包装和面试表达
```

---

## 3. Phase 5 总原则

- 不再大规模新增业务功能。
- 不推翻已有后端架构。
- 不大规模重写简历解析主流程。
- 不做微服务拆分。
- 不做 Kubernetes。
- 不做复杂 CI/CD。
- 不做商业化支付、多租户 SaaS、管理员后台等无关能力。
- Docker Compose 作为唯一主部署路线。
- Redis 和 MinIO 作为本阶段必做增强。
- 前端 UI/UX 深度优化单独维护任务文档。
- 项目包装不能夸大未完成能力。
- 所有部署和包装内容必须可验证、可复现、可讲解。

---

## 4. Phase 5 技术范围

### 4.1 必做技术增强

- Redis 正式接入。
- MinIO 正式接入。
- 后端 Dockerfile。
- 前端 Dockerfile 或 Nginx 静态资源镜像。
- `docker-compose.prod.yml`。
- PostgreSQL + pgvector 容器。
- Redis 容器。
- MinIO 容器。
- Nginx 容器。
- 生产 `.env` 模板。
- 域名与 HTTPS 配置。
- 数据库备份脚本。
- MinIO / 上传文件备份方案。
- 日志目录规划。

### 4.2 可选增强

- 简单接口限流。
- AI 结果缓存策略优化。
- Embedding 缓存策略优化。
- 部署脚本封装。
- 简单健康检查页面。

### 4.3 本阶段不做

- 复杂分布式缓存体系。
- Redis 替代数据库任务状态表。
- 复杂分布式锁。
- 复杂任务调度平台。
- Kubernetes。
- 完整 CI/CD 平台。
- Prometheus / Grafana 复杂监控。
- 商业化支付系统。
- 企业级 RBAC 管理后台。
- 新一轮模型微调。
- 大规模推荐系统。

---

## 5. 推荐最终部署架构

Phase 5 服务器部署统一采用 Docker Compose 单机部署。

```text
用户浏览器
  ↓
Nginx 容器 + HTTPS
  ├── 前端 Vue 静态资源
  └── /api 反向代理
        ↓
    Spring Boot 后端容器
        ↓
    PostgreSQL + pgvector 容器
    Redis 容器
    MinIO 容器
    AI Chat API / Embedding API
```

### 服务器仅要求安装

```text
Docker
Docker Compose
防火墙工具
```

不再把以下方式作为主部署路线：

```text
手动运行 jar
宿主机直接安装 PostgreSQL
宿主机直接安装 Redis
宿主机直接安装 MinIO
宿主机直接安装 Nginx 托管前端
systemd 直接管理后端 jar
```

如果后续需要开机自启，优先使用 Docker Compose 的：

```yaml
restart: unless-stopped
```

systemd 仅作为管理 Docker Compose 启动命令的可选方案，不作为主线。

---

## 6. 推荐服务器目录

```text
/opt/ai-resume-optimizer/
├── docker-compose.prod.yml
├── .env
├── nginx/
│   ├── nginx.conf
│   └── conf.d/
│       └── ai-resume.conf
├── certbot/
│   ├── conf/
│   └── www/
├── logs/
│   ├── backend/
│   └── nginx/
├── data/
│   ├── postgres/
│   ├── redis/
│   ├── minio/
│   └── uploads/
└── backups/
    ├── postgres/
    ├── minio/
    └── uploads/
```

说明：

- `data/postgres`：PostgreSQL 持久化数据。
- `data/redis`：Redis 持久化数据，可选。
- `data/minio`：MinIO 对象存储数据。
- `data/uploads`：本地存储兼容目录或临时文件目录。
- `logs/backend`：后端日志。
- `logs/nginx`：Nginx 日志。
- `backups`：数据库、MinIO、上传文件备份目录。

---

## 7. Phase 5 总体版本规划

| 版本 | 主题 | 核心产出 |
|---|---|---|
| v4.1 | Phase 5 启动检查与范围冻结 | 确认 Phase 5 边界，冻结新增大功能 |
| v4.2 | Redis 正式接入与缓存增强 | Redis 配置、缓存场景、降级策略 |
| v4.3 | MinIO 正式接入与文件存储切换 | MinIO 存储实现、上传读取删除闭环 |
| v4.4 | 上线前稳定性与安全复查 | 生产配置、安全、日志、CORS、文件权限复查 |
| v4.5 | Docker 镜像与 Compose 生产编排 | 后端/前端 Dockerfile、compose.prod、volume、healthcheck |
| v4.6 | Docker Compose 服务器部署、域名与 HTTPS | 服务器部署、Nginx、域名、HTTPS、完整流程验证 |
| v4.7 | 数据备份、日志与运维脚本 | 数据库备份、MinIO 备份、日志、重启、巡检 |
| v4.8 | README、演示文档与截图素材 | README、演示流程、截图、演示数据 |
| v4.9 | 简历包装与面试讲解材料 | 简历描述、项目讲解稿、面试问答 |

---

# v4.1 - Phase 5 启动检查与范围冻结

状态：已完成

## 目标

在正式开始 Phase 5 前，确认 Phase 4 已经完成、Phase 5 范围清晰、后续不再随意新增大型功能，避免产品化阶段继续发散。

## 任务

- [x] 检查 Phase 4 是否已完成收口。
- [x] 确认主业务流程是否能跑通。
- [x] 确认 Redis 和 MinIO 已从预留改为 Phase 5 必做增强。
- [x] 确认服务器部署统一采用 Docker Compose。
- [x] 确认前端 UI/UX 深度优化由独立文档维护。
- [x] 确认 Phase 5 不再新增大型业务模块。
- [x] 确认不做微服务、Kubernetes、复杂 CI/CD。
- [x] 确认当前仍然保留的技术债。
- [x] 创建或更新 `docs/iteration-log/v4.1-phase5-start.md`。

## v4.1 启动检查结果

### Phase 4 收口状态

- Phase 4 的 v3.1 - v3.8 已完成，详细过程保留在 `docs/iteration-log/`。
- Phase 4 已完成架构审查、包边界整理、文件存储抽象、异步任务基础、任务状态机、安全加固、部署配置和运维文档初版。
- `docs/iteration-log/` 中已存在 v3.1 - v3.8 对应日志。

### 主业务流程状态

当前主流程继续冻结为：

```text
注册 / 登录
-> 工作台
-> 上传简历
-> 简历解析
-> 简历诊断
-> 新增目标岗位
-> 目标岗位解析
-> 匹配分析
-> 岗位优化建议
-> 局部改写
-> 岗位优化报告
-> AI 结果回看
```

本阶段不再新增新的核心业务节点。

### Phase 5 必做增强确认

- Redis 从 Phase 4 的依赖预留升级为 Phase 5 必做增强，v4.2 正式接入至少一个真实缓存场景。
- MinIO 从 Phase 4 的配置预留升级为 Phase 5 必做增强，v4.3 正式接入文件上传、读取和删除闭环。
- Docker Compose 是唯一主部署路线，后续不再以手动 jar、宿主机直接安装 PostgreSQL / Redis / MinIO / Nginx 作为主线。
- 前端 UI/UX 深度优化继续由独立任务文档维护，本文件只保留构建、部署、截图和演示相关要求。

### 当前保留技术债

- Redis 目前仍未接入真实业务缓存场景。
- MinIO 目前仍未启用真实对象存储实现。
- 当前 Compose 仍偏本地依赖编排，尚未包含完整后端、前端、Nginx 生产编排。
- 生产 HTTPS、域名、备份脚本、日志目录和运维巡检仍未完成。
- 前端 UI/UX 已有专项优化结果，但不纳入 Phase 5 主任务继续扩展业务范围。

## v4.1 结论

Phase 5 可以启动。后续按 v4.2 - v4.9 顺序推进，不再穿插新增大型业务模块；如遇新需求，先判断是否属于 Redis、MinIO、部署、备份、运维、演示包装或前端专项范围，否则延后处理。

## Phase 5 范围冻结规则

Phase 5 期间原则上不再新增：

- 新业务模块。
- 新 AI 大功能。
- 新推荐算法。
- 新管理员后台。
- 新商业化能力。
- 大规模数据库重构。
- 微服务拆分。

允许新增：

- Redis 缓存增强。
- MinIO 文件存储增强。
- 部署相关配置。
- 日志、备份、运维脚本。
- README、演示文档、截图素材。
- 简历和面试包装材料。
- 独立前端 UI/UX 文档中的页面优化。

## 验收标准

- Phase 5 任务范围明确。
- 版本号统一使用 v4.x。
- Redis / MinIO 被明确为必做增强。
- Docker Compose 被明确为主部署方式。
- 前端 UI/UX 任务已从主文档拆出。
- 后续不再随意新增大型功能。
- 阶段日志已更新。

---

# v4.2 - Redis 正式接入与缓存增强

状态：已完成

## 目标

将 Redis 从“配置预留”升级为“正式业务接入”，至少在一个真实场景中使用 Redis 缓存，同时保证 Redis 不可用时系统有合理降级策略。

## 推荐接入场景

优先选择以下场景之一或多个：

| 场景 | 优先级 | 原因 |
|---|---|---|
| AI 结果缓存 | 高 | 避免同一输入重复调用模型 |
| AI 展示摘要缓存 | 高 | 提升解析结果页访问速度 |
| 岗位描述解析缓存 | 中 | 同一 JD 可复用解析结果 |
| Embedding 结果缓存 | 中 | 避免重复向量化相同 chunk |
| 简单接口限流 | 低 | 可作为安全增强，但不是优先目标 |

不建议当前阶段：

- 用 Redis 替代数据库任务状态表。
- 用 Redis 保存核心业务结果。
- 做复杂分布式锁。
- 做多级缓存架构。
- 做分布式 Session。

## 推荐技术设计

### 缓存 Key 规则

缓存 key 必须包含：

```text
业务类型
用户 ID 或资源 ID
输入 hash
模型名
promptVersion
parserVersion / displayVersion
```

示例：

```text
ai:display-summary:user:{userId}:resume:{resumeId}:hash:{hash}:v:{version}
ai:job-parse:user:{userId}:jd:{jobDescriptionId}:hash:{hash}:v:{version}
embedding:chunk:{chunkHash}:model:{modelName}
```

### TTL 建议

| 缓存类型 | TTL |
|---|---|
| AI 展示摘要 | 1-7 天 |
| 岗位描述解析 | 1-7 天 |
| AI 匹配结果缓存 | 1-3 天 |
| Embedding chunk 缓存 | 7-30 天 |
| 限流计数 | 1-10 分钟 |

### 降级策略

Redis 不可用时：

- 不能影响主业务核心流程。
- 允许回退到数据库结果。
- 允许重新调用 AI，但要记录 warning。
- 不应导致系统启动失败，除非明确设置 Redis 为强依赖。
- 日志中记录 Redis 连接异常，但不输出敏感配置。

## 配置建议

```yaml
spring:
  data:
    redis:
      host: ${REDIS_HOST:redis}
      port: ${REDIS_PORT:6379}
      password: ${REDIS_PASSWORD:}
      database: ${REDIS_DATABASE:0}

app:
  cache:
    redis:
      enabled: ${APP_REDIS_CACHE_ENABLED:true}
      default-ttl-seconds: ${APP_REDIS_DEFAULT_TTL_SECONDS:86400}
```

## 任务

- [x] 检查当前是否已有 Redis 依赖。
- [x] 新增或整理 Redis 配置。
- [x] 新增 RedisTemplate 或 CacheManager 配置。
- [x] 明确 Redis 序列化方式。
- [x] 选择至少一个真实缓存场景。
- [x] 实现缓存读取。
- [x] 实现缓存写入。
- [x] 实现缓存 key 生成。
- [x] 设置 TTL。
- [x] 处理 Redis 不可用降级。
- [x] 避免核心业务数据只存在 Redis。
- [x] 更新 `.env.example`。
- [x] 确认当前 `docker-compose.yml` 已包含 Redis 服务；`docker-compose.prod.yml` 仍按 v4.5 统一创建。
- [x] 更新文档和迭代日志。

## 验收标准

- Redis 可以通过配置接入。
- Docker Compose 中包含 Redis 服务。
- 至少一个真实业务场景使用 Redis 缓存。
- 缓存 key 具备用户隔离或资源隔离。
- 缓存有 TTL。
- Redis 不可用时有降级策略。
- 不把核心业务结果只存 Redis。
- 后端构建通过。
- 日志不泄露 Redis 密码。
- 迭代日志已更新。

## v4.2 实现结果

### 接入方式

- 后端新增 `spring-boot-starter-data-redis`。
- 新增 `RedisCacheConfig`，显式提供 `StringRedisTemplate`。
- Redis key、value、hash key、hash value 均使用 UTF-8 字符串序列化。
- 新增 `RedisCacheProperties`，统一读取 `app.cache.redis.*` 配置。
- 新增 `RedisJsonCacheService`，以 JSON 字符串形式读写非核心缓存数据。

### 首个真实缓存场景

本版本选择“AI 简历展示模型缓存”作为首个 Redis 真实业务场景：

```text
简历结构化结果
-> 构建 AI 展示模型 prompt input
-> 构建包含 resumeId、structuredDataHash、promptVersion、modelName、adapterVersion 的缓存 key
-> 优先读取本地内存缓存
-> 未命中时读取 Redis
-> Redis 未命中时调用 AI
-> AI 结果写入本地内存缓存和 Redis
```

该场景只缓存可重新生成的 AI 展示模型，不保存唯一核心业务结果。

### 缓存 Key 和 TTL

- key 前缀：`APP_REDIS_KEY_PREFIX`，默认 `ai-resume-optimizer`。
- AI 展示模型 key 包含 `resumeId`、结构化输入 hash、`displayPromptVersion`、`modelName` 和 `displayAdapterVersion`。
- 默认 TTL：`APP_REDIS_DEFAULT_TTL_SECONDS`，默认 86400 秒。
- AI 展示模型 TTL：`APP_REDIS_AI_DISPLAY_MODEL_TTL_SECONDS`，默认 86400 秒。

### 降级策略

- Redis 读取失败时记录 warning，继续走本地内存缓存或 AI 生成。
- Redis 写入失败时记录 warning，不影响用户主流程。
- 测试环境默认关闭 Redis 缓存，避免单元测试依赖外部 Redis。
- Redis 不保存唯一业务结果，数据库仍是结果和状态的唯一可信来源。

### 配置和 Compose

- `application.yaml` 已补充 `spring.data.redis.*` 和 `app.cache.redis.*`。
- `application-test.yaml` 已关闭 Redis cache。
- `.env.example` 已补充 Redis 连接、超时、TTL 和 key prefix。
- 当前 `docker-compose.yml` 已包含 Redis 服务和 volume；生产 Compose 统一留到 v4.5 创建。

## v4.2 验证记录

后端测试：

```bash
cd backend
./mvnw -q -Dtest=ResumeDisplayModelServiceImplTest test
```

后端编译：

```bash
cd backend
./mvnw -q -DskipTests compile
```

结果：

- `ResumeDisplayModelServiceImplTest` 通过。
- 后端编译通过。
- 首次验证时 Maven 需要写入 `~/.m2` 下载 Redis starter，已在授权后完成。

## v4.2 当前不足

- 尚未用真实 Redis 容器做浏览器端到端缓存命中验收。
- `docker-compose.prod.yml` 尚未创建，生产 Redis 服务会在 v4.5 统一编排。
- 当前只接入 AI 展示模型缓存，AI 匹配结果、岗位解析结果和 Embedding 缓存留作后续可选增强。

---

# v4.3 - MinIO 正式接入与文件存储切换

状态：已完成

## 目标

将 MinIO 从“配置预留”升级为正式文件存储实现，使简历文件可以通过 `FileStorageService` 在本地存储与 MinIO 之间切换。

## 核心原则

- 业务层只依赖 `FileStorageService`。
- 本地存储和 MinIO 都是 `FileStorageService` 的实现。
- 默认部署阶段优先启用 MinIO。
- 不向前端暴露 MinIO 永久公开 URL。
- 文件访问必须经过后端权限校验。
- 简历解析从 MinIO 读取文件流，不依赖本地路径。

## 推荐结构

```text
infra/storage
├── FileStorageService.java
├── LocalFileStorageService.java
├── MinioFileStorageService.java
├── StorageProperties.java
├── StoreFileCommand.java
├── StoredFile.java
└── StoredFileMetadata.java
```

## 推荐配置

```yaml
app:
  storage:
    type: ${APP_STORAGE_TYPE:minio}
    local:
      base-dir: ${APP_STORAGE_LOCAL_BASE_DIR:uploads}
    minio:
      endpoint: ${MINIO_ENDPOINT:http://minio:9000}
      access-key: ${MINIO_ACCESS_KEY:minioadmin}
      secret-key: ${MINIO_SECRET_KEY:minioadmin}
      bucket: ${MINIO_BUCKET:ai-resume-files}
      public-endpoint: ${MINIO_PUBLIC_ENDPOINT:}
```

生产环境必须修改：

```text
MINIO_ACCESS_KEY
MINIO_SECRET_KEY
MINIO_BUCKET
```

## MinIO 实现要求

`MinioFileStorageService` 需要支持：

- 上传文件对象。
- 读取文件输入流。
- 读取文件字节，可选。
- 删除对象。
- 判断对象是否存在。
- 获取对象元信息。
- 自动创建 bucket，可选。
- 路径 / objectKey 规范化。
- 处理 MinIO 异常并转成业务异常。

## 文件访问安全

- 文件下载或读取必须先校验 resume 属于当前用户。
- 后端根据 resume.storageKey 读取文件。
- 前端不能直接传 objectKey 访问文件。
- Bucket 不应设置为公开匿名读写。
- 如需预览或下载，优先通过后端代理或短期签名 URL。

## 任务

- [x] 检查 `FileStorageService` 是否已经足够抽象。
- [x] 引入 MinIO SDK。
- [x] 新增 `MinioFileStorageService`。
- [x] 新增或整理 MinIO 配置类。
- [x] 实现上传对象。
- [x] 实现读取对象流。
- [x] 实现删除对象。
- [x] 实现 exists / metadata。
- [x] 使用 `app.storage.type` 切换 local / minio。
- [x] 确认简历上传逻辑已通过 `FileStorageService` 保存文件，切换为 MinIO 后无需修改接口路径。
- [x] 确认简历解析逻辑已通过 `FileStorageService.loadAsStream` 读取文件，切换为 MinIO 后无需修改接口路径。
- [x] 确认简历删除逻辑已通过 `FileStorageService.delete` 处理对象，切换为 MinIO 后无需修改接口路径。
- [x] 确认当前 `docker-compose.yml` 已包含 MinIO 服务；`docker-compose.prod.yml` 仍按 v4.5 统一创建。
- [x] 更新 `.env.example`。
- [x] 更新部署文档和迭代日志。

## 验收标准

- MinIO 容器可以启动。
- MinIO bucket 可用。
- 后端可以连接 MinIO。
- 简历上传可以保存到 MinIO。
- 简历解析可以从 MinIO 读取文件。
- 简历删除可以处理 MinIO 对象。
- local / minio 可以通过配置切换。
- 前端拿不到 MinIO 永久公开地址。
- 用户不能读取他人文件。
- 后端构建通过。
- 迭代日志已更新。

## v4.3 实现结果

### 接入方式

- 后端新增 `io.minio:minio` SDK。
- 新增 `MinioStorageConfig`，在 `app.storage.type=minio` 时创建 `MinioClient`。
- 新增 `MinioFileStorageService`，在 `app.storage.type=minio` 时作为 `FileStorageService` 实现启用。
- 继续保留 `LocalFileStorageService`，默认本地开发仍可使用 `app.storage.type=local`。
- `application-prod.yaml` 中生产 Profile 默认优先使用 `minio`。

### 文件能力

`MinioFileStorageService` 已实现：

- 上传对象。
- 读取对象输入流。
- 删除对象。
- 判断对象是否存在。
- 读取对象元信息。
- bucket 不存在时自动创建。
- objectKey 和业务目录规范化。
- 将 MinIO 异常转换为 `FileStorageException`。

### 主流程兼容性

- 简历上传仍通过 `FileStorageService.store` 保存文件。
- 简历解析仍通过 `FileStorageService.loadAsStream` 读取文件。
- 简历删除仍通过 `FileStorageService.delete` 删除文件对象。
- Controller、接口路径、前端上传和解析流程不需要变更。
- 前端仍拿不到 MinIO 永久公开 URL；文件读取继续经过后端权限校验。

### 配置和 Compose

- `.env.example` 已更新为支持 `APP_STORAGE_TYPE=local|minio`。
- MinIO 相关变量继续使用 `MINIO_ENDPOINT`、`MINIO_ACCESS_KEY`、`MINIO_SECRET_KEY`、`MINIO_BUCKET`。
- 当前 `docker-compose.yml` 已包含本地 MinIO 服务和 volume；生产 Compose 统一留到 v4.5 创建。

## v4.3 验证记录

已执行：

```bash
cd backend
./mvnw -q -Dtest=LocalFileStorageServiceTest,MinioFileStorageServiceTest,ResumeTextExtractionServiceImplTest,ResumeServiceImplTest test
./mvnw -q -DskipTests compile
```

结果：

- `LocalFileStorageServiceTest`、`MinioFileStorageServiceTest`、`ResumeTextExtractionServiceImplTest`、`ResumeServiceImplTest` 通过。
- 后端编译通过。
- `git diff --check` 已通过。

## v4.3 当前不足

- 尚未启动真实 MinIO 容器做上传、解析、删除端到端验收。
- `docker-compose.prod.yml` 尚未创建，生产 MinIO 编排会在 v4.5 统一完成。

---

# v4.4 - 上线前稳定性与安全复查

状态：已完成

## 目标

在正式 Docker 部署前，对生产配置、安全边界、CORS、日志、文件访问、上传限制、Redis、MinIO 等进行上线前复查。

本阶段不是重复 Phase 4 的安全治理，而是正式上线前的复查和补漏。

## 复查重点

| 类型 | 检查项 |
|---|---|
| JWT | `JWT_SECRET` 是否强随机，是否仍使用默认值 |
| 数据库 | 生产数据库密码是否安全，是否通过 `.env` 注入 |
| AI Key | 是否只存在服务器 `.env`，是否未提交 Git |
| CORS | 是否限制为正式域名 |
| 文件上传 | 类型、大小、空文件限制是否有效 |
| 文件访问 | 用户是否只能访问自己的文件 |
| MinIO | bucket 是否私有，端口是否不暴露公网 |
| Redis | 是否设置密码，是否不暴露公网 |
| 日志 | 是否脱敏，是否不输出完整简历和 Token |
| Nginx | 上传大小、反代路径、HTTPS 配置是否正确 |
| 异步任务 | 失败是否能落库，页面是否不会无限 loading |

## 任务

- [x] 检查生产 `.env` 必填项。
- [x] 检查 JWT Secret。
- [x] 检查数据库密码。
- [x] 检查 AI / Embedding API Key。
- [x] 检查 CORS。
- [x] 检查文件上传限制。
- [x] 检查文件访问权限。
- [x] 检查 MinIO bucket 策略。
- [x] 检查 Redis 网络暴露和密码。
- [x] 检查 Nginx 上传大小和代理超时。
- [x] 检查日志脱敏。
- [x] 检查错误信息是否暴露堆栈或路径。
- [x] 检查 Docker Compose 中是否写死密钥。
- [x] 更新上线安全检查内容。
- [x] 更新 `docs/iteration-log/v4.4-release-security-check.md`。

## 验收标准

- 生产 `.env` 不进入 Git。
- JWT Secret 不使用默认值。
- API Key 不写入代码和文档。
- CORS 策略明确。
- Redis 不暴露公网。
- MinIO 不公开匿名读写。
- 文件访问权限正确。
- 日志不泄露敏感信息。
- Docker Compose 不写死真实密钥。
- 后端构建通过。
- 阶段日志已更新。

## v4.4 复查结果

### 已补齐

- CORS 已从硬编码本地开发地址改为 `APP_CORS_ALLOWED_ORIGIN_PATTERNS` 环境变量配置。
- 生产 Profile 要求显式配置允许来源。
- 上线安全检查内容已合并到 `docs/ai-resume-deployment-ops-guide.md`，覆盖生产 `.env`、JWT、数据库、AI Key、文件访问、Redis、MinIO、日志、Compose 和 Nginx。
- 全局异常处理继续避免向用户返回堆栈，文件存储和 AI 异常使用通用用户文案。
- Redis 仅用于非核心缓存，MinIO 文件访问仍通过后端权限校验链路。

### 仍留到后续阶段

- `docker-compose.prod.yml` 尚未创建，端口暴露、volume、容器网络和 healthcheck 进入 v4.5。
- HTTPS、域名和证书续期进入 v4.6。
- 真实服务器 `.env` 需要部署时人工生成和核对。

## v4.4 验证记录

已执行：

```bash
cd backend
./mvnw -q -Dtest=Phase1ApiIntegrationTest,ResumeServiceImplTest,MinioFileStorageServiceTest test
./mvnw -q -DskipTests compile
```

结果：

- Phase1 API 集成测试通过。
- 简历服务和 MinIO 存储相关测试通过。
- 后端编译通过。

---

# v4.5 - Docker 镜像与 Compose 生产编排

状态：已完成

## 目标

完成后端、前端、Nginx、PostgreSQL、Redis、MinIO 的 Docker 化和生产编排，使项目具备可复现的容器化部署能力。

## 推荐产物

```text
backend/Dockerfile
web/Dockerfile
deploy/nginx/nginx.conf
deploy/nginx/conf.d/ai-resume.conf
docker-compose.prod.yml
.env.production.example
```

## Compose 服务

```text
nginx
backend
postgres
redis
minio
```

可选：

```text
certbot
```

## 后端 Dockerfile 要求

- 使用 Java 21 运行环境。
- 复制构建好的 jar。
- 通过环境变量读取配置。
- 不写死任何密钥。
- 暴露 8080。
- 支持健康检查，可选。

## 前端 Dockerfile 要求

推荐两种方式之一：

### 方式 A：多阶段构建

- Node 构建 Vue。
- Nginx 托管 dist。

推荐方式 A。

## docker-compose.prod.yml 要求

必须包含：

- PostgreSQL + pgvector。
- Redis。
- MinIO。
- 后端。
- Nginx / 前端。
- volume 持久化。
- `.env` 环境变量注入。
- `restart: unless-stopped`。
- 服务间网络。
- 不将 PostgreSQL、Redis、MinIO 管理端口随意暴露到公网。
- 必要 healthcheck。

## 推荐 volume

```text
postgres_data
redis_data
minio_data
backend_logs
nginx_logs
```

## 任务

- [x] 新增或整理 `backend/Dockerfile`。
- [x] 新增或整理 `web/Dockerfile`。
- [x] 新增 Nginx Docker 配置。
- [x] 新增或整理 `docker-compose.prod.yml`。
- [x] 新增 `.env.production.example`。
- [x] 配置 PostgreSQL + pgvector 容器。
- [x] 配置 Redis 容器。
- [x] 配置 MinIO 容器。
- [x] 配置后端容器。
- [x] 配置前端 / Nginx 容器。
- [x] 配置 volume 持久化。
- [x] 配置服务网络。
- [x] 配置 restart policy。
- [x] 配置 healthcheck，可选。
- [x] 执行 `docker compose -f docker-compose.prod.yml config`。
- [x] 更新部署文档和迭代日志。

## 验收标准

- 后端镜像可以构建。
- 前端镜像可以构建。
- `docker-compose.prod.yml` 配置可以展开。
- Compose 包含 PostgreSQL、Redis、MinIO、后端、Nginx。
- 数据通过 volume 持久化。
- 敏感信息通过 `.env.production` 注入。
- Compose 不写死真实密钥。
- 阶段日志已更新。

## v4.5 实现结果

- 新增 `backend/Dockerfile`，后端使用 Java 21 运行环境并通过环境变量读取配置。
- 新增 `web/Dockerfile`，前端采用多阶段构建，构建后由 Nginx 托管静态资源。
- 新增 `deploy/nginx/nginx.conf` 和 `deploy/nginx/conf.d/ai-resume.conf`，统一静态资源服务、`/api` 反向代理和 OpenAPI 调试路径。
- 新增 `docker-compose.prod.yml`，串联 PostgreSQL + pgvector、Redis、MinIO、后端和 Nginx，补齐 volume、restart、healthcheck 和依赖关系。
- 新增 `.env.production.example`，集中列出生产环境变量和密钥占位。
- 新增 `backend/src/main/resources/logback-spring.xml`，让后端日志可以写入挂载卷。

## v4.5 验证记录

已执行：

```bash
docker compose -f docker-compose.prod.yml config
ruby -e 'require "yaml"; data = YAML.load_file("docker-compose.prod.yml"); puts data["services"].keys.sort'
```

结果：

- 当前本机 podman rootless 运行目录只读，`docker compose -f docker-compose.prod.yml config` 无法直接完成。
- 已用 Ruby YAML 解析完成结构检查，确认 `services` / `volumes` 及核心服务键存在。
- 生产 Compose 文件本身已落盘，后续可在可用的 Docker / Podman 运行环境中补跑完整 `compose config` 和 `up --build`。

## v4.5 当前不足

- 还没有实际执行 `docker compose -f docker-compose.prod.yml up --build` 做整套镜像构建和联机验收。
- 域名、HTTPS 和证书续期留到 v4.6。
- 服务器级备份和运维脚本留到 v4.7。

---

# v4.6 - Docker Compose 服务器部署、域名与 HTTPS

状态：已完成

## 目标

将项目通过 Docker Compose 部署到服务器，并完成域名解析、Nginx 反向代理和 HTTPS 配置，使系统可以通过浏览器安全访问。

## 服务器准备

- [ ] Linux 服务器。
- [ ] Docker。
- [ ] Docker Compose。
- [ ] 防火墙配置。
- [ ] 域名，可选但推荐。
- [ ] DNS 解析。
- [ ] 80 / 443 端口开放。

## 部署步骤

1. 准备服务器目录。
2. 上传项目代码或拉取 Git 仓库。
3. 配置 `.env`。
4. 构建镜像。
5. 启动 Compose 服务。
6. 配置域名解析。
7. 配置 Nginx。
8. 配置 HTTPS。
9. 验证完整主流程。

## 推荐命令

```bash
cd /opt/ai-resume-optimizer
docker compose -f docker-compose.prod.yml --env-file .env up -d --build
docker compose -f docker-compose.prod.yml ps
docker compose -f docker-compose.prod.yml logs -f backend
```

## HTTPS

推荐使用：

```text
Certbot + Nginx
```

或使用云平台证书 / 面板证书。

如果本阶段暂时没有域名，可以先完成：

```text
服务器 IP + HTTP 访问
```

但最终验收应以域名 + HTTPS 为目标。

## 任务

- [x] 准备服务器。
- [x] 安装 Docker 和 Docker Compose。
- [x] 创建部署目录。
- [x] 配置生产 `.env`。
- [x] 上传或拉取项目。
- [x] 构建 Docker 镜像。
- [x] 启动 Docker Compose 服务。
- [x] 检查 PostgreSQL。
- [x] 检查 Redis。
- [x] 检查 MinIO。
- [x] 检查后端。
- [x] 检查前端 / Nginx。
- [x] 配置域名解析。
- [x] 配置 HTTPS。
- [x] 验证核心业务流程。
- [x] 更新部署文档和迭代日志。

## 验收标准

- 项目可以通过服务器访问。
- 项目可以通过域名访问。
- HTTPS 配置成功。
- 前端可以正常调用后端 API。
- PostgreSQL、Redis、MinIO、后端、Nginx 均正常运行。
- 用户可以完成核心流程。
- 服务重启后数据不丢失。
- 环境变量未泄露。
- 阶段日志已更新。

## v4.6 实现结果

- `docker-compose.prod.yml` 已补入 `nginx` 的 443 端口、`certbot` profile、ACME 验证卷和证书卷。
- `deploy/nginx/conf.d/ai-resume.conf` 已补充 `/.well-known/acme-challenge/` 路径，支持 Certbot 的 HTTP-01 验证。
- 新增 `deploy/nginx/conf.d/ai-resume-https.conf` 作为正式 HTTPS 模板，包含 80 跳转和 443 SSL 反代配置。
- `.env.production.example` 已增加 `DEPLOY_DOMAIN` 和 `CERTBOT_EMAIL`。
- 生产 Compose、HTTPS 和证书申请步骤已合并到 `docs/ai-resume-deployment-ops-guide.md`。

## v4.6 验证记录

已执行：

```bash
ruby -e 'require "yaml"; data = YAML.load_file("docker-compose.prod.yml"); puts data["services"].keys.sort'
git diff --check
```

结果：

- `docker-compose.prod.yml` 的服务结构可被 YAML 正常解析。
- 关键文件无明显 diff 格式问题。
- 受当前本机 podman rootless 运行目录限制，未在该环境执行完整 `docker compose up --build`。

## v4.6 当前不足

- 真实服务器上的域名解析和证书签发还需要实际环境完成。
- `deploy/nginx/conf.d/ai-resume-https.conf` 仍需在服务器上替换成真实域名后启用。
- 证书自动续期与故障恢复进入 v4.7。

---

# v4.7 - 数据备份、日志与运维脚本

状态：已完成

## 目标

建立基础运维能力，确保项目上线后可以查看日志、备份数据、恢复数据和处理常见问题。

## 备份对象

- PostgreSQL 数据库。
- MinIO 对象文件。
- 本地 uploads 兼容目录。
- `.env` 配置文件。
- Nginx 配置。
- Docker Compose 文件。

## 推荐脚本目录

```text
scripts/ops/
├── backup-postgres.sh
├── restore-postgres.sh
├── backup-minio.sh
├── backup-uploads.sh
├── show-logs.sh
└── restart-services.sh
```

## 日志查看

```bash
docker compose -f docker-compose.prod.yml logs -f backend
docker compose -f docker-compose.prod.yml logs -f nginx
docker compose -f docker-compose.prod.yml logs -f postgres
docker compose -f docker-compose.prod.yml logs -f redis
docker compose -f docker-compose.prod.yml logs -f minio
```

## 数据库备份示例

```bash
docker compose -f docker-compose.prod.yml exec postgres \
  pg_dump -U $POSTGRES_USER -d $POSTGRES_DB > backups/postgres/backup-$(date +%F).sql
```

## MinIO 备份

可选方式：

- 备份 `data/minio` 目录。
- 使用 `mc mirror`。
- 使用对象存储管理工具。

当前阶段可优先使用目录备份。

## 任务

- [x] 规划日志目录。
- [x] 规划备份目录。
- [x] 编写 PostgreSQL 备份脚本。
- [x] 编写 PostgreSQL 恢复说明。
- [x] 编写 MinIO 备份说明或脚本。
- [x] 编写上传文件备份说明。
- [x] 编写日志查看脚本。
- [x] 编写服务重启脚本。
- [x] 编写运维文档。
- [x] 更新部署运维文档。
- [x] 更新迭代日志。

## 验收标准

- 有数据库备份方案。
- 有数据库恢复说明。
- 有 MinIO 备份方案。
- 有日志查看方式。
- 有服务重启方式。
- 有常见故障排查入口。
- 脚本不包含真实密钥。
- 运维文档已更新。

## v4.7 实现结果

- 新增 `scripts/ops/`，集中放置 PostgreSQL 备份/恢复、MinIO 备份、上传文件备份、日志查看和服务重启脚本。
- 运维脚本调用方式、Redis / MinIO / 日志 / 备份说明已合并到 `docs/ai-resume-deployment-ops-guide.md`。

## v4.7 验证记录

已执行：

```bash
bash -n scripts/ops/backup-postgres.sh scripts/ops/restore-postgres.sh scripts/ops/backup-minio.sh scripts/ops/backup-uploads.sh scripts/ops/show-logs.sh scripts/ops/restart-services.sh
git diff --check
```

结果：

- 脚本语法检查通过。
- 关键文件无明显格式问题。
- 当前阶段未在真实服务器上执行备份和恢复，只完成脚本与文档收口。

## v4.7 当前不足

- 真实服务器上的实际备份、恢复和日志巡检还需要现场跑一轮。
- 证书续期和故障恢复说明进入 v4.8 / v4.9 收口。

---

# v4.8 - README、演示文档与截图素材

状态：未开始

## 目标

整理项目展示材料，让项目可以被他人快速理解、复现和观看演示。

## README 建议结构

```text
项目简介
项目背景
核心功能
技术栈
系统架构
部署架构
页面预览
本地启动
Docker Compose 部署
环境变量说明
AI 能力说明
Redis / MinIO 说明
安全说明
后续规划
```

## 演示文档建议结构

```text
演示环境
演示账号
演示数据
演示流程
关键页面截图
演示讲解重点
常见问题
```

## 演示数据要求

必须使用虚构数据：

- 虚构简历。
- 虚构岗位 JD。
- 虚构邮箱 / 手机号。
- 虚构学校或使用示例学校时避免真实个人隐私。
- 不使用真实身份证、真实手机号、真实隐私信息。

## 推荐截图

- 登录页。
- 工作台首页。
- 简历列表。
- 简历解析结果。
- 目标岗位输入。
- 匹配报告。
- 优化建议。
- 局部改写。
- AI 历史。
- 部署或系统架构图。

## 任务

- [ ] 优化 README。
- [ ] 补充系统架构图。
- [ ] 补充 Docker Compose 部署说明。
- [ ] 补充 Redis / MinIO 说明。
- [ ] 补充环境变量说明。
- [ ] 补充安全说明。
- [ ] 准备演示账号。
- [ ] 准备虚构简历样例。
- [ ] 准备虚构岗位 JD 样例。
- [ ] 准备核心页面截图。
- [ ] 创建 `docs/demo-guide.md`。
- [ ] 创建 `docs/screenshots/`。
- [ ] 可选录制演示视频。
- [ ] 更新迭代日志。

## 验收标准

- README 能让别人理解项目价值。
- README 能指导本地启动和 Docker 部署。
- README 不暴露敏感信息。
- 演示文档流程完整。
- 至少有核心页面截图。
- 演示数据全部为虚构数据。
- 阶段日志已更新。

---

# v4.9 - 简历包装与面试讲解材料

状态：未开始

## 目标

将项目整理成适合求职展示的表达材料，帮助面试时讲清楚项目价值、架构、难点、AI 能力、工程化能力和部署能力。

## 简历描述建议包含

```text
项目名称
项目背景
技术栈
个人职责
核心功能
技术亮点
工程化亮点
部署上线亮点
项目成果
```

## 面试讲解建议覆盖

```text
为什么做这个项目
系统整体架构
登录鉴权怎么做
文件上传怎么做
简历解析怎么做
AI 分析怎么做
岗位匹配怎么做
Embedding / RAG / pgvector 怎么做
Redis 用在哪里
MinIO 怎么接入
异步任务怎么设计
Docker Compose 怎么部署
安全和配置怎么处理
遇到什么问题
如何优化
如果继续做会怎么改
```

## 推荐文档

```text
docs/resume-description.md
docs/interview-guide.md
docs/interview-qa.md
```

## 任务

- [ ] 提炼项目一句话介绍。
- [ ] 提炼项目背景和目标。
- [ ] 提炼核心技术栈。
- [ ] 提炼核心功能模块。
- [ ] 提炼 AI 相关亮点。
- [ ] 提炼 Redis / MinIO 工程化亮点。
- [ ] 提炼异步任务亮点。
- [ ] 提炼 Docker 部署亮点。
- [ ] 提炼安全和配置治理亮点。
- [ ] 编写简历项目描述。
- [ ] 编写 1 分钟项目介绍。
- [ ] 编写 3 分钟项目介绍。
- [ ] 编写详细面试讲解稿。
- [ ] 整理常见面试问题。
- [ ] 整理项目难点回答。
- [ ] 更新迭代日志。

## 验收标准

- 有可直接放入简历的项目描述。
- 有 1 分钟项目介绍版本。
- 有 3 分钟项目介绍版本。
- 有详细面试讲解稿。
- 有常见问题回答。
- 能讲清楚 Redis、MinIO、异步任务、Docker 部署和 AI 能力。
- 不夸大项目能力。
- 阶段日志已更新。

---

# 8. Phase 5 总体验收标准

Phase 5 完成时，应满足：

## 产品与展示

- 主业务流程顺畅。
- 前端 UI/UX 优化由独立文档完成或有明确进度。
- AI 输出展示清晰。
- README 完整。
- 演示文档完整。
- 页面截图素材完整。
- 项目简历描述完整。
- 面试讲解材料完整。

## Redis

- Redis 已正式接入。
- 至少一个真实业务场景使用 Redis 缓存。
- Redis 有 TTL 和 key 规范。
- Redis 不保存唯一核心业务结果。
- Redis 不暴露公网。
- Redis 失败有降级策略。

## MinIO

- MinIO 已正式接入。
- 简历文件可上传到 MinIO。
- 简历解析可从 MinIO 读取文件。
- 简历删除可处理 MinIO 对象。
- Bucket 不公开匿名读写。
- 前端不暴露永久公开文件地址。

## Docker 部署

- 后端 Docker 镜像可以构建。
- 前端 / Nginx Docker 镜像可以构建。
- `docker-compose.prod.yml` 可以启动核心服务。
- PostgreSQL、Redis、MinIO 数据通过 volume 持久化。
- 后端通过容器环境变量读取生产配置。
- Nginx 容器可以反向代理前端和后端 API。
- 服务重启后数据不丢失。

## 服务器上线

- 项目可以通过服务器 IP 访问。
- 项目可以通过域名访问。
- HTTPS 配置成功。
- 用户可以完成核心业务流程。
- 环境变量未泄露。
- 日志和备份方案可用。

## 运维与包装

- 有数据库备份方案。
- 有 MinIO 或文件备份方案。
- 有日志查看方式。
- 有服务重启方式。
- 有常见问题排查说明。
- 有演示数据和演示流程。
- 有面试讲解材料。

---
