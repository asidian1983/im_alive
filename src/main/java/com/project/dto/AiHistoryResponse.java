package com.project.dto;

import com.project.domain.AiGeneration;

import java.time.LocalDateTime;

public record AiHistoryResponse(
        Long id,
        String prompt,
        String result,
        String model,
        Integer tokensUsed,
        LocalDateTime createdAt
) {
    public static AiHistoryResponse from(AiGeneration gen) {
        return new AiHistoryResponse(
                gen.getId(),
                gen.getPrompt(),
                gen.getResult(),
                gen.getModel(),
                gen.getTokensUsed(),
                gen.getCreatedAt()
        );
    }
}
