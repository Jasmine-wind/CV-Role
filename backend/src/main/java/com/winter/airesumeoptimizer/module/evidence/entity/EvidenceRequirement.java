package com.winter.airesumeoptimizer.module.evidence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("evidence_requirements")
public class EvidenceRequirement {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Long evidenceAnalysisId;

    private String requirementText;

    private String importance;

    private String matchLevel;

    private String conclusion;

    private String suggestion;

    private Integer displayOrder;

    private LocalDateTime createdAt;
}
