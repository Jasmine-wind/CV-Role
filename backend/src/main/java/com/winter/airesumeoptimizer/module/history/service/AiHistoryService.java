package com.winter.airesumeoptimizer.module.history.service;

import com.winter.airesumeoptimizer.module.history.vo.AiResultPageVO;
import com.winter.airesumeoptimizer.module.history.vo.AiResultDetailVO;

public interface AiHistoryService {

    AiResultPageVO list(
            Long userId,
            String resultType,
            Long resumeId,
            Long jobDescriptionId,
            String status,
            Integer page,
            Integer size);

    AiResultDetailVO detail(Long userId, String resultType, Long recordId);
}
