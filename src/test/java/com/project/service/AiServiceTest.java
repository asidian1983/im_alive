package com.project.service;

import com.project.common.exception.ForbiddenException;
import com.project.common.exception.NotFoundException;
import com.project.domain.AiGeneration;
import com.project.domain.User;
import com.project.dto.AiRequest;
import com.project.dto.AiResponse;
import com.project.infra.AiClient;
import com.project.infra.RateLimiter;
import com.project.repository.AiGenerationRepository;
import com.project.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AiServiceTest {

    @Mock private AiClient aiClient;
    @Mock private RateLimiter rateLimiter;
    @Mock private AiGenerationRepository aiGenerationRepository;
    @Mock private UserRepository userRepository;

    private AiService aiService;

    private User testUser;

    @BeforeEach
    void setUp() {
        aiService = new AiService(aiClient, rateLimiter, aiGenerationRepository, userRepository);
        testUser = new User("test@test.com", "encoded", "Test");
    }

    // --- generate ---

    @Test
    void generate_success() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(aiClient.generate("hello")).thenReturn(new AiResponse("world", "model", 10));
        when(aiGenerationRepository.save(any(AiGeneration.class))).thenAnswer(inv -> inv.getArgument(0));

        AiResponse response = aiService.generate(1L, new AiRequest("hello"));

        assertEquals("world", response.content());
        assertEquals(10, response.tokensUsed());
        verify(rateLimiter).checkRateLimit(1L);
        verify(aiGenerationRepository).save(any(AiGeneration.class));
    }

    @Test
    void generate_userNotFound_throws() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class,
                () -> aiService.generate(99L, new AiRequest("hello")));
    }

    @Test
    void generate_rateLimitChecked() {
        doThrow(new com.project.common.exception.RateLimitException())
                .when(rateLimiter).checkRateLimit(1L);

        assertThrows(com.project.common.exception.RateLimitException.class,
                () -> aiService.generate(1L, new AiRequest("hello")));

        verify(aiClient, never()).generate(any());
    }

    // --- getHistory ---

    @Test
    void getHistory_success() {
        AiGeneration gen = new AiGeneration(testUser, "prompt", "result", "model", 50);
        Page<AiGeneration> page = new PageImpl<>(List.of(gen), PageRequest.of(0, 20), 1);
        when(aiGenerationRepository.findByUserIdOrderByCreatedAtDesc(eq(1L), any()))
                .thenReturn(page);

        var response = aiService.getHistory(1L, 0, 20);

        assertEquals(1, response.content().size());
        assertEquals("prompt", response.content().get(0).prompt());
        assertEquals(1, response.totalElements());
    }

    @Test
    void getHistory_empty() {
        Page<AiGeneration> empty = new PageImpl<>(List.of(), PageRequest.of(0, 20), 0);
        when(aiGenerationRepository.findByUserIdOrderByCreatedAtDesc(eq(1L), any()))
                .thenReturn(empty);

        var response = aiService.getHistory(1L, 0, 20);

        assertTrue(response.content().isEmpty());
        assertEquals(0, response.totalElements());
    }

    // --- getHistoryDetail ---

    @Test
    void getHistoryDetail_success() {
        AiGeneration gen = new AiGeneration(testUser, "prompt", "result", "model", 50);
        when(aiGenerationRepository.findById(1L)).thenReturn(Optional.of(gen));

        var response = aiService.getHistoryDetail(testUser.getId(), 1L);

        assertEquals("prompt", response.prompt());
        assertEquals("result", response.result());
    }

    @Test
    void getHistoryDetail_notFound_throws() {
        when(aiGenerationRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class,
                () -> aiService.getHistoryDetail(1L, 99L));
    }

    @Test
    void getHistoryDetail_otherUser_forbidden() {
        User otherUser = new User("other@test.com", "encoded", "Other");
        AiGeneration gen = new AiGeneration(otherUser, "prompt", "result", "model", 50);
        when(aiGenerationRepository.findById(1L)).thenReturn(Optional.of(gen));

        assertThrows(ForbiddenException.class,
                () -> aiService.getHistoryDetail(999L, 1L));
    }

    // --- deleteHistory ---

    @Test
    void deleteHistory_success() {
        AiGeneration gen = new AiGeneration(testUser, "prompt", "result", "model", 50);
        when(aiGenerationRepository.findById(1L)).thenReturn(Optional.of(gen));

        aiService.deleteHistory(testUser.getId(), 1L);

        verify(aiGenerationRepository).delete(gen);
    }

    @Test
    void deleteHistory_notFound_throws() {
        when(aiGenerationRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class,
                () -> aiService.deleteHistory(1L, 99L));
    }

    @Test
    void deleteHistory_otherUser_forbidden() {
        User otherUser = new User("other@test.com", "encoded", "Other");
        AiGeneration gen = new AiGeneration(otherUser, "prompt", "result", "model", 50);
        when(aiGenerationRepository.findById(1L)).thenReturn(Optional.of(gen));

        assertThrows(ForbiddenException.class,
                () -> aiService.deleteHistory(999L, 1L));

        verify(aiGenerationRepository, never()).delete(any());
    }
}
