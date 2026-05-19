package com.winter.airesumeoptimizer.module.resume.service;

import com.winter.airesumeoptimizer.module.resume.dto.ResumeIndexedLineDTO;
import com.winter.airesumeoptimizer.module.resume.dto.ResumeSourceRefDTO;
import java.util.List;

public interface ResumePointerValidator {

    boolean validLineId(Integer lineId, List<ResumeIndexedLineDTO> indexedLines);

    boolean validLineRange(Integer startLine, Integer endLine, List<ResumeIndexedLineDTO> indexedLines);

    boolean validEntityLine(Integer lineId, List<ResumeIndexedLineDTO> indexedLines);

    ResumeSourceRefDTO sourceRef(Integer startLine, Integer endLine, List<ResumeIndexedLineDTO> indexedLines);
}
