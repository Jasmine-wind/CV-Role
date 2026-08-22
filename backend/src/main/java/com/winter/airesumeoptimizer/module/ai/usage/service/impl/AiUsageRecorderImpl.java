package com.winter.airesumeoptimizer.module.ai.usage.service.impl;

import com.winter.airesumeoptimizer.infra.ai.AiFailureCode;
import com.winter.airesumeoptimizer.infra.ai.AiInvocationContext;
import com.winter.airesumeoptimizer.infra.ai.AiSelectionSnapshot;
import com.winter.airesumeoptimizer.infra.ai.AiUsageMetrics;
import com.winter.airesumeoptimizer.module.ai.usage.entity.AiUsageRecord;
import com.winter.airesumeoptimizer.module.ai.usage.mapper.AiUsageRecordMapper;
import com.winter.airesumeoptimizer.module.ai.usage.service.AiUsageRecorder;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;

/** Best-effort ledger: its failure is deliberately isolated by the gateway. */
@Service
public class AiUsageRecorderImpl implements AiUsageRecorder {

    private final AiUsageRecordMapper usageRecordMapper;

    public AiUsageRecorderImpl(AiUsageRecordMapper usageRecordMapper) {
        this.usageRecordMapper = usageRecordMapper;
    }

    @Override
    public void recordSuccess(AiInvocationContext context, AiSelectionSnapshot selection, AiUsageMetrics usage) {
        insert(context, selection, "SUCCESS", null, usage.latencyMs(), usage.inputTokens(), usage.outputTokens());
    }

    @Override
    public void recordFailure(
            AiInvocationContext context,
            AiSelectionSnapshot selection,
            AiFailureCode failureCode,
            long latencyMs,
            int attempts) {
        insert(context, selection, "FAILURE", failureCode, latencyMs, null, null);
    }

    private void insert(
            AiInvocationContext context,
            AiSelectionSnapshot selection,
            String outcome,
            AiFailureCode failureCode,
            long latencyMs,
            Long promptTokens,
            Long completionTokens) {
        AiUsageRecord record = new AiUsageRecord();
        record.setUserId(context.userId());
        record.setOptimizationTaskId(context.taskId());
        record.setOperation(context.operation());
        record.setSource(selection.source().name());
        record.setProvider(selection.providerType());
        record.setModel(selection.model());
        record.setCredentialRevision(selection.credentialRevision());
        record.setOutcome(outcome);
        record.setFailureCode(failureCode == null ? null : failureCode.name());
        record.setLatencyMs(Math.max(0L, latencyMs));
        record.setPromptTokens(toInt(promptTokens));
        record.setCompletionTokens(toInt(completionTokens));
        record.setTotalTokens(sum(promptTokens, completionTokens));
        record.setCreatedAt(LocalDateTime.now());
        usageRecordMapper.insert(record);
    }

    private Integer toInt(Long value) {
        if (value == null || value < 0 || value > Integer.MAX_VALUE) {
            return null;
        }
        return value.intValue();
    }

    private Integer sum(Long first, Long second) {
        if (first == null || second == null || first < 0 || second < 0) {
            return null;
        }
        long total = first + second;
        return total > Integer.MAX_VALUE ? null : (int) total;
    }
}
