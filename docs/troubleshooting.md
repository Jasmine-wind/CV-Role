# AI 简历优化与岗位匹配系统常见问题排查

本文档用于记录当前单体部署形态下的常见故障排查路径。排查时先确认当前运行方式：后端 jar、前端 Nginx 静态托管、Compose 依赖服务，还是本地开发模式。

## 1. 快速定位顺序

建议按以下顺序排查：

1. 确认前端页面是否能打开。
2. 确认后端进程是否启动。
3. 确认 `/api/` 请求是否能到达后端。
4. 确认 PostgreSQL 是否可连接。
5. 确认上传目录是否可写。
6. 确认 AI / Embedding 外部服务是否可用。
7. 确认异步任务状态和后端 ERROR 日志。

常用命令：

```bash
java -version
echo $SPRING_PROFILES_ACTIVE
ps aux | grep java
lsof -i :8080
docker compose ps
docker compose logs -f postgres
df -h
```

## 2. 后端启动失败

常见原因：

- Java 版本不是 21。
- `SPRING_PROFILES_ACTIVE` 错误。
- `.env` 缺失或变量名错误。
- `JWT_SECRET`、`DB_URL`、`DB_USERNAME`、`DB_PASSWORD` 等生产必需变量缺失。
- PostgreSQL 未启动或不可连接。
- Flyway 迁移失败。
- 端口 `8080` 被占用。

检查命令：

```bash
java -version
echo $SPRING_PROFILES_ACTIVE
lsof -i :8080
```

生产 Profile 打包检查：

```bash
cd backend
SPRING_PROFILES_ACTIVE=prod ./mvnw -q -DskipTests package
```

处理方式：

- Java 版本不对时，切换到 Java 21。
- 环境变量缺失时，对照 `.env.example` 补齐服务器 `.env`。
- 端口占用时，停止旧进程或调整 `SERVER_PORT`。
- Flyway 失败时，先备份数据库，再根据迁移错误处理，不要直接删除生产数据。

## 3. 数据库连接失败

常见原因：

- PostgreSQL 未启动。
- `DB_URL` 指向错误地址或端口。
- 数据库名不存在。
- 用户名或密码错误。
- 数据库用户权限不足。
- Compose 网络或宿主机端口映射错误。

检查命令：

```bash
psql -h localhost -U dawn -d ai_resume_optimizer
docker compose logs -f postgres
docker compose ps postgres
```

检查环境变量：

```bash
echo $DB_URL
echo $DB_USERNAME
```

处理方式：

- 使用 Compose 时，确认 `postgres` 服务健康。
- 使用外部数据库时，确认服务器防火墙和 PostgreSQL 监听地址。
- 密码错误时，更新 `.env` 后重启后端。
- 权限不足时，为应用用户补齐当前数据库的读写和迁移权限。

## 4. 前端请求失败

常见表现：

- 页面能打开，但列表、登录、上传等接口失败。
- 浏览器 Network 中请求 404、401、403、502 或 CORS 错误。

常见原因：

- `VITE_API_BASE_URL` 配置错误。
- 后端未启动。
- Nginx `/api/` 代理路径错误。
- 后端端口和 Nginx proxy_pass 不一致。
- Token 过期或未登录。
- 本地开发跨域配置不匹配。

检查方法：

- 打开浏览器 DevTools 的 Network 面板。
- 检查请求 URL 是否以 `/api` 或正确后端地址开头。
- 检查响应状态码和响应体。
- 查看后端日志和 Nginx error log。

Nginx 草案检查：

```bash
rg -n "location /api/|proxy_pass" deploy/nginx/ai-resume.conf
```

处理方式：

- 同域部署时，前端生产环境建议使用 `VITE_API_BASE_URL=/api`。
- 502 通常优先检查后端是否启动，以及 Nginx proxy_pass 目标是否正确。
- 401 / 403 优先检查登录状态、Token 是否过期、接口是否需要鉴权。
- CORS 问题在 Nginx 同域代理下通常应避免，若跨域部署需单独配置后端允许来源。

## 5. 文件上传失败

常见原因：

- 文件类型不支持。
- 扩展名、MIME 和真实文件内容不匹配。
- 文件超过后端或 Nginx 限制。
- `APP_STORAGE_LOCAL_BASE_DIR` 不存在。
- 后端进程用户没有写权限。
- 磁盘空间不足。
- storageKey 被路径安全校验拒绝。

检查命令：

```bash
df -h
ls -lah uploads
echo $APP_STORAGE_LOCAL_BASE_DIR
```

如果生产目录为 `/data/ai-resume/uploads`：

```bash
ls -lah /data/ai-resume/uploads
```

处理方式：

- 确认上传文件是 PDF、DOC 或 DOCX，且真实内容匹配扩展名。
- 调整 Nginx `client_max_body_size`，不要低于后端上传限制。
- 创建上传目录并赋予后端进程用户读写权限。
- 磁盘空间不足时，清理无关文件或扩容。

## 6. AI 调用失败

常见原因：

- `AI_API_KEY` 缺失或无效。
- `AI_BASE_URL` 写成了完整 endpoint，导致路径重复。
- `AI_MODEL` 不存在或账号无权限。
- 请求超时。
- 服务商返回空文本、非 JSON 或被截断内容。
- Prompt 输入过长。

检查环境变量：

```bash
echo $AI_BASE_URL
echo $AI_MODEL
```

不要在终端、日志或截图中展示真实 `AI_API_KEY`。

排查方式：

- 查看后端日志中的脱敏错误信息。
- 检查异步任务失败原因。
- 确认 base URL 是服务根地址，不要重复包含 `/chat/completions`。
- 增大 `AI_TIMEOUT_SECONDS` 或 `AI_MAX_TOKENS` 前，先确认失败原因是否真的是超时或输出不足。

处理方式：

- 修正 `.env` 后重启后端。
- 模型不存在时，切换为账号可用模型。
- 响应格式异常时，优先检查后端日志中的 provider 返回摘要。
- Prompt 过长时，优先缩小输入或确认现有截断策略。

## 7. Embedding 服务失败

常见原因：

- `EMBEDDING_ENABLED` 与实际部署不一致。
- Embedding 服务未启动。
- `EMBEDDING_BASE_URL` 端口或路径错误。
- `EMBEDDING_API_KEY` 无效。
- `EMBEDDING_MODEL` 不存在。
- `EMBEDDING_DIMENSION` 与数据库向量字段不一致。
- 本地模型服务显存或内存不足。

检查命令：

```bash
echo $EMBEDDING_ENABLED
echo $EMBEDDING_BASE_URL
echo $EMBEDDING_MODEL
curl http://localhost:8000/v1/models
```

说明：

- 上面的 `curl` 只适用于本地 OpenAI-compatible Embedding 服务示例。
- 第三方服务需要按实际 base URL 和鉴权方式检查。

处理方式：

- 暂不使用向量能力时，将 `EMBEDDING_ENABLED=false`。
- 启用向量能力时，确认模型维度与数据库字段一致。
- 服务超时时，检查模型服务资源和 `EMBEDDING_TIMEOUT_SECONDS`。

## 8. 异步任务卡住

常见表现：

- 前端进度条长时间不结束。
- 任务状态停留在 `RUNNING`。
- 任务没有正常写入失败原因。

常见原因：

- AI / Embedding 请求长时间超时。
- 后台线程执行异常。
- 线程池配置过小或队列阻塞。
- 数据库写入失败。
- 前端轮询中断或请求失败。

检查方向：

- 查看后端 ERROR 日志。
- 查看对应任务记录状态、开始时间、结束时间和失败原因。
- 检查 AI / Embedding 服务是否可用。
- 检查前端 Network 中任务状态轮询是否还在进行。

相关配置：

```env
APP_ASYNC_CORE_POOL_SIZE=
APP_ASYNC_MAX_POOL_SIZE=
APP_ASYNC_QUEUE_CAPACITY=
APP_ASYNC_AWAIT_TERMINATION_SECONDS=
```

处理方式：

- 外部服务慢时，优先修复 AI / Embedding 服务可用性。
- 单个任务异常时，保留失败原因供前端展示。
- 大量任务阻塞时，再评估线程池配置，不要盲目加大线程数。
- 后续可增加超时扫描任务，本阶段不强行实现。

## 9. Nginx 相关问题

### 刷新前端二级路由返回 404

检查是否存在：

```nginx
try_files $uri $uri/ /index.html;
```

### 上传返回 413

检查：

```nginx
client_max_body_size 20m;
```

### `/api/` 返回 502

检查：

- 后端是否启动。
- `proxy_pass` 是否指向 `http://127.0.0.1:8080/api/`。
- 后端端口是否被防火墙或进程问题影响。

## 10. 常用命令清单

后端：

```bash
cd backend
./mvnw clean package -DskipTests
SPRING_PROFILES_ACTIVE=prod java -jar target/*.jar
```

前端：

```bash
cd web
npm install
npm run build
```

Compose：

```bash
docker compose --env-file .env.example config
docker compose --env-file .env up -d postgres redis minio
docker compose logs -f postgres
docker compose down
```

磁盘和端口：

```bash
df -h
lsof -i :8080
```

Nginx：

```bash
nginx -t
nginx -s reload
tail -f /var/log/nginx/error.log
```

## 11. 排查记录建议

每次故障建议记录：

- 发生时间。
- 操作入口。
- 用户可见错误。
- 后端日志关键错误。
- 异步任务 ID。
- 请求路径和响应状态码。
- 当时的 Profile 和关键配置项，不记录密钥。
- 处理方式。
- 是否需要补测试或补文档。
