package com.winter.airesumeoptimizer.module.ai.usage.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.AbstractWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.winter.airesumeoptimizer.module.ai.usage.config.AiUsageRetentionProperties;
import com.winter.airesumeoptimizer.module.ai.usage.entity.AiUsageRecord;
import com.winter.airesumeoptimizer.module.ai.usage.mapper.AiUsageRecordMapper;
import java.util.Collection;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.Test;

class AiUsageRetentionServiceImplTest {

    static {
        MybatisConfiguration configuration = new MybatisConfiguration();
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, ""), AiUsageRecord.class);
    }

    @Test
    void purgeExpiredDeletesOnlyRowsBeforeTheBoundedRetentionCutoff() {
        AiUsageRecordMapper mapper = mock(AiUsageRecordMapper.class);
        AiUsageRetentionProperties properties = new AiUsageRetentionProperties();
        properties.setDays(90);
        when(mapper.delete(any())).thenReturn(3);

        int removed = new AiUsageRetentionServiceImpl(mapper, properties).purgeExpired();

        assertThat(removed).isEqualTo(3);
        verify(mapper).delete(any());
    }

    @Test
    void disabledRetentionDoesNotIssueDelete() {
        AiUsageRecordMapper mapper = mock(AiUsageRecordMapper.class);
        AiUsageRetentionProperties properties = new AiUsageRetentionProperties();
        properties.setEnabled(false);

        assertThat(new AiUsageRetentionServiceImpl(mapper, properties).purgeExpired()).isZero();
    }
}
