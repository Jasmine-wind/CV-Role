package com.winter.airesumeoptimizer.module.optimization.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("optimization_tasks")
public class OptimizationTask {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Long sourceResumeVersionId;

    private Long targetResumeVersionId;

    private Long jobTargetId;

    private Long asyncTaskId;

    private Long analysisResultId;

    private Long legacyMatchResultId;

    private String status;

    private String resumeInputSnapshot;

    private String jobInputSnapshot;

    private String promptSnapshot;

    private String rulesSnapshot;

    private String providerSnapshot;

    private String modelSnapshot;

    private String templateVersion;

    private String errorCode;

    private String errorMessage;

    private LocalDateTime startedAt;

    private LocalDateTime finishedAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
