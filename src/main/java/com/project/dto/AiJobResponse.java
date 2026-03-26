package com.project.dto;

import com.project.domain.AiJob;

import java.time.LocalDateTime;

public record AiJobResponse(
        Long jobId,
        String status,
        String result,
        String errorMessage,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static AiJobResponse from(AiJob job) {
        String result = null;
        if (job.getGeneration() != null) {
            result = job.getGeneration().getResult();
        }
        return new AiJobResponse(
                job.getId(),
                job.getStatus().name(),
                result,
                job.getErrorMessage(),
                job.getCreatedAt(),
                job.getUpdatedAt()
        );
    }
}
