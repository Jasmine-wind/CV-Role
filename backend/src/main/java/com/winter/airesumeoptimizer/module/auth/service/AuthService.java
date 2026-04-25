package com.winter.airesumeoptimizer.module.auth.service;

import com.winter.airesumeoptimizer.module.auth.dto.RegisterRequestDTO;

public interface AuthService {

    Long register(RegisterRequestDTO requestDTO);
}
