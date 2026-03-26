package com.project.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AiAsyncRequest(
        @NotBlank(message = "Prompt is required")
        @Size(max = 50000, message = "Prompt must be under 50000 characters")
        String prompt,

        @Max(value = 4096, message = "Max tokens must be 4096 or less")
        Integer maxTokens
) {}
