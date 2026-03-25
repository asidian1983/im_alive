package com.project.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AiRequest(
        @NotBlank(message = "Prompt is required")
        @Size(max = 10000, message = "Prompt must be under 10000 characters")
        String prompt
) {}
