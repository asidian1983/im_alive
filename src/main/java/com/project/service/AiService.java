package com.project.service;

import com.project.common.exception.ForbiddenException;
import com.project.common.exception.NotFoundException;
import com.project.domain.AiGeneration;
import com.project.domain.User;
import com.project.dto.*;
import com.project.infra.AiClient;
import com.project.infra.RateLimiter;
import com.project.repository.AiGenerationRepository;
import com.project.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AiService {

    private final AiClient aiClient;
    private final RateLimiter rateLimiter;
    private final AiGenerationRepository aiGenerationRepository;
    private final UserRepository userRepository;

    public AiService(AiClient aiClient,
                     RateLimiter rateLimiter,
                     AiGenerationRepository aiGenerationRepository,
                     UserRepository userRepository) {
        this.aiClient = aiClient;
        this.rateLimiter = rateLimiter;
        this.aiGenerationRepository = aiGenerationRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public AiResponse generate(Long userId, AiRequest request) {
        rateLimiter.checkRateLimit(userId);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User"));

        AiResponse response = aiClient.generate(request.prompt());

        AiGeneration generation = new AiGeneration(
                user, request.prompt(), response.content(), response.model(), response.tokensUsed()
        );
        aiGenerationRepository.save(generation);

        return response;
    }

    @Transactional(readOnly = true)
    public PageResponse<AiHistoryResponse> getHistory(Long userId, int page, int size) {
        Page<AiGeneration> generations = aiGenerationRepository
                .findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(page, size));
        return PageResponse.from(generations, AiHistoryResponse::from);
    }

    @Transactional(readOnly = true)
    public AiHistoryResponse getHistoryDetail(Long userId, Long generationId) {
        AiGeneration generation = aiGenerationRepository.findById(generationId)
                .orElseThrow(() -> new NotFoundException("AI generation"));

        if (!generation.getUser().getId().equals(userId)) {
            throw new ForbiddenException();
        }

        return AiHistoryResponse.from(generation);
    }

    @Transactional
    public void deleteHistory(Long userId, Long generationId) {
        AiGeneration generation = aiGenerationRepository.findById(generationId)
                .orElseThrow(() -> new NotFoundException("AI generation"));

        if (!generation.getUser().getId().equals(userId)) {
            throw new ForbiddenException();
        }

        aiGenerationRepository.delete(generation);
    }
}
