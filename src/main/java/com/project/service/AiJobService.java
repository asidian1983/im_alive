package com.project.service;

import com.project.common.exception.AiServiceException;
import com.project.common.exception.ForbiddenException;
import com.project.common.exception.NotFoundException;
import com.project.domain.AiGeneration;
import com.project.domain.AiJob;
import com.project.domain.User;
import com.project.dto.AiJobResponse;
import com.project.dto.AiResponse;
import com.project.infra.AiClient;
import com.project.infra.RateLimiter;
import com.project.repository.AiGenerationRepository;
import com.project.repository.AiJobRepository;
import com.project.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;

@Service
public class AiJobService {

    private static final Logger log = LoggerFactory.getLogger(AiJobService.class);

    private final AiJobRepository aiJobRepository;
    private final AiGenerationRepository aiGenerationRepository;
    private final UserRepository userRepository;
    private final AiClient aiClient;
    private final RateLimiter rateLimiter;
    private final RedisTemplate<String, String> redisTemplate;
    private final int maxRetries;

    public AiJobService(AiJobRepository aiJobRepository,
                        AiGenerationRepository aiGenerationRepository,
                        UserRepository userRepository,
                        AiClient aiClient,
                        RateLimiter rateLimiter,
                        RedisTemplate<String, String> redisTemplate,
                        @Value("${ai.max-retries}") int maxRetries) {
        this.aiJobRepository = aiJobRepository;
        this.aiGenerationRepository = aiGenerationRepository;
        this.userRepository = userRepository;
        this.aiClient = aiClient;
        this.rateLimiter = rateLimiter;
        this.redisTemplate = redisTemplate;
        this.maxRetries = maxRetries;
    }

    @Transactional
    public AiJobResponse createJob(Long userId, String prompt) {
        rateLimiter.checkRateLimit(userId);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User"));

        AiJob job = new AiJob(user, prompt);
        aiJobRepository.save(job);

        redisTemplate.opsForList().leftPush("ai:jobs", job.getId().toString());
        updateJobCache(job);

        return AiJobResponse.from(job);
    }

    @Transactional(readOnly = true)
    public AiJobResponse getJobStatus(Long jobId, Long userId) {
        String cached = redisTemplate.opsForValue().get("ai:job:" + jobId + ":status");
        if (cached != null && cached.equals("QUEUED") || cached != null && cached.equals("PROCESSING")) {
            AiJob job = aiJobRepository.findByIdAndUserId(jobId, userId)
                    .orElseThrow(() -> new NotFoundException("AI job"));
            return AiJobResponse.from(job);
        }

        AiJob job = aiJobRepository.findByIdAndUserId(jobId, userId)
                .orElseThrow(() -> new NotFoundException("AI job"));
        return AiJobResponse.from(job);
    }

    @Transactional
    public void processJob(Long jobId) {
        AiJob job = aiJobRepository.findById(jobId).orElse(null);
        if (job == null) {
            log.warn("Job {} not found, skipping", jobId);
            return;
        }

        job.markProcessing();
        aiJobRepository.save(job);
        updateJobCache(job);

        try {
            AiResponse response = aiClient.generate(job.getPrompt());

            AiGeneration generation = new AiGeneration(
                    job.getUser(), job.getPrompt(), response.content(),
                    response.model(), response.tokensUsed()
            );
            aiGenerationRepository.save(generation);

            job.markCompleted(generation);
            aiJobRepository.save(job);
            updateJobCache(job);

            log.info("Job {} completed successfully", jobId);
        } catch (AiServiceException e) {
            handleJobFailure(job, e.getMessage());
        } catch (Exception e) {
            handleJobFailure(job, "Unexpected error: " + e.getMessage());
        }
    }

    private void handleJobFailure(AiJob job, String errorMessage) {
        if (job.getRetryCount() < maxRetries) {
            job.incrementRetry();
            aiJobRepository.save(job);
            redisTemplate.opsForList().leftPush("ai:jobs", job.getId().toString());
            updateJobCache(job);
            log.warn("Job {} failed, retry {}/{}", job.getId(), job.getRetryCount(), maxRetries);
        } else {
            job.markFailed(errorMessage);
            aiJobRepository.save(job);
            updateJobCache(job);
            log.error("Job {} failed permanently: {}", job.getId(), errorMessage);
        }
    }

    private void updateJobCache(AiJob job) {
        redisTemplate.opsForValue().set(
                "ai:job:" + job.getId() + ":status",
                job.getStatus().name(),
                Duration.ofHours(1)
        );
    }
}
