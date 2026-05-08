package com.winter.airesumeoptimizer.module.job.service;

import com.winter.airesumeoptimizer.module.job.vo.JobMatchResultVO;
import java.util.List;

public interface JobMatchResultService {

    JobMatchResultVO match(Long userId, Long resumeId, Long jobId);

    List<JobMatchResultVO> listByResume(Long userId, Long resumeId);
}
