# 简历解析回归评估

本目录用于保存固定的简历解析回归样例、人工期望结果、每次解析实际输出和评估报告。每次修改解析规则、AI 分类策略、缓存策略或前端展示前后，都应使用同一批样例进行对比，避免只靠页面肉眼判断。

## 目录结构

```text
docs/evaluation/parse/regression/
├── README.md
├── samples/
├── expected-results/
├── outputs/
├── reports/
└── private-inputs/
```

目录说明：

- `samples/`：提交到 Git 的样例说明文件，只写脱敏后的样本特点和验证重点。
- `expected-results/`：提交到 Git 的人工标注期望结果，只标注关键字段和关键约束。
- `outputs/`：保存每次解析后的 actual output，用于和 expected 对比。
- `reports/`：保存回归报告和汇总记录。
- `private-inputs/`：本地保存真实 DOCX / PDF 样本，不提交 Git。

## 隐私规则

真实简历文件不得提交到 Git。真实 DOCX / PDF 只放入：

```text
docs/evaluation/parse/regression/private-inputs/
```

该目录已在 `.gitignore` 中忽略。需要提交样本信息时，只提交脱敏后的 Markdown 说明和关键字段期望结果。

## 样例命名规则

样例统一使用 `sample-三位序号-样本主题-文件类型`：

```text
sample-001-java-intern-docx
sample-002-javaee-engineer-2y-docx
sample-003-javaee-engineer-2y-pdf
sample-004-javaee-developer-2y-docx
sample-005-javaee-engineer-2y-pdf
```

对应文件命名：

```text
samples/sample-001-java-intern-docx.md
expected-results/sample-001-expected.json
outputs/sample-001-fast-output.json
outputs/sample-001-balanced-output.json
outputs/sample-001-accurate-output.json
```

如果本地存在真实输入文件，建议命名为：

```text
private-inputs/sample-001-java-intern.docx
private-inputs/sample-002-javaee-engineer-2y.docx
private-inputs/sample-003-javaee-engineer-2y.pdf
private-inputs/sample-004-javaee-developer-2y.docx
private-inputs/sample-005-javaee-engineer-2y.pdf
```

## expected 与 actual 的关系

`expected-results/` 保存人工标注的期望结果，表示“这个样例应该被解析成什么样”。它不需要覆盖简历全文，只标注当前阶段关心的关键字段、必须包含内容、禁止出现内容和质量约束。

`outputs/` 保存实际解析输出，表示某个 `parserVersion`、`parseMode` 和代码版本下的解析结果。一次样例通常至少保存三种模式输出：

```text
FAST
BALANCED
ACCURATE
```

对比时以 expected 为基准检查 actual 是否命中关键字段、是否出现 forbidden values、是否残留纯序号、是否 others 过多、是否发生降级，以及耗时是否在阈值内。

## 回归评估要求

每次优化解析逻辑后，需要重新运行固定样例，并把 actual output 和 report 保存到本目录：

```text
outputs/
reports/regression-report-YYYY-MM-DD.md
```

报告至少记录：

- 本次代码版本或 parserVersion。
- 本次测试样例数量。
- 每个样例在 FAST / BALANCED / ACCURATE 下的结果。
- basicInfo 命中情况。
- section 分类命中情况。
- skills 命中情况。
- others 数量和纯序号残留情况。
- 是否降级。
- 总耗时和 AI 耗时。
- 相比上一次是否提升或回退。

## 半自动评估工具

本目录提供轻量评估脚本：

```bash
python docs/evaluation/parse/regression/evaluate_regression.py
```

默认读取：

```text
expected-results/*.json
outputs/*.json
```

默认输出：

```text
reports/regression-report-latest.md
```

脚本会按 `sample-001` 这类编号匹配 expected 与 actual，统计 basicInfo 命中、章节关键内容命中、纯序号残留、others 数量、重复警告、耗时、cacheHit 和 fallbackOccurred。若当前没有对应 actual output，报告会标记为 `MISSING_ACTUAL`。
