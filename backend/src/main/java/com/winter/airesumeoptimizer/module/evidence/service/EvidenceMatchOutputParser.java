package com.winter.airesumeoptimizer.module.evidence.service;

import com.winter.airesumeoptimizer.module.evidence.dto.EvidenceMatchOutcomeDTO;

public interface EvidenceMatchOutputParser {

    /**
     * 解析并校核 AI 输出。岗位要求必须可追溯到冻结 JD 及其结构化解析结果，证据引用必须逐字出现在
     * resumeCorpus（冻结的简历输入快照）中并与要求存在可验证锚点；失去全部证据的要求会被降级为无证据。
     */
    EvidenceMatchOutcomeDTO parse(
            String aiOutput,
            String frozenJobDescription,
            String jobStructuredContent,
            String resumeCorpus);
}
