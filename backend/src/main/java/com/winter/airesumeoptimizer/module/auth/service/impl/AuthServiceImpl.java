package com.winter.airesumeoptimizer.module.auth.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.winter.airesumeoptimizer.common.exception.BusinessException;
import com.winter.airesumeoptimizer.module.auth.dto.LoginRequestDTO;
import com.winter.airesumeoptimizer.module.auth.dto.RegisterRequestDTO;
import com.winter.airesumeoptimizer.module.auth.service.AuthService;
import com.winter.airesumeoptimizer.module.auth.support.AccountIdentifierNormalizer;
import com.winter.airesumeoptimizer.module.auth.vo.LoginVO;
import com.winter.airesumeoptimizer.module.user.entity.User;
import com.winter.airesumeoptimizer.module.user.mapper.UserMapper;
import com.winter.airesumeoptimizer.security.JwtTokenProvider;
import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthServiceImpl implements AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthServiceImpl.class);

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
        String username = AccountIdentifierNormalizer.normalizeUsername(requestDTO.getUsername());
        String email = AccountIdentifierNormalizer.normalizeEmail(requestDTO.getEmail());
        validateRegistrationIdentifiers(username, email);

        if (existsByUsername(username)) {
            throw new BusinessException(409, "用户名已存在");
        }
        if (existsByEmail(email)) {
            throw new BusinessException(409, "邮箱已存在");
        }

        LocalDateTime now = LocalDateTime.now();
        User user = new User();
        user.setUsername(username);
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(requestDTO.getPassword()));
        user.setNickname(requestDTO.getNickname());
        user.setCreatedAt(now);
        user.setUpdatedAt(now);

        try {
            int rows = userMapper.insert(user);
            if (rows != 1 || user.getId() == null) {
                log.warn("User registration persistence failed");
                throw new BusinessException(500, "注册失败，请稍后重试");
            }
        } catch (DuplicateKeyException exception) {
            // The pre-checks improve the common response, but only the database can
            // close the concurrent-registration race. Do not log either identifier.
            log.warn("User registration rejected by unique account constraint");
            throw new BusinessException(409, "用户名或邮箱已存在");
        }
        log.info("User registered: userId={}", user.getId());
        return user.getId();
    }

    @Override
    public LoginVO login(LoginRequestDTO requestDTO) {
        String account = AccountIdentifierNormalizer.normalizeLoginAccount(requestDTO.getAccount());
        boolean emailAccount = AccountIdentifierNormalizer.isEmailLike(account);
        User user = emailAccount
                ? userMapper.selectByEmail(account)
                : userMapper.selectByUsername(account);

        if (user == null || !passwordEncoder.matches(requestDTO.getPassword(), user.getPasswordHash())) {
            log.warn("User login failed: accountType={}", emailAccount ? "email" : "username");
            throw new BusinessException(400, "用户名、邮箱或密码错误");
        }

        String token = jwtTokenProvider.generateToken(user.getId(), user.getUsername());
        log.info("User logged in: userId={}", user.getId());
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

    private void validateRegistrationIdentifiers(String username, String email) {
        if (username == null || username.isBlank()) {
            throw new BusinessException(400, "用户名不能为空");
        }
        if (AccountIdentifierNormalizer.isEmailLike(username)) {
            throw new BusinessException(400, "用户名不能使用邮箱格式");
        }
        if (email == null || email.isBlank()) {
            throw new BusinessException(400, "邮箱不能为空");
        }
        if (!AccountIdentifierNormalizer.isEmailLike(email)) {
            throw new BusinessException(400, "邮箱格式不正确");
        }
    }
}
