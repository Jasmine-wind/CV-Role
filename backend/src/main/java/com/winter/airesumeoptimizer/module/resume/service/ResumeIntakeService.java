package com.winter.airesumeoptimizer.module.resume.service;

import com.winter.airesumeoptimizer.module.resume.vo.ResumeUploadVO;
import com.winter.airesumeoptimizer.module.task.vo.AsyncTaskVO;
import org.springframework.web.multipart.MultipartFile;

/**
 * Accepts a resume and starts all preparation required by the default user flow.
 */
public interface ResumeIntakeService {

    ResumeUploadVO uploadAndPrepare(Long userId, MultipartFile file);

    AsyncTaskVO prepare(Long userId, Long resumeId);
}
