package com.winter.airesumeoptimizer.module.resume.service.impl;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.winter.airesumeoptimizer.infra.storage.FileStorageService;
import com.winter.airesumeoptimizer.module.analysis.mapper.ResumeAiAnalysisMapper;
import com.winter.airesumeoptimizer.module.job.mapper.JobMatchResultMapper;
import com.winter.airesumeoptimizer.module.resume.entity.Resume;
import com.winter.airesumeoptimizer.module.resume.mapper.ResumeMapper;
import com.winter.airesumeoptimizer.module.resume.mapper.ResumeParseResultMapper;
import com.winter.airesumeoptimizer.module.resume.service.ResumeStructureParseService;
import com.winter.airesumeoptimizer.module.resume.service.ResumeTextExtractionService;
import org.junit.jupiter.api.Test;

class ResumeServiceImplTest {

    private final ResumeMapper resumeMapper = mock(ResumeMapper.class);
    private final ResumeParseResultMapper resumeParseResultMapper = mock(ResumeParseResultMapper.class);
    private final ResumeAiAnalysisMapper resumeAiAnalysisMapper = mock(ResumeAiAnalysisMapper.class);
    private final JobMatchResultMapper jobMatchResultMapper = mock(JobMatchResultMapper.class);
    private final FileStorageService fileStorageService = mock(FileStorageService.class);
    private final ResumeTextExtractionService resumeTextExtractionService = mock(ResumeTextExtractionService.class);
    private final ResumeStructureParseService resumeStructureParseService = mock(ResumeStructureParseService.class);
    private final ResumeServiceImpl service = new ResumeServiceImpl(
            resumeMapper,
            resumeParseResultMapper,
            resumeAiAnalysisMapper,
            jobMatchResultMapper,
            fileStorageService,
            resumeTextExtractionService,
            resumeStructureParseService,
            new ObjectMapper(),
            10 * 1024 * 1024);

    @Test
    void deleteShouldRemoveChildrenAndStoredFile() {
        Resume resume = new Resume();
        resume.setId(100L);
        resume.setUserId(1L);
        resume.setObjectKey("resumes/1/demo.pdf");

        when(resumeMapper.selectOne(any(Wrapper.class))).thenReturn(resume);

        service.delete(1L, 100L);

        verify(jobMatchResultMapper).delete(any(Wrapper.class));
        verify(resumeAiAnalysisMapper).delete(any(Wrapper.class));
        verify(resumeParseResultMapper).delete(any(Wrapper.class));
        verify(resumeMapper).deleteById(100L);
        verify(fileStorageService).delete("resumes/1/demo.pdf");
    }
}
