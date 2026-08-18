package com.winter.airesumeoptimizer.module.evidence.service;

import com.winter.airesumeoptimizer.module.evidence.dto.EvidenceMatchOutcomeDTO;

public interface EvidenceMatchOutputParser {

    /**
     * 解析并校核 AI 输出。证据引用必须真实出现在 resumeCorpus（冻结的简历输入快照）中，
     * 校核失败的证据会被丢弃；失去全部证据的要求会被降级为无证据，避免编造内容进入正式结果。
     */
    EvidenceMatchOutcomeDTO parse(String aiOutput, String resumeCorpus);
}
