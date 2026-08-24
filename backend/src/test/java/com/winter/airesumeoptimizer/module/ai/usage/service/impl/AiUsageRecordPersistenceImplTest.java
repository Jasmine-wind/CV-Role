package com.winter.airesumeoptimizer.module.ai.usage.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.winter.airesumeoptimizer.module.ai.usage.entity.AiUsageRecord;
import com.winter.airesumeoptimizer.module.ai.usage.mapper.AiUsageRecordMapper;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

class AiUsageRecordPersistenceImplTest {

    @Test
    void persistUsesIndependentTransactionAndWritesExactlyOneRow() throws Exception {
        AiUsageRecordMapper mapper = mock(AiUsageRecordMapper.class);
        AiUsageRecord record = new AiUsageRecord();
        when(mapper.insert(record)).thenReturn(1);

        new AiUsageRecordPersistenceImpl(mapper).persist(record);

        verify(mapper).insert(record);
        Method method = AiUsageRecordPersistenceImpl.class.getMethod("persist", AiUsageRecord.class);
        Transactional transactional = method.getAnnotation(Transactional.class);
        assertThat(transactional).isNotNull();
        assertThat(transactional.propagation()).isEqualTo(Propagation.REQUIRES_NEW);
    }
}
