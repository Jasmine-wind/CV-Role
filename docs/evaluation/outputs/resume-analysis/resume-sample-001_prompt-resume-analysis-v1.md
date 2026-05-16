# resume-sample-001_prompt-resume-analysis-v1

## metadata

| 字段 | 值 |
|---|---|
| sampleResumeId | resume-sample-001 |
| sampleJobDescriptionId | 无 |
| promptName | resume-analysis |
| promptVersion | resume_analysis_v1 |
| modelName | deepseek-v4-flash |
| outputTime | 2026-05-16 00:00:00 |
| parseSuccess | true |
| recordType | evaluation-output |

## rawOutput

```json
{"score":78,"strengths":["Java 后端基础较完整","有 Spring Boot 项目经历","项目职责描述较清晰"],"problems":["项目成果量化不足","缺少真实实习经历","Redis 和 Docker 说明偏基础"],"suggestionsSummary":["补充接口数量和模块职责","说明权限校验和缓存使用场景","只补充真实可验证的数据"]}
```

## structuredOutput

```json
{
  "score": 78,
  "strengths": [
    "Java 后端基础较完整",
    "有 Spring Boot 项目经历",
    "项目职责描述较清晰"
  ],
  "problems": [
    "项目成果量化不足",
    "缺少真实实习经历",
    "Redis 和 Docker 说明偏基础"
  ],
  "suggestionsSummary": [
    "补充接口数量和模块职责",
    "说明权限校验和缓存使用场景",
    "只补充真实可验证的数据"
  ]
}
```

## notes

- 输出未进行岗位匹配，只评价简历完整度和表达质量。
- 建议保持事实边界，没有代填不存在的量化指标。
