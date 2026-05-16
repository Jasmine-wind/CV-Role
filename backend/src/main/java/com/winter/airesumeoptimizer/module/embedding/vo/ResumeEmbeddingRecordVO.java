package com.winter.airesumeoptimizer.module.embedding.vo;

import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ResumeEmbeddingRecordVO {

    private Long id;

    private Long resumeId;

    private Integer chunkIndex;

    private String chunkText;

    private String embeddingModel;

    private Integer embeddingDimension;

    private String embeddingStatus;

    private String errorMessage;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
