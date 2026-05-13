package com.winter.airesumeoptimizer.module.job.service;

import com.winter.airesumeoptimizer.module.job.dto.JobDescriptionSubmitDTO;
import com.winter.airesumeoptimizer.module.job.vo.JobDescriptionVO;
import java.util.List;

public interface JobDescriptionService {

    JobDescriptionVO submit(Long userId, JobDescriptionSubmitDTO request);

    List<JobDescriptionVO> listByUser(Long userId);

    JobDescriptionVO getDetail(Long userId, Long jobDescriptionId);
}
