package com.winter.airesumeoptimizer.module.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema(description = "用户注册请求")
public class RegisterRequestDTO {

    @Schema(description = "用户名", example = "winter")
    @NotBlank(message = "用户名不能为空")
    @Size(min = 3, max = 20, message = "用户名长度必须在3到20个字符之间")
    private String username;

    @Schema(description = "邮箱", example = "winter@example.com")
    @NotBlank(message = "邮箱不能为空")
    @Email(message = "邮箱格式不正确")
    private String email;

    @Schema(description = "密码", example = "123456")
    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 100, message = "密码长度必须在6到100个字符之间")
    private String password;

    @Schema(description = "昵称", example = "Winter")
    @Size(max = 50, message = "昵称长度不能超过50个字符")
    private String nickname;
}
