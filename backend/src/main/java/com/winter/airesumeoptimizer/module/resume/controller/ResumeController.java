package com.winter.airesumeoptimizer.module.resume.controller;

import com.winter.airesumeoptimizer.common.result.Result;
import com.winter.airesumeoptimizer.module.resume.service.ResumeService;
import com.winter.airesumeoptimizer.module.resume.vo.ResumeDetailVO;
import com.winter.airesumeoptimizer.module.resume.vo.ResumeListVO;
import com.winter.airesumeoptimizer.module.resume.vo.ResumeParseResultVO;
import com.winter.airesumeoptimizer.module.resume.vo.ResumeUploadVO;
import com.winter.airesumeoptimizer.security.AuthenticatedUser;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/resumes")
public class ResumeController {

    private final ResumeService resumeService;

    public ResumeController(ResumeService resumeService) {
        this.resumeService = resumeService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<ResumeUploadVO> upload(
            @RequestParam("file") MultipartFile file,
            Authentication authentication) {
        AuthenticatedUser authenticatedUser = (AuthenticatedUser) authentication.getPrincipal();
        return Result.success("上传成功", resumeService.upload(authenticatedUser.getUserId(), file));
    }

    @GetMapping
    public Result<List<ResumeListVO>> list(Authentication authentication) {
        AuthenticatedUser authenticatedUser = (AuthenticatedUser) authentication.getPrincipal();
        return Result.success(resumeService.listByUser(authenticatedUser.getUserId()));
    }

    @GetMapping("/{id}")
    public Result<ResumeDetailVO> detail(
            @PathVariable Long id,
            Authentication authentication) {
        AuthenticatedUser authenticatedUser = (AuthenticatedUser) authentication.getPrincipal();
        return Result.success(resumeService.getDetail(authenticatedUser.getUserId(), id));
    }

    @PostMapping("/{id}/parse")
    public Result<ResumeParseResultVO> parse(
            @PathVariable Long id,
            Authentication authentication) {
        AuthenticatedUser authenticatedUser = (AuthenticatedUser) authentication.getPrincipal();
        ResumeParseResultVO result = resumeService.parse(authenticatedUser.getUserId(), id);
        String message = "FAILED".equals(result.getParseStatus()) ? "解析失败" : "解析完成";
        return Result.success(message, result);
    }

    @GetMapping("/{id}/parse-result")
    public Result<ResumeParseResultVO> parseResult(
            @PathVariable Long id,
            Authentication authentication) {
        AuthenticatedUser authenticatedUser = (AuthenticatedUser) authentication.getPrincipal();
        return Result.success(resumeService.getParseResult(authenticatedUser.getUserId(), id));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(
            @PathVariable Long id,
            Authentication authentication) {
        AuthenticatedUser authenticatedUser = (AuthenticatedUser) authentication.getPrincipal();
        resumeService.delete(authenticatedUser.getUserId(), id);
        return Result.success("删除成功", null);
    }
}
