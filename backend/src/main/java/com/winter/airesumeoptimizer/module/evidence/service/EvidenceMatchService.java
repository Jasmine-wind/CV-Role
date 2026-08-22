package com.winter.airesumeoptimizer.module.evidence.service;

import com.winter.airesumeoptimizer.infra.ai.AiSelectionSnapshot;
import com.winter.airesumeoptimizer.module.evidence.entity.EvidenceAnalysis;
import com.winter.airesumeoptimizer.module.evidence.vo.EvidenceAnalysisResultVO;
import com.winter.airesumeoptimizer.module.job.vo.JobDescriptionVO;

public interface EvidenceMatchService {

    /**
     * 为正式优化任务生成岗位证据分析。输入只使用任务已冻结的简历快照与刚完成的岗位解析结果；
     * 同一任务的旧分析会被整体替换，并与任务成功状态在同一事务提交，保证失败重试后的幂等与状态一致。
     */
    EvidenceAnalysis analyze(Long userId, Long optimizationTaskId, JobDescriptionVO parsedJob);

    default EvidenceAnalysis analyze(
            Long userId,
            Long optimizationTaskId,
            JobDescriptionVO parsedJob,
            AiSelectionSnapshot selection) {
        return analyze(userId, optimizationTaskId, parsedJob);
    }

    /**
     * 按当前用户读取正式分析结果；任务尚未生成正式分析时返回 null。
     */
    EvidenceAnalysisResultVO getResult(Long userId, Long optimizationTaskId);
}
