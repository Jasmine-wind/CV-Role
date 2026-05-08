package com.winter.airesumeoptimizer.module.job.service;

import com.winter.airesumeoptimizer.module.job.vo.JobDetailVO;
import com.winter.airesumeoptimizer.module.job.vo.JobListVO;
import java.util.List;

public interface JobService {

    List<JobListVO> listEnabledJobs();

    JobDetailVO getEnabledJobDetail(Long jobId);
}
