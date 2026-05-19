package com.winter.airesumeoptimizer.module.resume.service;

import com.winter.airesumeoptimizer.module.resume.dto.ResumeIndexedLineDTO;
import com.winter.airesumeoptimizer.module.resume.dto.ResumeRawSectionDTO;
import java.util.List;

public interface ResumeLineIndexer {

    List<ResumeIndexedLineDTO> index(List<ResumeRawSectionDTO> rawSections);
}
