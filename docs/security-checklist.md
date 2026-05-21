# 上线前安全复查清单

本文档记录 Phase 5 v4.4 的上线前安全复查结果。

## 生产环境变量

上线前必须确认：

- `.env` 不提交到 Git。
- `JWT_SECRET` 使用强随机值，不能使用示例值。
- `DB_PASSWORD` 使用生产密码，不能使用本地示例值。
- `AI_API_KEY`、`EMBEDDING_API_KEY` 只存在服务器 `.env`。
- `MINIO_ACCESS_KEY`、`MINIO_SECRET_KEY` 只存在服务器 `.env`。
- `REDIS_PASSWORD` 如生产 Redis 开启认证，只存在服务器 `.env`。
- `APP_CORS_ALLOWED_ORIGIN_PATTERNS` 设置为正式域名，不使用本地开发地址。

## CORS

当前状态：

- 后端 CORS 已改为 `APP_CORS_ALLOWED_ORIGIN_PATTERNS` 配置化。
- 本地默认允许 `127.0.0.1` 和 `localhost` 的 Vite 开发端口。
- 生产 Profile 要求通过环境变量显式设置允许来源。

生产示例：

```text
APP_CORS_ALLOWED_ORIGIN_PATTERNS=https://example.com,https://www.example.com
```

## 文件上传与访问

当前状态：

- 后端限制上传文件大小。
- 简历上传、解析、删除均通过 `FileStorageService`。
- MinIO 接入后，前端仍不能直接访问 objectKey 或永久公开 URL。
- 文件读取前仍需要业务侧校验简历属于当前用户。
- Bucket 不应设置公开匿名读写。

上线前确认：

- Nginx `client_max_body_size` 不小于后端上传限制。
- MinIO bucket 为私有。
- 不暴露 MinIO API / Console 到公网，除非有明确防火墙和认证策略。

## Redis

当前状态：

- Redis 只用于非核心缓存数据。
- 当前真实场景为 AI 简历展示模型缓存。
- Redis 不保存唯一核心业务结果。
- Redis 读写失败不会影响主业务流程。

上线前确认：

- Redis 不暴露公网。
- 如部署环境要求认证，设置 `REDIS_PASSWORD`。
- 缓存 key 不包含明文简历全文、Token、API Key 或数据库密码。

## 日志与错误信息

当前状态：

- 全局异常处理不向用户返回堆栈。
- 业务异常和非预期异常会经过 `LogSanitizer` 脱敏。
- 文件存储异常对用户返回通用错误文案。
- AI 调用异常对用户返回通用错误文案。

上线前确认：

- 生产日志级别不使用 `debug`。
- 日志中不输出完整简历原文、JWT、AI Key、数据库密码、MinIO Secret。
- 日志目录由 volume 或宿主机目录持久化。

## Docker Compose

当前状态：

- 本地 `docker-compose.yml` 已包含 PostgreSQL、Redis、MinIO 依赖服务。
- 生产 `docker-compose.prod.yml` 留到 v4.5 统一创建。

上线前确认：

- Compose 不写死真实密钥。
- PostgreSQL、Redis、MinIO 不直接暴露到公网。
- 后端只通过容器网络访问 PostgreSQL、Redis、MinIO。
- Nginx 是唯一公网入口。

## v4.4 结论

当前已完成上线前安全复查和 CORS 配置化补漏。剩余生产编排、端口暴露和 HTTPS 细节进入 v4.5 / v4.6 继续处理。
