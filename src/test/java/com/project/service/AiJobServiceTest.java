package com.project.service;

import com.project.common.exception.NotFoundException;
import com.project.domain.AiJob;
import com.project.domain.AiJobStatus;
import com.project.domain.User;
import com.project.dto.AiJobResponse;
import com.project.dto.AiResponse;
import com.project.infra.AiClient;
import com.project.infra.RateLimiter;
import com.project.repository.AiGenerationRepository;
import com.project.repository.AiJobRepository;
import com.project.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AiJobServiceTest {

    @Mock private AiJobRepository aiJobRepository;
    @Mock private AiGenerationRepository aiGenerationRepository;
    @Mock private UserRepository userRepository;
    @Mock private AiClient aiClient;
    @Mock private RateLimiter rateLimiter;
    @Mock private RedisTemplate<String, String> redisTemplate;
    @Mock private ListOperations<String, String> listOps;
    @Mock private ValueOperations<String, String> valueOps;

    private AiJobService aiJobService;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForList()).thenReturn(listOps);
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOps);
        aiJobService = new AiJobService(
                aiJobRepository, aiGenerationRepository, userRepository,
                aiClient, rateLimiter, redisTemplate, 3);
    }

    @Test
    void createJob_success() {
        User user = new User("test@test.com", "encoded", "Test");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(aiJobRepository.save(any(AiJob.class))).thenAnswer(inv -> inv.getArgument(0));

        AiJobResponse response = aiJobService.createJob(1L, "test prompt");

        assertEquals("QUEUED", response.status());
        verify(listOps).leftPush(eq("ai:jobs"), any());
        verify(rateLimiter).checkRateLimit(1L);
    }

    @Test
    void createJob_userNotFound_throws() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> aiJobService.createJob(99L, "prompt"));
    }

    @Test
    void getJobStatus_notFound_throws() {
        when(aiJobRepository.findByIdAndUserId(1L, 1L)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> aiJobService.getJobStatus(1L, 1L));
    }

    @Test
    void processJob_success() {
        User user = new User("test@test.com", "encoded", "Test");
        AiJob job = new AiJob(user, "test prompt");
        when(aiJobRepository.findById(1L)).thenReturn(Optional.of(job));
        when(aiClient.generate("test prompt")).thenReturn(new AiResponse("result", "model", 10));
        when(aiGenerationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(aiJobRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        aiJobService.processJob(1L);

        assertEquals(AiJobStatus.COMPLETED, job.getStatus());
    }

    @Test
    void processJob_notFound_skips() {
        when(aiJobRepository.findById(999L)).thenReturn(Optional.empty());

        aiJobService.processJob(999L);

        verify(aiClient, never()).generate(any());
    }
}
