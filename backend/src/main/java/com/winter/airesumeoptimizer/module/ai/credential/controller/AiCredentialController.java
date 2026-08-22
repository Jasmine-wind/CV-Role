package com.winter.airesumeoptimizer.module.ai.credential.controller;

import com.winter.airesumeoptimizer.common.result.Result;
import com.winter.airesumeoptimizer.infra.ai.AiCredentialTestGateway;
import com.winter.airesumeoptimizer.infra.ai.AiCredentialTestResult;
import com.winter.airesumeoptimizer.module.ai.credential.dto.AiCredentialUpsertRequestDTO;
import com.winter.airesumeoptimizer.module.ai.credential.service.AiCredentialService;
import com.winter.airesumeoptimizer.module.ai.credential.vo.AiCredentialTestVO;
import com.winter.airesumeoptimizer.module.ai.credential.vo.AiCredentialVO;
import com.winter.airesumeoptimizer.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/settings/ai-provider")
@Tag(name = "AI Provider Settings", description = "账户级 OpenAI-compatible Provider 配置")
@SecurityRequirement(name = "bearerAuth")
public class AiCredentialController {

    private final AiCredentialService credentialService;
    private final AiCredentialTestGateway testGateway;

    public AiCredentialController(
            AiCredentialService credentialService,
            AiCredentialTestGateway testGateway) {
        this.credentialService = credentialService;
        this.testGateway = testGateway;
    }

    @GetMapping
    @Operation(summary = "读取 AI Provider 配置状态", description = "只返回非敏感配置和 masked/configured 状态")
    public Result<AiCredentialVO> get(Authentication authentication) {
        return Result.success(credentialService.get(userId(authentication)));
    }

    @PutMapping
    @Operation(summary = "保存或替换 AI Provider 配置", description = "替换后默认 DISABLED，必须显式启用")
    public Result<AiCredentialVO> save(
            @Valid @RequestBody AiCredentialUpsertRequestDTO request,
            Authentication authentication) {
        return Result.success(credentialService.saveOrReplace(userId(authentication), request));
    }

    @PostMapping("/test")
    @Operation(summary = "测试 AI Provider 配置", description = "只使用本次请求中的短生命周期 API Key，不保存")
    public Result<AiCredentialTestVO> test(
            @Valid @RequestBody AiCredentialUpsertRequestDTO request,
            Authentication authentication) {
        AiCredentialTestResult result = testGateway.test(
                userId(authentication),
                request.getApiKey(),
                request.getBaseUrl(),
                request.getModel(),
                request.getConfig());
        return Result.success(AiCredentialTestVO.builder()
                .success(result.success())
                .failureCode(result.failureCode() == null ? null : result.failureCode().name())
                .message(result.message())
                .build());
    }

    @PostMapping("/enable")
    @Operation(summary = "启用账户级 AI Provider")
    public Result<AiCredentialVO> enable(Authentication authentication) {
        return Result.success(credentialService.enable(userId(authentication)));
    }

    @PostMapping("/disable")
    @Operation(summary = "停用账户级 AI Provider")
    public Result<AiCredentialVO> disable(Authentication authentication) {
        return Result.success(credentialService.disable(userId(authentication)));
    }

    @DeleteMapping
    @Operation(summary = "删除账户级 AI Provider")
    public Result<Void> delete(Authentication authentication) {
        credentialService.delete(userId(authentication));
        return Result.success(null);
    }

    private Long userId(Authentication authentication) {
        return ((AuthenticatedUser) authentication.getPrincipal()).getUserId();
    }
}
