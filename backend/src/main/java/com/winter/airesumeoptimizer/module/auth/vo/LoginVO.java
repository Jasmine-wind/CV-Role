package com.winter.airesumeoptimizer.module.auth.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@Schema(description = "登录响应")
public class LoginVO {

    @Schema(description = "用户 ID", example = "1")
    private Long userId;

    @Schema(description = "用户名", example = "winter")
    private String username;

    @Schema(description = "邮箱", example = "winter@example.com")
    private String email;

    @Schema(description = "昵称", example = "Winter")
    private String nickname;

    @Schema(description = "JWT Token")
    private String token;

    @Schema(description = "Token 类型", example = "Bearer")
    private String tokenType;

    @Schema(description = "过期秒数", example = "1800")
    private Long expiresIn;
}
