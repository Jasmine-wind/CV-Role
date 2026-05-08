package com.winter.airesumeoptimizer.module.job.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("jobs")
public class Job {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String title;

    private String companyName;

    private String jobCategory;

    private String location;

    private String description;

    private String requirements;

    private String requiredSkills;

    private String status;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
