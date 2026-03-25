package com.project.dto;

public record AiResponse(
        String content,
        String model,
        int tokensUsed
) {}
