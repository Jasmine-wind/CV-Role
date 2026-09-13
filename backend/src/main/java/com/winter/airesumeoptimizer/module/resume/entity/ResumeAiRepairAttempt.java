package com.winter.airesumeoptimizer.module.resume.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/** Durable reservation and terminal result for one source-backed AI repair identity. */
@Getter
@Setter
@TableName("resume_ai_repair_attempts")
public class ResumeAiRepairAttempt {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;
    private Long resumeId;
    private String repairKey;
    private String status;
    private String ownerToken;
    private String resultJson;
    private String failureReason;
    private Integer providerDispatchCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
