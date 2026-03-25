package com.project.service;

import com.project.dto.AiRequest;
import com.project.dto.AiResponse;
import com.project.infra.AiClient;
import org.springframework.stereotype.Service;

@Service
public class AiService {

    private final AiClient aiClient;

    public AiService(AiClient aiClient) {
        this.aiClient = aiClient;
    }

    public AiResponse generate(AiRequest request) {
        return aiClient.generate(request.prompt());
    }
}
