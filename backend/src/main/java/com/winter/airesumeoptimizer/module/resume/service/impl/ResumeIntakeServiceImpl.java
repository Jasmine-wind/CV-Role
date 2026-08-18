package com.winter.airesumeoptimizer.module.resume.service.impl;

import com.winter.airesumeoptimizer.module.resume.service.ResumeAsyncTaskService;
import com.winter.airesumeoptimizer.module.resume.service.ResumeIntakeService;
import com.winter.airesumeoptimizer.module.resume.service.ResumeService;
import com.winter.airesumeoptimizer.module.resume.vo.ResumeUploadVO;
import com.winter.airesumeoptimizer.module.task.vo.AsyncTaskVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class ResumeIntakeServiceImpl implements ResumeIntakeService {

    private static final Logger log = LoggerFactory.getLogger(ResumeIntakeServiceImpl.class);

    private final ResumeService resumeService;
    private final ResumeAsyncTaskService resumeAsyncTaskService;

    public ResumeIntakeServiceImpl(
            ResumeService resumeService,
            ResumeAsyncTaskService resumeAsyncTaskService) {
        this.resumeService = resumeService;
        this.resumeAsyncTaskService = resumeAsyncTaskService;
    }

    @Override
    public ResumeUploadVO uploadAndPrepare(Long userId, MultipartFile file) {
        ResumeUploadVO uploaded = resumeService.upload(userId, file);
        Long preparationTaskId = null;
        try {
            AsyncTaskVO preparationTask = prepare(userId, uploaded.getId());
            preparationTaskId = preparationTask.getTaskId();
        } catch (RuntimeException exception) {
            log.warn(
                    "Resume uploaded but preparation task submission failed: userId={}, resumeId={}, exceptionType={}",
                    userId,
                    uploaded.getId(),
                    exception.getClass().getSimpleName());
        }
        return ResumeUploadVO.builder()
                .id(uploaded.getId())
                .originalFilename(uploaded.getOriginalFilename())
                .fileType(uploaded.getFileType())
                .fileSize(uploaded.getFileSize())
                .uploadStatus(uploaded.getUploadStatus())
                .parseStatus("PENDING")
                .preparationTaskId(preparationTaskId)
                .createdAt(uploaded.getCreatedAt())
                .build();
    }

    @Override
    public AsyncTaskVO prepare(Long userId, Long resumeId) {
        return resumeAsyncTaskService.submitParseTask(userId, resumeId, null);
    }
}
