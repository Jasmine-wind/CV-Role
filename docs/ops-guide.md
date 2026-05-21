# 运维脚本指南

本文件对应 `scripts/ops/` 下的基础运维脚本，适用于 `docker-compose.prod.yml` 的单机生产编排。

## 备份

```bash
scripts/ops/backup-postgres.sh
scripts/ops/backup-minio.sh
scripts/ops/backup-uploads.sh
```

可通过环境变量覆盖默认值：

```bash
COMPOSE_FILE=docker-compose.prod.yml ENV_FILE=.env.production scripts/ops/backup-postgres.sh
```

## 恢复

```bash
scripts/ops/restore-postgres.sh backups/postgres/postgres-YYYY-MM-DD_HH-MM-SS.sql
```

## 日志

```bash
scripts/ops/show-logs.sh
scripts/ops/show-logs.sh backend
```

## 重启

```bash
scripts/ops/restart-services.sh
scripts/ops/restart-services.sh backend nginx
```

## 说明

- 脚本默认读取 `.env.production`。
- 备份产物默认写入 `backups/`。
- 脚本不包含真实密钥。

