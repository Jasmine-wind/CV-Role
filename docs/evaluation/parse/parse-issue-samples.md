# 简历解析问题样例记录

## 说明

本文档用于记录 v2.9.1 的当前解析问题样例，为后续文本提取质量检查、文本清洗、章节识别和基础结构化字段解析优化提供依据。

样例均为虚构内容或基于现有虚构评估样例改写，不包含真实姓名、手机号、邮箱、身份证号、住址、学号、真实学校或真实公司信息。本文档只记录问题形态，不提交真实简历附件。

## 问题分类

| 问题类型 | 说明 |
|---|---|
| EMPTY_TEXT | 文本提取为空 |
| TOO_SHORT_TEXT | 文本过短，明显不完整 |
| ORDER_MESSY | 文本顺序混乱 |
| TABLE_LOST | 表格内容丢失 |
| BULLET_MESSY | 项目符号或列表混乱 |
| SECTION_MISSING | 章节标题缺失 |
| SECTION_WRONG | 章节识别错误 |
| FIELD_WRONG | 结构化字段识别错误 |
| SCANNED_PDF | 疑似扫描版 PDF |

## 样例汇总

| 样例编号 | 模拟原始文件 | 文件类型 | 问题层级 | 问题类型 | 后续优化方向 |
|---|---|---|---|---|---|
| parse-sample-001 | Java 后端实习简历 PDF | PDF | 文本提取层 + 结构化解析层 | ORDER_MESSY、BULLET_MESSY、FIELD_WRONG | 文本清洗、项目符号规范、章节识别 |
| parse-sample-002 | AI 应用开发简历 DOCX | DOCX | 结构化解析层 | TABLE_LOST、SECTION_WRONG、FIELD_WRONG | 表格文本展开、章节标题归一化 |
| parse-sample-003 | 数据分析实习简历 DOC | DOC | 文本提取层 | TOO_SHORT_TEXT、SECTION_MISSING | 文本质量检查、低质量解析提示 |
| parse-sample-004 | 有工作经验 Java 后端简历 DOCX | DOCX | 结构化解析层 + 前端展示层 | DUPLICATE_CONTENT、SECTION_WRONG、FIELD_WRONG | 去重、归属控制、技能白名单、前端默认展示收敛 |

---

## parse-sample-001 PDF 简历文本顺序与项目符号混乱

### 样例元信息

| 字段 | 内容 |
|---|---|
| 模拟原始文件 | `parse-sample-001-java-backend.pdf` |
| 文件类型 | PDF |
| 样例来源 | 基于 `docs/evaluation/resumes/resume-sample-001.md` 虚构改写 |
| 候选方向 | Java 后端开发实习 |
| 是否包含真实隐私 | 否 |

### 当前系统提取文本

```text
项目经历
校园二手交易平台
Spring Boot MyBatis-Plus MySQL Redis JWT
完成 20 个左右后端接口
使用 Redis 缓存首页热门商品列表
技能
Java Spring Boot MyBatis-Plus Spring Security
教育经历
示例理工大学 软件工程 2023.09 - 2027.06
负责用户注册登录 商品发布 商品搜索 收藏接口开发
使用 JWT 完成登录态校验
简易博客系统
文章发布 编辑 删除 分类 评论
获奖经历 校级程序设计竞赛三等奖
```

### 当前结构化解析结果

```json
{
  "education": [
    {
      "school": "示例理工大学",
      "major": "软件工程",
      "period": "2023.09 - 2027.06"
    }
  ],
  "skills": [
    "Java",
    "Spring Boot",
    "MyBatis-Plus",
    "Spring Security",
    "校园二手交易平台"
  ],
  "projects": [
    {
      "name": "校园二手交易平台",
      "description": "Spring Boot MyBatis-Plus MySQL Redis JWT 完成 20 个左右后端接口 使用 Redis 缓存首页热门商品列表"
    },
    {
      "name": "简易博客系统",
      "description": "文章发布 编辑 删除 分类 评论 获奖经历 校级程序设计竞赛三等奖"
    }
  ],
  "internships": [],
  "awards": []
}
```

### 问题标记

| 检查项 | 结果 |
|---|---|
| 文本提取是否为空 | 否 |
| 文本提取是否过短 | 否 |
| 文本顺序是否混乱 | 是 |
| 表格内容是否丢失 | 否 |
| 项目符号是否异常 | 是 |
| 章节标题是否识别错误 | 部分错误 |
| 字段拆分是否错误 | 是 |

### 问题归类

- 文本提取层：`ORDER_MESSY`、`BULLET_MESSY`
- 结构化解析层：`FIELD_WRONG`

### 具体问题

- PDF 提取文本中项目职责、技能和项目成果混在一起，导致项目描述缺少层次。
- 项目符号消失后，职责和成果被合并为长句，后续 AI 难以判断“做了什么”和“结果是什么”。
- `校园二手交易平台` 被错误放入 `skills`。
- `获奖经历` 被拼接到 `简易博客系统` 的项目描述里，导致 `awards` 为空。

### 后续优化依据

- v2.9.3 需要规范项目符号和连续短行。
- v2.9.3 需要识别 `项目经历`、`技能`、`教育经历`、`获奖经历` 等章节边界。
- v2.9.4 解析项目经历时应避免把项目名称写入技能字段。

---

## parse-sample-002 DOCX 表格型技能区丢失结构

### 样例元信息

| 字段 | 内容 |
|---|---|
| 模拟原始文件 | `parse-sample-002-ai-application.docx` |
| 文件类型 | DOCX |
| 样例来源 | 基于 `docs/evaluation/resumes/resume-sample-002.md` 虚构改写 |
| 候选方向 | AI 应用开发实习 |
| 是否包含真实隐私 | 否 |

### 当前系统提取文本

```text
个人信息
AI 应用开发方向 本科在读
技术能力
编程语言 Python JavaScript
框架工具 FastAPI Vue LangChain
模型与数据 Prompt Engineering RAG 向量检索
项目经历
智能知识库问答系统
负责文档上传 文本切分 向量化 检索增强问答
使用 FastAPI 提供接口 Vue 实现前端页面
校园课程助手
接入大模型 API 生成课程问答和复习提纲
```

### 当前结构化解析结果

```json
{
  "education": [],
  "skills": [
    "Python",
    "JavaScript",
    "FastAPI",
    "Vue",
    "LangChain",
    "Prompt Engineering RAG 向量检索 项目经历 智能知识库问答系统"
  ],
  "projects": [
    {
      "name": "校园课程助手",
      "description": "接入大模型 API 生成课程问答和复习提纲"
    }
  ],
  "internships": [],
  "awards": []
}
```

### 问题标记

| 检查项 | 结果 |
|---|---|
| 文本提取是否为空 | 否 |
| 文本提取是否过短 | 否 |
| 文本顺序是否混乱 | 否 |
| 表格内容是否丢失 | 是 |
| 项目符号是否异常 | 否 |
| 章节标题是否识别错误 | 是 |
| 字段拆分是否错误 | 是 |

### 问题归类

- 文本提取层：`TABLE_LOST`
- 结构化解析层：`SECTION_WRONG`、`FIELD_WRONG`

### 具体问题

- DOCX 表格中的技能分类被展平成普通文本，分类关系丢失。
- `技术能力` 未被稳定归一到 `SKILLS` 章节。
- `Prompt Engineering RAG 向量检索 项目经历 智能知识库问答系统` 被合并为单个技能项。
- 第一个项目 `智能知识库问答系统` 没有被识别为项目，导致项目经历缺失。
- 教育经历在该样例中没有明显章节，当前解析结果直接为空，需要质量提示。

### 后续优化依据

- v2.9.3 需要将 `技术能力`、`技术栈`、`专业技能` 归一为技能章节。
- v2.9.3 需要识别被表格展开后的连续技能行。
- v2.9.4 项目解析应识别章节标题后的首个项目名称。

---

## parse-sample-003 DOC 文本过短且章节缺失

### 样例元信息

| 字段 | 内容 |
|---|---|
| 模拟原始文件 | `parse-sample-003-data-analysis.doc` |
| 文件类型 | DOC |
| 样例来源 | 基于 `docs/evaluation/resumes/resume-sample-003.md` 虚构改写 |
| 候选方向 | 数据分析或算法实习 |
| 是否包含真实隐私 | 否 |

### 当前系统提取文本

```text
数据分析实习方向
Python SQL Pandas
销售数据分析项目
```

### 当前结构化解析结果

```json
{
  "education": [],
  "skills": [
    "Python",
    "SQL",
    "Pandas"
  ],
  "projects": [
    {
      "name": "销售数据分析项目",
      "description": ""
    }
  ],
  "internships": [],
  "awards": []
}
```

### 问题标记

| 检查项 | 结果 |
|---|---|
| 文本提取是否为空 | 否 |
| 文本提取是否过短 | 是 |
| 文本顺序是否混乱 | 未知 |
| 表格内容是否丢失 | 未知 |
| 项目符号是否异常 | 未知 |
| 章节标题是否识别错误 | 是 |
| 字段拆分是否错误 | 是 |

### 问题归类

- 文本提取层：`TOO_SHORT_TEXT`
- 结构化解析层：`SECTION_MISSING`、`FIELD_WRONG`

### 具体问题

- DOC 文件提取文本明显过短，缺少教育经历、项目细节、实习经历和成果描述。
- 当前系统仍继续生成结构化结果，可能让用户误以为解析完整。
- `销售数据分析项目` 被识别为项目名称，但描述为空，后续 AI 分析和向量生成依据不足。
- 该场景需要先给出质量警告，而不是直接进入完整解析链路。

### 后续优化依据

- v2.9.2 需要新增文本长度阈值和质量状态。
- v2.9.2 需要对 `TOO_SHORT_TEXT` 生成用户可理解的提示。
- v2.9.4 项目描述为空时应标记字段质量不足，而不是当作完整项目经历。

---

## parse-sample-004 有工作经验简历重复归属与技能污染

### 样例元信息

| 字段 | 内容 |
|---|---|
| 模拟原始文件 | `parse-sample-004-experienced-java.docx` |
| 文件类型 | DOCX |
| 样例来源 | 基于当前 v2.9 复杂解析问题虚构改写 |
| 候选方向 | Java 后端开发工程师 |
| 是否包含真实隐私 | 否 |

### 当前系统提取文本

```text
王某
男 28岁 上海
手机号：13800000000
邮箱：sample@example.com
求职意向：Java 后端开发工程师
教育背景
示例大学 软件工程 本科
技术栈
熟悉 Spring Boot、SpringMVC、MyBatis 的设计思想及实现过程
Java / Spring Boot / MySQL / Redis / RabbitMQ
工作经历
示例科技有限公司 Java 后端工程师 2021.07 - 至今
负责订单系统、支付系统、报表模块的接口开发和性能优化
项目经历
订单中台系统
使用 Spring Boot、MyBatis、Redis 完成核心交易接口开发
自我评价
学习能力强，工作认真，沟通协作良好
```

### 修复前问题形态

```json
{
  "skills": [
    "Java",
    "Spring Boot",
    "熟悉 Spring Boot、SpringMVC、MyBatis 的设计思想及实现过程",
    "负责订单系统、支付系统、报表模块的接口开发和性能优化"
  ],
  "workExperiences": [
    "示例科技有限公司 Java 后端工程师 2021.07 - 至今",
    "负责订单系统、支付系统、报表模块的接口开发和性能优化"
  ],
  "projects": [
    "订单中台系统",
    "使用 Spring Boot、MyBatis、Redis 完成核心交易接口开发"
  ],
  "others": [
    "熟悉 Spring Boot、SpringMVC、MyBatis 的设计思想及实现过程",
    "负责订单系统、支付系统、报表模块的接口开发和性能优化",
    "学习能力强，工作认真，沟通协作良好"
  ]
}
```

### 问题标记

| 检查项 | 结果 |
|---|---|
| 页面内容是否重复 | 是 |
| 章节标题是否误判 | 是 |
| 技能字段是否混入整句 | 是 |
| 教育经历和技术能力是否混在一起 | 是 |
| 有工作经验简历是否被当作实习简历 | 是 |
| rawText / cleanedText 是否默认大面积展示 | 是 |

### 问题归类

- 文本清洗层：`DUPLICATE_CONTENT`
- 章节识别层：`SECTION_WRONG`
- 结构化解析层：`FIELD_WRONG`
- 前端展示层：`DEBUG_INFO_OVEREXPOSED`

### 修复方向

- 对清洗文本行做 normalize 后去重。
- 章节标题必须满足短行、无手机号、无邮箱、无日期区间、不是完整描述句等条件。
- 个人信息从全文前若干行和全文内容中全局抽取，不只依赖 `BASIC_INFO` 章节。
- 结构化字段按章节归属，已归入教育、技能、工作、项目、证书、自我评价等字段的内容不再进入 `others`。
- 技能只输出白名单技术词、工具、框架、数据库、中间件、语言和平台，不输出整句描述。
- 简历类型使用 `STUDENT`、`INTERN`、`EXPERIENCED`、`UNKNOWN`，有工作经验简历优先识别为 `EXPERIENCED`。
- 前端默认只展示结构化结果，`rawText`、`cleanedText` 和 `sectionResult` 放入折叠的调试信息。

### v2.9.14 回归记录

- DOCX 文本块已记录 `originalIndex` 和 `displayOrder`，解析展示不再完全依赖原始 XML 顺序。
- 已支持 `个人技能 Technique`、`自我评价 About me`、`在校经历 Experience`、`教育背景 Education` 等“内容在前、标题在后”的向前挂载。
- 基础信息已改为全文扫描，可从文本末尾识别姓名、电话、邮箱、学历和求职意向。
- AI 章节归类已支持批量分批、Prompt v2 精简输出、内存缓存和 JSON 容错解析；失败时降级为规则章节识别。
- 前端默认展示结构化结果，`rawText`、`cleanedText`、blocks 和 AI classifiedBlocks 均放入折叠调试区。
- 用户已在前端验证复杂 DOCX 样例，当前核心字段和章节展示基本无问题。
- 当前仍不做 OCR、视觉版面还原或模型微调。

---

## v2.9.1 结论

- 当前样例覆盖 PDF、DOCX、DOC 三种简历来源。
- 文本提取层问题主要集中在文本顺序混乱、项目符号丢失、表格结构丢失和文本过短。
- 结构化解析层问题主要集中在章节标题归一化不足、字段混入、项目经历漏识别和空字段未提示。
- 后续 v2.9.2 应优先增加文本质量检查，避免空文本、过短文本或疑似扫描版文件继续进入后续解析链路。
- 后续 v2.9.3 应优先做文本清洗、项目符号规范化、章节标题归一和基础章节切分。
- 后续 v2.9.4 应基于章节结果优化教育经历、技能、项目经历和实习经历字段解析。
