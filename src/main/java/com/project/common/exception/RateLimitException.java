package com.project.common.exception;

public class RateLimitException extends BusinessException {

    public RateLimitException() {
        super(429, "Too many requests. Please try again later.");
    }
}
