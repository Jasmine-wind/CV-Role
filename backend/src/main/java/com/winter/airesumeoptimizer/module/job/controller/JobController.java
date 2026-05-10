package com.winter.airesumeoptimizer.module.job.controller;

import com.winter.airesumeoptimizer.common.result.Result;
import com.winter.airesumeoptimizer.module.job.service.JobService;
import com.winter.airesumeoptimizer.module.job.vo.JobDetailVO;
import com.winter.airesumeoptimizer.module.job.vo.JobListVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Positive;
import java.util.List;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/jobs")
@Validated
@Tag(name = "Job", description = "岗位列表和岗位详情接口")
public class JobController {

    private final JobService jobService;

    public JobController(JobService jobService) {
        this.jobService = jobService;
    }

    @GetMapping
    @Operation(summary = "岗位列表", description = "查询所有启用状态的岗位")
    public Result<List<JobListVO>> list() {
        return Result.success(jobService.listEnabledJobs());
    }

    @GetMapping("/{id}")
    @Operation(summary = "岗位详情", description = "查询启用岗位的详细信息")
    public Result<JobDetailVO> detail(@PathVariable @Positive(message = "岗位 ID 必须大于 0") Long id) {
        return Result.success(jobService.getEnabledJobDetail(id));
    }
}
