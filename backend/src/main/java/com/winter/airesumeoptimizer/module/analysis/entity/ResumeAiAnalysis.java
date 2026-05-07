package com.winter.airesumeoptimizer.module.analysis.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("resume_ai_analyses")
public class ResumeAiAnalysis {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long resumeId;

    private String analysisStatus;

    private Integer score;

    private String strengths;

    private String problems;

    private String suggestionsSummary;

    private String modelName;

    private String promptVersion;

    private String errorMessage;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
