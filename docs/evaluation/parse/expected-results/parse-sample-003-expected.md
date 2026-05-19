# parse-sample-003 期望解析结果

```json
{
  "textQualityStatus": "WARNING",
  "textQualityIssues": [
    "TOO_SHORT_TEXT"
  ],
  "parseQualityStatus": "WARNING",
  "expectedWarnings": [
    "SECTION_TOO_FEW",
    "TEXT_QUALITY_WARNING"
  ],
  "structured": {
    "education": [],
    "skills": [
      "Python",
      "SQL",
      "Pandas"
    ],
    "projects": [
      "销售数据分析项目"
    ],
    "internships": [],
    "awards": []
  }
}
```

## 判定规则

- 文本过短必须有用户可见提示。
- 可以保留有限解析结果，但不能把结果当作完整简历。
- 后续 AI 分析前应提醒用户检查解析结果。
