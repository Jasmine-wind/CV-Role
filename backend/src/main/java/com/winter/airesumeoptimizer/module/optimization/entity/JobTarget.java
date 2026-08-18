package com.winter.airesumeoptimizer.module.optimization.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("job_targets")
public class JobTarget {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Long legacyJobDescriptionId;

    private String title;

    private String rawJd;

    private String sourceType;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
