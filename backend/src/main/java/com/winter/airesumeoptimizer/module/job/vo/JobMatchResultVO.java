package com.winter.airesumeoptimizer.module.job.vo;

import com.winter.airesumeoptimizer.module.job.dto.JobMatchSuggestionDTO;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class JobMatchResultVO {

    private Long matchId;

    private Long resumeId;

    private Long jobId;

    private String jobTitle;

    private String companyName;

    private Integer matchScore;

    private List<String> matchedItems;

    private List<String> missingItems;

    private String matchReason;

    private List<JobMatchSuggestionDTO> suggestions;

    private LocalDateTime updatedAt;
}
