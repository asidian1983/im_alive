package com.project.common.exception;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ExceptionTest {

    @Test
    void notFoundException() {
        NotFoundException ex = new NotFoundException("User");
        assertEquals(404, ex.getStatus());
        assertEquals("User not found", ex.getMessage());
    }

    @Test
    void duplicateException() {
        DuplicateException ex = new DuplicateException("Email already exists");
        assertEquals(409, ex.getStatus());
    }

    @Test
    void forbiddenException() {
        ForbiddenException ex = new ForbiddenException();
        assertEquals(403, ex.getStatus());
        assertEquals("Access denied", ex.getMessage());
    }

    @Test
    void rateLimitException() {
        RateLimitException ex = new RateLimitException();
        assertEquals(429, ex.getStatus());
    }

    @Test
    void aiServiceException() {
        AiServiceException ex = new AiServiceException("API down");
        assertEquals("API down", ex.getMessage());

        AiServiceException exWithCause = new AiServiceException("fail", new RuntimeException("cause"));
        assertNotNull(exWithCause.getCause());
    }
}
