package com.winter.airesumeoptimizer.module.ai.usage.service;

/** Narrow maintenance seam for expiring raw provider-attempt metadata. */
public interface AiUsageRetentionService {

    int purgeExpired();
}
