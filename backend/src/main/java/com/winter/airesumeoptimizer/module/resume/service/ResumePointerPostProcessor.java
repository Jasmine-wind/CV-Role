package com.winter.airesumeoptimizer.module.resume.service;

import com.winter.airesumeoptimizer.module.resume.dto.ResumeIndexedLineDTO;
import com.winter.airesumeoptimizer.module.resume.dto.ResumeStructuredContentDTO;
import java.util.List;

public interface ResumePointerPostProcessor {

    void attachSourceRefs(ResumeStructuredContentDTO structuredContent, List<ResumeIndexedLineDTO> indexedLines);
}
