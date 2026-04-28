package com.winter.airesumeoptimizer.module.resume.vo;

import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ResumeUploadVO {

    private Long id;
    private String originalFilename;
    private String fileType;
    private Long fileSize;
    private String objectKey;
    private String uploadStatus;
    private LocalDateTime createdAt;
}
