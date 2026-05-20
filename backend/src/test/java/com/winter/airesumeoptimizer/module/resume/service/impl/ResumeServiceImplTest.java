package com.winter.airesumeoptimizer.module.resume.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.winter.airesumeoptimizer.common.exception.BusinessException;
import com.winter.airesumeoptimizer.infra.storage.FileStorageService;
import com.winter.airesumeoptimizer.infra.storage.StoreFileCommand;
import com.winter.airesumeoptimizer.infra.storage.StoredFile;
import com.winter.airesumeoptimizer.module.analysis.mapper.AiJobMatchResultMapper;
import com.winter.airesumeoptimizer.module.analysis.mapper.AiResumeSuggestionMapper;
import com.winter.airesumeoptimizer.module.analysis.mapper.AiRewriteSuggestionMapper;
import com.winter.airesumeoptimizer.module.analysis.mapper.ResumeAiAnalysisMapper;
import com.winter.airesumeoptimizer.module.embedding.mapper.ResumeEmbeddingMapper;
import com.winter.airesumeoptimizer.module.job.mapper.JobMatchResultMapper;
import com.winter.airesumeoptimizer.module.resume.config.ResumeParseProperties;
import com.winter.airesumeoptimizer.module.resume.entity.Resume;
import com.winter.airesumeoptimizer.module.resume.mapper.ResumeMapper;
import com.winter.airesumeoptimizer.module.resume.mapper.ResumeParseResultMapper;
import com.winter.airesumeoptimizer.module.resume.dto.ResumeBlockDTO;
import com.winter.airesumeoptimizer.module.resume.dto.ResumeParseOptionsDTO;
import com.winter.airesumeoptimizer.module.resume.dto.ResumeParseQualityResultDTO;
import com.winter.airesumeoptimizer.module.resume.dto.ResumeAiStructuredParseResultDTO;
import com.winter.airesumeoptimizer.module.resume.dto.ResumeSectionClassificationDTO;
import com.winter.airesumeoptimizer.module.resume.dto.ResumeSectionClassifyResultDTO;
import com.winter.airesumeoptimizer.module.resume.dto.ResumeStructuredContentDTO;
import com.winter.airesumeoptimizer.module.resume.dto.ResumeTextCleanResultDTO;
import com.winter.airesumeoptimizer.module.resume.service.ResumeAiSectionClassifier;
import com.winter.airesumeoptimizer.module.resume.service.ResumeAiStructuredParser;
import com.winter.airesumeoptimizer.module.resume.service.ResumeBlockBuilder;
import com.winter.airesumeoptimizer.module.resume.service.ResumeBlockReorderService;
import com.winter.airesumeoptimizer.module.resume.service.ResumeDisplayModelService;
import com.winter.airesumeoptimizer.module.resume.service.ResumeLineIndexer;
import com.winter.airesumeoptimizer.module.resume.service.ResumeParseQualityCheckService;
import com.winter.airesumeoptimizer.module.resume.service.ResumePointerPostProcessor;
import com.winter.airesumeoptimizer.module.resume.service.ResumeStructureParseService;
import com.winter.airesumeoptimizer.module.resume.service.ResumeTextCleanService;
import com.winter.airesumeoptimizer.module.resume.service.ResumeTextExtractionService;
import com.winter.airesumeoptimizer.module.resume.service.ResumeTextQualityCheckService;
import com.winter.airesumeoptimizer.module.resume.dto.ResumeTextSectionDTO;
import com.winter.airesumeoptimizer.module.resume.dto.ResumeTextQualityResultDTO;
import com.winter.airesumeoptimizer.module.resume.entity.ResumeParseResult;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;

class ResumeServiceImplTest {

    private final ResumeMapper resumeMapper = mock(ResumeMapper.class);
    private final ResumeParseResultMapper resumeParseResultMapper = mock(ResumeParseResultMapper.class);
    private final ResumeAiAnalysisMapper resumeAiAnalysisMapper = mock(ResumeAiAnalysisMapper.class);
    private final JobMatchResultMapper jobMatchResultMapper = mock(JobMatchResultMapper.class);
    private final AiJobMatchResultMapper aiJobMatchResultMapper = mock(AiJobMatchResultMapper.class);
    private final AiResumeSuggestionMapper aiResumeSuggestionMapper = mock(AiResumeSuggestionMapper.class);
    private final AiRewriteSuggestionMapper aiRewriteSuggestionMapper = mock(AiRewriteSuggestionMapper.class);
    private final ResumeEmbeddingMapper resumeEmbeddingMapper = mock(ResumeEmbeddingMapper.class);
    private final FileStorageService fileStorageService = mock(FileStorageService.class);
    private final ResumeTextExtractionService resumeTextExtractionService = mock(ResumeTextExtractionService.class);
    private final ResumeTextQualityCheckService resumeTextQualityCheckService = mock(ResumeTextQualityCheckService.class);
    private final ResumeTextCleanService resumeTextCleanService = mock(ResumeTextCleanService.class);
    private final ResumeBlockBuilder resumeBlockBuilder = mock(ResumeBlockBuilder.class);
    private final ResumeBlockReorderService resumeBlockReorderService = new ResumeBlockReorderServiceImpl();
    private final ResumeAiSectionClassifier resumeAiSectionClassifier = mock(ResumeAiSectionClassifier.class);
    private final ResumeAiStructuredParser resumeAiStructuredParser = mock(ResumeAiStructuredParser.class);
    private final ResumeStructureParseService resumeStructureParseService = mock(ResumeStructureParseService.class);
    private final ResumeParseQualityCheckService resumeParseQualityCheckService = mock(ResumeParseQualityCheckService.class);
    private final ResumeDisplayModelService resumeDisplayModelService = mock(ResumeDisplayModelService.class);
    private final ResumeLineIndexer resumeLineIndexer = mock(ResumeLineIndexer.class);
    private final ResumePointerPostProcessor resumePointerPostProcessor = mock(ResumePointerPostProcessor.class);
    private final ResumeParseProperties resumeParseProperties = new ResumeParseProperties();
    private final ResumeServiceImpl service = new ResumeServiceImpl(
            resumeMapper,
            resumeParseResultMapper,
            resumeAiAnalysisMapper,
            jobMatchResultMapper,
            aiJobMatchResultMapper,
            aiResumeSuggestionMapper,
            aiRewriteSuggestionMapper,
            resumeEmbeddingMapper,
            fileStorageService,
            resumeTextExtractionService,
            resumeTextQualityCheckService,
            resumeTextCleanService,
            resumeBlockBuilder,
            resumeBlockReorderService,
            resumeAiSectionClassifier,
            resumeAiStructuredParser,
            resumeStructureParseService,
            resumeParseQualityCheckService,
            resumeDisplayModelService,
            resumeLineIndexer,
            resumePointerPostProcessor,
            resumeParseProperties,
            new ObjectMapper(),
            10 * 1024 * 1024,
            false);

    @Test
    void uploadShouldSaveResumeMetadata() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "resume.pdf",
                "application/pdf",
                pdfBytes());
        StoredFile storedFile = new StoredFile(
                "resumes/1/resume.pdf",
                "resume.pdf",
                "application/pdf",
                file.getSize(),
                "LOCAL");

        when(fileStorageService.store(any(StoreFileCommand.class))).thenReturn(storedFile);
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
        assertThat(uploadVO.getUploadStatus()).isEqualTo("UPLOADED");

        ArgumentCaptor<Resume> resumeCaptor = ArgumentCaptor.forClass(Resume.class);
        verify(resumeMapper).insert(resumeCaptor.capture());
        Resume savedResume = resumeCaptor.getValue();
        assertThat(savedResume.getUserId()).isEqualTo(1L);
        assertThat(savedResume.getStorageType()).isEqualTo("LOCAL");
        assertThat(savedResume.getCreatedAt()).isNotNull();
        assertThat(savedResume.getUpdatedAt()).isNotNull();

        ArgumentCaptor<StoreFileCommand> commandCaptor = ArgumentCaptor.forClass(StoreFileCommand.class);
        verify(fileStorageService).store(commandCaptor.capture());
        StoreFileCommand command = commandCaptor.getValue();
        assertThat(command.userId()).isEqualTo(1L);
        assertThat(command.bizType()).isEqualTo("resumes");
        assertThat(command.originalFilename()).isEqualTo("resume.pdf");
        assertThat(command.contentType()).isEqualTo("application/pdf");
        assertThat(command.size()).isEqualTo(file.getSize());
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

        verify(fileStorageService, never()).store(any(StoreFileCommand.class));
    }

    @Test
    void uploadShouldRejectOversizedFile() {
        ResumeServiceImpl limitedService = new ResumeServiceImpl(
                resumeMapper,
                resumeParseResultMapper,
                resumeAiAnalysisMapper,
                jobMatchResultMapper,
                aiJobMatchResultMapper,
                aiResumeSuggestionMapper,
                aiRewriteSuggestionMapper,
                resumeEmbeddingMapper,
                fileStorageService,
                resumeTextExtractionService,
                resumeTextQualityCheckService,
                resumeTextCleanService,
                resumeBlockBuilder,
                resumeBlockReorderService,
                resumeAiSectionClassifier,
                resumeAiStructuredParser,
                resumeStructureParseService,
                resumeParseQualityCheckService,
                resumeDisplayModelService,
                resumeLineIndexer,
                resumePointerPostProcessor,
                resumeParseProperties,
                new ObjectMapper(),
                5,
                false);
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "resume.pdf",
                "application/pdf",
                pdfBytes());

        assertThatThrownBy(() -> limitedService.upload(1L, file))
                .isInstanceOf(BusinessException.class)
                .hasMessage("简历文件大小不能超过 5 字节");

        verify(fileStorageService, never()).store(any(StoreFileCommand.class));
    }

    @Test
    void uploadShouldRejectContentTypeMismatch() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "resume.docx",
                "application/pdf",
                docxBytes());

        assertThatThrownBy(() -> service.upload(1L, file))
                .isInstanceOf(BusinessException.class)
                .hasMessage("文件类型与扩展名不匹配");

        verify(fileStorageService, never()).store(any(StoreFileCommand.class));
    }

    @Test
    void uploadShouldAllowBrowserFallbackContentType() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "resume.docx",
                "application/octet-stream",
                docxBytes());
        StoredFile storedFile = new StoredFile(
                "resumes/1/resume.docx",
                "resume.docx",
                "application/octet-stream",
                file.getSize(),
                "LOCAL");

        when(fileStorageService.store(any(StoreFileCommand.class))).thenReturn(storedFile);
        when(resumeMapper.insert(any(Resume.class))).thenAnswer(invocation -> {
            Resume resume = invocation.getArgument(0);
            resume.setId(101L);
            return 1;
        });

        var uploadVO = service.upload(1L, file);

        assertThat(uploadVO.getId()).isEqualTo(101L);
        assertThat(uploadVO.getFileType()).isEqualTo("DOCX");
        verify(fileStorageService).store(any(StoreFileCommand.class));
    }

    @Test
    void uploadShouldAllowContentTypeWithParameters() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "resume.pdf",
                "application/pdf; charset=binary",
                pdfBytes());
        StoredFile storedFile = new StoredFile(
                "resumes/1/resume.pdf",
                "resume.pdf",
                "application/pdf; charset=binary",
                file.getSize(),
                "LOCAL");

        when(fileStorageService.store(any(StoreFileCommand.class))).thenReturn(storedFile);
        when(resumeMapper.insert(any(Resume.class))).thenAnswer(invocation -> {
            Resume resume = invocation.getArgument(0);
            resume.setId(102L);
            return 1;
        });

        var uploadVO = service.upload(1L, file);

        assertThat(uploadVO.getId()).isEqualTo(102L);
        assertThat(uploadVO.getFileType()).isEqualTo("PDF");
    }

    @Test
    void uploadShouldDeleteStoredFileWhenMetadataSaveFails() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "resume.pdf",
                "application/pdf",
                pdfBytes());
        StoredFile storedFile = new StoredFile(
                "resumes/1/resume.pdf",
                "resume.pdf",
                "application/pdf",
                file.getSize(),
                "LOCAL");

        when(fileStorageService.store(any(StoreFileCommand.class))).thenReturn(storedFile);
        when(resumeMapper.insert(any(Resume.class))).thenReturn(0);

        assertThatThrownBy(() -> service.upload(1L, file))
                .isInstanceOf(BusinessException.class)
                .hasMessage("简历元数据保存失败");

        verify(fileStorageService).delete("resumes/1/resume.pdf");
    }

    @Test
    void uploadShouldRejectContentSignatureMismatch() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "resume.pdf",
                "application/pdf",
                "not-a-pdf".getBytes());

        assertThatThrownBy(() -> service.upload(1L, file))
                .isInstanceOf(BusinessException.class)
                .hasMessage("文件内容与扩展名不匹配");

        verify(fileStorageService, never()).store(any(StoreFileCommand.class));
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
        verify(aiRewriteSuggestionMapper).delete(any(Wrapper.class));
        verify(aiResumeSuggestionMapper).delete(any(Wrapper.class));
        verify(aiJobMatchResultMapper).delete(any(Wrapper.class));
        verify(jobMatchResultMapper).delete(any(Wrapper.class));
        verify(resumeAiAnalysisMapper).delete(any(Wrapper.class));
        verify(resumeParseResultMapper).delete(any(Wrapper.class));
        verify(resumeMapper).deleteById(100L);
        verify(fileStorageService).delete("resumes/1/demo.pdf");
    }

    private byte[] pdfBytes() {
        return "%PDF-1.4\nresume-content".getBytes();
    }

    private byte[] docxBytes() {
        try {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            try (ZipOutputStream zipOutputStream = new ZipOutputStream(outputStream)) {
                zipOutputStream.putNextEntry(new ZipEntry("[Content_Types].xml"));
                zipOutputStream.write("<Types/>".getBytes());
                zipOutputStream.closeEntry();
                zipOutputStream.putNextEntry(new ZipEntry("word/document.xml"));
                zipOutputStream.write("<document/>".getBytes());
                zipOutputStream.closeEntry();
            }
            return outputStream.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to create test docx bytes", exception);
        }
    }

    @Test
    void deleteShouldRejectUnownedResumeWithoutDeletingFile() {
        when(resumeMapper.selectOne(any(Wrapper.class))).thenReturn(null);

        assertThatThrownBy(() -> service.delete(2L, 100L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("简历不存在");

        verify(fileStorageService, never()).delete(anyString());
        verify(resumeMapper, never()).deleteById(100L);
    }

    @Test
    void parseShouldRejectUnownedResumeWithoutReadingFile() {
        when(resumeMapper.selectOne(any(Wrapper.class))).thenReturn(null);

        assertThatThrownBy(() -> service.parse(2L, 100L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("简历不存在");

        verify(resumeTextExtractionService, never()).extractText(anyString(), anyString());
    }

    @Test
    void parseShouldStopWhenTextQualityFailed() {
        Resume resume = new Resume();
        resume.setId(100L);
        resume.setUserId(1L);
        resume.setFileType("PDF");
        resume.setObjectKey("resumes/1/demo.pdf");

        when(resumeMapper.selectOne(any(Wrapper.class))).thenReturn(resume);
        when(resumeTextExtractionService.extractText("resumes/1/demo.pdf", "PDF")).thenReturn("");
        when(resumeTextQualityCheckService.check("", "PDF")).thenReturn(ResumeTextQualityResultDTO.builder()
                .status("FAILED")
                .issues(List.of("EMPTY_TEXT", "SCANNED_PDF"))
                .message("未能从文件中提取到有效文本，请确认文件不是扫描版图片 PDF")
                .build());
        when(resumeParseResultMapper.selectOne(any(Wrapper.class))).thenReturn(null);
        when(resumeParseResultMapper.insert(any(ResumeParseResult.class))).thenAnswer(invocation -> {
            ResumeParseResult result = invocation.getArgument(0);
            result.setId(200L);
            return 1;
        });

        var result = service.parse(1L, 100L);

        assertThat(result.getParseStatus()).isEqualTo("FAILED");
        assertThat(result.getTextQualityStatus()).isEqualTo("FAILED");
        assertThat(result.getTextQualityIssues()).contains("EMPTY_TEXT", "SCANNED_PDF");
        assertThat(result.getErrorMessage()).contains("扫描版图片 PDF");
        verify(resumeStructureParseService, never()).parse(anyString());
    }

    @Test
    void parseShouldContinueWhenTextQualityWarning() {
        Resume resume = new Resume();
        resume.setId(100L);
        resume.setUserId(1L);
        resume.setFileType("DOCX");
        resume.setObjectKey("resumes/1/demo.docx");

        when(resumeMapper.selectOne(any(Wrapper.class))).thenReturn(resume);
        when(resumeTextExtractionService.extractText("resumes/1/demo.docx", "DOCX")).thenReturn("Java SQL 项目");
        when(resumeTextQualityCheckService.check("Java SQL 项目", "DOCX")).thenReturn(ResumeTextQualityResultDTO.builder()
                .status("WARNING")
                .issues(List.of("TOO_SHORT_TEXT"))
                .message("提取文本过短，解析结果可能不完整")
                .build());
        when(resumeTextCleanService.cleanAndSplitSections("Java SQL 项目")).thenReturn(ResumeTextCleanResultDTO.builder()
                .cleanedText("Java SQL 项目")
                .sections(List.of(ResumeTextSectionDTO.builder()
                        .sectionType("GENERAL")
                        .heading("未识别章节")
                        .lines(List.of("Java SQL 项目"))
                        .build()))
                .build());
        ResumeStructuredContentDTO structuredContent = ResumeStructuredContentDTO.builder()
                .skills(List.of("Java", "SQL"))
                .build();
        when(resumeStructureParseService.parse(anyString(), anyList())).thenReturn(structuredContent);
        when(resumeParseQualityCheckService.check(any(), any(), any())).thenReturn(ResumeParseQualityResultDTO.builder()
                .status("WARNING")
                .warnings(List.of("PROJECTS_MISSING", "SECTION_TOO_FEW"))
                .message("未识别到项目经历，可能影响后续简历诊断和岗位匹配")
                .score(75)
                .build());
        when(resumeParseResultMapper.selectOne(any(Wrapper.class))).thenReturn(null);
        when(resumeParseResultMapper.insert(any(ResumeParseResult.class))).thenAnswer(invocation -> {
            ResumeParseResult result = invocation.getArgument(0);
            result.setId(200L);
            return 1;
        });

        var result = service.parse(1L, 100L);

        assertThat(result.getParseStatus()).isEqualTo("SUCCESS");
        assertThat(result.getTextQualityStatus()).isEqualTo("WARNING");
        assertThat(result.getTextQualityIssues()).contains("TOO_SHORT_TEXT");
        assertThat(result.getTextQualityMessage()).contains("提取文本过短");
        assertThat(result.getCleanedText()).isEqualTo("Java SQL 项目");
        assertThat(result.getSectionResult()).contains("GENERAL");
        assertThat(result.getParseQualityStatus()).isEqualTo("WARNING");
        assertThat(result.getParseQualityWarnings()).contains("PROJECTS_MISSING");
        assertThat(result.getParseQualityScore()).isEqualTo(75);
        verify(resumeStructureParseService).parse(anyString(), anyList());
    }

    @Test
    void parseShouldUseCleanedTextAndSaveSections() {
        Resume resume = new Resume();
        resume.setId(100L);
        resume.setUserId(1L);
        resume.setFileType("PDF");
        resume.setObjectKey("resumes/1/demo.pdf");

        String extractedText = "专业技能：  Java   Spring Boot\n项目经历\n• AI 简历优化系统";
        String cleanedText = "专业技能： Java Spring Boot\n项目经历\n- AI 简历优化系统";
        when(resumeMapper.selectOne(any(Wrapper.class))).thenReturn(resume);
        when(resumeTextExtractionService.extractText("resumes/1/demo.pdf", "PDF")).thenReturn(extractedText);
        when(resumeTextQualityCheckService.check(extractedText, "PDF")).thenReturn(ResumeTextQualityResultDTO.builder()
                .status("GOOD")
                .issues(List.of())
                .message("文本质量正常")
                .build());
        when(resumeTextCleanService.cleanAndSplitSections(extractedText)).thenReturn(ResumeTextCleanResultDTO.builder()
                .cleanedText(cleanedText)
                .sections(List.of(
                        ResumeTextSectionDTO.builder()
                                .sectionType("SKILLS")
                                .heading("专业技能")
                                .lines(List.of("Java Spring Boot"))
                                .build(),
                        ResumeTextSectionDTO.builder()
                                .sectionType("PROJECTS")
                                .heading("项目经历")
                                .lines(List.of("- AI 简历优化系统"))
                                .build()))
                .build());
        ResumeStructuredContentDTO structuredContent = ResumeStructuredContentDTO.builder()
                .skills(List.of("Java", "Spring Boot"))
                .projects(List.of("AI 简历优化系统"))
                .sections(List.of())
                .build();
        when(resumeStructureParseService.parse(anyString(), anyList())).thenReturn(structuredContent);
        when(resumeParseQualityCheckService.check(any(), any(), any())).thenReturn(ResumeParseQualityResultDTO.builder()
                .status("GOOD")
                .warnings(List.of())
                .message("解析结果质量正常")
                .score(100)
                .build());
        when(resumeParseResultMapper.selectOne(any(Wrapper.class))).thenReturn(null);
        when(resumeParseResultMapper.insert(any(ResumeParseResult.class))).thenAnswer(invocation -> {
            ResumeParseResult result = invocation.getArgument(0);
            result.setId(200L);
            return 1;
        });

        var result = service.parse(1L, 100L);

        assertThat(result.getParseStatus()).isEqualTo("SUCCESS");
        assertThat(result.getExtractedText()).isEqualTo(extractedText);
        assertThat(result.getCleanedText()).isEqualTo(cleanedText);
        assertThat(result.getSectionResult()).contains("SKILLS", "PROJECTS");
        assertThat(result.getParseQualityStatus()).isEqualTo("GOOD");
        assertThat(result.getParseQualityMessage()).isEqualTo("解析结果质量正常");
        verify(resumeStructureParseService).parse(anyString(), anyList());
    }

    @Test
    void parseShouldNotFailWhenDisplayModelBuildFails() {
        Resume resume = new Resume();
        resume.setId(100L);
        resume.setUserId(1L);
        resume.setFileType("PDF");
        resume.setObjectKey("resumes/1/demo.pdf");

        String extractedText = "专业技能\nJava Spring Boot\n项目经历\nAI 简历优化系统";
        when(resumeMapper.selectOne(any(Wrapper.class))).thenReturn(resume);
        when(resumeTextExtractionService.extractText("resumes/1/demo.pdf", "PDF")).thenReturn(extractedText);
        when(resumeTextQualityCheckService.check(extractedText, "PDF")).thenReturn(ResumeTextQualityResultDTO.builder()
                .status("GOOD")
                .issues(List.of())
                .message("文本质量正常")
                .build());
        ResumeTextCleanResultDTO cleanResult = ResumeTextCleanResultDTO.builder()
                .cleanedText(extractedText)
                .sections(List.of(
                        ResumeTextSectionDTO.builder()
                                .sectionType("SKILLS")
                                .heading("专业技能")
                                .lines(List.of("Java Spring Boot"))
                                .build(),
                        ResumeTextSectionDTO.builder()
                                .sectionType("PROJECTS")
                                .heading("项目经历")
                                .lines(List.of("AI 简历优化系统"))
                                .build()))
                .build();
        when(resumeTextCleanService.cleanAndSplitSections(extractedText)).thenReturn(cleanResult);
        ResumeStructuredContentDTO structuredContent = ResumeStructuredContentDTO.builder()
                .skills(List.of("Java", "Spring Boot"))
                .projects(List.of("AI 简历优化系统"))
                .sections(cleanResult.getSections())
                .build();
        when(resumeStructureParseService.parse(anyString(), anyList())).thenReturn(structuredContent);
        when(resumeParseQualityCheckService.check(any(), any(), any())).thenReturn(ResumeParseQualityResultDTO.builder()
                .status("GOOD")
                .warnings(List.of())
                .message("解析结果质量正常")
                .score(100)
                .build());
        when(resumeDisplayModelService.buildRuleDisplayModel(eq(100L), any())).thenThrow(new IllegalStateException("display failed"));
        when(resumeParseResultMapper.selectOne(any(Wrapper.class))).thenReturn(null);
        when(resumeParseResultMapper.insert(any(ResumeParseResult.class))).thenAnswer(invocation -> {
            ResumeParseResult result = invocation.getArgument(0);
            result.setId(200L);
            return 1;
        });

        var result = service.parse(1L, 100L);

        assertThat(result.getParseStatus()).isEqualTo("SUCCESS");
        assertThat(result.getErrorMessage()).isNull();
        assertThat(result.getStructuredJson()).contains("DISPLAY_MODEL_FAILED");
    }

    @Test
    void parseShouldPassAiParseOptionsAndSaveAiMetadata() {
        Resume resume = new Resume();
        resume.setId(100L);
        resume.setUserId(1L);
        resume.setFileType("PDF");
        resume.setObjectKey("resumes/1/demo.pdf");

        String extractedText = "张三\n专业技能 Java Spring Boot";
        when(resumeMapper.selectOne(any(Wrapper.class))).thenReturn(resume);
        when(resumeTextExtractionService.extractText("resumes/1/demo.pdf", "PDF")).thenReturn(extractedText);
        when(resumeTextQualityCheckService.check(extractedText, "PDF")).thenReturn(ResumeTextQualityResultDTO.builder()
                .status("GOOD")
                .issues(List.of())
                .message("文本质量正常")
                .build());
        when(resumeTextCleanService.cleanAndSplitSections(extractedText)).thenReturn(ResumeTextCleanResultDTO.builder()
                .cleanedText(extractedText)
                .sections(List.of(ResumeTextSectionDTO.builder()
                        .sectionType("GENERAL")
                        .heading("未识别章节")
                        .lines(List.of("张三", "专业技能 Java Spring Boot"))
                        .build()))
                .build());
        when(resumeAiSectionClassifier.classify(eq(100L), anyList(), eq(true))).thenReturn(ResumeSectionClassifyResultDTO.builder()
                .aiEnabled(true)
                .applied(false)
                .fallbackReason("AI 章节归类失败：timeout")
                .build());
        ResumeStructuredContentDTO structuredContent = ResumeStructuredContentDTO.builder()
                .name("张三")
                .skills(List.of("Java", "Spring Boot"))
                .build();
        when(resumeStructureParseService.parse(anyString(), anyList())).thenReturn(structuredContent);
        when(resumeAiStructuredParser.parse(anyList(), any(), anyList(), eq(false))).thenReturn(ResumeAiStructuredParseResultDTO.builder()
                .aiEnabled(false)
                .applied(false)
                .fallbackReason("AI 结构化补全未开启")
                .structuredContent(structuredContent)
                .qualityWarnings(List.of())
                .build());
        when(resumeParseQualityCheckService.check(any(), any(), any())).thenReturn(ResumeParseQualityResultDTO.builder()
                .status("GOOD")
                .warnings(List.of())
                .message("解析结果质量正常")
                .score(100)
                .build());
        when(resumeParseResultMapper.selectOne(any(Wrapper.class))).thenReturn(null);
        when(resumeParseResultMapper.insert(any(ResumeParseResult.class))).thenAnswer(invocation -> {
            ResumeParseResult result = invocation.getArgument(0);
            result.setId(200L);
            return 1;
        });

        service.parse(1L, 100L, ResumeParseOptionsDTO.builder()
                .aiSectionClassifyEnabled(true)
                .aiStructuredParseEnabled(true)
                .build());

        verify(resumeAiSectionClassifier).classify(eq(100L), anyList(), eq(true));
        verify(resumeAiStructuredParser).parse(anyList(), any(), anyList(), eq(false));
        ArgumentCaptor<ResumeParseResult> resultCaptor = ArgumentCaptor.forClass(ResumeParseResult.class);
        verify(resumeParseResultMapper).insert(resultCaptor.capture());
        assertThat(resultCaptor.getValue().getStructuredJson())
                .contains("\"parseMode\":\"BALANCED\"")
                .contains("\"parserVersion\":\"" + ResumeParseVersions.PARSER_VERSION + "\"")
                .contains("\"aiSectionClassifyEnabled\":true")
                .contains("\"aiStructuredParseEnabled\":false")
                .contains("AI 章节归类失败")
                .contains("AI 结构化补全未开启");
    }

    @Test
    void parseShouldStoreSkippedAiStatusWhenRulesConfirmAllBlocks() {
        Resume resume = new Resume();
        resume.setId(100L);
        resume.setUserId(1L);
        resume.setFileType("PDF");
        resume.setObjectKey("resumes/1/demo.pdf");

        String extractedText = "专业技能\nJava Spring Boot";
        ResumeTextCleanResultDTO cleanResult = ResumeTextCleanResultDTO.builder()
                .cleanedText(extractedText)
                .sections(List.of(ResumeTextSectionDTO.builder()
                        .sectionType("SKILLS")
                        .heading("专业技能")
                        .lines(List.of("Java Spring Boot"))
                        .build()))
                .build();
        List<ResumeBlockDTO> blocks = List.of(ResumeBlockDTO.builder()
                .index(0)
                .text("Java Spring Boot")
                .sourceSection("SKILLS")
                .sourceSectionConfidence("HIGH")
                .sectionLocked(true)
                .build());

        when(resumeMapper.selectOne(any(Wrapper.class))).thenReturn(resume);
        when(resumeTextExtractionService.extractText("resumes/1/demo.pdf", "PDF")).thenReturn(extractedText);
        when(resumeTextQualityCheckService.check(extractedText, "PDF")).thenReturn(ResumeTextQualityResultDTO.builder()
                .status("GOOD")
                .issues(List.of())
                .message("文本质量正常")
                .build());
        when(resumeTextCleanService.cleanAndSplitSections(extractedText)).thenReturn(cleanResult);
        when(resumeBlockBuilder.build(cleanResult)).thenReturn(blocks);
        when(resumeAiSectionClassifier.classify(eq(100L), anyList(), any())).thenReturn(ResumeSectionClassifyResultDTO.builder()
                .aiEnabled(true)
                .applied(false)
                .aiInvoked(false)
                .aiStatus("SKIPPED")
                .skippedReason("ALL_BLOCKS_RULE_CONFIRMED")
                .fallbackOccurred(false)
                .cacheHit(false)
                .durationMs(1L)
                .classifications(List.of())
                .build());
        ResumeStructuredContentDTO structuredContent = ResumeStructuredContentDTO.builder()
                .skills(List.of("Java", "Spring Boot"))
                .sections(cleanResult.getSections())
                .build();
        when(resumeStructureParseService.parse(anyString(), anyList())).thenReturn(structuredContent);
        when(resumeAiStructuredParser.parse(anyList(), any(), anyList(), eq(false))).thenReturn(ResumeAiStructuredParseResultDTO.builder()
                .aiEnabled(false)
                .applied(false)
                .aiInvoked(false)
                .aiStatus("DISABLED")
                .skippedReason("AI_STRUCTURED_PARSE_DISABLED")
                .fallbackOccurred(false)
                .structuredContent(structuredContent)
                .qualityWarnings(List.of())
                .build());
        when(resumeParseQualityCheckService.check(any(), any(), any())).thenReturn(ResumeParseQualityResultDTO.builder()
                .status("GOOD")
                .warnings(List.of())
                .message("解析结果质量正常")
                .score(100)
                .build());
        when(resumeParseResultMapper.selectOne(any(Wrapper.class))).thenReturn(null);
        when(resumeParseResultMapper.insert(any(ResumeParseResult.class))).thenAnswer(invocation -> {
            ResumeParseResult result = invocation.getArgument(0);
            result.setId(200L);
            return 1;
        });

        service.parse(1L, 100L, ResumeParseOptionsDTO.builder()
                .parseMode("ACCURATE")
                .build());

        ArgumentCaptor<ResumeParseResult> resultCaptor = ArgumentCaptor.forClass(ResumeParseResult.class);
        verify(resumeParseResultMapper).insert(resultCaptor.capture());
        assertThat(resultCaptor.getValue().getStructuredJson())
                .contains("\"parseMeta\"")
                .contains("\"aiStatus\":\"SKIPPED\"")
                .contains("\"aiUsed\":false")
                .contains("\"aiSkippedReason\":\"ALL_BLOCKS_RULE_CONFIRMED\"")
                .contains("\"aiFallbackOccurred\":false")
                .doesNotContain("\"aiStatus\":\"FALLBACK\"");
    }

    @Test
    void parseShouldDisableAiInFastMode() {
        Resume resume = new Resume();
        resume.setId(100L);
        resume.setUserId(1L);
        resume.setFileType("PDF");
        resume.setObjectKey("resumes/1/demo.pdf");

        String extractedText = "张三\n专业技能 Java Spring Boot";
        when(resumeMapper.selectOne(any(Wrapper.class))).thenReturn(resume);
        when(resumeTextExtractionService.extractText("resumes/1/demo.pdf", "PDF")).thenReturn(extractedText);
        when(resumeTextQualityCheckService.check(extractedText, "PDF")).thenReturn(ResumeTextQualityResultDTO.builder()
                .status("GOOD")
                .issues(List.of())
                .message("文本质量正常")
                .build());
        when(resumeTextCleanService.cleanAndSplitSections(extractedText)).thenReturn(ResumeTextCleanResultDTO.builder()
                .cleanedText(extractedText)
                .sections(List.of(ResumeTextSectionDTO.builder()
                        .sectionType("GENERAL")
                        .heading("未识别章节")
                        .lines(List.of("张三", "专业技能 Java Spring Boot"))
                        .build()))
                .build());
        ResumeStructuredContentDTO structuredContent = ResumeStructuredContentDTO.builder()
                .name("张三")
                .skills(List.of("Java", "Spring Boot"))
                .build();
        when(resumeStructureParseService.parse(anyString(), anyList())).thenReturn(structuredContent);
        when(resumeAiStructuredParser.parse(anyList(), any(), anyList(), eq(false))).thenReturn(ResumeAiStructuredParseResultDTO.builder()
                .aiEnabled(false)
                .applied(false)
                .fallbackReason("AI 结构化补全未开启")
                .structuredContent(structuredContent)
                .qualityWarnings(List.of())
                .build());
        when(resumeParseQualityCheckService.check(any(), any(), any())).thenReturn(ResumeParseQualityResultDTO.builder()
                .status("GOOD")
                .warnings(List.of())
                .message("解析结果质量正常")
                .score(100)
                .build());
        when(resumeParseResultMapper.selectOne(any(Wrapper.class))).thenReturn(null);
        when(resumeParseResultMapper.insert(any(ResumeParseResult.class))).thenAnswer(invocation -> {
            ResumeParseResult result = invocation.getArgument(0);
            result.setId(200L);
            return 1;
        });

        service.parse(1L, 100L, ResumeParseOptionsDTO.builder()
                .parseMode("FAST")
                .aiSectionClassifyEnabled(true)
                .aiStructuredParseEnabled(true)
                .build());

        verify(resumeAiSectionClassifier).classify(eq(100L), anyList(), eq(false));
        verify(resumeAiStructuredParser).parse(anyList(), any(), anyList(), eq(false));
        ArgumentCaptor<ResumeParseResult> resultCaptor = ArgumentCaptor.forClass(ResumeParseResult.class);
        verify(resumeParseResultMapper).insert(resultCaptor.capture());
        assertThat(resultCaptor.getValue().getStructuredJson())
                .contains("\"parseMode\":\"FAST\"")
                .contains("\"aiStructuredParseEnabled\":false");
    }

    @Test
    void parseShouldEnableStructuredAiInAccurateModeWhenClassificationApplied() {
        resumeParseProperties.setMode("ACCURATE");
        Resume resume = new Resume();
        resume.setId(100L);
        resume.setUserId(1L);
        resume.setFileType("PDF");
        resume.setObjectKey("resumes/1/demo.pdf");

        String extractedText = "张三\n专业技能 Java Spring Boot";
        when(resumeMapper.selectOne(any(Wrapper.class))).thenReturn(resume);
        when(resumeTextExtractionService.extractText("resumes/1/demo.pdf", "PDF")).thenReturn(extractedText);
        when(resumeTextQualityCheckService.check(extractedText, "PDF")).thenReturn(ResumeTextQualityResultDTO.builder()
                .status("GOOD")
                .issues(List.of())
                .message("文本质量正常")
                .build());
        when(resumeTextCleanService.cleanAndSplitSections(extractedText)).thenReturn(ResumeTextCleanResultDTO.builder()
                .cleanedText(extractedText)
                .sections(List.of(ResumeTextSectionDTO.builder()
                        .sectionType("GENERAL")
                        .heading("未识别章节")
                        .lines(List.of("张三", "专业技能 Java Spring Boot"))
                        .build()))
                .build());
        when(resumeAiSectionClassifier.classify(eq(100L), anyList(), eq(true))).thenReturn(ResumeSectionClassifyResultDTO.builder()
                .aiEnabled(true)
                .applied(true)
                .classifications(List.of(ResumeSectionClassificationDTO.builder()
                        .index(0)
                        .section("BASIC_INFO")
                        .confidence(0.9)
                        .build()))
                .build());
        ResumeStructuredContentDTO structuredContent = ResumeStructuredContentDTO.builder()
                .name("张三")
                .skills(List.of("Java", "Spring Boot"))
                .build();
        when(resumeStructureParseService.parse(anyString(), anyList())).thenReturn(structuredContent);
        when(resumeAiStructuredParser.parse(anyList(), any(), anyList(), eq(true))).thenReturn(ResumeAiStructuredParseResultDTO.builder()
                .aiEnabled(true)
                .applied(false)
                .fallbackReason("AI 未补充字段")
                .structuredContent(structuredContent)
                .qualityWarnings(List.of())
                .build());
        when(resumeParseQualityCheckService.check(any(), any(), any())).thenReturn(ResumeParseQualityResultDTO.builder()
                .status("GOOD")
                .warnings(List.of())
                .message("解析结果质量正常")
                .score(100)
                .build());
        when(resumeParseResultMapper.selectOne(any(Wrapper.class))).thenReturn(null);
        when(resumeParseResultMapper.insert(any(ResumeParseResult.class))).thenAnswer(invocation -> {
            ResumeParseResult result = invocation.getArgument(0);
            result.setId(200L);
            return 1;
        });

        service.parse(1L, 100L);

        verify(resumeAiSectionClassifier).classify(eq(100L), anyList(), eq(true));
        verify(resumeAiStructuredParser).parse(anyList(), any(), anyList(), eq(true));
        ArgumentCaptor<ResumeParseResult> resultCaptor = ArgumentCaptor.forClass(ResumeParseResult.class);
        verify(resumeParseResultMapper).insert(resultCaptor.capture());
        assertThat(resultCaptor.getValue().getStructuredJson())
                .contains("\"parseMode\":\"ACCURATE\"")
                .contains("\"aiStructuredParseEnabled\":true");
    }

    @Test
    void parseShouldKeepLockedSourceSectionWhenAiClassificationConflicts() {
        Resume resume = new Resume();
        resume.setId(100L);
        resume.setUserId(1L);
        resume.setFileType("PDF");
        resume.setObjectKey("resumes/1/demo.pdf");

        String extractedText = "在校经历 Experience\n组织校园技术分享活动，获得校级奖项";
        ResumeTextCleanResultDTO cleanResult = ResumeTextCleanResultDTO.builder()
                .cleanedText(extractedText)
                .sections(List.of(ResumeTextSectionDTO.builder()
                        .sectionType("CAMPUS_EXPERIENCES")
                        .heading("在校经历")
                        .lines(List.of("组织校园技术分享活动，获得校级奖项"))
                        .build()))
                .build();
        List<ResumeBlockDTO> blocks = List.of(ResumeBlockDTO.builder()
                .index(0)
                .originalIndex(0)
                .displayOrder(0)
                .text("组织校园技术分享活动，获得校级奖项")
                .sourceType("cleanedText")
                .sourceSection("CAMPUS_EXPERIENCES")
                .sourceSectionConfidence("HIGH")
                .lockedLevel("HIGH")
                .sectionLocked(true)
                .build());

        when(resumeMapper.selectOne(any(Wrapper.class))).thenReturn(resume);
        when(resumeTextExtractionService.extractText("resumes/1/demo.pdf", "PDF")).thenReturn(extractedText);
        when(resumeTextQualityCheckService.check(extractedText, "PDF")).thenReturn(ResumeTextQualityResultDTO.builder()
                .status("GOOD")
                .issues(List.of())
                .message("文本质量正常")
                .build());
        when(resumeTextCleanService.cleanAndSplitSections(extractedText)).thenReturn(cleanResult);
        when(resumeBlockBuilder.build(cleanResult)).thenReturn(blocks);
        when(resumeAiSectionClassifier.classify(eq(100L), anyList(), any())).thenReturn(ResumeSectionClassifyResultDTO.builder()
                .aiEnabled(true)
                .applied(true)
                .classifications(List.of(ResumeSectionClassificationDTO.builder()
                        .index(0)
                        .section("AWARDS")
                        .confidence(0.95)
                        .build()))
                .build());
        ResumeStructuredContentDTO structuredContent = ResumeStructuredContentDTO.builder()
                .campusExperiences(List.of("组织校园技术分享活动，获得校级奖项"))
                .build();
        when(resumeStructureParseService.parse(anyString(), anyList())).thenReturn(structuredContent);
        when(resumeParseQualityCheckService.check(any(), any(), any())).thenReturn(ResumeParseQualityResultDTO.builder()
                .status("WARNING")
                .warnings(List.of("AI_SECTION_CONFLICT"))
                .message("AI 章节归类与规则章节存在冲突，已按置信度策略处理")
                .score(90)
                .build());
        when(resumeParseResultMapper.selectOne(any(Wrapper.class))).thenReturn(null);
        when(resumeParseResultMapper.insert(any(ResumeParseResult.class))).thenAnswer(invocation -> {
            ResumeParseResult result = invocation.getArgument(0);
            result.setId(200L);
            return 1;
        });

        service.parse(1L, 100L);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ResumeTextSectionDTO>> sectionsCaptor = ArgumentCaptor.forClass(List.class);
        verify(resumeStructureParseService).parse(eq(extractedText), sectionsCaptor.capture());
        List<ResumeTextSectionDTO> finalSections = sectionsCaptor.getValue();
        assertThat(finalSections).singleElement()
                .satisfies(section -> {
                    assertThat(section.getSectionType()).isEqualTo("CAMPUS_EXPERIENCES");
                    assertThat(section.getLines()).containsExactly("组织校园技术分享活动，获得校级奖项");
                });
        assertThat(cleanResult.getSectionConflictWarnings())
                .containsExactly("AI_SECTION_CONFLICT:RULE_SOURCE_SECTION:0:CAMPUS_EXPERIENCES>AWARDS");
        assertThat(finalSections.get(0).getBlocks().get(0).getFinalSectionSource()).isEqualTo("RULE_SOURCE_SECTION");
    }

    @Test
    void parseShouldAllowHighConfidenceAiToOverrideMediumSourceSection() {
        Resume resume = new Resume();
        resume.setId(100L);
        resume.setUserId(1L);
        resume.setFileType("PDF");
        resume.setObjectKey("resumes/1/demo.pdf");

        String extractedText = "在校经历 Experience\n国家励志奖学金";
        ResumeTextCleanResultDTO cleanResult = ResumeTextCleanResultDTO.builder()
                .cleanedText(extractedText)
                .sections(List.of(ResumeTextSectionDTO.builder()
                        .sectionType("CAMPUS_EXPERIENCES")
                        .heading("在校经历")
                        .sourceSectionConfidence("MEDIUM")
                        .lines(List.of("国家励志奖学金"))
                        .build()))
                .build();
        List<ResumeBlockDTO> blocks = List.of(ResumeBlockDTO.builder()
                .index(0)
                .originalIndex(0)
                .displayOrder(0)
                .text("国家励志奖学金")
                .sourceType("cleanedText")
                .sourceSection("CAMPUS_EXPERIENCES")
                .sourceSectionConfidence("MEDIUM")
                .lockedLevel("MEDIUM")
                .sectionLocked(false)
                .build());

        when(resumeMapper.selectOne(any(Wrapper.class))).thenReturn(resume);
        when(resumeTextExtractionService.extractText("resumes/1/demo.pdf", "PDF")).thenReturn(extractedText);
        when(resumeTextQualityCheckService.check(extractedText, "PDF")).thenReturn(ResumeTextQualityResultDTO.builder()
                .status("GOOD")
                .issues(List.of())
                .message("文本质量正常")
                .build());
        when(resumeTextCleanService.cleanAndSplitSections(extractedText)).thenReturn(cleanResult);
        when(resumeBlockBuilder.build(cleanResult)).thenReturn(blocks);
        when(resumeAiSectionClassifier.classify(eq(100L), anyList(), any())).thenReturn(ResumeSectionClassifyResultDTO.builder()
                .aiEnabled(true)
                .applied(true)
                .classifications(List.of(ResumeSectionClassificationDTO.builder()
                        .index(0)
                        .section("AWARDS")
                        .confidence(0.91)
                        .build()))
                .build());
        ResumeStructuredContentDTO structuredContent = ResumeStructuredContentDTO.builder()
                .awards(List.of("国家励志奖学金"))
                .build();
        when(resumeStructureParseService.parse(anyString(), anyList())).thenReturn(structuredContent);
        when(resumeParseQualityCheckService.check(any(), any(), any())).thenReturn(ResumeParseQualityResultDTO.builder()
                .status("WARNING")
                .warnings(List.of("AI_SECTION_CONFLICT"))
                .message("AI 章节归类与规则章节存在冲突")
                .score(90)
                .build());
        when(resumeParseResultMapper.selectOne(any(Wrapper.class))).thenReturn(null);
        when(resumeParseResultMapper.insert(any(ResumeParseResult.class))).thenAnswer(invocation -> {
            ResumeParseResult result = invocation.getArgument(0);
            result.setId(200L);
            return 1;
        });

        service.parse(1L, 100L);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ResumeTextSectionDTO>> sectionsCaptor = ArgumentCaptor.forClass(List.class);
        verify(resumeStructureParseService).parse(eq(extractedText), sectionsCaptor.capture());
        List<ResumeTextSectionDTO> finalSections = sectionsCaptor.getValue();
        assertThat(finalSections).singleElement()
                .satisfies(section -> {
                    assertThat(section.getSectionType()).isEqualTo("AWARDS");
                    assertThat(section.getLines()).containsExactly("国家励志奖学金");
                    assertThat(section.getBlocks().get(0).getFinalSectionSource()).isEqualTo("AI_OVERRIDE");
                    assertThat(section.getBlocks().get(0).getSectionLocked()).isFalse();
                });
        assertThat(cleanResult.getSectionConflictWarnings())
                .containsExactly("AI_SECTION_CONFLICT:AI_OVERRIDE:0:CAMPUS_EXPERIENCES>AWARDS");
    }

    @Test
    void parseShouldMarkFailedWhenStructuredQualityFailed() {
        Resume resume = new Resume();
        resume.setId(100L);
        resume.setUserId(1L);
        resume.setFileType("PDF");
        resume.setObjectKey("resumes/1/demo.pdf");

        String extractedText = "这是一段可提取但没有有效简历字段的长文本";
        when(resumeMapper.selectOne(any(Wrapper.class))).thenReturn(resume);
        when(resumeTextExtractionService.extractText("resumes/1/demo.pdf", "PDF")).thenReturn(extractedText);
        when(resumeTextQualityCheckService.check(extractedText, "PDF")).thenReturn(ResumeTextQualityResultDTO.builder()
                .status("GOOD")
                .issues(List.of())
                .message("文本质量正常")
                .build());
        when(resumeTextCleanService.cleanAndSplitSections(extractedText)).thenReturn(ResumeTextCleanResultDTO.builder()
                .cleanedText(extractedText)
                .sections(List.of(ResumeTextSectionDTO.builder()
                        .sectionType("GENERAL")
                        .heading("未识别章节")
                        .lines(List.of(extractedText))
                        .build()))
                .build());
        ResumeStructuredContentDTO structuredContent = ResumeStructuredContentDTO.builder().build();
        when(resumeStructureParseService.parse(anyString(), anyList())).thenReturn(structuredContent);
        when(resumeParseQualityCheckService.check(any(), any(), any())).thenReturn(ResumeParseQualityResultDTO.builder()
                .status("FAILED")
                .warnings(List.of("CORE_FIELDS_MISSING"))
                .message("未能识别到有效的简历核心内容，请检查文件格式或重新上传排版更清晰的简历")
                .score(40)
                .build());
        when(resumeParseResultMapper.selectOne(any(Wrapper.class))).thenReturn(null);
        when(resumeParseResultMapper.insert(any(ResumeParseResult.class))).thenAnswer(invocation -> {
            ResumeParseResult result = invocation.getArgument(0);
            result.setId(200L);
            return 1;
        });

        var result = service.parse(1L, 100L);

        assertThat(result.getParseStatus()).isEqualTo("FAILED");
        assertThat(result.getParseQualityStatus()).isEqualTo("FAILED");
        assertThat(result.getParseQualityWarnings()).contains("CORE_FIELDS_MISSING");
        assertThat(result.getErrorMessage()).contains("未能识别到有效的简历核心内容");
        assertThat(result.getStructuredJson()).isNotBlank();
    }
}
