# AI 简历优化器

AI 简历优化器是一个基于 Spring Boot 的项目，用于管理简历、解析简历内容，并生成面向岗位要求的简历优化建议。

本项目采用文档驱动的开发方式。产品范围、工程决策和迭代记录统一维护在 `docs/` 目录下。

## 技术栈

- Java 17
- Spring Boot 4
- Spring MVC
- Spring Security
- Bean Validation
- PostgreSQL 驱动
- Maven Wrapper

## 项目结构

```text
.
├── docs/
│   ├── 00-development-workflow.md
│   ├── 01-project-overview.md
│   ├── phase-1-mvp.md
│   ├── phase-2-engineering.md
│   ├── phase-3-ai.md
│   ├── phase-4-architecture.md
│   └── iteration-log/
│       ├── v0.1-auth.md
│       ├── v0.2-upload.md
│       └── v0.3-parse.md
├── src/
├── pom.xml
└── README.md
```

## 文档说明

- [开发流程](docs/00-development-workflow.md)：通用开发 SOP。
- [项目总览](docs/01-project-overview.md)：产品目标、边界和四阶段路线。
- [第一阶段 MVP](docs/phase-1-mvp.md)：第一个可用版本。
- [第二阶段 工程化](docs/phase-2-engineering.md)：可靠性、质量和可维护性。
- [第三阶段 AI 能力](docs/phase-3-ai.md)：AI 功能设计与效果评估。
- [第四阶段 架构演进](docs/phase-4-architecture.md)：长期架构方向。
- [迭代记录](docs/iteration-log/)：实际开发过程记录。

## 本地运行

前置要求：

- JDK 17+
- 使用项目内置 Maven Wrapper

运行测试：

```bash
./mvnw test
```

启动应用：

```bash
./mvnw spring-boot:run
```

默认应用名称配置在 `src/main/resources/application.yaml` 中。

## 开发规则

实现功能前：

1. 确认当前阶段文档。
2. 将工作拆分为明确的一轮迭代。
3. 在 `docs/iteration-log/` 下记录实现结果。
4. 只有当项目使用方式或对外行为变化时，才更新 README。
