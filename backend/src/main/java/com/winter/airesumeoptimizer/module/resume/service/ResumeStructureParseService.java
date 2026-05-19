package com.winter.airesumeoptimizer.module.resume.service;

import com.winter.airesumeoptimizer.module.resume.dto.ResumeStructuredContentDTO;
import com.winter.airesumeoptimizer.module.resume.dto.ResumeTextSectionDTO;
import java.util.List;

public interface ResumeStructureParseService {

    ResumeStructuredContentDTO parse(String rawText);

    ResumeStructuredContentDTO parse(String rawText, List<ResumeTextSectionDTO> sections);
}
