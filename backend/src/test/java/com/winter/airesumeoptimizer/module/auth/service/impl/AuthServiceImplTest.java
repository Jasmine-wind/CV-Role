package com.winter.airesumeoptimizer.module.auth.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.winter.airesumeoptimizer.common.exception.BusinessException;
import com.winter.airesumeoptimizer.module.auth.dto.LoginRequestDTO;
import com.winter.airesumeoptimizer.module.auth.dto.RegisterRequestDTO;
import com.winter.airesumeoptimizer.module.auth.vo.LoginVO;
import com.winter.airesumeoptimizer.module.user.entity.User;
import com.winter.airesumeoptimizer.module.user.mapper.UserMapper;
import com.winter.airesumeoptimizer.security.JwtTokenProvider;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;

class AuthServiceImplTest {

    private final UserMapper userMapper = mock(UserMapper.class);
    private final JwtTokenProvider jwtTokenProvider = mock(JwtTokenProvider.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    private final AuthServiceImpl service = new AuthServiceImpl(userMapper, jwtTokenProvider, passwordEncoder);

    @Test
    void registerShouldCreateUserWithEncodedPassword() {
        RegisterRequestDTO request = new RegisterRequestDTO();
        request.setUsername("winter");
        request.setEmail("winter@example.com");
        request.setPassword("raw-password");
        request.setNickname("Winter");

        when(userMapper.exists(any(Wrapper.class))).thenReturn(false);
        when(passwordEncoder.encode("raw-password")).thenReturn("encoded-password");
        when(userMapper.insert(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(10L);
            return 1;
        });

        Long userId = service.register(request);

        assertThat(userId).isEqualTo(10L);
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userMapper).insert(userCaptor.capture());
        User savedUser = userCaptor.getValue();
        assertThat(savedUser.getUsername()).isEqualTo("winter");
        assertThat(savedUser.getEmail()).isEqualTo("winter@example.com");
        assertThat(savedUser.getPasswordHash()).isEqualTo("encoded-password");
        assertThat(savedUser.getCreatedAt()).isNotNull();
        assertThat(savedUser.getUpdatedAt()).isNotNull();
    }

    @Test
    void registerShouldRejectDuplicateUsername() {
        RegisterRequestDTO request = new RegisterRequestDTO();
        request.setUsername("winter");
        request.setEmail("winter@example.com");
        request.setPassword("raw-password");

        when(userMapper.exists(any(Wrapper.class))).thenReturn(true);

        assertThatThrownBy(() -> service.register(request))
                .isInstanceOf(BusinessException.class)
                .hasMessage("用户名已存在");
    }

    @Test
    void loginShouldReturnTokenWhenPasswordMatches() {
        LoginRequestDTO request = new LoginRequestDTO();
        request.setAccount("winter");
        request.setPassword("raw-password");

        User user = new User();
        user.setId(10L);
        user.setUsername("winter");
        user.setEmail("winter@example.com");
        user.setNickname("Winter");
        user.setPasswordHash("encoded-password");

        when(userMapper.selectOne(any(Wrapper.class))).thenReturn(user);
        when(passwordEncoder.matches("raw-password", "encoded-password")).thenReturn(true);
        when(jwtTokenProvider.generateToken(10L, "winter")).thenReturn("jwt-token");
        when(jwtTokenProvider.getExpirationSeconds()).thenReturn(7200L);

        LoginVO loginVO = service.login(request);

        assertThat(loginVO.getUserId()).isEqualTo(10L);
        assertThat(loginVO.getUsername()).isEqualTo("winter");
        assertThat(loginVO.getToken()).isEqualTo("jwt-token");
        assertThat(loginVO.getTokenType()).isEqualTo("Bearer");
        assertThat(loginVO.getExpiresIn()).isEqualTo(7200L);
    }

    @Test
    void loginShouldRejectWrongPassword() {
        LoginRequestDTO request = new LoginRequestDTO();
        request.setAccount("winter@example.com");
        request.setPassword("wrong-password");

        User user = new User();
        user.setPasswordHash("encoded-password");

        when(userMapper.selectOne(any(Wrapper.class))).thenReturn(user);
        when(passwordEncoder.matches("wrong-password", "encoded-password")).thenReturn(false);

        assertThatThrownBy(() -> service.login(request))
                .isInstanceOf(BusinessException.class)
                .hasMessage("用户名、邮箱或密码错误");
    }
}
