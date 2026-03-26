package com.project.infra;

import com.project.common.exception.AiServiceException;
import com.project.dto.AiResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@Component
public class AiClient {

    private final WebClient webClient;
    private final String model;
    private final int maxTokens;

    public AiClient(
            @Value("${ai.api-url}") String apiUrl,
            @Value("${ai.api-key}") String apiKey,
            @Value("${ai.model}") String model,
            @Value("${ai.max-tokens}") int maxTokens) {
        this.webClient = WebClient.builder()
                .baseUrl(apiUrl)
                .defaultHeader("x-api-key", apiKey)
                .defaultHeader("anthropic-version", "2023-06-01")
                .build();
        this.model = model;
        this.maxTokens = maxTokens;
    }

    public String getModel() {
        return model;
    }

    public AiResponse generate(String prompt) {
        try {
            Map<String, Object> body = Map.of(
                    "model", model,
                    "max_tokens", maxTokens,
                    "messages", List.of(Map.of("role", "user", "content", prompt))
            );

            Map<?, ?> response = webClient.post()
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .timeout(Duration.ofSeconds(30))
                    .block();

            String content = extractContent(response);
            int tokens = extractTokensUsed(response);

            return new AiResponse(content, model, tokens);
        } catch (AiServiceException e) {
            throw e;
        } catch (Exception e) {
            throw new AiServiceException("AI service call failed", e);
        }
    }

    @SuppressWarnings("unchecked")
    private String extractContent(Map<?, ?> response) {
        if (response == null || !response.containsKey("content")) {
            throw new AiServiceException("Invalid AI response");
        }
        List<Map<String, Object>> contentList = (List<Map<String, Object>>) response.get("content");
        if (contentList.isEmpty()) {
            throw new AiServiceException("Empty AI response");
        }
        return (String) contentList.get(0).get("text");
    }

    private int extractTokensUsed(Map<?, ?> response) {
        if (response == null || !response.containsKey("usage")) {
            return 0;
        }
        Map<?, ?> usage = (Map<?, ?>) response.get("usage");
        Number output = (Number) usage.get("output_tokens");
        return output != null ? output.intValue() : 0;
    }
}
