# AI 简历优化与岗位匹配系统运维文档

本文档用于记录当前单机部署形态下的基础运维方式，覆盖服务组成、日志查看、启动停止、备份恢复、配置修改、AI / Embedding 检查和日常巡检。

当前项目尚未完成应用容器化、systemd 服务文件、HTTPS 和生产级监控平台，因此本文档只记录当前可执行或可作为后续部署参考的运维动作。

## 1. 运维目标

- 能确认系统是否正常运行。
- 能查看后端、前端代理、数据库和依赖服务日志。
- 能完成后端、前端静态资源、依赖服务的基础重启。
- 能备份和恢复 PostgreSQL 数据。
- 能备份上传文件目录。
- 能安全修改 `.env`、Profile 和 Nginx 配置。
- 能定位 AI / Embedding 调用失败、上传失败、任务失败等常见问题。

## 2. 服务组成

当前推荐部署组成：

| 服务 | 当前状态 | 运维关注点 |
|---|---|---|
| Spring Boot 后端 | jar 方式运行 | 进程状态、端口 `8080`、日志、`.env`、数据库连接 |
| Vue 前端 | Nginx 托管静态文件 | `dist` 发布目录、Nginx 配置、`VITE_API_BASE_URL` |
| PostgreSQL | 外部数据库或 Compose 依赖 | 数据 volume、连接数、备份、迁移 |
| Redis | Compose 预留 | 当前后端运行路径尚未依赖 Redis |
| MinIO | Compose 预留 | 当前上传链路尚未切换到 MinIO |
| 文件存储目录 | local 存储 | `APP_STORAGE_LOCAL_BASE_DIR`、读写权限、磁盘空间、备份 |
| AI Chat 服务 | 外部 OpenAI-compatible 服务 | base URL、API Key、模型、超时、返回格式 |
| Embedding 服务 | 外部 OpenAI-compatible 服务 | 启用状态、模型、维度、超时 |
| Nginx | 反向代理和静态资源托管 | `/api/` 代理、Vue fallback、上传大小、HTTPS |

## 3. 日志查看

### 后端 jar 运行

当前后端主要通过控制台输出日志。若部署时将日志重定向到文件，可用：

```bash
tail -f logs/ai-resume-optimizer.log
```

如果使用 shell 重定向启动：

```bash
SPRING_PROFILES_ACTIVE=prod java -jar target/*.jar >> logs/ai-resume-optimizer.log 2>&1 &
tail -f logs/ai-resume-optimizer.log
```

说明：

- `LOG_FILE_PATH` 当前只是部署预留变量，后端尚未正式接入文件日志配置。
- 日志中不应出现 JWT Secret、AI Key、数据库密码、MinIO Secret 或完整本地敏感路径。

### Compose 依赖服务

当前 Compose 只包含依赖服务：

```bash
docker compose logs -f postgres
docker compose logs -f redis
docker compose logs -f minio
```

如果使用 Podman：

```bash
podman compose logs -f postgres
podman compose logs -f redis
podman compose logs -f minio
```

### Nginx 日志

常见路径：

```bash
tail -f /var/log/nginx/access.log
tail -f /var/log/nginx/error.log
```

不同发行版或自定义安装路径可能不同，以服务器实际 Nginx 配置为准。

## 4. 服务启动、停止与重启

### 依赖服务

启动：

```bash
docker compose --env-file .env up -d postgres redis minio
```

停止：

```bash
docker compose down
```

重启单个依赖：

```bash
docker compose restart postgres
docker compose restart redis
docker compose restart minio
```

### 后端 jar

启动：

```bash
cd backend
SPRING_PROFILES_ACTIVE=prod java -jar target/*.jar
```

后台启动示例：

```bash
cd backend
mkdir -p logs
SPRING_PROFILES_ACTIVE=prod java -jar target/*.jar >> logs/ai-resume-optimizer.log 2>&1 &
```

停止：

```bash
pgrep -f 'ai-resume-optimizer.*jar'
kill <pid>
```

说明：

- 后续如果增加 systemd 服务，可改为 `systemctl restart ai-resume-backend`。
- 当前仓库尚未提供 systemd service 文件。

### 前端静态文件

前端构建后由 Nginx 托管，更新步骤：

```bash
cd web
npm install
npm run build
```

然后将 `web/dist/` 内容发布到 Nginx `root` 指向的目录。

重新加载 Nginx：

```bash
nginx -t
nginx -s reload
```

## 5. 数据库备份与恢复

备份：

```bash
pg_dump -h localhost -U dawn -d ai_resume_optimizer > backup.sql
```

带日期的备份文件示例：

```bash
pg_dump -h localhost -U dawn -d ai_resume_optimizer > backup-$(date +%F).sql
```

恢复：

```bash
psql -h localhost -U dawn -d ai_resume_optimizer < backup.sql
```

如果 PostgreSQL 在 Compose 中运行：

```bash
docker compose exec postgres pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" > backup.sql
```

注意：

- 数据库迁移前必须先备份。
- 恢复前确认目标库是否需要清空，避免重复数据或迁移冲突。
- 生产密码不要写入命令历史，必要时使用 `.pgpass` 或受控环境变量。

## 6. 上传文件备份

当前上传链路使用 local 存储，目录由以下变量控制：

```env
APP_STORAGE_LOCAL_BASE_DIR=/data/ai-resume/uploads
```

备份：

```bash
tar -czf uploads-backup-$(date +%F).tar.gz /data/ai-resume/uploads
```

恢复：

```bash
tar -xzf uploads-backup-YYYY-MM-DD.tar.gz -C /
```

检查：

- [ ] 上传目录存在。
- [ ] 后端进程用户有读写权限。
- [ ] 备份文件可解压。
- [ ] 恢复后历史简历文件仍可访问。

MinIO 说明：

- MinIO 当前只是依赖和配置预留。
- 后端上传链路尚未正式切换到 MinIO。
- 后续接入后可使用 `mc mirror` 或对象存储控制台做备份。

## 7. 配置修改

常见配置文件：

| 文件 | 用途 |
|---|---|
| `.env` | 服务器私有环境变量，不提交 Git |
| `.env.example` | 示例变量，不包含真实密钥 |
| `backend/src/main/resources/application-prod.yaml` | 生产 Profile 配置 |
| `web/.env.example` | 前端环境变量示例 |
| `deploy/nginx/ai-resume.conf` | Nginx 反向代理草案 |

修改流程：

1. 修改 `.env` 或 Nginx 配置。
2. 检查不要写入真实密钥到 Git 跟踪文件。
3. 重启后端或重新加载 Nginx。
4. 按部署后验证流程检查主链路。

常见需要重启的配置：

- `SPRING_PROFILES_ACTIVE`
- `DB_URL` / `DB_USERNAME` / `DB_PASSWORD`
- `JWT_SECRET`
- `AI_BASE_URL` / `AI_API_KEY` / `AI_MODEL`
- `EMBEDDING_*`
- `APP_STORAGE_LOCAL_BASE_DIR`

## 8. AI / Embedding 服务检查

AI Chat 检查项：

- [ ] `AI_BASE_URL` 是 base URL，不重复包含 `/chat/completions`。
- [ ] `AI_API_KEY` 有效。
- [ ] `AI_MODEL` 存在且服务商支持。
- [ ] `AI_TIMEOUT_SECONDS` 足够覆盖慢请求。
- [ ] Nginx `/api/` proxy timeout 不低于后端慢请求预期。

Embedding 检查项：

- [ ] `EMBEDDING_ENABLED` 符合当前部署预期。
- [ ] `EMBEDDING_BASE_URL` 可访问。
- [ ] `EMBEDDING_API_KEY` 有效。
- [ ] `EMBEDDING_MODEL` 与服务商支持一致。
- [ ] `EMBEDDING_DIMENSION` 与数据库向量字段一致。

业务侧验证：

- 简历诊断、岗位解析、匹配分析、优化建议可用于验证 AI Chat。
- 简历向量生成可用于验证 Embedding。
- 失败时前端应显示可理解错误，不应暴露密钥、请求头或服务端路径。

## 9. 常见故障处理

### 后端启动失败

检查：

- Java 是否为 21。
- `SPRING_PROFILES_ACTIVE` 是否为预期值。
- `.env` 是否存在且变量名正确。
- 数据库是否可连接。
- `JWT_SECRET` 是否已配置。
- 端口 `8080` 是否被占用。

### 前端能打开但接口失败

检查：

- `VITE_API_BASE_URL` 是否为 `/api` 或正确后端地址。
- Nginx 是否包含 `/api/` 代理。
- 后端是否已启动。
- 浏览器 Network 中请求路径和响应码。

### 上传失败

检查：

- 文件扩展名、MIME 和真实内容是否匹配。
- `APP_STORAGE_LOCAL_BASE_DIR` 是否存在。
- 后端进程用户是否有写权限。
- Nginx `client_max_body_size` 是否足够。
- 磁盘空间是否充足。

### AI 调用失败

检查：

- `AI_BASE_URL` 是否重复拼接 endpoint。
- `AI_API_KEY` 是否有效。
- `AI_MODEL` 是否正确。
- 超时配置是否过短。
- 服务商是否返回空文本或非预期 JSON。

### 异步任务卡住

检查：

- 后端日志是否有任务异常。
- 任务状态是否停留在 `RUNNING`。
- AI / Embedding 服务是否超时。
- 数据库 `async_task` 记录中的失败原因是否已写入。

## 10. 日常巡检清单

每日或每次部署后建议检查：

- [ ] 前端首页可访问。
- [ ] 登录 / 注册正常。
- [ ] 后端日志没有大量 `ERROR`。
- [ ] PostgreSQL 正常运行。
- [ ] 数据库备份任务正常。
- [ ] 上传文件目录存在且可写。
- [ ] 上传文件备份正常。
- [ ] 磁盘空间充足。
- [ ] AI Chat 服务可用。
- [ ] Embedding 服务状态符合预期。
- [ ] Nginx `/api/` 代理正常。
- [ ] `.env` 未进入 Git。
- [ ] 最近一次部署的回滚包仍保留。
