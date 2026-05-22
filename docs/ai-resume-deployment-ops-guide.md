# AI 简历优化匹配系统部署与运维文档

状态：已部署可用  
正式访问地址：`https://resume.dawn04.xyz`  
部署方式：Docker Compose 单机部署  
服务器部署目录：`/opt/ai-resume-optimizer`

说明：本文档已合并原部署文档、运维文档、运维脚本说明、安全检查清单和常见问题排查内容，是当前唯一维护的部署运维入口。

---

## 1. 部署架构

当前线上环境采用 Docker Compose 单机部署：

```text
用户浏览器
  ↓
Nginx 容器 + HTTPS
  ↓
Vue 前端静态资源
  ↓
/api 反向代理
  ↓
Spring Boot 后端容器
  ↓
PostgreSQL + pgvector 容器
Redis 容器
MinIO 容器
AI / Embedding API
```

核心服务：

| 服务 | 说明 |
|---|---|
| nginx | 前端静态资源托管、HTTPS、反向代理 `/api` |
| backend | Spring Boot 后端服务 |
| postgres | PostgreSQL + pgvector 数据库 |
| redis | 缓存服务 |
| minio | 简历文件对象存储 |
| certbot | Let's Encrypt 证书申请和续期 |

---

## 2. 服务器目录与数据持久化

服务器目录：

```text
/opt/ai-resume-optimizer/
├── backend/
├── web/
├── deploy/
│   └── nginx/
│       └── conf.d/
│           └── ai-resume.conf
├── docker-compose.prod.yml
├── .env
├── docs/
└── README.md
```

Docker volume 持久化数据包括：

```text
postgres_data
redis_data
minio_data
backend_logs
nginx_logs
certbot_www
letsencrypt
```

注意：

- `.env` 只存在服务器本地，不提交 Git。
- 真实 API Key、数据库密码、JWT Secret、MinIO 密钥都只放在服务器 `.env`。
- 不要随意执行 `docker compose down -v`，否则可能删除数据库、MinIO 文件和证书数据。

---

## 3. 生产环境关键配置

服务器 `.env` 中至少应包含：

```env
SPRING_PROFILES_ACTIVE=prod

POSTGRES_DB=ai_resume_optimizer
POSTGRES_USER=ai_resume
POSTGRES_PASSWORD=your-production-postgres-password
DATABASE_URL=jdbc:postgresql://postgres:5432/ai_resume_optimizer

REDIS_HOST=redis
REDIS_PORT=6379
REDIS_PASSWORD=your-production-redis-password

MINIO_ENDPOINT=http://minio:9000
MINIO_ROOT_USER=ai-resume-minio
MINIO_ROOT_PASSWORD=your-production-minio-password
MINIO_ACCESS_KEY=ai-resume-minio
MINIO_SECRET_KEY=your-production-minio-password
MINIO_BUCKET=ai-resume-files
APP_STORAGE_TYPE=minio

JWT_SECRET=your-long-random-jwt-secret
JWT_EXPIRATION_MINUTES=120

AI_BASE_URL=your-ai-base-url
AI_API_KEY=your-ai-api-key
AI_MODEL=your-ai-model

EMBEDDING_BASE_URL=https://api.siliconflow.cn/v1
EMBEDDING_API_KEY=your-siliconflow-api-key
EMBEDDING_MODEL=Qwen/Qwen3-Embedding-0.6B
EMBEDDING_DIMENSION=1024

DEPLOY_DOMAIN=resume.dawn04.xyz
CERTBOT_EMAIL=your-email@example.com
APP_CORS_ALLOWED_ORIGIN_PATTERNS=https://resume.dawn04.xyz
```

说明：

- Docker Compose 内部服务通信使用服务名，不使用 `localhost`。
- 后端访问数据库使用 `postgres:5432`。
- 后端访问 Redis 使用 `redis:6379`。
- 后端访问 MinIO 使用 `http://minio:9000`。
- 前端通过 Nginx 同域名 `/api` 访问后端。
- HTTPS 成功后，CORS 建议只保留 `https://resume.dawn04.xyz`。

---

## 4. 常用运维命令

进入项目目录：

```bash
cd /opt/ai-resume-optimizer
```

查看容器状态：

```bash
docker compose -f docker-compose.prod.yml --env-file .env ps
```

查看后端日志：

```bash
docker compose -f docker-compose.prod.yml --env-file .env logs -f backend
```

查看 Nginx 日志：

```bash
docker compose -f docker-compose.prod.yml --env-file .env logs -f nginx
```

查看 PostgreSQL 日志：

```bash
docker compose -f docker-compose.prod.yml --env-file .env logs -f postgres
```

查看 Redis 日志：

```bash
docker compose -f docker-compose.prod.yml --env-file .env logs -f redis
```

查看 MinIO 日志：

```bash
docker compose -f docker-compose.prod.yml --env-file .env logs -f minio
```

重启后端：

```bash
docker compose -f docker-compose.prod.yml --env-file .env restart backend
```

重启 Nginx：

```bash
docker compose -f docker-compose.prod.yml --env-file .env restart nginx
```

重启所有服务：

```bash
docker compose -f docker-compose.prod.yml --env-file .env restart
```

---

# 5. Git Pull 后更新 Docker 的流程

## 5.1 基本原则

服务器不需要重新 `git clone`。

正常更新流程：

```text
本地修改代码
↓
本地 commit + push
↓
服务器 git pull
↓
根据改动范围重新 build 对应 Docker 服务
↓
检查容器状态和日志
↓
浏览器强制刷新验证
```

不要动 `.env`，不要执行 `down -v`。

---

## 5.2 本地提交代码

在本地项目目录执行：

```bash
git status
git add .
git commit -m "fix: describe your change"
git push
```

---

## 5.3 服务器拉取最新代码

登录服务器：

```bash
ssh root@your-server-ip
```

进入项目目录：

```bash
cd /opt/ai-resume-optimizer
```

查看服务器是否有未提交改动：

```bash
git status
```

拉取最新代码：

```bash
git pull
```

---

## 5.4 根据修改范围重新构建 Docker

### 只修改后端

例如修改了：

```text
backend/src/
backend/pom.xml
backend/src/main/resources/
```

执行：

```bash
docker compose -f docker-compose.prod.yml --env-file .env up -d --build backend
```

查看日志：

```bash
docker compose -f docker-compose.prod.yml --env-file .env logs -f backend
```

---

### 只修改前端

例如修改了：

```text
web/src/
web/package.json
web/vite.config.ts
web/Dockerfile
```

如果前端由 Nginx 镜像构建并托管，执行：

```bash
docker compose -f docker-compose.prod.yml --env-file .env up -d --build nginx
```

浏览器强制刷新：

```text
Ctrl + F5
```

如果仍显示旧内容，可以用无痕窗口访问。

---

### 前后端都修改了

```bash
docker compose -f docker-compose.prod.yml --env-file .env up -d --build backend nginx
```

或者直接：

```bash
docker compose -f docker-compose.prod.yml --env-file .env up -d --build
```

---

### 修改了 docker-compose.prod.yml

先检查配置：

```bash
docker compose -f docker-compose.prod.yml --env-file .env config
```

没有报错后执行：

```bash
docker compose -f docker-compose.prod.yml --env-file .env up -d --build
```

---

### 修改了 Nginx 配置

例如修改了：

```text
deploy/nginx/conf.d/ai-resume.conf
```

先检查 Nginx 配置：

```bash
docker compose -f docker-compose.prod.yml --env-file .env exec nginx nginx -t
```

配置通过后重启：

```bash
docker compose -f docker-compose.prod.yml --env-file .env restart nginx
```

如果 Nginx 配置是构建时复制进镜像，也可以：

```bash
docker compose -f docker-compose.prod.yml --env-file .env up -d --build nginx
```

---

### 修改了 .env

如果修改的是后端运行时配置，例如：

```text
AI_API_KEY
EMBEDDING_API_KEY
APP_CORS_ALLOWED_ORIGIN_PATTERNS
JWT_EXPIRATION_MINUTES
```

重启或重建后端：

```bash
docker compose -f docker-compose.prod.yml --env-file .env up -d --build backend
```

如果修改的是前端构建时变量，例如：

```text
VITE_API_BASE_URL
```

需要重新构建 nginx / 前端镜像：

```bash
docker compose -f docker-compose.prod.yml --env-file .env up -d --build nginx
```

注意：Vite 的 `VITE_*` 变量通常是构建时写入静态文件的，只重启容器不一定生效。

---

## 5.5 更新后检查

查看服务状态：

```bash
docker compose -f docker-compose.prod.yml --env-file .env ps
```

查看后端启动日志：

```bash
docker compose -f docker-compose.prod.yml --env-file .env logs --tail=200 backend
```

查看 Nginx 日志：

```bash
docker compose -f docker-compose.prod.yml --env-file .env logs --tail=100 nginx
```

浏览器访问：

```text
https://resume.dawn04.xyz
```

核心功能简单验收：

```text
1. 登录
2. 上传简历
3. 简历解析
4. 目标岗位输入
5. 匹配分析
6. 优化建议
7. 局部改写
8. AI 历史
```

---

## 5.6 严禁随意执行的命令

不要随意执行：

```bash
docker compose -f docker-compose.prod.yml --env-file .env down -v
```

原因：

```text
-v 会删除 Docker volume
可能导致 PostgreSQL 数据丢失
可能导致 Redis 数据丢失
可能导致 MinIO 文件丢失
可能导致证书数据丢失
```

如果只是更新代码，不需要 `down -v`。

---

## 6. HTTPS 配置说明

当前正式域名：

```text
https://resume.dawn04.xyz
```

Nginx 配置文件：

```text
deploy/nginx/conf.d/ai-resume.conf
```

证书目录：

```text
/etc/letsencrypt/live/resume.dawn04.xyz/
```

检查证书：

```bash
docker compose -f docker-compose.prod.yml --env-file .env exec nginx   ls -lah /etc/letsencrypt/live/resume.dawn04.xyz
```

正确应包含：

```text
fullchain.pem
privkey.pem
```

测试 HTTPS：

```bash
curl -I https://resume.dawn04.xyz
```

测试 HTTP 是否跳转 HTTPS：

```bash
curl -I http://resume.dawn04.xyz
```

---

## 7. 证书续期

测试证书续期：

```bash
cd /opt/ai-resume-optimizer

docker compose -f docker-compose.prod.yml --env-file .env --profile certbot run --rm certbot renew --dry-run
```

如果测试通过，可以配置 crontab：

```bash
crontab -e
```

添加：

```cron
0 3 * * * cd /opt/ai-resume-optimizer && docker compose -f docker-compose.prod.yml --env-file .env --profile certbot run --rm certbot renew && docker compose -f docker-compose.prod.yml --env-file .env restart nginx
```

---

## 8. 数据库查看与排查

查看表：

```bash
docker compose -f docker-compose.prod.yml --env-file .env exec postgres sh -lc 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c "\dt"'
```

查看用户表：

```bash
docker compose -f docker-compose.prod.yml --env-file .env exec postgres sh -lc 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c "select id, username, email, created_at from users order by id desc limit 10;"'
```

查看 Flyway 迁移记录：

```bash
docker compose -f docker-compose.prod.yml --env-file .env exec postgres sh -lc 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c "select installed_rank, version, description, success, installed_on from flyway_schema_history order by installed_rank desc limit 20;"'
```

---

## 9. MinIO 检查

查看 MinIO 容器状态：

```bash
docker compose -f docker-compose.prod.yml --env-file .env ps minio
```

查看 MinIO 日志：

```bash
docker compose -f docker-compose.prod.yml --env-file .env logs --tail=100 minio
```

如果上传文件失败，重点检查：

```text
MINIO_ENDPOINT
MINIO_ACCESS_KEY
MINIO_SECRET_KEY
MINIO_BUCKET
APP_STORAGE_TYPE
```

后端访问 MinIO 应使用：

```env
MINIO_ENDPOINT=http://minio:9000
```

不要写成：

```env
MINIO_ENDPOINT=http://localhost:9000
```

---

## 10. Redis 检查

查看 Redis 容器状态：

```bash
docker compose -f docker-compose.prod.yml --env-file .env ps redis
```

查看 Redis 日志：

```bash
docker compose -f docker-compose.prod.yml --env-file .env logs --tail=100 redis
```

如果缓存异常，重点检查：

```text
REDIS_HOST=redis
REDIS_PORT=6379
REDIS_PASSWORD
APP_REDIS_CACHE_ENABLED
```

---

## 11. 常见问题排查

### 11.1 前端能打开，但接口失败

检查浏览器 Network：

```text
Request URL 是否为 https://resume.dawn04.xyz/api/...
是否出现 /api/api/...
状态码是 404 / 500 / 502 / CORS
```

如果出现 `/api/api/`，说明前端 baseURL 和接口路径重复拼接了 `/api`。

---

### 11.2 修改前端后页面还是旧的

```bash
docker compose -f docker-compose.prod.yml --env-file .env up -d --build nginx
```

浏览器强制刷新：

```text
Ctrl + F5
```

---

### 11.3 后端启动失败

```bash
docker compose -f docker-compose.prod.yml --env-file .env logs --tail=300 backend
```

常见原因：

```text
数据库连接失败
Redis 密码错误
MinIO 配置错误
JWT_SECRET 缺失
AI_API_KEY 缺失
Flyway 迁移失败
```

---

### 11.4 Nginx 启动失败

```bash
docker compose -f docker-compose.prod.yml --env-file .env exec nginx nginx -t
docker compose -f docker-compose.prod.yml --env-file .env logs --tail=100 nginx
```

常见原因：

```text
证书路径错误
Nginx 配置语法错误
443 端口被占用
后端服务名写错
```

---

### 11.5 HTTPS 访问失败

检查证书：

```bash
docker compose -f docker-compose.prod.yml --env-file .env exec nginx   ls -lah /etc/letsencrypt/live/resume.dawn04.xyz
```

检查 Nginx：

```bash
docker compose -f docker-compose.prod.yml --env-file .env exec nginx nginx -t
```

检查 443 端口：

```bash
ss -tulpn | grep ':443'
```

检查云服务器安全组是否放行 TCP 443。

---

## 12. 基础备份建议

至少定期备份：

```text
.env
docker-compose.prod.yml
deploy/nginx/conf.d/ai-resume.conf
PostgreSQL 数据
MinIO 文件
```

备份 `.env` 和部署配置：

```bash
mkdir -p /opt/ai-resume-optimizer/backups/config

cp /opt/ai-resume-optimizer/.env /opt/ai-resume-optimizer/backups/config/.env.$(date +%F)
cp /opt/ai-resume-optimizer/docker-compose.prod.yml /opt/ai-resume-optimizer/backups/config/docker-compose.prod.yml.$(date +%F)
cp /opt/ai-resume-optimizer/deploy/nginx/conf.d/ai-resume.conf /opt/ai-resume-optimizer/backups/config/ai-resume.conf.$(date +%F)
```

备份 PostgreSQL：

```bash
mkdir -p /opt/ai-resume-optimizer/backups/postgres

docker compose -f docker-compose.prod.yml --env-file .env exec postgres   sh -lc 'pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB"'   > /opt/ai-resume-optimizer/backups/postgres/ai_resume_$(date +%F).sql
```

---

## 13. 部署验收清单

每次部署或更新后，至少检查：

- [ ] `docker compose ps` 所有核心服务正常。
- [ ] `https://resume.dawn04.xyz` 可以访问。
- [ ] HTTP 自动跳转 HTTPS。
- [ ] 注册登录正常。
- [ ] 简历上传正常。
- [ ] 简历解析正常。
- [ ] 目标岗位新增正常。
- [ ] 匹配分析正常。
- [ ] 优化建议正常。
- [ ] 局部改写正常。
- [ ] AI 历史正常。
- [ ] 后端日志无持续 ERROR。
- [ ] Nginx 日志无大量 502。
- [ ] 重启后数据不丢失。

---

## 14. 当前正式入口

项目正式访问地址：

```text
https://resume.dawn04.xyz
```

README、演示文档、简历项目描述中统一使用该地址。
