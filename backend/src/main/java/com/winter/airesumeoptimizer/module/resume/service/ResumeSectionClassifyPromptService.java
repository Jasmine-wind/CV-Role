package com.winter.airesumeoptimizer.module.resume.service;

import com.winter.airesumeoptimizer.module.resume.dto.ResumeBlockDTO;
import com.winter.airesumeoptimizer.module.resume.dto.ResumeSectionClassifyPromptDTO;
import java.util.List;

public interface ResumeSectionClassifyPromptService {

    ResumeSectionClassifyPromptDTO buildPrompt(List<ResumeBlockDTO> blocks);
}
