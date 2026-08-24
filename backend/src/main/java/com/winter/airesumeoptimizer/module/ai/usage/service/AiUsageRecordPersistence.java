package com.winter.airesumeoptimizer.module.ai.usage.service;

import com.winter.airesumeoptimizer.module.ai.usage.entity.AiUsageRecord;

/** Persists one already-sanitized outbound-provider attempt in an isolated transaction. */
public interface AiUsageRecordPersistence {

    void persist(AiUsageRecord record);
}
