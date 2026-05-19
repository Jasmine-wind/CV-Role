# parse-sample-002 期望解析结果

```json
{
  "textQualityStatus": "GOOD",
  "parseQualityStatus": "WARNING",
  "expectedWarnings": [
    "PROJECTS_MISSING only if first project still missed",
    "SECTION_TOO_FEW if 技术能力未归一"
  ],
  "structured": {
    "education": [],
    "skills": [
      "Python",
      "JavaScript",
      "FastAPI",
      "Vue",
      "LangChain",
      "Prompt Engineering",
      "RAG",
      "向量检索"
    ],
    "projects": [
      "智能知识库问答系统",
      "校园课程助手"
    ],
    "internships": [],
    "awards": []
  }
}
```

## 判定规则

- `技术能力` 应视为技能章节。
- `智能知识库问答系统` 和 `校园课程助手` 都应进入项目经历。
- 教育经历缺失可以为空，但应由质量提示解释。
