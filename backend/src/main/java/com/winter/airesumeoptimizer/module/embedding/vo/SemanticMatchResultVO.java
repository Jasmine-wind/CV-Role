package com.winter.airesumeoptimizer.module.embedding.vo;

import java.util.List;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class SemanticMatchResultVO {

    private Long resumeId;

    private Long jobDescriptionId;

    private String embeddingModel;

    private Integer embeddingDimension;

    private Integer topK;

    private Double overallSimilarity;

    private List<SemanticMatchItemVO> matches;
}
