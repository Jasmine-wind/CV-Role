# 部署检查清单

本文档用于记录当前项目从本地开发迁移到服务器部署前需要确认的事项。当前版本目标是让项目具备“可部署准备”，不是完成完整生产级高可用部署。

## 1. 当前部署边界

当前推荐部署形态：

```text
用户浏览器 -> Nginx -> Vue 静态文件
用户浏览器 -> Nginx /api -> Spring Boot 后端 -> PostgreSQL
                                           -> 本地文件存储目录
                                           -> AI / Embedding 服务
```

当前已准备：

- 后端支持 `local` / `dev` / `prod` / `test` Profile。
- 生产配置位于 `backend/src/main/resources/application-prod.yaml`。
- 环境变量示例位于 `.env.example`。
- 前端环境变量示例位于 `web/.env.example`。
- 本地依赖 Compose 位于 `docker-compose.yml`。
- Nginx 反向代理草案位于 `deploy/nginx/ai-resume.conf`。

当前未完成：

- `docker-compose.yml` 只编排 PostgreSQL、Redis、MinIO 等依赖，不包含后端和前端应用镜像。
- 后端上传链路当前仍使用本地文件存储，MinIO 只是部署预留。
- Redis 当前只作为依赖预留，后端运行路径尚未依赖 Redis。
- HTTPS、正式域名、证书自动续期和后端 systemd 服务不在本轮实现范围。
- `LOG_FILE_PATH` 已作为环境变量预留，当前后端主要完成日志级别环境化。

## 2. 服务器准备清单

- [ ] Linux 服务器已准备。
- [ ] 已安装 Java 21。
- [ ] 如需在服务器构建前端，已安装 Node.js 和 npm。
- [ ] 已安装 Docker / Podman。
- [ ] 已安装 Docker Compose / Podman Compose。
- [ ] 已准备 PostgreSQL，或使用仓库 Compose 中的 PostgreSQL。
- [ ] 已准备 Nginx。
- [ ] 域名已解析到服务器，可选。
- [ ] HTTPS 证书已准备，可选。
- [ ] 防火墙开放 `80` / `443`。
- [ ] 后端端口 `8080` 不直接暴露公网，建议仅允许本机或内网访问。
- [ ] 上传文件目录使用服务器持久化目录。
- [ ] 数据库数据目录或 volume 可持久化。
- [ ] 日志目录按服务器策略持久化。

## 3. 环境变量清单

部署前从 `.env.example` 复制并创建服务器私有 `.env`，不要提交 `.env`。

必须确认：

- [ ] `SPRING_PROFILES_ACTIVE=prod`
- [ ] `SERVER_PORT=8080`
- [ ] `POSTGRES_DB` 已设置。
- [ ] `POSTGRES_USER` 已设置。
- [ ] `POSTGRES_PASSWORD` 已替换为强密码。
- [ ] `DB_URL` 指向生产 PostgreSQL。
- [ ] `DB_USERNAME` 与生产数据库用户一致。
- [ ] `DB_PASSWORD` 与生产数据库密码一致。
- [ ] `JWT_SECRET` 已替换为足够长的强随机值。
- [ ] `JWT_EXPIRATION_MINUTES` 符合部署预期。
- [ ] `AI_BASE_URL` 已设置。
- [ ] `AI_API_KEY` 已设置，且不是示例值。
- [ ] `AI_MODEL` 已设置。
- [ ] `AI_TIMEOUT_SECONDS` 已按慢请求场景设置。
- [ ] `AI_MAX_TOKENS` 已按模型输出需要设置。
- [ ] `EMBEDDING_ENABLED` 按实际部署能力设置。
- [ ] 如果启用向量能力，`EMBEDDING_BASE_URL` / `EMBEDDING_API_KEY` / `EMBEDDING_MODEL` / `EMBEDDING_DIMENSION` 已确认。
- [ ] `APP_STORAGE_TYPE=local`
- [ ] `APP_STORAGE_LOCAL_BASE_DIR` 指向服务器持久化目录。
- [ ] `LOG_LEVEL_ROOT` 和 `LOG_LEVEL_APP` 符合生产环境预期。

前端确认：

- [ ] 如果前端通过 Nginx 同域代理后端，生产构建使用 `VITE_API_BASE_URL=/api`。
- [ ] 如果前端和后端不同域，`VITE_API_BASE_URL` 指向后端公开代理地址。
- [ ] 不在前端环境变量中放置后端密钥、AI Key 或数据库密码。

## 4. 构建命令

后端：

```bash
cd backend
./mvnw clean package -DskipTests
```

前端：

```bash
cd web
npm install
npm run build
```

说明：

- 后端构建产物位于 `backend/target/`。
- 前端构建产物位于 `web/dist/`。
- `target/` 和 `dist/` 属于构建产物，不提交 Git。

## 5. 启动命令

启动依赖服务：

```bash
docker compose --env-file .env up -d postgres redis minio
```

如果使用 Podman：

```bash
podman compose --env-file .env up -d postgres redis minio
```

启动后端：

```bash
cd backend
SPRING_PROFILES_ACTIVE=prod java -jar target/*.jar
```

前端静态文件：

```text
将 web/dist/ 发布到 Nginx 配置中的 root 目录。
```

Nginx：

- 参考 `deploy/nginx/ai-resume.conf`。
- 替换 `server_name`。
- 替换前端静态文件 `root`。
- 确认 `/api/` 代理到后端本机地址。
- 如需 HTTPS，单独增加证书和 `listen 443 ssl` 配置。

## 6. 部署后手动验收

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
- [ ] 局部改写仍只针对用户选中片段。
- [ ] 历史记录只查询已有 AI 结果，不触发新的 AI 生成。

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

## 7. 回滚清单

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
7. 按部署后手动验收清单重新验证主流程。

## 8. 后续待补

- 后端应用容器镜像和前端静态资源镜像。
- systemd 服务文件或 Compose 应用服务。
- HTTPS 正式配置和证书续期说明。
- 生产文件日志输出路径配置。
- MinIO 存储实现接入。
- Redis 实际运行能力接入。
- CORS 从硬编码本地开发地址调整为部署可配置。
