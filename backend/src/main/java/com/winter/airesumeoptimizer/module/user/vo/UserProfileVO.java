package com.winter.airesumeoptimizer.module.user.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@Schema(description = "当前用户资料")
public class UserProfileVO {

    @Schema(description = "用户 ID", example = "1")
    private Long id;
    @Schema(description = "用户名", example = "winter")
    private String username;
    @Schema(description = "邮箱", example = "winter@example.com")
    private String email;
    @Schema(description = "昵称", example = "Winter")
    private String nickname;
    @Schema(description = "注册时间")
    private LocalDateTime createdAt;
}
