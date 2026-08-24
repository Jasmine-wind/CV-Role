package com.winter.airesumeoptimizer.module.observability.service.impl;

import com.winter.airesumeoptimizer.common.exception.BusinessException;
import com.winter.airesumeoptimizer.module.observability.mapper.ProductObservabilityMapper;
import com.winter.airesumeoptimizer.module.observability.service.ProductObservabilityService;
import com.winter.airesumeoptimizer.module.observability.vo.ProductObservabilitySnapshotVO;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProductObservabilityServiceImpl implements ProductObservabilityService {

    private final ProductObservabilityMapper observabilityMapper;

    public ProductObservabilityServiceImpl(ProductObservabilityMapper observabilityMapper) {
        this.observabilityMapper = observabilityMapper;
    }

    @Override
    @Transactional(readOnly = true)
    public ProductObservabilitySnapshotVO snapshot(LocalDateTime fromInclusive, LocalDateTime toExclusive) {
        if (fromInclusive == null || toExclusive == null || !fromInclusive.isBefore(toExclusive)) {
            throw new BusinessException(400, "观测时间范围不正确");
        }
        ProductObservabilitySnapshotVO snapshot = observabilityMapper.selectSnapshot(fromInclusive, toExclusive);
        if (snapshot == null) {
            snapshot = new ProductObservabilitySnapshotVO();
        }
        snapshot.setFromInclusive(fromInclusive);
        snapshot.setToExclusive(toExclusive);
        normalizeNulls(snapshot);
        return snapshot;
    }

    private void normalizeNulls(ProductObservabilitySnapshotVO snapshot) {
        if (snapshot.getRegistrations() == null) snapshot.setRegistrations(0L);
        if (snapshot.getUploadedResumes() == null) snapshot.setUploadedResumes(0L);
        if (snapshot.getResumePreparationSuccesses() == null) snapshot.setResumePreparationSuccesses(0L);
        if (snapshot.getResumePreparationFailures() == null) snapshot.setResumePreparationFailures(0L);
        if (snapshot.getAnalysisSuccesses() == null) snapshot.setAnalysisSuccesses(0L);
        if (snapshot.getAnalysisFailures() == null) snapshot.setAnalysisFailures(0L);
        if (snapshot.getSuccessfulExports() == null) snapshot.setSuccessfulExports(0L);
        if (snapshot.getAnalysesWithExport() == null) snapshot.setAnalysesWithExport(0L);
        if (snapshot.getAverageFirstSuccessfulAnalysisMs() == null) {
            snapshot.setAverageFirstSuccessfulAnalysisMs(0L);
        }
        if (snapshot.getProviderAttempts() == null) snapshot.setProviderAttempts(0L);
        if (snapshot.getProviderFailures() == null) snapshot.setProviderFailures(0L);
        if (snapshot.getReportedTotalTokens() == null) snapshot.setReportedTotalTokens(0L);
    }
}
