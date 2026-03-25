package com.project.service;

import com.project.domain.RefreshToken;
import com.project.domain.User;
import com.project.dto.AuthResponse;
import com.project.dto.LoginRequest;
import com.project.dto.SignupRequest;
import com.project.dto.TokenRefreshRequest;
import com.project.infra.JwtProvider;
import com.project.repository.RefreshTokenRepository;
import com.project.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private RefreshTokenRepository refreshTokenRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtProvider jwtProvider;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(
                userRepository, refreshTokenRepository, passwordEncoder, jwtProvider, 604800000L);
    }

    @Test
    void signup_success() {
        when(userRepository.existsByEmail("test@test.com")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("encoded");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(jwtProvider.generateToken(any(), anyString(), anyString())).thenReturn("jwt-token");
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(inv -> inv.getArgument(0));

        AuthResponse response = authService.signup(new SignupRequest("test@test.com", "password123", "Test"));

        assertNotNull(response.accessToken());
        assertEquals("test@test.com", response.email());
        assertEquals("Test", response.name());
        assertNotNull(response.refreshToken());
    }

    @Test
    void signup_duplicateEmail_throws() {
        when(userRepository.existsByEmail("dup@test.com")).thenReturn(true);

        assertThrows(IllegalArgumentException.class,
                () -> authService.signup(new SignupRequest("dup@test.com", "password123", "Test")));
    }

    @Test
    void login_success() {
        User user = new User("test@test.com", "encoded", "Test");
        when(userRepository.findByEmail("test@test.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("password123", "encoded")).thenReturn(true);
        when(jwtProvider.generateToken(any(), anyString(), anyString())).thenReturn("jwt-token");
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(inv -> inv.getArgument(0));

        AuthResponse response = authService.login(new LoginRequest("test@test.com", "password123"));

        assertNotNull(response.accessToken());
        assertEquals("test@test.com", response.email());
    }

    @Test
    void login_wrongPassword_throws() {
        User user = new User("test@test.com", "encoded", "Test");
        when(userRepository.findByEmail("test@test.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", "encoded")).thenReturn(false);

        assertThrows(IllegalArgumentException.class,
                () -> authService.login(new LoginRequest("test@test.com", "wrong")));
    }

    @Test
    void login_userNotFound_throws() {
        when(userRepository.findByEmail("none@test.com")).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class,
                () -> authService.login(new LoginRequest("none@test.com", "password123")));
    }

    @Test
    void refresh_success() {
        User user = new User("test@test.com", "encoded", "Test");
        RefreshToken token = new RefreshToken("valid-token", user, LocalDateTime.now().plusDays(7));
        when(refreshTokenRepository.findByToken("valid-token")).thenReturn(Optional.of(token));
        when(jwtProvider.generateToken(any(), anyString(), anyString())).thenReturn("new-jwt");
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(inv -> inv.getArgument(0));

        AuthResponse response = authService.refresh(new TokenRefreshRequest("valid-token"));

        assertNotNull(response.accessToken());
        assertNotNull(response.refreshToken());
    }

    @Test
    void refresh_expired_throws() {
        User user = new User("test@test.com", "encoded", "Test");
        RefreshToken token = new RefreshToken("expired-token", user, LocalDateTime.now().minusDays(1));
        when(refreshTokenRepository.findByToken("expired-token")).thenReturn(Optional.of(token));

        assertThrows(IllegalArgumentException.class,
                () -> authService.refresh(new TokenRefreshRequest("expired-token")));
    }

    @Test
    void refresh_invalidToken_throws() {
        when(refreshTokenRepository.findByToken("bad-token")).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class,
                () -> authService.refresh(new TokenRefreshRequest("bad-token")));
    }
}
