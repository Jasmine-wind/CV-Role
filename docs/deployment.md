# AI 简历优化与岗位匹配系统部署文档

本文档用于记录当前项目的单机部署准备流程，覆盖环境准备、环境变量、构建、启动、Nginx 反向代理、部署后验证和回滚。

当前版本目标是形成可执行的部署 runbook，不代表已经完成真实服务器上线、HTTPS、应用容器化或生产级高可用。

## 1. 部署目标

推荐部署形态：

```text
用户浏览器
  -> Nginx
      -> Vue 静态文件
      -> /api/ 反向代理到 Spring Boot 后端
            -> PostgreSQL
            -> 本地文件存储目录
            -> AI / Embedding 服务
```

当前已准备：

- 后端支持 `local` / `dev` / `prod` / `test` Profile。
- 生产配置位于 `backend/src/main/resources/application-prod.yaml`。
- 环境变量示例位于 `.env.example`。
- 前端环境变量示例位于 `web/.env.example`。
- 本地依赖 Compose 位于 `docker-compose.yml`。
- Nginx 草案位于 `deploy/nginx/ai-resume.conf`。

当前边界：

- `docker-compose.yml` 只编排 PostgreSQL、Redis、MinIO 等依赖，不包含后端和前端应用镜像。
- 后端当前推荐用 jar 方式启动。
- 前端当前推荐构建为静态文件后交给 Nginx 托管。
- 后端上传链路当前仍使用 local 存储，MinIO 只是后续对象存储接入预留。
- Redis 当前只作为依赖预留，后端运行路径尚未依赖 Redis。

## 2. 服务器环境要求

基础要求：

- [ ] Linux 服务器。
- [ ] Java 21。
- [ ] Git。
- [ ] PostgreSQL 16，或使用仓库 Compose 中的 PostgreSQL。
- [ ] Nginx。
- [ ] 防火墙开放 `80` / `443`。
- [ ] 后端端口 `8080` 不直接暴露公网，建议仅允许本机或内网访问。
- [ ] 上传文件目录使用服务器持久化目录。
- [ ] 数据库数据目录或 volume 可持久化。

可选要求：

- [ ] Node.js 和 npm，用于在服务器构建前端。
- [ ] Docker / Podman。
- [ ] Docker Compose / Podman Compose。
- [ ] Redis。
- [ ] MinIO。
- [ ] 域名。
- [ ] HTTPS 证书。

## 3. 必要软件

后端：

```text
Java 21
Maven Wrapper，仓库已提供 backend/mvnw
PostgreSQL 16
```

前端：

```text
Node.js
npm
Nginx
```

依赖编排：

```text
Docker / Podman
Docker Compose / Podman Compose
```

说明：

- 如果前端在本地构建后再发布到服务器，服务器不一定需要 Node.js。
- 如果 PostgreSQL 使用外部数据库，服务器不一定需要启动 Compose 中的 PostgreSQL。
- 如果不使用 MinIO，当前上传链路继续依赖 `APP_STORAGE_LOCAL_BASE_DIR` 指向的本地持久化目录。

## 4. 环境变量配置

部署前从 `.env.example` 复制服务器私有 `.env`，并替换所有生产值。不要提交 `.env`。

应用基础：

```env
SPRING_PROFILES_ACTIVE=prod
SERVER_PORT=8080
```

数据库：

```env
POSTGRES_DB=ai_resume_optimizer
POSTGRES_USER=your_db_user
POSTGRES_PASSWORD=change-to-strong-password
DB_URL=jdbc:postgresql://127.0.0.1:5432/ai_resume_optimizer
DB_USERNAME=your_db_user
DB_PASSWORD=change-to-strong-password
```

JWT：

```env
JWT_SECRET=change-to-a-long-random-secret-at-least-32-chars
JWT_EXPIRATION_MINUTES=1440
```

AI Chat：

```env
AI_BASE_URL=https://api.example.com
AI_API_KEY=change-to-real-ai-api-key
AI_MODEL=your-model-name
AI_TIMEOUT_SECONDS=120
AI_MAX_TOKENS=4096
```

Embedding：

```env
EMBEDDING_ENABLED=true
EMBEDDING_BASE_URL=https://embedding.example.com/v1
EMBEDDING_API_KEY=change-to-real-embedding-api-key
EMBEDDING_MODEL=your-embedding-model
EMBEDDING_DIMENSION=1024
```

如果暂不启用向量能力，应显式关闭或确保相关页面能接受失败提示：

```env
EMBEDDING_ENABLED=false
```

文件存储：

```env
APP_STORAGE_TYPE=local
APP_STORAGE_LOCAL_BASE_DIR=/data/ai-resume/uploads
```

日志：

```env
LOG_LEVEL_ROOT=info
LOG_LEVEL_APP=info
```

前端：

```env
VITE_API_BASE_URL=/api
```

生产环境必须确认：

- [ ] `SPRING_PROFILES_ACTIVE=prod`。
- [ ] `JWT_SECRET` 已替换为强随机值。
- [ ] `POSTGRES_PASSWORD` / `DB_PASSWORD` 已替换为强密码。
- [ ] `AI_API_KEY` 不是示例值。
- [ ] `APP_STORAGE_LOCAL_BASE_DIR` 指向服务器持久化目录。
- [ ] 前端环境变量不包含后端密钥、数据库密码或 AI Key。

## 5. 数据库准备

方式 A：使用 Compose 中的 PostgreSQL。

```bash
docker compose --env-file .env up -d postgres
```

方式 B：使用已有 PostgreSQL。

需要提前创建：

```text
database: ai_resume_optimizer
user:     与 DB_USERNAME 一致
password: 与 DB_PASSWORD 一致
```

项目使用 Flyway 管理数据库迁移。后端启动时会按配置执行迁移，生产部署前应先备份数据库。

检查项：

- [ ] PostgreSQL 可以从后端所在机器访问。
- [ ] `DB_URL` 指向正确地址。
- [ ] 数据库用户有建表、迁移和读写权限。
- [ ] 数据库数据目录或 volume 已持久化。

## 6. 后端构建与启动

构建：

```bash
cd backend
./mvnw clean package -DskipTests
```

启动：

```bash
cd backend
SPRING_PROFILES_ACTIVE=prod java -jar target/*.jar
```

建议：

- 在服务器上用 systemd 或进程管理工具托管后端进程。
- 后端监听 `8080` 即可，公网访问通过 Nginx `/api/` 代理。
- 不要把 `8080` 直接开放到公网。
- 启动日志中不应出现 JWT Secret、AI Key、数据库密码或本地敏感路径。

## 7. 前端构建与部署

如果在服务器构建：

```bash
cd web
npm install
npm run build
```

构建产物：

```text
web/dist/
```

部署方式：

```text
将 web/dist/ 内容发布到 Nginx 配置中的 root 目录。
```

如果使用同域 Nginx 代理，生产构建建议：

```env
VITE_API_BASE_URL=/api
```

检查项：

- [ ] 首页可以打开。
- [ ] 刷新前端二级路由不返回 404。
- [ ] 浏览器 Network 中 API 请求走 `/api/`。

## 8. Docker Compose 部署方式

当前 Compose 只用于依赖服务：

```bash
docker compose --env-file .env up -d postgres redis minio
```

如果使用 Podman：

```bash
podman compose --env-file .env up -d postgres redis minio
```

检查配置展开：

```bash
docker compose --env-file .env.example config
```

停止依赖服务：

```bash
docker compose down
```

清理 volume 会删除本地数据，谨慎使用：

```bash
docker compose down -v
```

说明：

- `postgres` 使用 `pgvector/pgvector:pg16`。
- `redis` 当前是部署预留，后端运行路径尚未依赖 Redis。
- `minio` 当前是对象存储预留，后端上传链路尚未切换到 MinIO。
- 当前没有后端 / 前端应用镜像，不能用 Compose 一键启动完整系统。

## 9. Nginx 反向代理

草案路径：

```text
deploy/nginx/ai-resume.conf
```

部署前必须替换：

- `server_name example.com`
- `root /var/www/ai-resume-optimizer/web`
- 如启用 HTTPS，补充证书路径和 `listen 443 ssl`

关键要求：

- `/` 托管前端静态文件。
- Vue Router history 模式使用 `try_files $uri $uri/ /index.html`。
- `/api/` 代理到 `http://127.0.0.1:8080/api/`。
- `client_max_body_size` 不低于后端上传限制。
- AI 慢请求场景下，代理超时不要过短。

如果服务器已安装 Nginx，可检查配置：

```bash
nginx -t
```

重新加载：

```bash
nginx -s reload
```

## 10. 文件存储目录

当前上传链路使用 local 存储。

生产环境建议：

```text
/data/ai-resume/uploads
```

配置：

```env
APP_STORAGE_TYPE=local
APP_STORAGE_LOCAL_BASE_DIR=/data/ai-resume/uploads
```

检查项：

- [ ] 目录存在。
- [ ] 后端进程用户有读写权限。
- [ ] 目录不在临时目录中。
- [ ] 目录有备份策略。
- [ ] 删除简历后相关文件访问按预期失效。

## 11. AI / Embedding 服务配置

AI Chat 用于简历诊断、岗位解析、匹配分析、优化建议和局部改写等已有能力。

必须配置：

```env
AI_BASE_URL=
AI_API_KEY=
AI_MODEL=
```

Embedding 用于向量生成和后续检索增强相关能力。当前项目中向量能力不是所有主流程的硬阻塞项，但如果开启，需要确认：

```env
EMBEDDING_ENABLED=true
EMBEDDING_BASE_URL=
EMBEDDING_API_KEY=
EMBEDDING_MODEL=
EMBEDDING_DIMENSION=
```

检查项：

- [ ] AI base URL 不重复拼接 endpoint。
- [ ] API Key 不写入代码或前端环境变量。
- [ ] 超时时间足够覆盖慢请求。
- [ ] Embedding 维度与数据库向量字段一致。
- [ ] 失败信息不会把密钥、请求头或完整服务端路径返回给前端。

## 12. 部署后验证

基础检查：

- [ ] 前端页面可以通过域名或服务器地址打开。
- [ ] 刷新前端二级路由不返回 404。
- [ ] `/api/` 请求能被 Nginx 转发到后端。
- [ ] 后端启动日志没有真实密钥明文。
- [ ] 数据库连接成功。
- [ ] 文件上传目录存在且可写。

业务主流程：

- [ ] 用户注册正常。
- [ ] 用户登录正常。
- [ ] 登录后能访问简历页面。
- [ ] 简历上传正常。
- [ ] 简历解析任务能提交并返回结果。
- [ ] 简历诊断任务能提交并返回结果。
- [ ] 目标岗位可以新增。
- [ ] 用户粘贴 JD 后可以解析目标岗位。
- [ ] 简历与目标岗位匹配分析正常。
- [ ] 岗位优化建议正常。
- [ ] 局部改写只针对用户选中片段。
- [ ] AI 结果回看只查询历史结果，不触发新的 AI 生成。

持久化检查：

- [ ] 重启后端后用户和历史数据仍存在。
- [ ] 重启依赖服务后 PostgreSQL 数据仍存在。
- [ ] 重启后上传文件仍可访问。
- [ ] 删除简历后相关文件访问按预期失效。

安全检查：

- [ ] `.env` 未提交 Git。
- [ ] `.env.example` 不包含真实密钥。
- [ ] 后端 `8080` 未直接暴露公网。
- [ ] Nginx 上传大小限制不低于后端上传限制。
- [ ] 错误提示不暴露数据库、JWT、AI Key 或本地绝对路径。

## 13. 回滚方案

部署前保留：

- [ ] 上一个可运行后端 jar。
- [ ] 上一版前端 `dist`。
- [ ] 数据库备份。
- [ ] 上传文件目录备份。
- [ ] 当前服务器 `.env` 备份。
- [ ] 当前 Nginx 配置备份。

回滚步骤：

1. 停止当前后端进程。
2. 恢复上一版后端 jar。
3. 恢复上一版前端静态文件。
4. 如涉及数据库迁移失败，恢复数据库备份。
5. 如涉及上传文件异常，恢复上传目录备份。
6. 重新加载 Nginx。
7. 按部署后验证清单重新验证主流程。

## 14. 常见问题

### 前端页面能打开，但 API 请求失败

检查：

- `VITE_API_BASE_URL` 是否为 `/api` 或正确后端代理地址。
- Nginx 是否包含 `/api/` 代理。
- 后端是否在 `8080` 启动。
- 浏览器 Network 中请求路径是否正确。

### 刷新前端页面返回 404

检查 Nginx 是否配置：

```nginx
try_files $uri $uri/ /index.html;
```

### 后端启动失败，提示数据库连接失败

检查：

- `DB_URL`、`DB_USERNAME`、`DB_PASSWORD` 是否正确。
- PostgreSQL 是否启动。
- 数据库用户是否有权限。
- 服务器防火墙或容器网络是否允许访问。

### 上传失败

检查：

- `APP_STORAGE_LOCAL_BASE_DIR` 是否存在。
- 后端进程用户是否有写权限。
- Nginx `client_max_body_size` 是否低于后端上传限制。
- 上传文件扩展名、MIME 和真实内容是否匹配。

### AI 请求超时或失败

检查：

- `AI_BASE_URL` 是否为 base URL，不要重复包含 `/chat/completions`。
- `AI_API_KEY` 是否有效。
- `AI_MODEL` 是否存在。
- `AI_TIMEOUT_SECONDS` 是否过短。
- Nginx proxy timeout 是否过短。

## 15. 后续待补

- 后端应用容器镜像和前端静态资源镜像。
- systemd 服务文件或 Compose 应用服务。
- HTTPS 正式配置和证书续期说明。
- 生产文件日志输出路径配置。
- MinIO 存储实现接入。
- Redis 实际运行能力接入。
- CORS 从硬编码本地开发地址调整为部署可配置。
