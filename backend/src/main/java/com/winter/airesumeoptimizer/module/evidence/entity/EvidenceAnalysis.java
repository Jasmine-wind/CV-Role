package com.winter.airesumeoptimizer.module.evidence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("evidence_analyses")
public class EvidenceAnalysis {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Long optimizationTaskId;

    private Integer matchedCount;

    private Integer partialEvidenceCount;

    private Integer noEvidenceCount;

    private String modelName;

    private String promptVersion;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
