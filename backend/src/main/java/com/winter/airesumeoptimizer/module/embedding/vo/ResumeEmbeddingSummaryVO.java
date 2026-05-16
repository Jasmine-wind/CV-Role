package com.winter.airesumeoptimizer.module.embedding.vo;

import java.util.List;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ResumeEmbeddingSummaryVO {

    private Long resumeId;

    private String embeddingModel;

    private Integer embeddingDimension;

    private Integer totalChunks;

    private Integer successChunks;

    private Integer failedChunks;

    private String embeddingStatus;

    private List<ResumeEmbeddingRecordVO> records;
}
