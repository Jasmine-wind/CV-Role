package com.winter.airesumeoptimizer.module.resume.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@Schema(description = "简历列表项")
public class ResumeListVO {

    @Schema(description = "简历 ID", example = "1")
    private Long id;
    @Schema(description = "原始文件名", example = "resume.pdf")
    private String originalFilename;
    @Schema(description = "文件类型", example = "PDF")
    private String fileType;
    @Schema(description = "文件大小，单位字节", example = "102400")
    private Long fileSize;
    @Schema(description = "上传状态", example = "UPLOADED")
    private String uploadStatus;
    @Schema(description = "简历准备状态", example = "SUCCESS")
    private String parseStatus;
    @Schema(description = "简历准备失败原因")
    private String parseErrorMessage;
    @Schema(description = "上传时间")
    private LocalDateTime createdAt;
}
