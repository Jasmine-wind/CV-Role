package com.winter.airesumeoptimizer.module.user.vo;

import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class UserProfileVO {

    private Long id;
    private String username;
    private String email;
    private String nickname;
    private LocalDateTime createdAt;
}
