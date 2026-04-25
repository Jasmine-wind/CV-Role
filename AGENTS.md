# AGENTS.md

## Project Overview

This repository is the AI Resume Optimization and Job Matching System.

The project uses a frontend-backend separated structure.

Main directories:

```text
backend/    - Spring Boot backend
web/        - Vue 3 + Vite frontend
docs/       - project documentation
deploy/     - deployment-related files
```

The repository root is only used as the project root.  
It must not be treated as a Spring Boot application.

---

## Global Rules

Before creating or modifying files, follow these documents:

```text
docs/project-structure.md
docs/phase-1-mvp.md
```

Do not create files freely without checking the project structure.

When adding new files, first determine whether the file belongs to:

- backend
- web
- docs
- deploy

If uncertain where a file belongs, ask before creating it.

---

## Root Directory Rules

The repository root must not contain Spring Boot application files.

Do not create the following files or directories in the repository root:

```text
src/
pom.xml
.mvn/
mvnw
mvnw.cmd
```

Backend code must go under:

```text
backend/
```

Frontend code must go under:

```text
web/
```

Documentation must go under:

```text
docs/
```

Deployment files must go under:

```text
deploy/
```

The root directory may contain:

```text
AGENTS.md
README.md
.gitignore
docker-compose.yml
.env.example
```

---

## Backend Rules

The backend project is located at:

```text
backend/
```

The backend root package is:

```text
com.winter.airesumeoptimizer
```

All backend Java source files must be placed under:

```text
backend/src/main/java/com/winter/airesumeoptimizer/
```

The Spring Boot application class should be located at:

```text
backend/src/main/java/com/winter/airesumeoptimizer/AiResumeOptimizerApplication.java
```

---

## Backend Package Structure

Use the following backend package structure:

```text
com.winter.airesumeoptimizer/
├── common/
├── config/
├── security/
├── infra/
└── module/
```

Directory responsibilities:

```text
common/      - shared utilities, result wrappers, exceptions, enums
config/      - Spring Boot configuration classes
security/    - authentication, authorization, JWT
infra/       - infrastructure adapters, storage, AI clients
module/      - business modules
```

---

## Backend Business Module Rules

All business code must be placed under:

```text
backend/src/main/java/com/winter/airesumeoptimizer/module/
```

Current business modules:

```text
auth/
user/
resume/
analysis/
job/
history/
```

Each business module should follow this structure:

```text
controller/
service/
service/impl/
entity/
mapper/
dto/
vo/
```

Example:

```text
module/user/
├── controller/
├── service/
│   └── impl/
├── entity/
├── mapper/
├── dto/
└── vo/
```

---

## Backend Placement Rules

### Controllers

Place controllers under the corresponding business module:

```text
module/{module-name}/controller/
```

Example:

```text
module/auth/controller/AuthController.java
```

### Services

Place service interfaces under:

```text
module/{module-name}/service/
```

Place service implementations under:

```text
module/{module-name}/service/impl/
```

Example:

```text
module/auth/service/AuthService.java
module/auth/service/impl/AuthServiceImpl.java
```

### Entities

Place database entities under:

```text
module/{module-name}/entity/
```

Example:

```text
module/user/entity/User.java
```

### Mappers

Place MyBatis-Plus mappers under:

```text
module/{module-name}/mapper/
```

Example:

```text
module/user/mapper/UserMapper.java
```

If XML mapper files are needed, place them under:

```text
backend/src/main/resources/mapper/
```

### DTOs

Place request DTOs under:

```text
module/{module-name}/dto/
```

Example:

```text
module/auth/dto/LoginRequestDTO.java
```

### VOs

Place response view objects under:

```text
module/{module-name}/vo/
```

Example:

```text
module/auth/vo/LoginVO.java
```

### Common Code

Place common shared code under:

```text
common/
```

Examples:

```text
common/result/
common/exception/
common/enums/
common/util/
```

### Infrastructure Code

Place infrastructure adapters under:

```text
infra/
```

Examples:

```text
infra/storage/
infra/ai/
```

---

## Backend Prohibited Patterns

Do not create business packages directly under:

```text
com.winter.airesumeoptimizer/
```

For example, do not create:

```text
user.domain
resume.service
job.controller
```

Instead, use:

```text
module/user/entity/
module/resume/service/
module/job/controller/
```

Do not mix Controller, Service, Entity, Mapper, DTO, and VO files in one directory.

Do not put business logic into:

```text
common/
config/
security/
infra/
```

---

## Frontend Rules

The frontend project is located at:

```text
web/
```

Frontend source code must be placed under:

```text
web/src/
```

Use the following frontend structure:

```text
web/src/
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

## Frontend Placement Rules

### API Requests

Place API request functions under:

```text
web/src/api/
```

Example:

```text
web/src/api/auth.ts
web/src/api/resume.ts
```

### Views

Place page-level Vue components under:

```text
web/src/views/
```

Recommended Phase 1 structure:

```text
views/auth/
views/resume/
views/job/
views/match/
views/history/
```

### Components

Place reusable components under:

```text
web/src/components/
```

Recommended structure:

```text
components/common/
components/layout/
components/business/
```

### Stores

Place Pinia stores under:

```text
web/src/stores/
```

### Types

Place TypeScript types under:

```text
web/src/types/
```

### Utils

Place utility functions under:

```text
web/src/utils/
```

### Styles

Place global styles under:

```text
web/src/styles/
```

---

## Frontend Prohibited Patterns

Do not create Spring Boot or Maven files under:

```text
web/
```

Do not create:

```text
web/pom.xml
web/.mvn/
web/mvnw
web/mvnw.cmd
```

Do not place backend code under:

```text
web/
```

Do not place frontend code under:

```text
backend/
```

---

## Documentation Rules

Project documents must be placed under:

```text
docs/
```

Iteration logs must be placed under:

```text
docs/iteration-log/
```

Each completed version should have one iteration log.

Example:

```text
docs/iteration-log/v0.1-auth.md
docs/iteration-log/v0.2-upload.md
```

---

## Deployment Rules

Deployment-related files should be placed under:

```text
deploy/
```

The root `docker-compose.yml` may be used for local development.

For Fedora local development, Docker or Podman may be used.

---

## Git Ignore Rules

Do not commit generated or local environment files.

The following files or directories should not be committed:

```text
.idea/
*.iml
.settings/
target/
node_modules/
dist/
.env
.env.local
```

---

## Development Phase Rules

Current phase:

```text
Phase 1 - Basic Runnable MVP
```

For Phase 1, prioritize the main business flow:

```text
register/login -> upload resume -> parse resume -> AI analysis -> job matching -> suggestions
```

Do not introduce unnecessary complex features in Phase 1.

Do not introduce the following unless explicitly requested:

```text
microservices
message queue
RAG
pgvector
complex admin system
complex statistics
Kubernetes
```

---

## Change Rules

Before adding a new top-level directory, explain why it is necessary.

Do not introduce new top-level directories unless the existing structure cannot reasonably contain the new files.

When implementing a task, prefer modifying existing files and directories that match the current architecture.

If uncertain where a file belongs, ask before creating it.

---

## Coding Assistant Rules

When acting as a coding assistant:

1. Follow `docs/project-structure.md`.
2. Follow `docs/phase-1-mvp.md`.
3. Do not create files in random locations.
4. Do not create business packages directly under the root backend package.
5. Place all backend business code under `module/`.
6. Place all frontend pages under `web/src/views/`.
7. Place all frontend API functions under `web/src/api/`.
8. If a new directory is necessary, explain the reason first.
9. Keep Phase 1 simple and runnable.
10. Prioritize stable implementation over over-engineering.

---

## Summary

The core structure rule is:

```text
Root only manages the repository.
backend contains all backend code.
web contains all frontend code.
docs contains all documents.
Business backend code must go under module/.
```