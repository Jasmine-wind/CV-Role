package com.winter.airesumeoptimizer.module.user.service;

import com.winter.airesumeoptimizer.module.user.vo.UserProfileVO;

public interface UserService {

    UserProfileVO getCurrentUserProfile(Long userId);
}
