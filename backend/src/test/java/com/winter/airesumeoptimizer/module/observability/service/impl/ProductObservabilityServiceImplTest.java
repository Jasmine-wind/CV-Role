package com.winter.airesumeoptimizer.module.observability.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.winter.airesumeoptimizer.common.exception.BusinessException;
import com.winter.airesumeoptimizer.module.observability.mapper.ProductObservabilityMapper;
import com.winter.airesumeoptimizer.module.observability.vo.ProductObservabilitySnapshotVO;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class ProductObservabilityServiceImplTest {

    private final ProductObservabilityMapper mapper = mock(ProductObservabilityMapper.class);
    private final ProductObservabilityServiceImpl service = new ProductObservabilityServiceImpl(mapper);

    @Test
    void returnsCommittedFactSnapshotWithoutInventingActivityEvents() {
        LocalDateTime from = LocalDateTime.of(2026, 1, 1, 0, 0);
        LocalDateTime to = from.plusDays(7);
        ProductObservabilitySnapshotVO row = new ProductObservabilitySnapshotVO();
        row.setAnalysisSuccesses(4L);
        row.setProviderAttempts(8L);
        when(mapper.selectSnapshot(from, to)).thenReturn(row);

        ProductObservabilitySnapshotVO result = service.snapshot(from, to);

        verify(mapper).selectSnapshot(from, to);
        assertThat(result.getFromInclusive()).isEqualTo(from);
        assertThat(result.getToExclusive()).isEqualTo(to);
        assertThat(result.getAnalysisSuccesses()).isEqualTo(4L);
        assertThat(result.getProviderAttempts()).isEqualTo(8L);
        assertThat(result.getSuccessfulExports()).isZero();
        assertThat(result.getReportedTotalTokens()).isZero();
    }

    @Test
    void rejectsUnboundedOrReversedTimeRange() {
        LocalDateTime now = LocalDateTime.now();
        assertThatThrownBy(() -> service.snapshot(null, now))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("时间范围");
        assertThatThrownBy(() -> service.snapshot(now, now))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("时间范围");
    }
}
