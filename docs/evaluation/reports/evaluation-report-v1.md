# evaluation-report-v1

## metadata

- evaluationRound: v1
- evaluationDate: 2026-05-16
- evaluator: manual-review
- modelName: deepseek-v4-flash
- promptVersions: job_description_parse_v1, resume_analysis_v1, ai_job_match_v1, resume_suggestion_v1, rewrite_suggestion_v1
- sampleScope: resume-sample-001 + jd-sample-001
- note: 本轮基于 v2.7.5 已保存的评估输出记录进行人工评分；如后续替换为真实联调输出，应重新评分。

## summary

| 指标 | 结果 |
|---|---|
| 样例简历数量 | 1 |
| 样例岗位描述数量 | 1 |
| 输出记录数量 | 5 |
| 可用输出数量 | 4 |
| 需要轻微调整数量 | 3 |
| 需要修改 Prompt 数量 | 1 |
| 不可用输出数量 | 1 |
| 严重问题数量 | 1 |

## scoreOverview

| 能力 | relevance | factualConsistency | specificity | usefulness | structureStability | explainability | safety | 平均分 |
|---|---:|---:|---:|---:|---:|---:|---:|---:|
| resume-analysis | 5 | 5 | 4 | 4 | 5 | 4 | 5 | 4.6 |
| job-description-parse | 5 | 5 | 4 | 4 | 5 | 4 | 5 | 4.6 |
| ai-job-match | 5 | 4 | 4 | 5 | 5 | 5 | 5 | 4.7 |
| ai-suggestions | 5 | 5 | 5 | 5 | 5 | 5 | 5 | 5.0 |
| rewrite-suggestions | 4 | 5 | 4 | 3 | 1 | 3 | 5 | 3.6 |

## majorFindings

- 简历分析、岗位解析、匹配分析和优化建议输出整体可用，结构稳定，未发现编造经历、证书、奖项或真实隐私泄露。
- 优化建议表现最好，每条建议都有目标章节、问题、建议、依据和 caution，适合作为后续页面展示和人工确认依据。
- 匹配分析中 `Linux` 被列入缺失技能，来源是岗位加分项而非必备项，后续 Prompt 可要求区分 required 与 bonus，避免弱化匹配结论。
- 局部改写输出未遵守 JSON 结构，虽然内容没有明显编造事实，但无法被系统稳定解析，应作为本轮最主要问题处理。

## severeIssues

| 编号 | 能力 | 样例 | 问题 | 影响 | 处理建议 |
|---|---|---|---|---|---|
| SI-001 | rewrite-suggestions | resume-sample-001 + jd-sample-001 | 输出为自然语言和 Markdown 风格文本，未返回固定 JSON 对象 | 影响后端解析、页面展示和保存流程 | 强化 `rewrite_suggestion_v1` 的 JSON-only 要求，并在 Prompt 中加入失败示例或字段校验提醒 |

## issueSummary

| 问题类型 | 数量 | 说明 |
|---|---:|---|
| FORMAT_ERROR | 1 | 局部改写输出格式错误，无法解析为固定 JSON |
| HALLUCINATION | 0 | 未发现编造项目、实习、证书、奖项或量化指标 |
| VAGUE_SUGGESTION | 1 | 简历分析中部分建议较概括，如“补充接口数量和模块职责”仍可更具体 |
| MISSING_EVIDENCE | 1 | 简历分析的优势和问题没有逐条引用输入依据 |
| OVER_OPTIMIZATION | 0 | 未发现明显过度改写或代填成果 |
| UNSAFE_CONTENT | 0 | 未发现隐私泄露、造假建议或敏感配置暴露 |
| LOW_RELEVANCE | 0 | 输出整体围绕样例简历、样例岗位和当前任务 |

## evaluationRecords

### ER-001 resume-analysis

- outputFile: `docs/evaluation/outputs/resume-analysis/resume-sample-001_prompt-resume-analysis-v1.md`
- finalDecision: minor-adjust

| 维度 | 分数 | 备注 |
|---|---:|---|
| relevance | 5 | 输出围绕简历质量诊断，没有混入岗位匹配 |
| factualConsistency | 5 | 未编造样例简历之外的经历 |
| specificity | 4 | 能指出量化不足和技能深度不足，但建议仍偏摘要 |
| usefulness | 4 | 可指导用户补充项目职责和真实数据 |
| structureStability | 5 | JSON 字段符合 `score`、`strengths`、`problems`、`suggestionsSummary` |
| explainability | 4 | 结论合理，但未逐条引用输入证据 |
| safety | 5 | 明确提示只补充真实可验证数据 |

### ER-002 job-description-parse

- outputFile: `docs/evaluation/outputs/job-description-parse/jd-sample-001_prompt-job-description-parse-v1.md`
- finalDecision: minor-adjust

| 维度 | 分数 | 备注 |
|---|---:|---|
| relevance | 5 | 输出围绕 Java 后端实习岗位 |
| factualConsistency | 5 | 没有补充真实公司或样例外岗位信息 |
| specificity | 4 | 技能和职责提取清晰，但未单独保留 bonusSkills |
| usefulness | 4 | 能支撑后续匹配分析 |
| structureStability | 5 | JSON 结构稳定 |
| explainability | 4 | 摘要能概括岗位，但字段级依据未显式保留 |
| safety | 5 | 无隐私或敏感信息 |

### ER-003 ai-job-match

- outputFile: `docs/evaluation/outputs/ai-job-match/resume-sample-001_jd-sample-001_prompt-ai-job-match-v1.md`
- finalDecision: minor-adjust

| 维度 | 分数 | 备注 |
|---|---:|---|
| relevance | 5 | 输出围绕样例简历和样例岗位匹配 |
| factualConsistency | 4 | `Linux` 来自加分项，作为缺失技能略偏严格 |
| specificity | 4 | 强弱匹配、缺失技能和弱经历描述较具体 |
| usefulness | 5 | 能指导用户理解匹配优势和风险 |
| structureStability | 5 | JSON 字段和数组结构符合预期 |
| explainability | 5 | evidence 能对应简历和岗位来源 |
| safety | 5 | 风险提示避免补写虚假经历 |

### ER-004 ai-suggestions

- outputFile: `docs/evaluation/outputs/ai-suggestions/resume-sample-001_jd-sample-001_prompt-resume-suggestion-v1.md`
- finalDecision: keep

| 维度 | 分数 | 备注 |
|---|---:|---|
| relevance | 5 | 建议紧扣 Java 后端岗位和样例简历 |
| factualConsistency | 5 | 仅建议补充真实经历，没有代填事实 |
| specificity | 5 | 每条建议有章节、问题、动作、依据和 caution |
| usefulness | 5 | 可直接指导用户如何完善简历 |
| structureStability | 5 | JSON 结构符合 `suggestions` 规范 |
| explainability | 5 | evidence 充分 |
| safety | 5 | 明确禁止虚构数据和经历 |

### ER-005 rewrite-suggestions

- outputFile: `docs/evaluation/outputs/rewrite-suggestions/resume-sample-001_jd-sample-001_prompt-rewrite-suggestion-v1_parse-failed.md`
- finalDecision: revise-prompt

| 维度 | 分数 | 备注 |
|---|---:|---|
| relevance | 4 | 内容与原文片段相关 |
| factualConsistency | 5 | 没有添加不存在的职责或指标 |
| specificity | 4 | 改写文本和注意事项可理解 |
| usefulness | 3 | 人能参考，但系统无法稳定使用 |
| structureStability | 1 | 未输出固定 JSON 对象 |
| explainability | 3 | 有简单理由，但不是结构化字段 |
| safety | 5 | 未发现造假或隐私风险 |

## promptAdjustmentPlan

| Prompt | 当前问题 | 调整方向 | 优先级 |
|---|---|---|---|
| rewrite_suggestion_v1 | 偶发自然语言输出，未遵守 JSON-only 结构 | 在 Prompt 开头和输出要求中重复强调“只输出 JSON 对象”，增加字段完整示例，并明确禁止“可以改成”等自然语言前缀 | HIGH |
| ai_job_match_v1 | 对加分项缺失的表达可能偏严格 | 要求区分 requiredSkills、bonusSkills 和 experienceSignals；缺失加分项应进入 riskNotes 或 weakMatches，不直接等同核心缺失 | MEDIUM |
| resume_analysis_v1 | 分析结论缺少逐条依据 | 要求 problems 和 suggestionsSummary 尽量指向具体章节或输入证据，但仍保持字段结构不变 | LOW |
| job_description_parse_v1 | bonusSkills 未在当前结构中充分体现 | 如后续结构允许，可保留加分项字段；当前阶段先在 summary 或 keywords 中体现即可 | LOW |

## promptModificationRules

- 如果输出不是固定 JSON 对象，应优先修改对应 Prompt 的输出格式约束，并用同一测试样例重新跑。
- 如果输出把岗位加分项当作核心缺失，应要求 Prompt 区分必备技能、加分技能和经验信号。
- 如果建议缺少依据，应要求每条建议至少引用一条简历或岗位输入证据。
- 如果输出建议补充量化指标，应只允许询问用户真实数据，禁止直接代填数字。
- 如果局部改写涉及新增技术栈、成果或职责，应要求输出 caution 并提醒用户确认事实边界。

## regressionChecklist

- [ ] 修改 Prompt 后重新运行相同样例。
- [ ] 对比修改前后结构稳定性。
- [ ] 检查是否新增事实一致性问题。
- [ ] 检查是否仍能被前端页面正常展示。
- [ ] 检查是否不泄露 API Key、Token、完整 Prompt 或真实隐私。

## conclusion

本轮 5 条输出记录中，简历分析、岗位解析、匹配分析和优化建议整体可用；局部改写存在结构错误，应优先调整 `rewrite_suggestion_v1`。下一轮应使用相同样例重新运行局部改写，确认 JSON 结构稳定后再推进 Prompt 进一步优化。
