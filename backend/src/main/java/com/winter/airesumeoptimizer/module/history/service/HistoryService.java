package com.winter.airesumeoptimizer.module.history.service;

import com.winter.airesumeoptimizer.module.history.vo.HistoryDetailVO;
import com.winter.airesumeoptimizer.module.history.vo.HistoryPageVO;

public interface HistoryService {

    HistoryPageVO list(Long userId, Integer page, Integer size);

    HistoryDetailVO detail(Long userId, Long resumeId);
}
