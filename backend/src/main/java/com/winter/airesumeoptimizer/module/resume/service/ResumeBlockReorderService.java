package com.winter.airesumeoptimizer.module.resume.service;

import com.winter.airesumeoptimizer.module.resume.dto.ResumeBlockDTO;
import java.util.List;

public interface ResumeBlockReorderService {

    List<ResumeBlockDTO> reorder(List<ResumeBlockDTO> blocks);
}
