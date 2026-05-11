# 项目结构规范

## 1. 文档目标

本文件用于规定“AI 简历优化与岗位匹配系统”的项目目录结构、代码文件放置规则、模块划分规则和新增文件约束。

本规范的目标是避免在迭代开发和 AI 辅助编码过程中出现目录混乱、文件乱放、模块边界不清等问题，确保项目在 Phase 1、Phase 2、Phase 3、Phase 4 中都能基于统一结构持续演进。

---

## 2. 项目根目录结构

项目采用前后端分离结构，根目录只作为仓库根目录，不作为 Spring Boot 应用。

推荐根目录结构如下：

```text
Ai-resume-optimizer/
├── backend/                 # Spring Boot 后端项目
├── web/                     # Vue 3 + Vite 前端项目
├── docs/                    # 项目文档
├── deploy/                  # 部署相关文件
├── AGENTS.md                # Codex / AI 编码助手约束文件
├── README.md                # 项目说明文档
├── .gitignore               # Git 忽略规则
└── docker-compose.yml       # 本地开发环境编排，可选
```

---

## 3. 根目录规则

### 3.1 根目录职责

根目录只负责管理整个项目仓库，包括：

- 后端项目目录：`backend/`
- 前端项目目录：`web/`
- 项目文档目录：`docs/`
- 部署配置目录：`deploy/`
- 项目总说明：`README.md`
- AI 编码约束文件：`AGENTS.md`
- 本地环境编排文件：`docker-compose.yml`

### 3.2 根目录禁止事项

根目录不是 Spring Boot 应用目录。

禁止在根目录创建以下 Spring Boot 项目文件或目录：

```text
src/
pom.xml
.mvn/
mvnw
mvnw.cmd
```

如果这些文件已经存在，并且不是有意作为 Maven 父工程使用，应当删除。

### 3.3 根目录允许存在的文件和目录

```text
backend/
web/
docs/
deploy/
AGENTS.md
README.md
.gitignore
docker-compose.yml
.env.example
```

说明：

- `.env` 不应提交到 Git。
- `.env.example` 可以提交，用于说明环境变量格式。
- `.idea/` 不应提交到 Git。
- `*.iml` 不应提交到 Git。

---

## 4. 后端目录规范

后端项目放在：

```text
backend/
```

后端技术栈：

- Java 21
- Spring Boot
- Spring Security + JWT
- MyBatis-Plus
- PostgreSQL
- MinIO
- Spring AI
- Maven

后端根包名固定为：

```text
com.winter.airesumeoptimizer
```

后端源码根目录为：

```text
backend/src/main/java/com/winter/airesumeoptimizer/
```

推荐结构如下：

```text
backend/
├── .mvn/
├── mvnw
├── mvnw.cmd
├── pom.xml
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── com/
│   │   │       └── winter/
│   │   │           └── airesumeoptimizer/
│   │   │               ├── AiResumeOptimizerApplication.java
│   │   │               ├── common/
│   │   │               ├── config/
│   │   │               ├── security/
│   │   │               ├── infra/
│   │   │               └── module/
│   │   └── resources/
│   │       ├── application.yml
│   │       ├── application-dev.yml
│   │       ├── application-prod.yml
│   │       └── mapper/
│   └── test/
│       └── java/
└── target/
```

说明：

- `target/` 是 Maven 构建产物，不提交到 Git。
- `.settings/` 是 Eclipse/STS 配置目录，如果使用 IDEA 开发，不需要保留。
- `.idea/` 和 `*.iml` 属于 IDEA 本地配置，不提交到 Git。

---

## 5. 后端核心目录说明

后端主包下的核心目录如下：

```text
com.winter.airesumeoptimizer/
├── common/          # 通用能力
├── config/          # 全局配置
├── security/        # 安全认证与 JWT
├── infra/           # 基础设施适配层
└── module/          # 业务模块
```

### 5.1 `common/`

存放通用代码，不直接属于某个业务模块。

推荐结构：

```text
common/
├── result/          # 统一返回结果
├── exception/       # 全局异常与业务异常
├── enums/           # 通用枚举
└── util/            # 通用工具类
```

示例文件：

```text
common/result/Result.java
common/result/ResultCode.java
common/exception/BusinessException.java
common/exception/GlobalExceptionHandler.java
```

### 5.2 `config/`

存放 Spring Boot 全局配置类。

示例文件：

```text
config/SecurityConfig.java
config/CorsConfig.java
config/MinioConfig.java
config/JwtConfig.java
```

### 5.3 `security/`

存放认证、鉴权、JWT 相关代码。

示例文件：

```text
security/JwtAuthenticationFilter.java
security/JwtTokenProvider.java
security/UserDetailsServiceImpl.java
```

### 5.4 `infra/`

存放基础设施适配代码。

基础设施代码指“不直接属于业务，但为业务提供外部能力”的代码。

推荐结构：

```text
infra/
├── storage/         # 文件存储适配，例如 MinIO
└── ai/              # AI 模型调用适配，例如 Spring AI
```

示例文件：

```text
infra/storage/FileStorageService.java
infra/storage/MinioFileStorageService.java
infra/ai/AiClientService.java
```

#### 5.4.1 AI 相关分层

Phase 3 开始后，AI 能力按以下边界放置：

| 层级 | 推荐位置 | 职责 |
|---|---|---|
| AI 调用适配 | `infra/ai/` | 调用外部模型服务、读取模型配置、处理 HTTP 和供应商兼容问题 |
| Prompt 构建 | 当前业务模块 `service/` 或后续统一模板服务 | 根据业务输入生成 Prompt，并返回 Prompt 版本 |
| AI 输出解析 | 当前业务模块 `service/` 或后续统一解析服务 | 将 AI 输出转换为受控 DTO，处理 JSON 格式和字段兜底 |
| 业务编排 | `module/{module-name}/service/impl/` | 校验用户和资源、组织 Prompt 构建、AI 调用、解析和持久化 |
| 结果持久化 | `module/{module-name}/entity/`、`mapper/` | 保存业务 AI 结果、模型名称、Prompt 版本、状态和错误信息 |
| 展示对象 | `module/{module-name}/vo/` | 面向前端返回可展示字段，不直接暴露数据库 JSON 字符串 |

约束：

- `infra/ai/` 只放通用 AI 基础设施，不放简历、岗位、历史记录等业务流程。
- 业务 Service 不直接拼 HTTP 请求，不直接读取 API Key。
- 每个 AI 业务结果应记录模型名称和 Prompt 版本。
- 后续新增岗位描述解析、AI 匹配、优化建议、局部改写时，优先沿用该分层边界。

### 5.5 `module/`

存放所有业务模块代码。

所有业务功能都必须放在 `module/` 下，不允许在主包下直接创建类似 `user.domain`、`resume.service` 这类业务平级包。

---

## 6. 后端业务模块规范

所有业务模块统一放在：

```text
backend/src/main/java/com/winter/airesumeoptimizer/module/
```

Phase 1 建议包含以下业务模块：

```text
module/
├── auth/            # 注册、登录、认证相关业务
├── user/            # 用户信息相关业务
├── resume/          # 简历上传、简历解析相关业务
├── analysis/        # AI 简历分析相关业务
├── job/             # 岗位、岗位匹配相关业务
└── history/         # 历史记录聚合查询相关业务
```

每个业务模块内部统一使用以下结构：

```text
module/xxx/
├── controller/      # 控制层，处理 HTTP 请求
├── service/         # 服务接口
│   └── impl/        # 服务实现
├── entity/          # 数据库实体
├── mapper/          # MyBatis-Plus Mapper
├── dto/             # 请求参数对象
└── vo/              # 返回结果对象
```

示例：用户模块结构

```text
module/user/
├── controller/
│   └── UserController.java
├── service/
│   ├── UserService.java
│   └── impl/
│       └── UserServiceImpl.java
├── entity/
│   └── User.java
├── mapper/
│   └── UserMapper.java
├── dto/
└── vo/
    └── UserProfileVO.java
```

示例：认证模块结构

```text
module/auth/
├── controller/
│   └── AuthController.java
├── service/
│   ├── AuthService.java
│   └── impl/
│       └── AuthServiceImpl.java
├── dto/
│   ├── LoginRequestDTO.java
│   └── RegisterRequestDTO.java
└── vo/
    └── LoginVO.java
```

---

## 7. 后端文件放置规则

### 7.1 Controller

Controller 放在对应业务模块的 `controller/` 下。

例如：

```text
module/auth/controller/AuthController.java
module/resume/controller/ResumeController.java
module/job/controller/JobController.java
```

### 7.2 Service

Service 接口放在对应模块的 `service/` 下。

Service 实现类放在对应模块的 `service/impl/` 下。

例如：

```text
module/auth/service/AuthService.java
module/auth/service/impl/AuthServiceImpl.java
```

### 7.3 Entity

数据库实体放在对应模块的 `entity/` 下。

例如：

```text
module/user/entity/User.java
module/resume/entity/Resume.java
module/job/entity/Job.java
```

### 7.4 Mapper

MyBatis-Plus Mapper 放在对应模块的 `mapper/` 下。

例如：

```text
module/user/mapper/UserMapper.java
module/resume/mapper/ResumeMapper.java
```

XML Mapper 如果需要使用，放在：

```text
backend/src/main/resources/mapper/
```

### 7.5 DTO

前端请求参数对象放在对应模块的 `dto/` 下。

例如：

```text
module/auth/dto/LoginRequestDTO.java
module/auth/dto/RegisterRequestDTO.java
module/resume/dto/ResumeParseRequestDTO.java
```

### 7.6 VO

后端返回给前端的视图对象放在对应模块的 `vo/` 下。

例如：

```text
module/auth/vo/LoginVO.java
module/user/vo/UserProfileVO.java
module/resume/vo/ResumeDetailVO.java
```

### 7.7 工具类

通用工具类放在：

```text
common/util/
```

不允许在业务模块中重复创建通用工具类。

### 7.8 枚举类

通用枚举放在：

```text
common/enums/
```

如果枚举只属于某个业务模块，可以放在该模块内部，但 Phase 1 优先保持简单，建议统一放在 `common/enums/`。

---

## 8. 前端目录规范

前端项目放在：

```text
web/
```

前端技术栈：

- Vue 3
- TypeScript
- Vite
- Pinia
- Vue Router
- Element Plus
- Axios
- SCSS

前端推荐结构：

```text
web/
├── package.json
├── vite.config.ts
├── tsconfig.json
├── index.html
├── public/
└── src/
    ├── main.ts
    ├── App.vue
    ├── api/
    ├── assets/
    ├── components/
    ├── layout/
    ├── router/
    ├── stores/
    ├── styles/
    ├── types/
    ├── utils/
    └── views/
```

---

## 9. 前端核心目录说明

```text
web/src/
├── api/             # 接口请求封装
├── assets/          # 静态资源
├── components/      # 可复用组件
├── layout/          # 页面布局
├── router/          # 路由配置
├── stores/          # Pinia 状态管理
├── styles/          # 全局样式
├── types/           # TypeScript 类型定义
├── utils/           # 工具函数
└── views/           # 页面级组件
```

### 9.1 `api/`

存放前端接口请求函数。

推荐文件：

```text
api/request.ts
api/auth.ts
api/user.ts
api/resume.ts
api/analysis.ts
api/job.ts
api/history.ts
```

### 9.2 `views/`

存放页面级组件。

Phase 1 推荐结构：

```text
views/
├── auth/
│   ├── LoginView.vue
│   └── RegisterView.vue
├── resume/
│   ├── ResumeListView.vue
│   └── ResumeDetailView.vue
├── job/
│   ├── JobListView.vue
│   └── JobDetailView.vue
├── match/
│   └── MatchResultView.vue
├── history/
│   └── HistoryView.vue
└── HomeView.vue
```

### 9.3 `components/`

存放可复用组件。

推荐结构：

```text
components/
├── common/
├── layout/
└── business/
```

### 9.4 `stores/`

存放 Pinia 状态管理。

推荐文件：

```text
stores/auth.ts
stores/user.ts
stores/resume.ts
```

### 9.5 `types/`

存放 TypeScript 类型定义。

推荐文件：

```text
types/auth.ts
types/user.ts
types/resume.ts
types/analysis.ts
types/job.ts
```

---

## 10. 文档目录规范

项目文档统一放在：

```text
docs/
```

推荐结构：

```text
docs/
├── iteration-log/
│   ├── v0.1-auth.md
│   ├── v0.2-upload.md
│   ├── v0.3-parse.md
│   ├── v0.4-ai-analysis.md
│   ├── v0.5-job-match.md
│   └── v0.6-mvp-demo.md
├── 00-development-workflow.md
├── 01-project-overview.md
├── project-structure.md
├── phase-1-mvp.md
├── phase-2-engineering.md
├── phase-3-ai.md
└── phase-4-architecture.md
```

### 10.1 迭代日志规则

每完成一个可验收版本，写一篇迭代日志。

例如：

```text
v0.1-auth.md
v0.2-upload.md
v0.3-parse.md
```

每篇日志建议包含：

- 本轮目标
- 本轮完成内容
- 关键技术点
- 遇到的问题
- 解决方式
- 当前不足
- 下一轮计划

---

## 11. 部署目录规范

部署相关文件放在：

```text
deploy/
```

推荐结构：

```text
deploy/
├── docker/
├── nginx/
└── scripts/
```

说明：

- Phase 1 可以暂时只保留 `deploy/README.md`。
- 本地开发阶段可以在根目录保留 `docker-compose.yml`，用于启动 PostgreSQL、MinIO 等依赖服务。
- Fedora 环境下可以使用 Docker 或 Podman。

---

## 12. Git 忽略规则建议

根目录 `.gitignore` 至少应包含：

```gitignore
# IDE
.idea/
*.iml
.settings/

# Java / Maven
target/
*.log

# Node / Vue
node_modules/
dist/

# Env
.env
.env.local
.env.*.local

# OS
.DS_Store
Thumbs.db
```

---

## 13. 新增文件规则

新增文件前必须先判断其归属。

### 13.1 后端新增文件

- 属于业务功能：放入 `backend/src/main/java/com/winter/airesumeoptimizer/module/`
- 属于通用能力：放入 `common/`
- 属于全局配置：放入 `config/`
- 属于安全认证：放入 `security/`
- 属于文件存储、AI 调用等基础设施：放入 `infra/`

### 13.2 前端新增文件

- 页面：放入 `web/src/views/`
- 接口请求：放入 `web/src/api/`
- 可复用组件：放入 `web/src/components/`
- 类型定义：放入 `web/src/types/`
- 状态管理：放入 `web/src/stores/`
- 工具函数：放入 `web/src/utils/`

### 13.3 文档新增文件

- 项目规划、规范、阶段文档：放入 `docs/`
- 迭代日志：放入 `docs/iteration-log/`

---

## 14. 禁止事项

### 14.1 根目录禁止事项

- 禁止在根目录创建 Spring Boot 项目结构。
- 禁止在根目录创建 `src/`。
- 禁止在根目录创建业务代码。
- 禁止在根目录放置后端或前端源码。

### 14.2 后端禁止事项

- 禁止在 `com.winter.airesumeoptimizer` 下直接创建 `user.domain`、`resume.service` 等业务平级包。
- 禁止业务代码绕过 `module/` 直接放到主包下。
- 禁止将 Controller、Service、Entity 混放在同一目录。
- 禁止在 `common/` 中放具体业务逻辑。
- 禁止在 `infra/` 中放业务流程编排逻辑。

### 14.3 前端禁止事项

- 禁止在 `web/` 中创建 Spring Boot 结构。
- 禁止在 `web/src/views/` 中直接写大量接口请求逻辑。
- 禁止将所有页面都堆在 `views/` 根目录。
- 禁止把业务页面组件放入 `components/common/`。

### 14.4 AI 辅助编码禁止事项

- 禁止 AI 编码助手随意创建新顶层目录。
- 禁止 AI 编码助手在未说明理由的情况下新增与现有规范平级的新目录。
- 禁止 AI 编码助手忽略本项目既定包名和目录结构。

---

## 15.  AI 编码工具使用约束

使用 AI 编码工具时，必须要求其遵循本项目目录规范。

每次让 AI 修改代码前，建议附加以下说明：

```text
请严格遵循仓库根目录的 AGENTS.md 和 docs/project-structure.md，不要随意创建新目录。新增文件前先判断其归属，只能放入既定目录结构中。
```

如果 AI 认为必须新增目录，应先说明理由，不得直接创建。

---

## 16. 结构检查规则

每完成一个迭代版本后，需要检查一次项目结构。

检查项：

- 根目录是否只保留仓库级文件
- 后端代码是否都在 `backend/`
- 前端代码是否都在 `web/`
- 后端业务代码是否都在 `module/`
- 是否出现了不规范的平级业务包
- 是否出现了重复职责目录
- 是否误提交 `.idea/`、`.settings/`、`target/`、`node_modules/`

---

## 17. 结论

本项目结构规范的核心原则是：

```text
根目录只管项目整体，backend 只管后端，web 只管前端，docs 只管文档，业务代码统一进入 module。
```

后续所有开发、重构和 AI 辅助编码都必须遵循本规范。
