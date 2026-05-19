package com.winter.airesumeoptimizer.module.resume.service;

import com.winter.airesumeoptimizer.module.resume.dto.ResumeBlockDTO;
import com.winter.airesumeoptimizer.module.resume.dto.ResumeTextCleanResultDTO;
import java.util.List;

public interface ResumeBlockBuilder {

    List<ResumeBlockDTO> build(ResumeTextCleanResultDTO cleanResult);
}
