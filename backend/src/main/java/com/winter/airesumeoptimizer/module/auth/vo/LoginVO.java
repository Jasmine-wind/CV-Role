package com.winter.airesumeoptimizer.module.auth.vo;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class LoginVO {

    private Long userId;
    private String username;
    private String email;
    private String nickname;
    private String token;
    private String tokenType;
    private Long expiresIn;
}
