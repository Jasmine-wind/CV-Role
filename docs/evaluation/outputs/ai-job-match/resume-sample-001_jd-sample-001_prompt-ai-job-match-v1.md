# resume-sample-001_jd-sample-001_prompt-ai-job-match-v1

## metadata

| 字段 | 值 |
|---|---|
| sampleResumeId | resume-sample-001 |
| sampleJobDescriptionId | jd-sample-001 |
| promptName | ai-job-match |
| promptVersion | ai_job_match_v1 |
| modelName | deepseek-v4-flash |
| outputTime | 2026-05-16 00:00:00 |
| parseSuccess | true |
| recordType | evaluation-output |

## rawOutput

```json
{"overallScore":82,"strongMatches":[{"item":"Java 后端项目","reason":"简历有 Spring Boot 项目经历"},{"item":"接口开发","reason":"简历提到约 20 个后端接口"},{"item":"JWT 权限","reason":"简历和岗位均关注权限校验"}],"weakMatches":[{"item":"接口排查","reason":"简历未突出测试环境排错经历"},{"item":"单元测试","reason":"岗位提到基础测试，简历未体现"}],"missingSkills":[{"item":"Linux","reason":"岗位加分项相关，简历未体现"}],"weakExperienceDescriptions":[{"section":"项目经历","issue":"项目成果缺少量化说明"},{"section":"技能","issue":"Redis 和 Docker 使用深度不清"}],"evidence":[{"source":"resume","content":"负责注册登录和商品接口开发"},{"source":"resume","content":"使用 Redis 缓存首页热门商品"},{"source":"job","content":"岗位要求 RESTful API 和 SQL"}],"riskNotes":["不得补写未实际掌握的消息队列或高并发经验"]}
```

## structuredOutput

```json
{
  "overallScore": 82,
  "strongMatches": [
    {
      "item": "Java 后端项目",
      "reason": "简历有 Spring Boot 项目经历"
    },
    {
      "item": "接口开发",
      "reason": "简历提到约 20 个后端接口"
    },
    {
      "item": "JWT 权限",
      "reason": "简历和岗位均关注权限校验"
    }
  ],
  "weakMatches": [
    {
      "item": "接口排查",
      "reason": "简历未突出测试环境排错经历"
    },
    {
      "item": "单元测试",
      "reason": "岗位提到基础测试，简历未体现"
    }
  ],
  "missingSkills": [
    {
      "item": "Linux",
      "reason": "岗位加分项相关，简历未体现"
    }
  ],
  "weakExperienceDescriptions": [
    {
      "section": "项目经历",
      "issue": "项目成果缺少量化说明"
    },
    {
      "section": "技能",
      "issue": "Redis 和 Docker 使用深度不清"
    }
  ],
  "evidence": [
    {
      "source": "resume",
      "content": "负责注册登录和商品接口开发"
    },
    {
      "source": "resume",
      "content": "使用 Redis 缓存首页热门商品"
    },
    {
      "source": "job",
      "content": "岗位要求 RESTful API 和 SQL"
    }
  ],
  "riskNotes": [
    "不得补写未实际掌握的消息队列或高并发经验"
  ]
}
```

## notes

- 输出能对应简历样例和岗位样例。
- 风险提示能约束不真实包装。
