# AI 简历优化与岗位匹配系统最终总结

## 项目定位

本项目是一个面向求职场景的 AI 简历优化与岗位匹配系统。核心目标是让用户围绕一个目标岗位完成：

```text
上传简历 -> 简历解析 -> 简历诊断 -> 目标岗位解析 -> 匹配分析 -> 优化建议 -> 局部改写 -> 优化报告 -> 历史回看
```

系统不是传统后台管理系统，也不是一次性简历评分工具，而是一个以“岗位匹配决策”和“简历表达优化”为主线的 AI SaaS 工作台。

## 当前核心能力

- 用户注册、登录、JWT 鉴权。
- PDF / DOC / DOCX 简历上传、存储、读取和删除。
- 简历文本提取、清洗、结构化解析和质量提示。
- 目标岗位提交、JD 结构化解析和岗位画像展示。
- AI 简历诊断，输出优势、问题和下一步建议。
- AI 岗位匹配，输出匹配分数、强匹配、弱匹配、缺失技能、风险提醒和依据。
- 岗位优化建议，按优先级输出可执行修改方向。
- 基于建议的局部改写，支持采纳状态管理。
- 岗位优化报告，聚合匹配、建议和改写结果，不重复调用 AI。
- AI 历史回看，按结果类型展示用户可读报告。
- Redis 缓存、MinIO 文件存储、PostgreSQL + pgvector、Docker Compose 生产部署。

## 技术架构

后端：

- Java 21
- Spring Boot 3.5
- Spring Security + JWT
- MyBatis-Plus
- PostgreSQL + Flyway
- pgvector
- Redis
- MinIO
- OpenAI-compatible Chat API
- SiliconFlow OpenAI-compatible Embeddings API
- Maven

前端：

- Vue 3
- TypeScript
- Vite
- Pinia
- Vue Router
- Element Plus
- Axios
- SCSS

部署：

- Docker Compose 单机部署
- Nginx 托管前端静态资源
- Nginx 反向代理 `/api` 到后端
- Let's Encrypt HTTPS
- Docker volume 持久化 PostgreSQL、Redis、MinIO、日志和证书

## AI 与 Embedding 边界

- AI 用于诊断、匹配、建议、局部改写和报告聚合展示。
- AI 不替用户编造经历、证书、奖项、技能或量化指标。
- 岗位优化报告只聚合已有结果，不为了补依据重新调用 AI。
- Embedding 使用 SiliconFlow OpenAI-compatible `/embeddings` 接口。
- 默认模型：`Qwen/Qwen3-Embedding-0.6B`。
- 默认维度：`1024`。
- AI 匹配默认会尝试使用 RAG / Embedding 上下文；如果向量未生成，会自动降级为普通 AI 匹配。

## 阶段成果摘要

| 阶段 | 结果 |
|---|---|
| Phase 1 | 打通注册登录、上传、解析、AI 分析、岗位匹配和历史记录 MVP。 |
| Phase 2 | 完成配置、异常、Flyway、日志、测试、OpenAPI 和本地编排工程化。 |
| Phase 3 | 完成目标岗位解析、AI 匹配、优化建议、局部改写、AI 历史、评估、Embedding/RAG 和解析质量优化。 |
| Phase 4 | 完成架构审查、包结构整理、文件存储抽象、异步任务、安全加固和部署配置准备。 |
| Phase 5 | 完成 Redis、MinIO、Docker Compose、HTTPS、运维脚本、线上部署和项目包装。 |

详细过程保留在 `docs/iteration-log/`。

## 当前线上部署状态

- 正式地址：`https://resume.dawn04.xyz`
- 部署方式：Docker Compose 单机部署
- 服务器目录：`/opt/ai-resume-optimizer`
- 运行服务：Nginx、backend、PostgreSQL + pgvector、Redis、MinIO、certbot

部署、更新、备份和排障统一查看：

```text
docs/ai-resume-deployment-ops-guide.md
```

## 主要验收路径

1. 注册并登录。
2. 上传简历。
3. 查看简历解析结果、结构化结果和完整原文。
4. 生成简历诊断。
5. 新增目标岗位并解析 JD。
6. 运行匹配分析。
7. 生成岗位优化建议。
8. 基于建议进行局部改写。
9. 查看岗位优化报告。
10. 在 AI 历史中回看结果。

## 文档边界

当前保留文档分为：

- 项目入口：`README.md`、`docs/README.md`
- 项目总结：`docs/project-final-summary.md`
- 结构规范：`docs/project-structure.md`
- 部署运维：`docs/ai-resume-deployment-ops-guide.md`
- 当前阶段：`docs/phase-5-productization-deployment-v4.md`
- 历史记录：`docs/iteration-log/`
- 演示与评估：`docs/demo/`、`docs/evaluation/`

旧的重复部署文档、运维文档、排障文档、阶段任务草稿和临时 Codex 任务文档已合并删除。
