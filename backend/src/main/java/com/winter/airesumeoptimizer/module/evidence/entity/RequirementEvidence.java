package com.winter.airesumeoptimizer.module.evidence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("requirement_evidences")
public class RequirementEvidence {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Long evidenceRequirementId;

    private Long sourceResumeVersionId;

    private String sectionLabel;

    private String evidenceText;

    private String expressionStatus;

    private LocalDateTime createdAt;
}
