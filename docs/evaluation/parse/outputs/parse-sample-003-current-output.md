# parse-sample-003 当前输出记录

## 运行方式

本记录为 v2.9.7 的人工评估基线，基于 v2.9.2-v2.9.6 已落地规则对样例文本进行预期输出记录；未调用真实上传接口，不包含真实附件。

## 当前输出摘要

| 字段 | 当前结果 |
|---|---|
| textQualityStatus | WARNING |
| textQualityIssues | TOO_SHORT_TEXT |
| parseQualityStatus | WARNING |
| 主要警告 | `SECTION_TOO_FEW`、`TEXT_QUALITY_WARNING`，可能还有 `PROJECTS_MISSING` |

## 命中字段

- 可命中 Python、SQL。
- 可保留 `销售数据分析项目` 原文供用户检查。

## 漏识别字段

- Pandas 当前不在基础技能关键词列表中，可能漏识别。
- 教育经历、实习经历、项目描述均缺失。

## 错误分类字段

- `销售数据分析项目` 可能无法稳定形成完整项目经历，只能作为普通行或简短项目线索。

## 文本提取问题

- DOC 文本明显过短。
- 章节标题缺失。

## 结论

当前规则可以明确提示文本过短，避免用户误以为解析完整；后续需要补充技能词表和项目描述完整性校验。
