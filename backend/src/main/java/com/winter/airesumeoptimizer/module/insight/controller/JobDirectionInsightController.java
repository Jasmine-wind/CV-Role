package com.winter.airesumeoptimizer.module.insight.controller;

import com.winter.airesumeoptimizer.common.result.Result;
import com.winter.airesumeoptimizer.module.insight.service.JobDirectionInsightService;
import com.winter.airesumeoptimizer.module.insight.vo.JobDirectionInsightsVO;
import com.winter.airesumeoptimizer.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/job-direction-insights")
@Tag(name = "Job Direction Insight", description = "基于正式历史岗位分析的只读方向洞察")
@SecurityRequirement(name = "bearerAuth")
public class JobDirectionInsightController {

    private final JobDirectionInsightService jobDirectionInsightService;

    public JobDirectionInsightController(JobDirectionInsightService jobDirectionInsightService) {
        this.jobDirectionInsightService = jobDirectionInsightService;
    }

    @GetMapping
    @Operation(summary = "查看岗位方向洞察", description = "仅聚合当前用户近 180 天的正式 Evidence 分析，不写入任何事实")
    public Result<JobDirectionInsightsVO> get(Authentication authentication) {
        AuthenticatedUser user = (AuthenticatedUser) authentication.getPrincipal();
        return Result.success(jobDirectionInsightService.getInsights(user.getUserId()));
    }
}
