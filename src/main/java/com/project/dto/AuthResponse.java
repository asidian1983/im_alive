package com.project.dto;

public record AuthResponse(
        String token,
        String email,
        String name
) {}
