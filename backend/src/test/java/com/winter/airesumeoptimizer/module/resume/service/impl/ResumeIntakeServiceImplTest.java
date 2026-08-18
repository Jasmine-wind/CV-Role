package com.winter.airesumeoptimizer.module.resume.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.winter.airesumeoptimizer.module.resume.service.ResumeAsyncTaskService;
import com.winter.airesumeoptimizer.module.resume.service.ResumeService;
import com.winter.airesumeoptimizer.module.resume.vo.ResumeUploadVO;
import com.winter.airesumeoptimizer.module.task.vo.AsyncTaskVO;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.web.multipart.MultipartFile;

class ResumeIntakeServiceImplTest {

    private final ResumeService resumeService = mock(ResumeService.class);
    private final ResumeAsyncTaskService resumeAsyncTaskService = mock(ResumeAsyncTaskService.class);
    private final ResumeIntakeServiceImpl service = new ResumeIntakeServiceImpl(
            resumeService,
            resumeAsyncTaskService);

    @Test
    void uploadAndPrepareShouldStartDefaultBackgroundPreparation() {
        MultipartFile file = mock(MultipartFile.class);
        LocalDateTime createdAt = LocalDateTime.now();
        when(resumeService.upload(1L, file)).thenReturn(ResumeUploadVO.builder()
                .id(10L)
                .originalFilename("resume.pdf")
                .fileType("PDF")
                .fileSize(1024L)
                .uploadStatus("UPLOADED")
                .createdAt(createdAt)
                .build());
        when(resumeAsyncTaskService.submitParseTask(1L, 10L, null)).thenReturn(AsyncTaskVO.builder()
                .taskId(100L)
                .status("PENDING")
                .build());

        ResumeUploadVO result = service.uploadAndPrepare(1L, file);

        assertThat(result.getId()).isEqualTo(10L);
        assertThat(result.getParseStatus()).isEqualTo("PENDING");
        assertThat(result.getPreparationTaskId()).isEqualTo(100L);
        assertThat(result.getCreatedAt()).isEqualTo(createdAt);
        verify(resumeAsyncTaskService).submitParseTask(1L, 10L, null);
    }

    @Test
    void prepareShouldSubmitDefaultPreparationTask() {
        when(resumeAsyncTaskService.submitParseTask(1L, 10L, null)).thenReturn(AsyncTaskVO.builder()
                .taskId(100L)
                .status("PENDING")
                .build());

        AsyncTaskVO result = service.prepare(1L, 10L);

        assertThat(result.getTaskId()).isEqualTo(100L);
        verify(resumeAsyncTaskService).submitParseTask(1L, 10L, null);
    }

    @Test
    void uploadAndPrepareShouldKeepUploadedResumeWhenTaskSubmissionFails() {
        MultipartFile file = mock(MultipartFile.class);
        when(resumeService.upload(1L, file)).thenReturn(ResumeUploadVO.builder()
                .id(10L)
                .originalFilename("resume.pdf")
                .fileType("PDF")
                .fileSize(1024L)
                .uploadStatus("UPLOADED")
                .createdAt(LocalDateTime.now())
                .build());
        when(resumeAsyncTaskService.submitParseTask(1L, 10L, null))
                .thenThrow(new IllegalStateException("task store unavailable"));

        ResumeUploadVO result = service.uploadAndPrepare(1L, file);

        assertThat(result.getId()).isEqualTo(10L);
        assertThat(result.getParseStatus()).isEqualTo("PENDING");
        assertThat(result.getPreparationTaskId()).isNull();
    }
}
