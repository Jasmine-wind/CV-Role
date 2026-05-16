package com.winter.airesumeoptimizer.module.embedding.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SemanticMatchRow {

    private Long resumeEmbeddingId;

    private Integer resumeChunkIndex;

    private String resumeChunkText;

    private Long jobDescriptionEmbeddingId;

    private Integer jobDescriptionChunkIndex;

    private String jobDescriptionChunkText;

    private Double similarityScore;
}
