# 项目文档入口

本目录已按项目收尾阶段重新整理。日常查看优先从以下文档进入，旧的阶段任务草稿、重复部署文档和临时排障文档已合并删除。

## 推荐阅读顺序

1. [README](../README.md)
   - 项目定位、核心功能、技术栈、本地启动方式。
2. [项目最终总结](project-final-summary.md)
   - 当前能力、技术架构、阶段成果、边界说明和后续可讲解重点。
3. [线上部署与运维指南](ai-resume-deployment-ops-guide.md)
   - Docker Compose 生产部署、HTTPS、更新流程、日志、备份和常见问题。
4. [项目结构规范](project-structure.md)
   - 代码目录、后端包结构、前端目录、文档和部署文件放置规则。
5. [Phase 5 收口文档](phase-5-productization-deployment-v4.md)
   - 产品化、部署、运维和项目包装阶段的最终执行记录。

## 演示材料

- [完整演示流程](demo/demo-flow.md)
- [Java 后端简历样例](demo/demo-resume-java-backend.md)
- [Java 后端岗位样例](demo/demo-job-java-backend.md)

## 评估材料

- [评估说明](evaluation/README.md)
- [解析问题样例](evaluation/parse/parse-issue-samples.md)
- [解析报告](evaluation/parse/parse-report.md)
- [评估报告](evaluation/reports/evaluation-report-v1.md)

## 历史记录

- [迭代日志目录](iteration-log/)

迭代日志保留每个版本的完成内容、当前不足和验证结果，用于回溯开发过程；不再保留重复的长篇阶段任务清单。

## 文档维护规则

- 新增部署、HTTPS、备份、日志、排障内容，统一更新 `ai-resume-deployment-ops-guide.md`。
- 新增项目介绍、技术亮点、功能边界，统一更新 `project-final-summary.md` 和根目录 `README.md`。
- 新增阶段执行记录，统一写入 `iteration-log/`。
- 不再新增临时 Codex 提示词文档、重复部署文档、重复运维文档。
