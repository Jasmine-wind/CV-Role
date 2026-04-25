package com.winter.airesumeoptimizer.module.auth.service;

import com.winter.airesumeoptimizer.module.auth.dto.LoginRequestDTO;
import com.winter.airesumeoptimizer.module.auth.dto.RegisterRequestDTO;
import com.winter.airesumeoptimizer.module.auth.vo.LoginVO;

public interface AuthService {

    Long register(RegisterRequestDTO requestDTO);

    LoginVO login(LoginRequestDTO requestDTO);
}
