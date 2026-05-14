package com.winter.airesumeoptimizer.module.analysis.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("ai_rewrite_suggestions")
public class AiRewriteSuggestion {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long resumeId;

    private Long jobDescriptionId;

    private Long aiJobMatchResultId;

    private Long aiResumeSuggestionId;

    private String rewriteType;

    private String targetSection;

    private String originalText;

    private String rewrittenText;

    private String rewriteReason;

    private String caution;

    private String acceptStatus;

    private String rewriteStatus;

    private String modelName;

    private String promptVersion;

    private String errorMessage;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
