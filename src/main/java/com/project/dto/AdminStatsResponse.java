package com.project.dto;

public record AdminStatsResponse(
        long totalUsers,
        long totalGenerations,
        long totalTokensUsed,
        long activeJobCount
) {}
