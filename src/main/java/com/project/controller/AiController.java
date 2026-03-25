package com.project.controller;

import com.project.dto.AiRequest;
import com.project.dto.AiResponse;
import com.project.service.AiService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/ai")
public class AiController {

    private final AiService aiService;

    public AiController(AiService aiService) {
        this.aiService = aiService;
    }

    @PostMapping("/generate")
    public ResponseEntity<AiResponse> generate(@Valid @RequestBody AiRequest request) {
        return ResponseEntity.ok(aiService.generate(request));
    }
}
