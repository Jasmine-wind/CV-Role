package com.winter.airesumeoptimizer.module.auth.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.winter.airesumeoptimizer.common.exception.BusinessException;
import com.winter.airesumeoptimizer.module.auth.dto.LoginRequestDTO;
import com.winter.airesumeoptimizer.module.auth.dto.RegisterRequestDTO;
import com.winter.airesumeoptimizer.module.auth.service.AuthService;
import com.winter.airesumeoptimizer.module.auth.vo.LoginVO;
import com.winter.airesumeoptimizer.module.user.entity.User;
import com.winter.airesumeoptimizer.module.user.mapper.UserMapper;
import com.winter.airesumeoptimizer.security.JwtTokenProvider;
import java.time.LocalDateTime;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthServiceImpl implements AuthService {

    private final UserMapper userMapper;
    private final JwtTokenProvider jwtTokenProvider;
    private final PasswordEncoder passwordEncoder;

    public AuthServiceImpl(
            UserMapper userMapper,
            JwtTokenProvider jwtTokenProvider,
            PasswordEncoder passwordEncoder) {
        this.userMapper = userMapper;
        this.jwtTokenProvider = jwtTokenProvider;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public Long register(RegisterRequestDTO requestDTO) {
        if (existsByUsername(requestDTO.getUsername())) {
            throw new BusinessException(400, "用户名已存在");
        }
        if (existsByEmail(requestDTO.getEmail())) {
            throw new BusinessException(400, "邮箱已存在");
        }

        LocalDateTime now = LocalDateTime.now();
        User user = new User();
        user.setUsername(requestDTO.getUsername());
        user.setEmail(requestDTO.getEmail());
        user.setPasswordHash(passwordEncoder.encode(requestDTO.getPassword()));
        user.setNickname(requestDTO.getNickname());
        user.setCreatedAt(now);
        user.setUpdatedAt(now);

        int rows = userMapper.insert(user);
        if (rows != 1 || user.getId() == null) {
            throw new BusinessException(500, "注册失败，请稍后重试");
        }
        return user.getId();
    }

    @Override
    public LoginVO login(LoginRequestDTO requestDTO) {
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, requestDTO.getAccount())
                .or()
                .eq(User::getEmail, requestDTO.getAccount())
                .last("LIMIT 1"));

        if (user == null || !passwordEncoder.matches(requestDTO.getPassword(), user.getPasswordHash())) {
            throw new BusinessException(400, "用户名、邮箱或密码错误");
        }

        String token = jwtTokenProvider.generateToken(user.getId(), user.getUsername());
        return LoginVO.builder()
                .userId(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .nickname(user.getNickname())
                .token(token)
                .tokenType("Bearer")
                .expiresIn(jwtTokenProvider.getExpirationSeconds())
                .build();
    }

    private boolean existsByUsername(String username) {
        return userMapper.exists(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, username));
    }

    private boolean existsByEmail(String email) {
        return userMapper.exists(new LambdaQueryWrapper<User>()
                .eq(User::getEmail, email));
    }
}
