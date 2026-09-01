package com.winter.airesumeoptimizer.module.resume.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("resume_parse_results")
public class ResumeParseResult {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long resumeId;

    private String parseStatus;

    private String extractedText;

    private String cleanedText;

    private String sectionResult;

    private String structuredJson;

    private String errorMessage;

    private String textQualityStatus;

    private String textQualityIssues;

    private String textQualityMessage;

    private String parseQualityStatus;

    private String parseQualityWarnings;

    private String parseQualityMessage;

    private Integer parseQualityScore;

    private String qualityStatus;

    private String qualityIssues;

    private String unresolvedItems;

    /** 指向当前 canonical SOURCE 版本；不保存第二份简历内容。 */
    private Long canonicalSourceVersionId;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
