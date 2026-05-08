package com.winter.airesumeoptimizer.module.job.controller;

import com.winter.airesumeoptimizer.common.result.Result;
import com.winter.airesumeoptimizer.module.job.service.JobService;
import com.winter.airesumeoptimizer.module.job.vo.JobDetailVO;
import com.winter.airesumeoptimizer.module.job.vo.JobListVO;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/jobs")
public class JobController {

    private final JobService jobService;

    public JobController(JobService jobService) {
        this.jobService = jobService;
    }

    @GetMapping
    public Result<List<JobListVO>> list() {
        return Result.success(jobService.listEnabledJobs());
    }

    @GetMapping("/{id}")
    public Result<JobDetailVO> detail(@PathVariable Long id) {
        return Result.success(jobService.getEnabledJobDetail(id));
    }
}
