package com.winter.airesumeoptimizer.module.job.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("job_match_results")
public class JobMatchResult {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long resumeId;

    private Long jobId;

    private Integer matchScore;

    private String matchedItems;

    private String missingItems;

    private String matchReason;

    private String suggestions;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
