package com.winter.airesumeoptimizer.module.history.vo;

import java.util.List;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class HistoryPageVO {

    private List<HistoryListVO> records;

    private Integer page;

    private Integer size;

    private Long total;

    private Integer totalPages;
}
