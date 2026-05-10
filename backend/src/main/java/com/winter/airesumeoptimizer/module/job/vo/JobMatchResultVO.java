package com.winter.airesumeoptimizer.module.job.vo;

import com.winter.airesumeoptimizer.module.job.dto.JobMatchSuggestionDTO;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@Schema(description = "岗位匹配结果")
public class JobMatchResultVO {

    @Schema(description = "匹配结果 ID", example = "1")
    private Long matchId;

    @Schema(description = "简历 ID", example = "1")
    private Long resumeId;

    @Schema(description = "岗位 ID", example = "1")
    private Long jobId;

    @Schema(description = "岗位标题", example = "Java 后端开发工程师")
    private String jobTitle;

    @Schema(description = "公司名称", example = "Demo Inc.")
    private String companyName;

    @Schema(description = "匹配分数", example = "80")
    private Integer matchScore;

    @Schema(description = "已匹配项目")
    private List<String> matchedItems;

    @Schema(description = "缺失项目")
    private List<String> missingItems;

    @Schema(description = "匹配原因")
    private String matchReason;

    @Schema(description = "优化建议")
    private List<JobMatchSuggestionDTO> suggestions;

    @Schema(description = "更新时间")
    private LocalDateTime updatedAt;
}
