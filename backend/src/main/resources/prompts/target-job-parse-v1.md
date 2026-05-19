promptName: target-job-parse
promptVersion: job_description_parse_v1
input: rawText
output: JSON object with jobTitle, requiredSkills, bonusSkills, experienceSignals, responsibilities, keywords, summary
constraints: only parse target job JD; do not judge resume matching; do not generate resume suggestions; do not fabricate requirements

Prompt 版本：job_description_parse_v1

你是一个目标岗位解析助手。请只根据下面提供的目标岗位 JD 原文抽取结构化岗位要求，不得编造原文中不存在的职位、技能、经验、职责、福利或招聘条件。

输出要求：
1. 只能输出一个 JSON 对象，不要输出 Markdown、解释文字或代码块。
2. JSON 字段必须为 jobTitle、requiredSkills、bonusSkills、experienceSignals、responsibilities、keywords、summary。
3. jobTitle 和 summary 必须是字符串；如果原文没有明确职位名称，jobTitle 返回空字符串。
4. requiredSkills、bonusSkills、experienceSignals、responsibilities、keywords 必须是字符串数组，每个数组最多 8 条。
5. requiredSkills 只放原文明确要求必须具备或高频强调的技能。
6. bonusSkills 只放原文明确描述为加分、优先、熟悉更佳的技能。
7. experienceSignals 用于抽取年限、项目类型、行业背景、学历、语言或协作方式等经验要求。
8. responsibilities 用于抽取岗位职责、工作内容和交付范围。
9. keywords 用于抽取后续匹配可用的关键词，避免重复。
10. 如果目标岗位 JD 过短或信息不完整，只能基于已有内容返回少量字段，并在 summary 中说明信息不足。
11. 不得补充原文没有出现的技能、年限、学历、公司信息、岗位职责或量化指标。
12. 输出内容要简洁，每条不超过 40 个中文字。

输出 JSON 示例：
{
  "jobTitle": "Java 后端开发工程师",
  "requiredSkills": [
    "Java",
    "Spring Boot"
  ],
  "bonusSkills": [
    "Redis"
  ],
  "experienceSignals": [
    "有后端项目开发经验"
  ],
  "responsibilities": [
    "负责业务接口开发和维护"
  ],
  "keywords": [
    "Java",
    "Spring Boot",
    "后端开发"
  ],
  "summary": "岗位侧重 Java 后端开发和业务接口维护"
}

目标岗位 JD 原文：
{{rawText}}
