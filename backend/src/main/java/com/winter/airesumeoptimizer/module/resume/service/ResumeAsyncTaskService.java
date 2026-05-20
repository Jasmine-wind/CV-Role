package com.winter.airesumeoptimizer.module.resume.service;

import com.winter.airesumeoptimizer.module.resume.dto.ResumeParseOptionsDTO;
import com.winter.airesumeoptimizer.module.task.vo.AsyncTaskVO;

public interface ResumeAsyncTaskService {

    AsyncTaskVO submitParseTask(Long userId, Long resumeId, ResumeParseOptionsDTO options);

    AsyncTaskVO submitDiagnosisTask(Long userId, Long resumeId);

    AsyncTaskVO submitEmbeddingTask(Long userId, Long resumeId);
}
