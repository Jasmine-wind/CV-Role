package com.winter.airesumeoptimizer.module.task.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("async_tasks")
public class AsyncTask {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String taskType;

    private String bizType;

    private Long bizId;

    private String status;

    private Integer progress;

    private String message;

    private String resultType;

    private Long resultId;

    private String resultSummary;

    private String errorCode;

    private String errorMessage;

    private LocalDateTime startedAt;

    private LocalDateTime finishedAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
