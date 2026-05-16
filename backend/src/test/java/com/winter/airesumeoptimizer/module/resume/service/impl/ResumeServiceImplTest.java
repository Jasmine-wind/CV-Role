package com.winter.airesumeoptimizer.module.resume.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.winter.airesumeoptimizer.common.exception.BusinessException;
import com.winter.airesumeoptimizer.infra.storage.FileStorageService;
import com.winter.airesumeoptimizer.infra.storage.StoredFile;
import com.winter.airesumeoptimizer.module.analysis.mapper.AiJobMatchResultMapper;
import com.winter.airesumeoptimizer.module.analysis.mapper.ResumeAiAnalysisMapper;
import com.winter.airesumeoptimizer.module.embedding.mapper.ResumeEmbeddingMapper;
import com.winter.airesumeoptimizer.module.job.mapper.JobMatchResultMapper;
import com.winter.airesumeoptimizer.module.resume.entity.Resume;
import com.winter.airesumeoptimizer.module.resume.mapper.ResumeMapper;
import com.winter.airesumeoptimizer.module.resume.mapper.ResumeParseResultMapper;
import com.winter.airesumeoptimizer.module.resume.service.ResumeStructureParseService;
import com.winter.airesumeoptimizer.module.resume.service.ResumeTextExtractionService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;

class ResumeServiceImplTest {

    private final ResumeMapper resumeMapper = mock(ResumeMapper.class);
    private final ResumeParseResultMapper resumeParseResultMapper = mock(ResumeParseResultMapper.class);
    private final ResumeAiAnalysisMapper resumeAiAnalysisMapper = mock(ResumeAiAnalysisMapper.class);
    private final JobMatchResultMapper jobMatchResultMapper = mock(JobMatchResultMapper.class);
    private final AiJobMatchResultMapper aiJobMatchResultMapper = mock(AiJobMatchResultMapper.class);
    private final ResumeEmbeddingMapper resumeEmbeddingMapper = mock(ResumeEmbeddingMapper.class);
    private final FileStorageService fileStorageService = mock(FileStorageService.class);
    private final ResumeTextExtractionService resumeTextExtractionService = mock(ResumeTextExtractionService.class);
    private final ResumeStructureParseService resumeStructureParseService = mock(ResumeStructureParseService.class);
    private final ResumeServiceImpl service = new ResumeServiceImpl(
            resumeMapper,
            resumeParseResultMapper,
            resumeAiAnalysisMapper,
            jobMatchResultMapper,
            aiJobMatchResultMapper,
            resumeEmbeddingMapper,
            fileStorageService,
            resumeTextExtractionService,
            resumeStructureParseService,
            new ObjectMapper(),
            10 * 1024 * 1024);

    @Test
    void uploadShouldSaveResumeMetadata() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "resume.pdf",
                "application/pdf",
                "pdf-content".getBytes());
        StoredFile storedFile = new StoredFile(
                "resumes/1/resume.pdf",
                "resume.pdf",
                "application/pdf",
                file.getSize());

        when(fileStorageService.store(file, "resumes/1")).thenReturn(storedFile);
        when(resumeMapper.insert(any(Resume.class))).thenAnswer(invocation -> {
            Resume resume = invocation.getArgument(0);
            resume.setId(100L);
            return 1;
        });

        var uploadVO = service.upload(1L, file);

        assertThat(uploadVO.getId()).isEqualTo(100L);
        assertThat(uploadVO.getOriginalFilename()).isEqualTo("resume.pdf");
        assertThat(uploadVO.getFileType()).isEqualTo("PDF");
        assertThat(uploadVO.getFileSize()).isEqualTo(file.getSize());
        assertThat(uploadVO.getObjectKey()).isEqualTo("resumes/1/resume.pdf");
        assertThat(uploadVO.getUploadStatus()).isEqualTo("UPLOADED");

        ArgumentCaptor<Resume> resumeCaptor = ArgumentCaptor.forClass(Resume.class);
        verify(resumeMapper).insert(resumeCaptor.capture());
        Resume savedResume = resumeCaptor.getValue();
        assertThat(savedResume.getUserId()).isEqualTo(1L);
        assertThat(savedResume.getStorageType()).isEqualTo("LOCAL");
        assertThat(savedResume.getCreatedAt()).isNotNull();
        assertThat(savedResume.getUpdatedAt()).isNotNull();
    }

    @Test
    void uploadShouldRejectUnsupportedFileExtension() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "resume.txt",
                "text/plain",
                "plain text".getBytes());

        assertThatThrownBy(() -> service.upload(1L, file))
                .isInstanceOf(BusinessException.class)
                .hasMessage("仅支持 PDF、DOC、DOCX 简历文件");

        verify(fileStorageService, never()).store(any(), any());
    }

    @Test
    void uploadShouldRejectOversizedFile() {
        ResumeServiceImpl limitedService = new ResumeServiceImpl(
                resumeMapper,
                resumeParseResultMapper,
                resumeAiAnalysisMapper,
                jobMatchResultMapper,
                aiJobMatchResultMapper,
                resumeEmbeddingMapper,
                fileStorageService,
                resumeTextExtractionService,
                resumeStructureParseService,
                new ObjectMapper(),
                5);
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "resume.pdf",
                "application/pdf",
                "too-large".getBytes());

        assertThatThrownBy(() -> limitedService.upload(1L, file))
                .isInstanceOf(BusinessException.class)
                .hasMessage("简历文件大小不能超过 5 字节");

        verify(fileStorageService, never()).store(any(), any());
    }

    @Test
    void uploadShouldDeleteStoredFileWhenMetadataSaveFails() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "resume.pdf",
                "application/pdf",
                "pdf-content".getBytes());
        StoredFile storedFile = new StoredFile(
                "resumes/1/resume.pdf",
                "resume.pdf",
                "application/pdf",
                file.getSize());

        when(fileStorageService.store(file, "resumes/1")).thenReturn(storedFile);
        when(resumeMapper.insert(any(Resume.class))).thenReturn(0);

        assertThatThrownBy(() -> service.upload(1L, file))
                .isInstanceOf(BusinessException.class)
                .hasMessage("简历元数据保存失败");

        verify(fileStorageService).delete("resumes/1/resume.pdf");
    }

    @Test
    void deleteShouldRemoveChildrenAndStoredFile() {
        Resume resume = new Resume();
        resume.setId(100L);
        resume.setUserId(1L);
        resume.setObjectKey("resumes/1/demo.pdf");

        when(resumeMapper.selectOne(any(Wrapper.class))).thenReturn(resume);

        service.delete(1L, 100L);

        verify(resumeEmbeddingMapper).deleteByResumeId(100L);
        verify(aiJobMatchResultMapper).delete(any(Wrapper.class));
        verify(jobMatchResultMapper).delete(any(Wrapper.class));
        verify(resumeAiAnalysisMapper).delete(any(Wrapper.class));
        verify(resumeParseResultMapper).delete(any(Wrapper.class));
        verify(resumeMapper).deleteById(100L);
        verify(fileStorageService).delete("resumes/1/demo.pdf");
    }
}
