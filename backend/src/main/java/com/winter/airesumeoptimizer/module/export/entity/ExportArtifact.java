package com.winter.airesumeoptimizer.module.export.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * 成功生成的 PDF 导出物。只有编译成功、存储成功与数据库记录全部完成才会存在；
 * storage_key 属于内部存储位置，不得出现在对客户端的响应中。
 */
@Getter
@Setter
@TableName("export_artifacts")
public class ExportArtifact {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Long optimizationTaskId;

    private Long targetResumeVersionId;

    private Long contentRevision;

    private String templateId;

    private String templateVersion;

    private String rendererVersion;

    private String storageKey;

    private String mimeType;

    private Long fileSize;

    private String checksumSha256;

    private String status;

    private Integer pageCount;

    private Boolean missingContact;

    private Boolean pageLimitExceeded;

    private Boolean overflowDetected;

    /** Slice A 导出时刻的 Document Quality Gate 结果；历史行为 NULL。 */
    private String documentGateStatus;

    /** Slice A 导出时刻是否检出孤立末页；历史行为 NULL。 */
    private Boolean orphanFinalPage;

    /** Slice A 导出时刻是否低于最低可读字号；历史行为 NULL。 */
    private Boolean readabilityTooSmall;

    private LocalDateTime createdAt;
}
