package com.winter.airesumeoptimizer.module.auth.controller;

import com.winter.airesumeoptimizer.common.result.Result;
import com.winter.airesumeoptimizer.module.auth.dto.LoginRequestDTO;
import com.winter.airesumeoptimizer.module.auth.dto.RegisterRequestDTO;
import com.winter.airesumeoptimizer.module.auth.service.AuthService;
import com.winter.airesumeoptimizer.module.auth.vo.LoginVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Auth", description = "注册、登录相关接口")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    @Operation(summary = "用户注册", description = "创建新用户并返回用户 ID")
    public Result<Map<String, Long>> register(@Valid @RequestBody RegisterRequestDTO requestDTO) {
        Long userId = authService.register(requestDTO);
        return Result.success("注册成功", Map.of("userId", userId));
    }

    @PostMapping("/login")
    @Operation(summary = "用户登录", description = "使用用户名或邮箱登录，返回 JWT Token")
    public Result<LoginVO> login(@Valid @RequestBody LoginRequestDTO requestDTO) {
        return Result.success("登录成功", authService.login(requestDTO));
    }
}
