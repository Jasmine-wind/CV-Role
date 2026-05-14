package com.winter.airesumeoptimizer.module.analysis.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("ai_resume_suggestions")
public class AiResumeSuggestion {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long resumeId;

    private Long jobDescriptionId;

    private Long aiJobMatchResultId;

    private String suggestionStatus;

    private String suggestions;

    private String modelName;

    private String promptVersion;

    private String errorMessage;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
