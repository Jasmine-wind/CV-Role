# AI 输出评估集说明

本目录用于记录 Phase 3 v2.7 的轻量 AI 输出评估材料，帮助对比 Prompt 修改前后的输出质量。

v2.7 只建立小型、可维护的人工评估流程，不做自动打分系统、模型微调、大规模评测平台或复杂 A/B 测试。

## 目录结构

```text
docs/evaluation/
├── README.md
├── resumes/
├── job-descriptions/
├── outputs/
│   ├── resume-analysis/
│   ├── job-description-parse/
│   ├── ai-job-match/
│   ├── ai-suggestions/
│   └── rewrite-suggestions/
├── reports/
└── evaluation-notes.md
```

## 目录用途

| 目录 | 用途 |
|---|---|
| `resumes/` | 存放脱敏或虚构的样例简历 |
| `job-descriptions/` | 存放脱敏或公开改写后的样例岗位描述 |
| `outputs/resume-analysis/` | 存放简历诊断输出记录 |
| `outputs/job-description-parse/` | 存放目标岗位解析输出记录 |
| `outputs/ai-job-match/` | 存放匹配分析输出记录 |
| `outputs/ai-suggestions/` | 存放岗位优化建议输出记录 |
| `outputs/rewrite-suggestions/` | 存放局部改写输出记录 |
| `reports/` | 存放人工评估报告 |
| `evaluation-notes.md` | 记录评估规则、隐私边界和评估过程说明 |

## 命名规则

| 类型 | 命名示例 | 说明 |
|---|---|---|
| 样例简历 | `resume-sample-001.md` | 按样例编号递增 |
| 样例岗位描述 | `jd-sample-001.md` | 按样例编号递增 |
| 简历诊断输出 | `resume-sample-001_prompt-resume-analysis-v1.md` | 包含输入样例和 Prompt 版本 |
| 岗位解析输出 | `jd-sample-001_prompt-job-description-parse-v1.md` | 包含输入样例和 Prompt 版本 |
| 匹配分析输出 | `resume-sample-001_jd-sample-001_prompt-ai-job-match-v1.md` | 包含简历样例、岗位样例和 Prompt 版本 |
| 优化建议输出 | `resume-sample-001_jd-sample-001_prompt-resume-suggestion-v1.md` | 包含简历样例、岗位样例和 Prompt 版本 |
| 局部改写输出 | `resume-sample-001_jd-sample-001_prompt-rewrite-suggestion-v1.md` | 包含简历样例、岗位样例和 Prompt 版本 |
| 评估报告 | `evaluation-report-v1.md` | 按评估轮次递增 |

## 可提交内容

- 虚构样例简历。
- 经过脱敏和改写的公开岗位描述。
- 脱敏后的 AI 输出记录。
- 人工评分表和问题归纳。
- Prompt 修改前后的结果对比摘要。

## 禁止提交内容

- 真实姓名、手机号、邮箱、身份证号、住址、学校学号等个人隐私。
- 未经授权的真实简历全文。
- 未经脱敏的真实岗位描述。
- API Key、Token、环境变量或完整请求头。
- 完整系统 Prompt 或包含敏感配置的 Prompt。
- 可反推出真实用户身份的公司、项目、奖项或经历组合。
