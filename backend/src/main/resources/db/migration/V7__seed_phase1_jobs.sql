INSERT INTO jobs (
    title,
    company_name,
    job_category,
    location,
    description,
    requirements,
    required_skills,
    status
) VALUES
(
    'Java 后端开发工程师',
    '星河软件',
    'Java 后端',
    '成都',
    '参与业务系统后端接口开发、数据库设计和服务端问题排查，配合前端完成核心业务流程联调。',
    '熟悉 Java、Spring Boot、MyBatis 或 MyBatis-Plus，了解 PostgreSQL 或 MySQL，具备 RESTful API 开发经验，能使用 Git 和 Maven 完成日常开发。',
    '["Java","Spring Boot","MyBatis-Plus","PostgreSQL","RESTful","Git","Maven"]',
    'ENABLED'
),
(
    'AI 应用开发工程师',
    '青杉智能',
    'AI 应用开发',
    '杭州',
    '负责 AI 应用后端能力建设，完成模型接口接入、Prompt 编排、业务数据处理和应用功能落地。',
    '熟悉 Java 或 Python，了解 OpenAI 兼容 API 调用方式，具备 Spring Boot、HTTP API、JSON 数据处理和基础 Prompt 设计经验。',
    '["Java","Python","Spring Boot","OpenAI API","Prompt","JSON","HTTP"]',
    'ENABLED'
),
(
    '前端开发工程师',
    '云桥科技',
    '前端开发',
    '上海',
    '负责 Web 前端页面开发、接口联调和基础交互优化，参与前后端分离项目的功能迭代。',
    '熟悉 Vue 3、TypeScript、Vite 和 Axios，了解 Pinia、Vue Router 和 Element Plus，能根据接口文档完成页面开发。',
    '["Vue 3","TypeScript","Vite","Axios","Pinia","Vue Router","Element Plus"]',
    'ENABLED'
),
(
    '数据分析助理',
    '启明数据',
    '数据分析',
    '深圳',
    '协助完成业务数据整理、指标分析、报表制作和基础数据洞察，为业务复盘提供数据支持。',
    '熟悉 SQL、Excel，了解 Python 数据处理，具备基础统计分析能力，能够清晰整理分析结论。',
    '["SQL","Excel","Python","数据分析","统计分析"]',
    'ENABLED'
),
(
    '软件测试工程师',
    '北辰系统',
    '软件测试',
    '北京',
    '参与 Web 系统功能测试、接口测试、缺陷跟踪和回归验证，保障版本交付质量。',
    '熟悉测试用例设计、接口测试和缺陷管理，了解 Postman、SQL、Linux 基础命令，有自动化测试经验优先。',
    '["测试用例","接口测试","Postman","SQL","Linux","自动化测试"]',
    'ENABLED'
)
ON CONFLICT (title, company_name) DO UPDATE SET
    job_category = EXCLUDED.job_category,
    location = EXCLUDED.location,
    description = EXCLUDED.description,
    requirements = EXCLUDED.requirements,
    required_skills = EXCLUDED.required_skills,
    status = EXCLUDED.status,
    updated_at = CURRENT_TIMESTAMP;
