# resume-sample-001_jd-sample-001_prompt-resume-suggestion-v1

## metadata

| 字段 | 值 |
|---|---|
| sampleResumeId | resume-sample-001 |
| sampleJobDescriptionId | jd-sample-001 |
| promptName | resume-suggestion |
| promptVersion | resume_suggestion_v1 |
| modelName | deepseek-v4-flash |
| outputTime | 2026-05-16 00:00:00 |
| parseSuccess | true |
| recordType | evaluation-output |

## rawOutput

```json
{"suggestions":[{"type":"PROJECT_DESCRIPTION","priority":"HIGH","targetSection":"项目经历","issue":"项目成果量化不足","suggestion":"建议补充真实可验证的接口数量、负责模块、缓存使用场景和联调结果，让项目价值更清晰。","evidence":["简历提到完成 20 个左右后端接口","岗位关注接口开发、数据库和联调"],"caution":"不要代填用户量、性能提升比例或不存在的业务规模。","relatedItems":["接口开发","项目成果"]},{"type":"EXPERIENCE_WEAKNESS","priority":"MEDIUM","targetSection":"项目经历","issue":"排错和测试经历不突出","suggestion":"如果确实做过接口错误排查、参数校验或测试环境问题定位，可在项目经历中补充真实案例。","evidence":["岗位要求协助排查接口错误和数据异常","简历当前未突出排错经历"],"caution":"没有实际排查经历时不要强行补写。","relatedItems":["接口排查","联调"]},{"type":"HIGHLIGHT_STRENGTH","priority":"MEDIUM","targetSection":"技能","issue":"JWT 和 Redis 使用场景可以更聚焦","suggestion":"建议在技能或项目中说明 JWT 用户归属校验、Redis 热门商品缓存等真实应用场景。","evidence":["简历提到 JWT 登录态校验","简历提到 Redis 缓存首页热门商品"],"caution":"只描述实际完成的功能边界。","relatedItems":["JWT","Redis"]}]}
```

## structuredOutput

```json
{
  "suggestions": [
    {
      "type": "PROJECT_DESCRIPTION",
      "priority": "HIGH",
      "targetSection": "项目经历",
      "issue": "项目成果量化不足",
      "suggestion": "建议补充真实可验证的接口数量、负责模块、缓存使用场景和联调结果，让项目价值更清晰。",
      "evidence": [
        "简历提到完成 20 个左右后端接口",
        "岗位关注接口开发、数据库和联调"
      ],
      "caution": "不要代填用户量、性能提升比例或不存在的业务规模。",
      "relatedItems": [
        "接口开发",
        "项目成果"
      ]
    },
    {
      "type": "EXPERIENCE_WEAKNESS",
      "priority": "MEDIUM",
      "targetSection": "项目经历",
      "issue": "排错和测试经历不突出",
      "suggestion": "如果确实做过接口错误排查、参数校验或测试环境问题定位，可在项目经历中补充真实案例。",
      "evidence": [
        "岗位要求协助排查接口错误和数据异常",
        "简历当前未突出排错经历"
      ],
      "caution": "没有实际排查经历时不要强行补写。",
      "relatedItems": [
        "接口排查",
        "联调"
      ]
    },
    {
      "type": "HIGHLIGHT_STRENGTH",
      "priority": "MEDIUM",
      "targetSection": "技能",
      "issue": "JWT 和 Redis 使用场景可以更聚焦",
      "suggestion": "建议在技能或项目中说明 JWT 用户归属校验、Redis 热门商品缓存等真实应用场景。",
      "evidence": [
        "简历提到 JWT 登录态校验",
        "简历提到 Redis 缓存首页热门商品"
      ],
      "caution": "只描述实际完成的功能边界。",
      "relatedItems": [
        "JWT",
        "Redis"
      ]
    }
  ]
}
```

## notes

- 输出不生成完整简历，只给优化建议。
- 每条建议包含依据和 caution。
