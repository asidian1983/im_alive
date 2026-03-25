package com.project.infra;

import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class JwtProviderTest {

    private JwtProvider jwtProvider;

    @BeforeEach
    void setUp() {
        jwtProvider = new JwtProvider(
                "test-secret-key-must-be-at-least-256-bits-long-for-hmac-sha", 3600000L);
    }

    @Test
    void generateAndParseToken() {
        String token = jwtProvider.generateToken(1L, "test@test.com", "USER");

        assertTrue(jwtProvider.validateToken(token));

        Claims claims = jwtProvider.parseToken(token);
        assertEquals("1", claims.getSubject());
        assertEquals("test@test.com", claims.get("email"));
        assertEquals("USER", claims.get("role"));
    }

    @Test
    void getUserId() {
        String token = jwtProvider.generateToken(42L, "test@test.com", "USER");
        assertEquals(42L, jwtProvider.getUserId(token));
    }

    @Test
    void validateToken_invalid_returnsFalse() {
        assertFalse(jwtProvider.validateToken("invalid.token.here"));
    }

    @Test
    void validateToken_expired_returnsFalse() {
        JwtProvider shortLived = new JwtProvider(
                "test-secret-key-must-be-at-least-256-bits-long-for-hmac-sha", -1000L);
        String token = shortLived.generateToken(1L, "test@test.com", "USER");

        assertFalse(jwtProvider.validateToken(token));
    }
}
