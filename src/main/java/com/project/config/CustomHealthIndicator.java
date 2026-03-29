package com.project.config;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.stereotype.Component;

@Component("redisQueue")
public class CustomHealthIndicator implements HealthIndicator {

    private final RedisConnectionFactory redisConnectionFactory;

    public CustomHealthIndicator(RedisConnectionFactory redisConnectionFactory) {
        this.redisConnectionFactory = redisConnectionFactory;
    }

    @Override
    public Health health() {
        try {
            var connection = redisConnectionFactory.getConnection();
            Long queueSize = connection.lLen("ai:jobs".getBytes());
            connection.close();

            if (queueSize != null && queueSize > 100) {
                return Health.down()
                        .withDetail("queueSize", queueSize)
                        .withDetail("reason", "Queue backlog exceeds threshold (100)")
                        .build();
            }

            return Health.up()
                    .withDetail("queueSize", queueSize != null ? queueSize : 0)
                    .build();
        } catch (Exception e) {
            return Health.down()
                    .withDetail("error", e.getMessage())
                    .build();
        }
    }
}
