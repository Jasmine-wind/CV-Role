package com.winter.airesumeoptimizer.module.ai.usage.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.winter.airesumeoptimizer.module.ai.usage.config.AiUsageRetentionProperties;
import com.winter.airesumeoptimizer.module.ai.usage.entity.AiUsageRecord;
import com.winter.airesumeoptimizer.module.ai.usage.mapper.AiUsageRecordMapper;
import com.winter.airesumeoptimizer.module.ai.usage.service.AiUsageRetentionService;
import java.time.LocalDateTime;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnProperty(prefix = "app.ai.usage.retention", name = "enabled", havingValue = "true", matchIfMissing = true)
public class AiUsageRetentionServiceImpl implements AiUsageRetentionService {

    private final AiUsageRecordMapper usageRecordMapper;
    private final AiUsageRetentionProperties properties;

    public AiUsageRetentionServiceImpl(
            AiUsageRecordMapper usageRecordMapper,
            AiUsageRetentionProperties properties) {
        this.usageRecordMapper = usageRecordMapper;
        this.properties = properties;
    }

    @Override
    @Transactional
    public int purgeExpired() {
        if (!properties.isEnabled()) {
            return 0;
        }
        LocalDateTime cutoff = LocalDateTime.now().minusDays(properties.getDays());
        return usageRecordMapper.delete(new LambdaQueryWrapper<AiUsageRecord>()
                .lt(AiUsageRecord::getCreatedAt, cutoff));
    }
}
