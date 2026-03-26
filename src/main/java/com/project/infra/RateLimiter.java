package com.project.infra;

import com.project.common.exception.RateLimitException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class RateLimiter {

    private final RedisTemplate<String, String> redisTemplate;
    private final int maxRequests;

    public RateLimiter(RedisTemplate<String, String> redisTemplate,
                       @Value("${ai.rate-limit}") int maxRequests) {
        this.redisTemplate = redisTemplate;
        this.maxRequests = maxRequests;
    }

    public void checkRateLimit(Long userId) {
        String key = "ai:ratelimit:" + userId;
        Long count = redisTemplate.opsForValue().increment(key);

        if (count != null && count == 1) {
            redisTemplate.expire(key, Duration.ofSeconds(60));
        }

        if (count != null && count > maxRequests) {
            throw new RateLimitException();
        }
    }
}
