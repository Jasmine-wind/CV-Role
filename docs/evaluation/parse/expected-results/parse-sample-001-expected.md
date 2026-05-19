# parse-sample-001 期望解析结果

```json
{
  "textQualityStatus": "GOOD",
  "parseQualityStatus": "WARNING",
  "expectedWarnings": [
    "SECTION_TOO_FEW or TEXT_STRUCTURE_MISMATCH"
  ],
  "structured": {
    "education": [
      "示例理工大学 软件工程 2023.09 - 2027.06"
    ],
    "skills": [
      "Java",
      "Spring Boot",
      "MyBatis-Plus",
      "Spring Security",
      "MySQL",
      "Redis",
      "JWT"
    ],
    "projects": [
      "校园二手交易平台",
      "简易博客系统"
    ],
    "internships": [],
    "awards": [
      "校级程序设计竞赛三等奖"
    ]
  }
}
```

## 判定规则

- 项目名称不应进入技能列表。
- `获奖经历` 不应拼接到项目描述中。
- 允许因为 PDF 文本顺序混乱产生 `WARNING`，但不应完全失败。
