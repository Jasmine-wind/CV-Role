package com.winter.airesumeoptimizer.module.analysis.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("ai_job_match_results")
public class AiJobMatchResult {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long resumeId;

    private Long jobDescriptionId;

    private String matchStatus;

    private Integer overallScore;

    private String strongMatches;

    private String weakMatches;

    private String missingSkills;

    private String weakExperienceDescriptions;

    private String evidence;

    private String riskNotes;

    private String modelName;

    private String promptVersion;

    private String errorMessage;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
