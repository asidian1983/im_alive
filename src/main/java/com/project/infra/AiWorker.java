package com.project.infra;

import com.project.service.AiJobService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class AiWorker {

    private static final Logger log = LoggerFactory.getLogger(AiWorker.class);

    private final RedisTemplate<String, String> redisTemplate;
    private final AiJobService aiJobService;

    public AiWorker(RedisTemplate<String, String> redisTemplate, AiJobService aiJobService) {
        this.redisTemplate = redisTemplate;
        this.aiJobService = aiJobService;
    }

    @Scheduled(fixedDelay = 1000)
    public void processQueue() {
        String jobId = redisTemplate.opsForList().rightPop("ai:jobs", Duration.ofSeconds(5));

        if (jobId == null) return;

        log.info("Processing job {}", jobId);
        aiJobService.processJob(Long.parseLong(jobId));
    }
}
