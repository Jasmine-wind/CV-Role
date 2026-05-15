package com.winter.airesumeoptimizer.module.history.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@Schema(description = "AI 历史结果分页")
public class AiResultPageVO {

    @Schema(description = "AI 历史结果列表")
    private List<AiResultRecordVO> records;

    @Schema(description = "当前页码", example = "1")
    private Integer page;

    @Schema(description = "每页数量", example = "10")
    private Integer size;

    @Schema(description = "总记录数", example = "20")
    private Long total;

    @Schema(description = "总页数", example = "2")
    private Integer totalPages;
}
