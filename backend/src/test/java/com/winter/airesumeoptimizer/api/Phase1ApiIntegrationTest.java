package com.winter.airesumeoptimizer.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.winter.airesumeoptimizer.common.exception.BusinessException;
import com.winter.airesumeoptimizer.common.exception.GlobalExceptionHandler;
import com.winter.airesumeoptimizer.common.logging.RequestIdFilter;
import com.winter.airesumeoptimizer.config.OpenApiConfig;
import com.winter.airesumeoptimizer.config.SecurityConfig;
import com.winter.airesumeoptimizer.module.analysis.controller.JobOptimizationReportController;
import com.winter.airesumeoptimizer.module.analysis.controller.ResumeAnalysisController;
import com.winter.airesumeoptimizer.module.analysis.controller.AiRewriteSuggestionController;
import com.winter.airesumeoptimizer.module.analysis.dto.AiJobMatchItemDTO;
import com.winter.airesumeoptimizer.module.analysis.dto.AiJobMatchRequestDTO;
import com.winter.airesumeoptimizer.module.analysis.dto.AiRewriteAcceptStatusUpdateDTO;
import com.winter.airesumeoptimizer.module.analysis.dto.AiResumeSuggestionItemDTO;
import com.winter.airesumeoptimizer.module.analysis.dto.AiResumeSuggestionRequestDTO;
import com.winter.airesumeoptimizer.module.analysis.dto.AiRewriteSuggestionRequestDTO;
import com.winter.airesumeoptimizer.module.analysis.entity.AiJobMatchResult;
import com.winter.airesumeoptimizer.module.analysis.entity.AiResumeSuggestion;
import com.winter.airesumeoptimizer.module.analysis.entity.AiRewriteSuggestion;
import com.winter.airesumeoptimizer.module.analysis.entity.ResumeAiAnalysis;
import com.winter.airesumeoptimizer.module.analysis.service.AiJobMatchService;
import com.winter.airesumeoptimizer.module.analysis.service.AiResumeSuggestionService;
import com.winter.airesumeoptimizer.module.analysis.service.AiRewriteSuggestionService;
import com.winter.airesumeoptimizer.module.analysis.service.JobOptimizationReportService;
import com.winter.airesumeoptimizer.module.analysis.service.ResumeAnalysisService;
import com.winter.airesumeoptimizer.module.analysis.vo.JobOptimizationReportVO;
import com.winter.airesumeoptimizer.module.auth.controller.AuthController;
import com.winter.airesumeoptimizer.module.auth.dto.LoginRequestDTO;
import com.winter.airesumeoptimizer.module.auth.dto.RegisterRequestDTO;
import com.winter.airesumeoptimizer.module.auth.service.AuthService;
import com.winter.airesumeoptimizer.module.auth.vo.LoginVO;
import com.winter.airesumeoptimizer.module.history.controller.HistoryController;
import com.winter.airesumeoptimizer.module.history.controller.AiHistoryController;
import com.winter.airesumeoptimizer.module.history.service.AiHistoryService;
import com.winter.airesumeoptimizer.module.history.service.HistoryService;
import com.winter.airesumeoptimizer.module.history.vo.AiResultDetailVO;
import com.winter.airesumeoptimizer.module.history.vo.AiResultPageVO;
import com.winter.airesumeoptimizer.module.history.vo.AiResultRecordVO;
import com.winter.airesumeoptimizer.module.history.vo.HistoryDetailVO;
import com.winter.airesumeoptimizer.module.history.vo.HistoryListVO;
import com.winter.airesumeoptimizer.module.history.vo.HistoryPageVO;
import com.winter.airesumeoptimizer.module.job.controller.JobController;
import com.winter.airesumeoptimizer.module.job.controller.JobDescriptionController;
import com.winter.airesumeoptimizer.module.job.controller.JobMatchController;
import com.winter.airesumeoptimizer.module.job.dto.JobDescriptionSubmitDTO;
import com.winter.airesumeoptimizer.module.job.dto.JobMatchRequestDTO;
import com.winter.airesumeoptimizer.module.job.service.JobDescriptionParseService;
import com.winter.airesumeoptimizer.module.job.service.JobDescriptionService;
import com.winter.airesumeoptimizer.module.job.service.JobMatchResultService;
import com.winter.airesumeoptimizer.module.job.service.JobService;
import com.winter.airesumeoptimizer.module.job.vo.JobDescriptionVO;
import com.winter.airesumeoptimizer.module.job.vo.JobDetailVO;
import com.winter.airesumeoptimizer.module.job.vo.JobListVO;
import com.winter.airesumeoptimizer.module.job.vo.JobMatchResultVO;
import com.winter.airesumeoptimizer.module.resume.controller.ResumeController;
import com.winter.airesumeoptimizer.module.resume.service.ResumeService;
import com.winter.airesumeoptimizer.module.resume.vo.ResumeDetailVO;
import com.winter.airesumeoptimizer.module.resume.vo.ResumeListVO;
import com.winter.airesumeoptimizer.module.resume.vo.ResumeParseResultVO;
import com.winter.airesumeoptimizer.module.resume.vo.ResumeUploadVO;
import com.winter.airesumeoptimizer.module.user.controller.UserController;
import com.winter.airesumeoptimizer.module.user.service.UserService;
import com.winter.airesumeoptimizer.module.user.vo.UserProfileVO;
import com.winter.airesumeoptimizer.security.JwtAccessDeniedHandler;
import com.winter.airesumeoptimizer.security.JwtAuthenticationEntryPoint;
import com.winter.airesumeoptimizer.security.JwtAuthenticationFilter;
import com.winter.airesumeoptimizer.security.JwtTokenProvider;
import io.swagger.v3.oas.models.OpenAPI;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.multipart.MultipartFile;

@WebMvcTest(controllers = {
        AuthController.class,
        UserController.class,
        ResumeController.class,
        ResumeAnalysisController.class,
        JobOptimizationReportController.class,
        AiRewriteSuggestionController.class,
        JobController.class,
        JobDescriptionController.class,
        JobMatchController.class,
        AiHistoryController.class,
        HistoryController.class
})
@Import({
        SecurityConfig.class,
        JwtAuthenticationFilter.class,
        JwtAuthenticationEntryPoint.class,
        JwtAccessDeniedHandler.class,
        GlobalExceptionHandler.class,
        RequestIdFilter.class,
        OpenApiConfig.class
})
@ActiveProfiles("test")
class Phase1ApiIntegrationTest {

    private static final String TOKEN = "valid-token";
    private static final String AUTHORIZATION = "Bearer " + TOKEN;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private OpenAPI openAPI;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private ResumeService resumeService;

    @MockitoBean
    private ResumeAnalysisService resumeAnalysisService;

    @MockitoBean
    private AiJobMatchService aiJobMatchService;

    @MockitoBean
    private AiResumeSuggestionService aiResumeSuggestionService;

    @MockitoBean
    private AiRewriteSuggestionService aiRewriteSuggestionService;

    @MockitoBean
    private JobOptimizationReportService jobOptimizationReportService;

    @MockitoBean
    private JobService jobService;

    @MockitoBean
    private JobDescriptionService jobDescriptionService;

    @MockitoBean
    private JobDescriptionParseService jobDescriptionParseService;

    @MockitoBean
    private JobMatchResultService jobMatchResultService;

    @MockitoBean
    private HistoryService historyService;

    @MockitoBean
    private AiHistoryService aiHistoryService;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @BeforeEach
    void setUpToken() {
        when(jwtTokenProvider.validateToken(TOKEN)).thenReturn(true);
        when(jwtTokenProvider.getUserId(TOKEN)).thenReturn(1L);
        when(jwtTokenProvider.getUsername(TOKEN)).thenReturn("winter");
    }

    @Test
    void authEndpointsShouldRegisterAndLogin() throws Exception {
        RegisterRequestDTO registerRequest = new RegisterRequestDTO();
        registerRequest.setUsername("winter");
        registerRequest.setEmail("winter@example.com");
        registerRequest.setPassword("123456");
        registerRequest.setNickname("Winter");

        LoginRequestDTO loginRequest = new LoginRequestDTO();
        loginRequest.setAccount("winter");
        loginRequest.setPassword("123456");

        when(authService.register(any(RegisterRequestDTO.class))).thenReturn(1L);
        when(authService.login(any(LoginRequestDTO.class))).thenReturn(LoginVO.builder()
                .userId(1L)
                .username("winter")
                .email("winter@example.com")
                .nickname("Winter")
                .token("jwt-token")
                .tokenType("Bearer")
                .expiresIn(1800L)
                .build());

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("注册成功"))
                .andExpect(jsonPath("$.data.userId").value(1));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("登录成功"))
                .andExpect(jsonPath("$.data.token").value("jwt-token"))
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"));
    }

    @Test
    void protectedEndpointShouldRejectAnonymousUser() throws Exception {
        mockMvc.perform(get("/api/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401))
                .andExpect(jsonPath("$.path").value("/api/users/me"))
                .andExpect(header().exists(RequestIdFilter.REQUEST_ID_HEADER));
    }

    @Test
    void jobOptimizationReportEndpointShouldReturnReportAndRejectInvalidRequests() throws Exception {
        when(jobOptimizationReportService.getReport(eq(1L), eq(100L), eq(10L)))
                .thenReturn(JobOptimizationReportVO.builder()
                        .resumeId(100L)
                        .resumeName("resume.pdf")
                        .jobDescriptionId(10L)
                        .jobTitle("Java后端开发")
                        .matchScore(82)
                        .matchLevel("HIGH")
                        .strongMatches(List.of(AiJobMatchItemDTO.builder()
                                .item("Java")
                                .reason("岗位和简历均包含 Java")
                                .build()))
                        .weakMatches(List.of())
                        .missingSkills(List.of(AiJobMatchItemDTO.builder()
                                .item("Docker")
                                .reason("岗位要求 Docker")
                                .build()))
                        .riskTips(List.of("不要虚构 Docker 经验"))
                        .suggestionSummary(JobOptimizationReportVO.SuggestionSummaryVO.builder()
                                .totalCount(1)
                                .highPriorityCount(1)
                                .mediumPriorityCount(0)
                                .lowPriorityCount(0)
                                .build())
                        .highPrioritySuggestions(List.of(AiResumeSuggestionItemDTO.builder()
                                .type("SKILL_GAP")
                                .priority("HIGH")
                                .targetSection("技能")
                                .issue("缺少 Docker")
                                .suggestion("真实掌握再补充 Docker")
                                .evidence(List.of("岗位要求 Docker"))
                                .caution("不要虚构技能")
                                .relatedItems(List.of("Docker"))
                                .build()))
                        .mediumPrioritySuggestions(List.of())
                        .lowPrioritySuggestions(List.of())
                        .rewriteSuggestions(List.of())
                        .acceptedRewriteSuggestions(List.of())
                        .pendingRewriteSuggestions(List.of())
                        .rejectedRewriteSuggestions(List.of())
                        .nextStepChecklist(List.of(JobOptimizationReportVO.NextStepItemVO.builder()
                                .key("REVIEW_HIGH_PRIORITY_SUGGESTIONS")
                                .text("优先处理高优先级岗位优化建议")
                                .source("SUGGESTION")
                                .status("PENDING")
                                .build()))
                        .modelInfo(List.of(JobOptimizationReportVO.ModelInfoVO.builder()
                                .sourceType("MATCH")
                                .sourceId(400L)
                                .modelName("deepseek-v4-flash")
                                .promptVersion("ai_job_match_v1")
                                .status("SUCCESS")
                                .updatedAt(LocalDateTime.now())
                                .build()))
                        .warnings(List.of())
                        .generatedAt(LocalDateTime.now())
                        .build());

        mockMvc.perform(get("/api/resumes/100/job-optimization-report")
                        .param("jobDescriptionId", "10")
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.resumeId").value(100))
                .andExpect(jsonPath("$.data.jobDescriptionId").value(10))
                .andExpect(jsonPath("$.data.matchScore").value(82))
                .andExpect(jsonPath("$.data.matchLevel").value("HIGH"))
                .andExpect(jsonPath("$.data.strongMatches[0].item").value("Java"))
                .andExpect(jsonPath("$.data.missingSkills[0].item").value("Docker"))
                .andExpect(jsonPath("$.data.highPrioritySuggestions[0].priority").value("HIGH"))
                .andExpect(jsonPath("$.data.nextStepChecklist[0].key").value("REVIEW_HIGH_PRIORITY_SUGGESTIONS"))
                .andExpect(jsonPath("$.data.modelInfo[0].promptVersion").value("ai_job_match_v1"));

        mockMvc.perform(get("/api/resumes/100/job-optimization-report")
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));

        mockMvc.perform(get("/api/resumes/0/job-optimization-report")
                        .param("jobDescriptionId", "10")
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("简历 ID 必须大于 0"));

        mockMvc.perform(get("/api/resumes/100/job-optimization-report")
                        .param("jobDescriptionId", "0")
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("目标岗位 ID 必须大于 0"));
    }

    @Test
    void jobOptimizationReportEndpointShouldRejectAnonymousUser() throws Exception {
        mockMvc.perform(get("/api/resumes/100/job-optimization-report")
                        .param("jobDescriptionId", "10"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401))
                .andExpect(jsonPath("$.path").value("/api/resumes/100/job-optimization-report"));
    }

    @Test
    void jobOptimizationReportEndpointShouldReturnBusinessError() throws Exception {
        when(jobOptimizationReportService.getReport(eq(1L), eq(100L), eq(99L)))
                .thenThrow(new BusinessException(404, "匹配分析结果不存在，请先生成匹配分析结果"));

        mockMvc.perform(get("/api/resumes/100/job-optimization-report")
                        .param("jobDescriptionId", "99")
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(404))
                .andExpect(jsonPath("$.message").value("匹配分析结果不存在，请先生成匹配分析结果"));
    }

    @Test
    void currentUserEndpointShouldReturnAuthenticatedProfile() throws Exception {
        when(userService.getCurrentUserProfile(1L)).thenReturn(UserProfileVO.builder()
                .id(1L)
                .username("winter")
                .email("winter@example.com")
                .nickname("Winter")
                .createdAt(LocalDateTime.now())
                .build());

        mockMvc.perform(get("/api/users/me")
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.username").value("winter"));
    }

    @Test
    void resumeEndpointsShouldUploadListDetailParseAndRejectCrossUserAccess() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "resume.pdf",
                "application/pdf",
                "resume-content".getBytes());
        when(resumeService.upload(eq(1L), any(MultipartFile.class))).thenReturn(ResumeUploadVO.builder()
                .id(100L)
                .originalFilename("resume.pdf")
                .fileType("PDF")
                .fileSize(14L)
                .objectKey("resumes/1/resume.pdf")
                .uploadStatus("UPLOADED")
                .createdAt(LocalDateTime.now())
                .build());
        when(resumeService.listByUser(1L)).thenReturn(List.of(ResumeListVO.builder()
                .id(100L)
                .originalFilename("resume.pdf")
                .fileType("PDF")
                .fileSize(14L)
                .uploadStatus("UPLOADED")
                .createdAt(LocalDateTime.now())
                .build()));
        when(resumeService.getDetail(1L, 100L)).thenReturn(ResumeDetailVO.builder()
                .id(100L)
                .originalFilename("resume.pdf")
                .fileType("PDF")
                .fileSize(14L)
                .uploadStatus("UPLOADED")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build());
        when(resumeService.getDetail(1L, 999L)).thenThrow(new BusinessException(404, "简历不存在"));
        when(resumeService.parse(eq(1L), eq(100L), any())).thenReturn(ResumeParseResultVO.builder()
                .resumeId(100L)
                .parseStatus("SUCCESS")
                .extractedText("Java Spring Boot")
                .structuredJson("{\"skills\":[\"Java\"]}")
                .updatedAt(LocalDateTime.now())
                .build());
        when(resumeService.getParseResult(1L, 100L)).thenReturn(ResumeParseResultVO.builder()
                .resumeId(100L)
                .parseStatus("SUCCESS")
                .extractedText("Java Spring Boot")
                .structuredJson("{\"skills\":[\"Java\"]}")
                .updatedAt(LocalDateTime.now())
                .build());

        mockMvc.perform(multipart("/api/resumes")
                        .file(file)
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("上传成功"))
                .andExpect(jsonPath("$.data.id").value(100));

        mockMvc.perform(get("/api/resumes")
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].id").value(100));

        mockMvc.perform(get("/api/resumes/100")
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(100));

        mockMvc.perform(get("/api/resumes/999")
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(404))
                .andExpect(jsonPath("$.message").value("简历不存在"));

        mockMvc.perform(post("/api/resumes/100/parse")
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("解析完成"))
                .andExpect(jsonPath("$.data.parseStatus").value("SUCCESS"));

        mockMvc.perform(get("/api/resumes/100/parse-result")
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.resumeId").value(100));

        mockMvc.perform(delete("/api/resumes/100")
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("删除成功"));
    }

    @Test
    void analysisJobMatchAndHistoryEndpointsShouldReturnCoreData() throws Exception {
        ResumeAiAnalysis analysis = new ResumeAiAnalysis();
        analysis.setResumeId(100L);
        analysis.setAnalysisStatus("SUCCESS");
        analysis.setScore(88);
        analysis.setStrengths("[\"Java 基础扎实\"]");
        analysis.setProblems("[\"项目结果需要量化\"]");
        analysis.setSuggestionsSummary("[\"补充项目指标\"]");
        analysis.setModelName("test-model");
        analysis.setPromptVersion("v1");
        analysis.setUpdatedAt(LocalDateTime.now());

        when(resumeAnalysisService.analyze(1L, 100L)).thenReturn(analysis);
        when(resumeAnalysisService.getAnalysis(1L, 100L)).thenReturn(analysis);
        when(aiJobMatchService.match(1L, 100L, 10L)).thenReturn(buildAiJobMatchResult("SUCCESS"));
        when(aiJobMatchService.match(1L, 100L, 11L)).thenReturn(buildFailedAiJobMatchResult());
        when(aiJobMatchService.listByResume(1L, 100L)).thenReturn(List.of(
                buildAiJobMatchResult("SUCCESS"),
                buildFailedAiJobMatchResult()));
        when(aiJobMatchService.getByResumeAndJobDescription(1L, 100L, 10L))
                .thenReturn(buildAiJobMatchResult("SUCCESS"));
        when(aiJobMatchService.getByResumeAndJobDescription(1L, 100L, 11L))
                .thenReturn(buildFailedAiJobMatchResult());
        when(aiJobMatchService.getByResumeAndJobDescription(1L, 100L, 99L))
                .thenThrow(new BusinessException(404, "匹配分析结果不存在"));
        when(aiJobMatchService.match(1L, 101L, 10L))
                .thenThrow(new BusinessException(400, "简历解析未成功，不能进行匹配分析"));
        when(aiResumeSuggestionService.generate(1L, 100L, 10L, 400L))
                .thenReturn(buildAiResumeSuggestion("SUCCESS"));
        when(aiResumeSuggestionService.generate(1L, 100L, 11L, 401L))
                .thenReturn(buildFailedAiResumeSuggestion());
        when(aiResumeSuggestionService.generate(1L, 100L, 12L, 402L))
                .thenThrow(new BusinessException(400, "匹配分析未成功，不能生成岗位优化建议"));
        when(aiResumeSuggestionService.listByResume(1L, 100L)).thenReturn(List.of(
                buildAiResumeSuggestion("SUCCESS"),
                buildFailedAiResumeSuggestion()));
        when(aiResumeSuggestionService.getByResumeAndJobDescription(1L, 100L, 10L))
                .thenReturn(buildAiResumeSuggestion("SUCCESS"));
        when(aiResumeSuggestionService.getByResumeAndMatchResult(1L, 100L, 400L))
                .thenReturn(buildAiResumeSuggestion("SUCCESS"));
        when(aiResumeSuggestionService.getByResumeAndJobDescription(1L, 100L, 99L))
                .thenThrow(new BusinessException(404, "AI 优化建议结果不存在"));
        when(aiRewriteSuggestionService.generate(1L, 100L, "PROJECT", "项目经历", "做了一个 AI 简历优化系统，负责后端开发。", 10L, 400L, 500L))
                .thenReturn(buildAiRewriteSuggestion("SUCCESS"));
        when(aiRewriteSuggestionService.generate(1L, 100L, "PROJECT", "项目经历", "触发失败", 10L, 400L, 500L))
                .thenReturn(buildFailedAiRewriteSuggestion());
        when(aiRewriteSuggestionService.listByResume(1L, 100L, null, null)).thenReturn(List.of(
                buildAiRewriteSuggestion("SUCCESS"),
                buildFailedAiRewriteSuggestion()));
        when(aiRewriteSuggestionService.listByResume(1L, 100L, "PROJECT", "PENDING"))
                .thenReturn(List.of(buildAiRewriteSuggestion("SUCCESS")));
        when(aiRewriteSuggestionService.updateAcceptStatus(1L, 600L, "ACCEPTED"))
                .thenReturn(buildAcceptedAiRewriteSuggestion());
        when(aiRewriteSuggestionService.updateAcceptStatus(1L, 600L, "REJECTED"))
                .thenReturn(buildRejectedAiRewriteSuggestion());
        when(aiRewriteSuggestionService.updateAcceptStatus(1L, 999L, "ACCEPTED"))
                .thenThrow(new BusinessException(404, "AI 局部改写建议不存在"));
        when(jobMatchResultService.match(1L, 100L, 200L)).thenReturn(JobMatchResultVO.builder()
                .matchId(300L)
                .resumeId(100L)
                .jobId(200L)
                .jobTitle("Java 后端开发")
                .companyName("Demo Inc.")
                .matchScore(80)
                .matchedItems(List.of("Java"))
                .missingItems(List.of("PostgreSQL"))
                .matchReason("已命中 1 项技能")
                .suggestions(List.of())
                .updatedAt(LocalDateTime.now())
                .build());
        when(jobMatchResultService.listByResume(1L, 100L)).thenReturn(List.of(JobMatchResultVO.builder()
                .matchId(300L)
                .resumeId(100L)
                .jobId(200L)
                .jobTitle("Java 后端开发")
                .companyName("Demo Inc.")
                .matchScore(80)
                .matchedItems(List.of("Java"))
                .missingItems(List.of("PostgreSQL"))
                .matchReason("已命中 1 项技能")
                .suggestions(List.of())
                .updatedAt(LocalDateTime.now())
                .build()));
        when(historyService.list(1L, 1, 10)).thenReturn(HistoryPageVO.builder()
                .records(List.of(HistoryListVO.builder()
                        .recordId(100L)
                        .resumeId(100L)
                        .resumeName("resume.pdf")
                        .parseStatus("SUCCESS")
                        .analysisStatus("SUCCESS")
                        .latestMatchScore(80)
                        .updatedAt(LocalDateTime.now())
                        .build()))
                .page(1)
                .size(10)
                .total(1L)
                .totalPages(1)
                .build());
        when(historyService.detail(1L, 100L)).thenReturn(HistoryDetailVO.builder()
                .recordId(100L)
                .resumeId(100L)
                .updatedAt(LocalDateTime.now())
                .build());
        when(aiHistoryService.list(1L, "MATCH_ANALYSIS", 100L, 10L, "SUCCESS", 1, 10))
                .thenReturn(AiResultPageVO.builder()
                        .records(List.of(AiResultRecordVO.builder()
                                .recordId(400L)
                                .resultType("MATCH_ANALYSIS")
                                .title("匹配分析 - Java 后端开发")
                                .summary("风险提示摘要")
                                .status("SUCCESS")
                                .resumeId(100L)
                                .resumeName("resume.pdf")
                                .jobDescriptionId(10L)
                                .jobTitle("Java 后端开发")
                                .modelName("deepseek-v4-flash")
                                .promptVersion("ai_job_match_v1")
                                .createdAt(LocalDateTime.now())
                                .updatedAt(LocalDateTime.now())
                                .build()))
                        .page(1)
                        .size(10)
                        .total(1L)
                        .totalPages(1)
                        .build());
        when(aiHistoryService.detail(1L, "MATCH_ANALYSIS", 400L))
                .thenReturn(AiResultDetailVO.builder()
                        .recordId(400L)
                        .resultType("MATCH_ANALYSIS")
                        .title("匹配分析 - Java 后端开发")
                        .status("SUCCESS")
                        .content(Map.of("overallScore", 86))
                        .resumeId(100L)
                        .resumeName("resume.pdf")
                        .jobDescriptionId(10L)
                        .jobTitle("Java 后端开发")
                        .modelName("deepseek-v4-flash")
                        .promptVersion("ai_job_match_v1")
                        .createdAt(LocalDateTime.now())
                        .updatedAt(LocalDateTime.now())
                        .build());

        mockMvc.perform(post("/api/resumes/100/ai-analysis")
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("简历诊断完成"))
                .andExpect(jsonPath("$.data.score").value(88));

        mockMvc.perform(get("/api/resumes/100/ai-analysis")
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.strengths[0]").value("Java 基础扎实"));

        AiJobMatchRequestDTO aiJobMatchRequest = new AiJobMatchRequestDTO();
        aiJobMatchRequest.setJobDescriptionId(10L);
        mockMvc.perform(post("/api/resumes/100/ai-job-matches")
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(aiJobMatchRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("匹配分析完成"))
                .andExpect(jsonPath("$.data.matchId").value(400))
                .andExpect(jsonPath("$.data.resumeId").value(100))
                .andExpect(jsonPath("$.data.jobDescriptionId").value(10))
                .andExpect(jsonPath("$.data.overallScore").value(82))
                .andExpect(jsonPath("$.data.matchStatus").value("SUCCESS"));

        aiJobMatchRequest.setJobDescriptionId(11L);
        mockMvc.perform(post("/api/resumes/100/ai-job-matches")
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(aiJobMatchRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("匹配分析失败"))
                .andExpect(jsonPath("$.data.matchStatus").value("FAILED"))
                .andExpect(jsonPath("$.data.errorMessage").value("AI 匹配结果不是合法 JSON"));

        aiJobMatchRequest.setJobDescriptionId(10L);
        mockMvc.perform(post("/api/resumes/101/ai-job-matches")
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(aiJobMatchRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("简历解析未成功，不能进行匹配分析"));

        aiJobMatchRequest.setJobDescriptionId(null);
        mockMvc.perform(post("/api/resumes/100/ai-job-matches")
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(aiJobMatchRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("目标岗位 ID 不能为空"));

        mockMvc.perform(get("/api/resumes/100/ai-job-matches")
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(2)))
                .andExpect(jsonPath("$.data[0].matchId").value(400))
                .andExpect(jsonPath("$.data[0].strongMatches[0].item").value("Java"))
                .andExpect(jsonPath("$.data[0].riskNotes[0]").value("部分技能缺少项目支撑"))
                .andExpect(jsonPath("$.data[1].matchStatus").value("FAILED"))
                .andExpect(jsonPath("$.data[1].errorMessage").value("AI 匹配结果不是合法 JSON"));

        mockMvc.perform(get("/api/resumes/100/ai-job-matches")
                        .param("jobDescriptionId", "10")
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.matchId").value(400))
                .andExpect(jsonPath("$.data.jobDescriptionId").value(10))
                .andExpect(jsonPath("$.data.overallScore").value(82))
                .andExpect(jsonPath("$.data.strongMatches[0].reason").value("双方都出现 Java"));

        mockMvc.perform(get("/api/resumes/100/ai-job-matches")
                        .param("jobDescriptionId", "99")
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(404))
                .andExpect(jsonPath("$.message").value("匹配分析结果不存在"));

        AiResumeSuggestionRequestDTO suggestionRequest = new AiResumeSuggestionRequestDTO();
        suggestionRequest.setJobDescriptionId(10L);
        suggestionRequest.setAiJobMatchResultId(400L);
        mockMvc.perform(post("/api/resumes/100/ai-suggestions")
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(suggestionRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("AI 优化建议生成完成"))
                .andExpect(jsonPath("$.data.suggestionId").value(500))
                .andExpect(jsonPath("$.data.resumeId").value(100))
                .andExpect(jsonPath("$.data.jobDescriptionId").value(10))
                .andExpect(jsonPath("$.data.aiJobMatchResultId").value(400))
                .andExpect(jsonPath("$.data.suggestionStatus").value("SUCCESS"))
                .andExpect(jsonPath("$.data.suggestionCount").value(1));

        suggestionRequest.setJobDescriptionId(11L);
        suggestionRequest.setAiJobMatchResultId(401L);
        mockMvc.perform(post("/api/resumes/100/ai-suggestions")
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(suggestionRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("AI 优化建议生成失败"))
                .andExpect(jsonPath("$.data.suggestionStatus").value("FAILED"))
                .andExpect(jsonPath("$.data.suggestionCount").value(0))
                .andExpect(jsonPath("$.data.errorMessage").value("AI 优化建议结果不是合法 JSON"));

        suggestionRequest.setJobDescriptionId(12L);
        suggestionRequest.setAiJobMatchResultId(402L);
        mockMvc.perform(post("/api/resumes/100/ai-suggestions")
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(suggestionRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("匹配分析未成功，不能生成岗位优化建议"));

        suggestionRequest.setJobDescriptionId(null);
        suggestionRequest.setAiJobMatchResultId(400L);
        mockMvc.perform(post("/api/resumes/100/ai-suggestions")
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(suggestionRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("目标岗位 ID 不能为空"));

        mockMvc.perform(get("/api/resumes/100/ai-suggestions")
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(2)))
                .andExpect(jsonPath("$.data[0].suggestionId").value(500))
                .andExpect(jsonPath("$.data[0].suggestions[0].type").value("SKILL_GAP"))
                .andExpect(jsonPath("$.data[1].suggestionStatus").value("FAILED"))
                .andExpect(jsonPath("$.data[1].errorMessage").value("AI 优化建议结果不是合法 JSON"));

        mockMvc.perform(get("/api/resumes/100/ai-suggestions")
                        .param("jobDescriptionId", "10")
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.suggestionId").value(500))
                .andExpect(jsonPath("$.data.jobDescriptionId").value(10))
                .andExpect(jsonPath("$.data.aiJobMatchResultId").value(400))
                .andExpect(jsonPath("$.data.suggestions[0].priority").value("HIGH"));

        mockMvc.perform(get("/api/resumes/100/ai-suggestions")
                        .param("aiJobMatchResultId", "400")
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.suggestionId").value(500))
                .andExpect(jsonPath("$.data.aiJobMatchResultId").value(400))
                .andExpect(jsonPath("$.data.promptVersion").value("resume_suggestion_v1"));

        mockMvc.perform(get("/api/resumes/100/ai-suggestions")
                        .param("jobDescriptionId", "99")
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(404))
                .andExpect(jsonPath("$.message").value("AI 优化建议结果不存在"));

        AiRewriteSuggestionRequestDTO rewriteRequest = new AiRewriteSuggestionRequestDTO();
        rewriteRequest.setRewriteType("PROJECT");
        rewriteRequest.setTargetSection("项目经历");
        rewriteRequest.setOriginalText("做了一个 AI 简历优化系统，负责后端开发。");
        rewriteRequest.setJobDescriptionId(10L);
        rewriteRequest.setAiJobMatchResultId(400L);
        rewriteRequest.setAiResumeSuggestionId(500L);
        mockMvc.perform(post("/api/resumes/100/rewrite-suggestions")
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(rewriteRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("AI 局部改写生成完成"))
                .andExpect(jsonPath("$.data.rewriteId").value(600))
                .andExpect(jsonPath("$.data.resumeId").value(100))
                .andExpect(jsonPath("$.data.rewriteType").value("PROJECT"))
                .andExpect(jsonPath("$.data.targetSection").value("项目经历"))
                .andExpect(jsonPath("$.data.rewriteStatus").value("SUCCESS"))
                .andExpect(jsonPath("$.data.acceptStatus").value("PENDING"))
                .andExpect(jsonPath("$.data.promptVersion").value("rewrite_suggestion_v1"));

        rewriteRequest.setOriginalText("触发失败");
        mockMvc.perform(post("/api/resumes/100/rewrite-suggestions")
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(rewriteRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("AI 局部改写生成失败"))
                .andExpect(jsonPath("$.data.rewriteStatus").value("FAILED"))
                .andExpect(jsonPath("$.data.errorMessage").value("AI 局部改写结果不是合法 JSON"));

        rewriteRequest.setOriginalText("");
        mockMvc.perform(post("/api/resumes/100/rewrite-suggestions")
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(rewriteRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("原文片段不能为空"));

        mockMvc.perform(get("/api/resumes/100/rewrite-suggestions")
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(2)))
                .andExpect(jsonPath("$.data[0].rewriteId").value(600))
                .andExpect(jsonPath("$.data[0].rewrittenText").value("负责 AI 简历优化系统后端开发。"))
                .andExpect(jsonPath("$.data[1].rewriteStatus").value("FAILED"))
                .andExpect(jsonPath("$.data[1].errorMessage").value("AI 局部改写结果不是合法 JSON"));

        mockMvc.perform(get("/api/resumes/100/rewrite-suggestions")
                        .param("rewriteType", "PROJECT")
                        .param("acceptStatus", "PENDING")
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].rewriteType").value("PROJECT"))
                .andExpect(jsonPath("$.data[0].acceptStatus").value("PENDING"));

        AiRewriteAcceptStatusUpdateDTO acceptRequest = new AiRewriteAcceptStatusUpdateDTO();
        acceptRequest.setAcceptStatus("ACCEPTED");
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch("/api/rewrite-suggestions/600/accept-status")
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(acceptRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("局部改写采纳状态已更新"))
                .andExpect(jsonPath("$.data.rewriteId").value(600))
                .andExpect(jsonPath("$.data.acceptStatus").value("ACCEPTED"));

        acceptRequest.setAcceptStatus("REJECTED");
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch("/api/rewrite-suggestions/600/accept-status")
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(acceptRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.acceptStatus").value("REJECTED"));

        acceptRequest.setAcceptStatus("");
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch("/api/rewrite-suggestions/600/accept-status")
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(acceptRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("采纳状态不能为空"));

        acceptRequest.setAcceptStatus("ACCEPTED");
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch("/api/rewrite-suggestions/999/accept-status")
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(acceptRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(404))
                .andExpect(jsonPath("$.message").value("AI 局部改写建议不存在"));

        JobMatchRequestDTO requestDTO = new JobMatchRequestDTO();
        requestDTO.setJobId(200L);
        mockMvc.perform(post("/api/resumes/100/job-matches")
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestDTO)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("岗位匹配完成"))
                .andExpect(jsonPath("$.data.matchScore").value(80));

        mockMvc.perform(get("/api/resumes/100/job-matches")
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].jobId").value(200));

        mockMvc.perform(get("/api/history")
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].resumeId").value(100));

        mockMvc.perform(get("/api/history/100")
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.resumeId").value(100));

        mockMvc.perform(get("/api/ai-results")
                        .param("resultType", "MATCH_ANALYSIS")
                        .param("resumeId", "100")
                        .param("jobDescriptionId", "10")
                        .param("status", "SUCCESS")
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].recordId").value(400))
                .andExpect(jsonPath("$.data.records[0].resultType").value("MATCH_ANALYSIS"))
                .andExpect(jsonPath("$.data.records[0].resumeId").value(100))
                .andExpect(jsonPath("$.data.records[0].jobDescriptionId").value(10));

        mockMvc.perform(get("/api/ai-results/MATCH_ANALYSIS/400")
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.recordId").value(400))
                .andExpect(jsonPath("$.data.resultType").value("MATCH_ANALYSIS"))
                .andExpect(jsonPath("$.data.content.overallScore").value(86));
    }

    @Test
    void publicJobEndpointsShouldReturnEnabledJobs() throws Exception {
        when(jobService.listEnabledJobs()).thenReturn(List.of(JobListVO.builder()
                .id(200L)
                .title("Java 后端开发")
                .companyName("Demo Inc.")
                .jobCategory("后端开发")
                .location("成都")
                .requiredSkills(List.of("Java", "Spring Boot"))
                .status("ENABLED")
                .build()));
        when(jobService.getEnabledJobDetail(200L)).thenReturn(JobDetailVO.builder()
                .id(200L)
                .title("Java 后端开发")
                .companyName("Demo Inc.")
                .jobCategory("后端开发")
                .location("成都")
                .description("负责后端开发")
                .requirements("熟悉 Java")
                .requiredSkills(List.of("Java", "Spring Boot"))
                .updatedAt(LocalDateTime.now())
                .build());

        mockMvc.perform(get("/api/jobs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].id").value(200));

        mockMvc.perform(get("/api/jobs/200"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(200))
                .andExpect(jsonPath("$.data.requiredSkills[0]").value("Java"));
    }

    @Test
    void jobDescriptionEndpointsShouldSubmitDetailAndParse() throws Exception {
        JobDescriptionSubmitDTO request = new JobDescriptionSubmitDTO();
        request.setTitle("Java 后端开发工程师");
        request.setRawText("负责 Java 后端开发，要求熟悉 Spring Boot");

        JobDescriptionVO pending = JobDescriptionVO.builder()
                .id(10L)
                .title("Java 后端开发工程师")
                .rawText("负责 Java 后端开发，要求熟悉 Spring Boot")
                .parseStatus("PENDING")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        JobDescriptionVO success = JobDescriptionVO.builder()
                .id(10L)
                .title("Java 后端开发工程师")
                .rawText("负责 Java 后端开发，要求熟悉 Spring Boot")
                .parseStatus("SUCCESS")
                .structuredContent("{\"jobTitle\":\"Java 后端开发工程师\"}")
                .modelName("test-model")
                .promptVersion("job_description_parse_v1")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        JobDescriptionVO failed = JobDescriptionVO.builder()
                .id(11L)
                .title("Java 后端开发工程师")
                .rawText("负责 Java 后端开发")
                .parseStatus("FAILED")
                .errorMessage("目标岗位解析结果不是合法 JSON")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        when(jobDescriptionService.submit(eq(1L), any(JobDescriptionSubmitDTO.class))).thenReturn(pending);
        when(jobDescriptionService.listByUser(1L)).thenReturn(List.of(success));
        when(jobDescriptionService.getDetail(1L, 10L)).thenReturn(success);
        when(jobDescriptionParseService.parse(1L, 10L)).thenReturn(success);
        when(jobDescriptionParseService.parse(1L, 11L)).thenReturn(failed);

        mockMvc.perform(post("/api/job-descriptions")
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("目标岗位提交成功"))
                .andExpect(jsonPath("$.data.id").value(10))
                .andExpect(jsonPath("$.data.parseStatus").value("PENDING"));

        mockMvc.perform(get("/api/job-descriptions")
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].id").value(10))
                .andExpect(jsonPath("$.data[0].parseStatus").value("SUCCESS"));

        mockMvc.perform(get("/api/job-descriptions/10")
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.parseStatus").value("SUCCESS"))
                .andExpect(jsonPath("$.data.structuredContent").value("{\"jobTitle\":\"Java 后端开发工程师\"}"));

        mockMvc.perform(post("/api/job-descriptions/10/parse")
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("目标岗位解析完成"))
                .andExpect(jsonPath("$.data.parseStatus").value("SUCCESS"));

        mockMvc.perform(post("/api/job-descriptions/11/parse")
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("目标岗位解析失败"))
                .andExpect(jsonPath("$.data.parseStatus").value("FAILED"))
                .andExpect(jsonPath("$.data.errorMessage").value("目标岗位解析结果不是合法 JSON"));

        mockMvc.perform(delete("/api/job-descriptions/10")
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("目标岗位删除成功"));
    }

    @Test
    void openApiDocsShouldBePublicAndDescribeJwtSecurity() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk());

        assertThat(openAPI.getInfo().getTitle()).isEqualTo("AI Resume Optimizer API");
        assertThat(openAPI.getComponents().getSecuritySchemes())
                .containsKey(OpenApiConfig.JWT_SECURITY_SCHEME);
        assertThat(openAPI.getComponents()
                .getSecuritySchemes()
                .get(OpenApiConfig.JWT_SECURITY_SCHEME)
                .getScheme()).isEqualTo("bearer");
    }

    private AiJobMatchResult buildAiJobMatchResult(String status) {
        AiJobMatchResult result = new AiJobMatchResult();
        result.setId(400L);
        result.setResumeId(100L);
        result.setJobDescriptionId(10L);
        result.setMatchStatus(status);
        result.setOverallScore(82);
        result.setStrongMatches("[{\"item\":\"Java\",\"reason\":\"双方都出现 Java\"}]");
        result.setWeakMatches("[{\"item\":\"Redis\",\"reason\":\"简历缺少项目支撑\"}]");
        result.setMissingSkills("[{\"item\":\"Docker\",\"reason\":\"岗位要求但简历未出现\"}]");
        result.setWeakExperienceDescriptions("[{\"section\":\"项目经历\",\"issue\":\"缺少结果说明\"}]");
        result.setEvidence("[{\"source\":\"resume\",\"content\":\"简历提到 Java\"}]");
        result.setRiskNotes("[\"部分技能缺少项目支撑\"]");
        result.setModelName("test-model");
        result.setPromptVersion("ai_job_match_v1");
        result.setUpdatedAt(LocalDateTime.now());
        return result;
    }

    private AiJobMatchResult buildFailedAiJobMatchResult() {
        AiJobMatchResult result = new AiJobMatchResult();
        result.setId(401L);
        result.setResumeId(100L);
        result.setJobDescriptionId(11L);
        result.setMatchStatus("FAILED");
        result.setModelName("test-model");
        result.setPromptVersion("ai_job_match_v1");
        result.setErrorMessage("AI 匹配结果不是合法 JSON");
        result.setUpdatedAt(LocalDateTime.now());
        return result;
    }

    private AiResumeSuggestion buildAiResumeSuggestion(String status) {
        AiResumeSuggestion suggestion = new AiResumeSuggestion();
        suggestion.setId(500L);
        suggestion.setResumeId(100L);
        suggestion.setJobDescriptionId(10L);
        suggestion.setAiJobMatchResultId(400L);
        suggestion.setSuggestionStatus(status);
        suggestion.setSuggestions("""
                [{"type":"SKILL_GAP","priority":"HIGH","targetSection":"技能","issue":"缺少 Docker","suggestion":"如果真实掌握 Docker，补充项目实践。","evidence":["岗位要求 Docker"],"caution":"不要虚构技能","relatedItems":["Docker"]}]
                """);
        suggestion.setModelName("test-model");
        suggestion.setPromptVersion("resume_suggestion_v1");
        suggestion.setUpdatedAt(LocalDateTime.now());
        return suggestion;
    }

    private AiResumeSuggestion buildFailedAiResumeSuggestion() {
        AiResumeSuggestion suggestion = new AiResumeSuggestion();
        suggestion.setId(501L);
        suggestion.setResumeId(100L);
        suggestion.setJobDescriptionId(11L);
        suggestion.setAiJobMatchResultId(401L);
        suggestion.setSuggestionStatus("FAILED");
        suggestion.setModelName("test-model");
        suggestion.setPromptVersion("resume_suggestion_v1");
        suggestion.setErrorMessage("AI 优化建议结果不是合法 JSON");
        suggestion.setUpdatedAt(LocalDateTime.now());
        return suggestion;
    }

    private AiRewriteSuggestion buildAiRewriteSuggestion(String status) {
        AiRewriteSuggestion suggestion = new AiRewriteSuggestion();
        suggestion.setId(600L);
        suggestion.setResumeId(100L);
        suggestion.setJobDescriptionId(10L);
        suggestion.setAiJobMatchResultId(400L);
        suggestion.setAiResumeSuggestionId(500L);
        suggestion.setRewriteType("PROJECT");
        suggestion.setTargetSection("项目经历");
        suggestion.setOriginalText("做了一个 AI 简历优化系统，负责后端开发。");
        suggestion.setRewrittenText("负责 AI 简历优化系统后端开发。");
        suggestion.setRewriteReason("表达更具体。");
        suggestion.setCaution("确认职责真实。");
        suggestion.setAcceptStatus("PENDING");
        suggestion.setRewriteStatus(status);
        suggestion.setModelName("test-model");
        suggestion.setPromptVersion("rewrite_suggestion_v1");
        suggestion.setUpdatedAt(LocalDateTime.now());
        return suggestion;
    }

    private AiRewriteSuggestion buildFailedAiRewriteSuggestion() {
        AiRewriteSuggestion suggestion = new AiRewriteSuggestion();
        suggestion.setId(601L);
        suggestion.setResumeId(100L);
        suggestion.setJobDescriptionId(10L);
        suggestion.setAiJobMatchResultId(400L);
        suggestion.setAiResumeSuggestionId(500L);
        suggestion.setRewriteType("PROJECT");
        suggestion.setTargetSection("项目经历");
        suggestion.setOriginalText("触发失败");
        suggestion.setAcceptStatus("PENDING");
        suggestion.setRewriteStatus("FAILED");
        suggestion.setModelName("test-model");
        suggestion.setPromptVersion("rewrite_suggestion_v1");
        suggestion.setErrorMessage("AI 局部改写结果不是合法 JSON");
        suggestion.setUpdatedAt(LocalDateTime.now());
        return suggestion;
    }

    private AiRewriteSuggestion buildAcceptedAiRewriteSuggestion() {
        AiRewriteSuggestion suggestion = buildAiRewriteSuggestion("SUCCESS");
        suggestion.setAcceptStatus("ACCEPTED");
        return suggestion;
    }

    private AiRewriteSuggestion buildRejectedAiRewriteSuggestion() {
        AiRewriteSuggestion suggestion = buildAiRewriteSuggestion("SUCCESS");
        suggestion.setAcceptStatus("REJECTED");
        return suggestion;
    }
}
