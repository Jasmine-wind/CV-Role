package com.winter.airesumeoptimizer.module.job.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("job_descriptions")
public class JobDescription {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String title;

    private String rawText;

    private String parseStatus;

    private String structuredContent;

    private String modelName;

    private String promptVersion;

    private String errorMessage;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
