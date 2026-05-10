package com.winter.airesumeoptimizer.module.resume.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("resumes")
public class Resume {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String originalFilename;

    private String fileType;

    private Long fileSize;

    private String objectKey;

    private String storageType;

    private String uploadStatus;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
