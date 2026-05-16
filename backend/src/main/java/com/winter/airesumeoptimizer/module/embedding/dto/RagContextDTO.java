package com.winter.airesumeoptimizer.module.embedding.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class RagContextDTO {

    private boolean used;

    private Integer matchCount;

    private String contextText;

    private String note;
}
