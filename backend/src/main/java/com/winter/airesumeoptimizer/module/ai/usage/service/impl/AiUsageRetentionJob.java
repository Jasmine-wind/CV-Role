package com.winter.airesumeoptimizer.module.ai.usage.service.impl;

import com.winter.airesumeoptimizer.module.ai.usage.service.AiUsageRetentionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Only expires the bounded AI attempt ledger; it is not a generic event processor. */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "app.ai.usage.retention", name = "enabled", havingValue = "true", matchIfMissing = true)
public class AiUsageRetentionJob {

    private final AiUsageRetentionService retentionService;

    public AiUsageRetentionJob(AiUsageRetentionService retentionService) {
        this.retentionService = retentionService;
    }

    @Scheduled(cron = "${app.ai.usage.retention.cron:0 17 3 * * *}")
    public void purgeExpired() {
        int removed = retentionService.purgeExpired();
        if (removed > 0) {
            log.info("Expired AI Usage records removed: count={}", removed);
        }
    }
}
