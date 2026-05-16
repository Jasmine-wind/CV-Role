package com.winter.airesumeoptimizer.module.embedding.vo;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class SemanticMatchItemVO {

    private Long resumeEmbeddingId;

    private Integer resumeChunkIndex;

    private String resumeChunkText;

    private Long jobDescriptionEmbeddingId;

    private Integer jobDescriptionChunkIndex;

    private String jobDescriptionChunkText;

    private Double similarityScore;
}
