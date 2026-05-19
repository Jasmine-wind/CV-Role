package com.winter.airesumeoptimizer.module.history.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@Schema(description = "历史记录列表项")
public class HistoryListVO {

    @Schema(description = "记录 ID", example = "1")
    private Long recordId;

    @Schema(description = "简历 ID", example = "1")
    private Long resumeId;

    @Schema(description = "简历名称", example = "resume.pdf")
    private String resumeName;

    @Schema(description = "文件类型", example = "PDF")
    private String fileType;

    @Schema(description = "文件大小，单位字节", example = "102400")
    private Long fileSize;

    @Schema(description = "上传状态", example = "UPLOADED")
    private String uploadStatus;

    @Schema(description = "上传时间")
    private LocalDateTime uploadTime;

    @Schema(description = "解析状态", example = "SUCCESS")
    private String parseStatus;

    @Schema(description = "AI 分析状态", example = "SUCCESS")
    private String analysisStatus;

    @Schema(description = "AI 分析分数", example = "85")
    private Integer analysisScore;

    @Schema(description = "最近匹配岗位 ID", example = "1")
    private Long latestJobId;

    @Schema(description = "最近匹配目标岗位 ID", example = "1")
    private Long latestJobDescriptionId;

    @Schema(description = "最近匹配来源", example = "AI_JOB_DESCRIPTION")
    private String latestMatchSource;

    @Schema(description = "最近匹配岗位标题")
    private String latestJobTitle;

    @Schema(description = "最近匹配公司名称")
    private String latestCompanyName;

    @Schema(description = "最近匹配分数", example = "80")
    private Integer latestMatchScore;

    @Schema(description = "更新时间")
    private LocalDateTime updatedAt;
}
