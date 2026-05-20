package com.winter.airesumeoptimizer.module.resume.controller;

import com.winter.airesumeoptimizer.common.result.Result;
import com.winter.airesumeoptimizer.module.resume.dto.ResumeParseOptionsDTO;
import com.winter.airesumeoptimizer.module.resume.service.ResumeAsyncTaskService;
import com.winter.airesumeoptimizer.module.resume.service.ResumeService;
import com.winter.airesumeoptimizer.module.resume.vo.ResumeDetailVO;
import com.winter.airesumeoptimizer.module.resume.vo.ResumeListVO;
import com.winter.airesumeoptimizer.module.resume.vo.ResumeParseResultVO;
import com.winter.airesumeoptimizer.module.resume.vo.ResumeUploadVO;
import com.winter.airesumeoptimizer.module.task.vo.AsyncTaskVO;
import com.winter.airesumeoptimizer.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Positive;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/resumes")
@Validated
@Tag(name = "Resume", description = "简历上传、查询、解析接口")
@SecurityRequirement(name = "bearerAuth")
public class ResumeController {

    private final ResumeService resumeService;
    private final ResumeAsyncTaskService resumeAsyncTaskService;

    public ResumeController(ResumeService resumeService, ResumeAsyncTaskService resumeAsyncTaskService) {
        this.resumeService = resumeService;
        this.resumeAsyncTaskService = resumeAsyncTaskService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "上传简历", description = "上传 PDF、DOC 或 DOCX 简历文件")
    public Result<ResumeUploadVO> upload(
            @RequestParam("file") MultipartFile file,
            Authentication authentication) {
        AuthenticatedUser authenticatedUser = (AuthenticatedUser) authentication.getPrincipal();
        return Result.success("上传成功", resumeService.upload(authenticatedUser.getUserId(), file));
    }

    @GetMapping
    @Operation(summary = "简历列表", description = "查询当前用户已上传的简历列表")
    public Result<List<ResumeListVO>> list(Authentication authentication) {
        AuthenticatedUser authenticatedUser = (AuthenticatedUser) authentication.getPrincipal();
        return Result.success(resumeService.listByUser(authenticatedUser.getUserId()));
    }

    @GetMapping("/{id}")
    @Operation(summary = "简历详情", description = "查询当前用户指定简历的基础信息")
    public Result<ResumeDetailVO> detail(
            @PathVariable @Positive(message = "简历 ID 必须大于 0") Long id,
            Authentication authentication) {
        AuthenticatedUser authenticatedUser = (AuthenticatedUser) authentication.getPrincipal();
        return Result.success(resumeService.getDetail(authenticatedUser.getUserId(), id));
    }

    @PostMapping("/{id}/parse")
    @Operation(summary = "触发简历解析", description = "提取简历文本并生成结构化解析结果")
    public Result<ResumeParseResultVO> parse(
            @PathVariable @Positive(message = "简历 ID 必须大于 0") Long id,
            @RequestBody(required = false) ResumeParseOptionsDTO options,
            Authentication authentication) {
        AuthenticatedUser authenticatedUser = (AuthenticatedUser) authentication.getPrincipal();
        ResumeParseResultVO result = resumeService.parse(authenticatedUser.getUserId(), id, options);
        String message = "FAILED".equals(result.getParseStatus()) ? "解析失败" : "解析完成";
        return Result.success(message, result);
    }

    @PostMapping("/{id}/parse/tasks")
    @Operation(summary = "提交简历解析任务", description = "提交异步简历解析任务，前端通过任务 ID 轮询状态")
    public Result<AsyncTaskVO> submitParseTask(
            @PathVariable @Positive(message = "简历 ID 必须大于 0") Long id,
            @RequestBody(required = false) ResumeParseOptionsDTO options,
            Authentication authentication) {
        AuthenticatedUser authenticatedUser = (AuthenticatedUser) authentication.getPrincipal();
        return Result.success("简历解析任务已提交",
                resumeAsyncTaskService.submitParseTask(authenticatedUser.getUserId(), id, options));
    }

    @GetMapping("/{id}/parse-result")
    @Operation(summary = "查询解析结果", description = "查询指定简历的文本提取和结构化解析结果")
    public Result<ResumeParseResultVO> parseResult(
            @PathVariable @Positive(message = "简历 ID 必须大于 0") Long id,
            Authentication authentication) {
        AuthenticatedUser authenticatedUser = (AuthenticatedUser) authentication.getPrincipal();
        return Result.success(resumeService.getParseResult(authenticatedUser.getUserId(), id));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除简历", description = "删除简历及其解析、AI 分析和匹配结果")
    public Result<Void> delete(
            @PathVariable @Positive(message = "简历 ID 必须大于 0") Long id,
            Authentication authentication) {
        AuthenticatedUser authenticatedUser = (AuthenticatedUser) authentication.getPrincipal();
        resumeService.delete(authenticatedUser.getUserId(), id);
        return Result.success("删除成功", null);
    }
}
