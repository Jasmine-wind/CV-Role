package com.winter.airesumeoptimizer.module.optimization.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("resume_versions")
public class ResumeVersion {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Long resumeId;

    private Long sourceVersionId;

    private Long jobTargetId;

    private Long legacyMatchResultId;

    private String versionType;

    private String sourceType;

    private String contentStatus;

    private String structuredContent;

    private Long contentRevision;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
