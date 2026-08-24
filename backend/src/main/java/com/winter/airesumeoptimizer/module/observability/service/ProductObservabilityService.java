package com.winter.airesumeoptimizer.module.observability.service;

import com.winter.airesumeoptimizer.module.observability.vo.ProductObservabilitySnapshotVO;
import java.time.LocalDateTime;

/** Internal, read-only report over committed retained business rows. */
public interface ProductObservabilityService {

    ProductObservabilitySnapshotVO snapshot(LocalDateTime fromInclusive, LocalDateTime toExclusive);
}
