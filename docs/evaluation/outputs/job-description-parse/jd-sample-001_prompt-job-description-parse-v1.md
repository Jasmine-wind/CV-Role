# jd-sample-001_prompt-job-description-parse-v1

## metadata

| 字段 | 值 |
|---|---|
| sampleResumeId | 无 |
| sampleJobDescriptionId | jd-sample-001 |
| promptName | job-description-parse |
| promptVersion | job_description_parse_v1 |
| modelName | deepseek-v4-flash |
| outputTime | 2026-05-16 00:00:00 |
| parseSuccess | true |
| recordType | evaluation-output |

## rawOutput

```json
{"jobTitle":"Java 后端开发实习生","companyName":"","requiredSkills":["Java","Spring Boot","MyBatis 或 MyBatis-Plus","MySQL 或 PostgreSQL","RESTful API","JSON","JWT","Git","Maven","Postman"],"responsibilities":["参与业务系统后端接口开发","完成 RESTful API 开发、参数校验、异常处理和统一返回","参与数据库表设计、SQL 编写和接口联调","协助排查测试环境接口错误、数据异常和权限校验问题","按团队规范提交代码并参与 Code Review"],"keywords":["Java","Spring Boot","MyBatis-Plus","SQL","JWT","Redis","Git","Maven","Postman"],"summary":"该岗位面向 Java 后端开发实习，重点关注 Spring Boot 分层开发、数据库基础、接口联调和权限校验能力。"}
```

## structuredOutput

```json
{
  "jobTitle": "Java 后端开发实习生",
  "companyName": "",
  "requiredSkills": [
    "Java",
    "Spring Boot",
    "MyBatis 或 MyBatis-Plus",
    "MySQL 或 PostgreSQL",
    "RESTful API",
    "JSON",
    "JWT",
    "Git",
    "Maven",
    "Postman"
  ],
  "responsibilities": [
    "参与业务系统后端接口开发",
    "完成 RESTful API 开发、参数校验、异常处理和统一返回",
    "参与数据库表设计、SQL 编写和接口联调",
    "协助排查测试环境接口错误、数据异常和权限校验问题",
    "按团队规范提交代码并参与 Code Review"
  ],
  "keywords": [
    "Java",
    "Spring Boot",
    "MyBatis-Plus",
    "SQL",
    "JWT",
    "Redis",
    "Git",
    "Maven",
    "Postman"
  ],
  "summary": "该岗位面向 Java 后端开发实习，重点关注 Spring Boot 分层开发、数据库基础、接口联调和权限校验能力。"
}
```

## notes

- 输出能覆盖岗位职责、技能关键词和岗位摘要。
- `companyName` 在样例 JD 中不存在，因此保持为空字符串。
- 未包含真实招聘方信息。
