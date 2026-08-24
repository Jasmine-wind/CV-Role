package com.winter.airesumeoptimizer.module.ai.usage.service.impl;

import com.winter.airesumeoptimizer.module.ai.usage.entity.AiUsageRecord;
import com.winter.airesumeoptimizer.module.ai.usage.mapper.AiUsageRecordMapper;
import com.winter.airesumeoptimizer.module.ai.usage.service.AiUsageRecordPersistence;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * A provider call has already happened when this is invoked. Keep that attempt
 * ledger independent from parsing/evidence transactions without making ledger
 * availability a prerequisite for business success.
 */
@Service
public class AiUsageRecordPersistenceImpl implements AiUsageRecordPersistence {

    private final AiUsageRecordMapper usageRecordMapper;

    public AiUsageRecordPersistenceImpl(AiUsageRecordMapper usageRecordMapper) {
        this.usageRecordMapper = usageRecordMapper;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void persist(AiUsageRecord record) {
        if (usageRecordMapper.insert(record) != 1) {
            throw new IllegalStateException("AI Usage ledger 写入失败");
        }
    }
}
