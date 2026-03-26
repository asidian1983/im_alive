package com.project.controller;

import com.project.dto.*;
import com.project.service.AiJobService;
import com.project.service.AiService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/ai")
public class AiController {

    private final AiService aiService;
    private final AiJobService aiJobService;

    public AiController(AiService aiService, AiJobService aiJobService) {
        this.aiService = aiService;
        this.aiJobService = aiJobService;
    }

    @PostMapping("/generate")
    public ResponseEntity<AiResponse> generate(Authentication auth,
                                                @Valid @RequestBody AiRequest request) {
        Long userId = (Long) auth.getPrincipal();
        return ResponseEntity.ok(aiService.generate(userId, request));
    }

    @PostMapping("/generate-async")
    public ResponseEntity<AiJobResponse> generateAsync(Authentication auth,
                                                        @Valid @RequestBody AiAsyncRequest request) {
        Long userId = (Long) auth.getPrincipal();
        AiJobResponse job = aiJobService.createJob(userId, request.prompt());
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(job);
    }

    @GetMapping("/jobs/{jobId}")
    public ResponseEntity<AiJobResponse> getJobStatus(Authentication auth,
                                                       @PathVariable Long jobId) {
        Long userId = (Long) auth.getPrincipal();
        return ResponseEntity.ok(aiJobService.getJobStatus(jobId, userId));
    }

    @GetMapping("/history")
    public ResponseEntity<PageResponse<AiHistoryResponse>> getHistory(
            Authentication auth,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Long userId = (Long) auth.getPrincipal();
        return ResponseEntity.ok(aiService.getHistory(userId, page, size));
    }

    @GetMapping("/history/{id}")
    public ResponseEntity<AiHistoryResponse> getHistoryDetail(Authentication auth,
                                                               @PathVariable Long id) {
        Long userId = (Long) auth.getPrincipal();
        return ResponseEntity.ok(aiService.getHistoryDetail(userId, id));
    }

    @DeleteMapping("/history/{id}")
    public ResponseEntity<Void> deleteHistory(Authentication auth,
                                               @PathVariable Long id) {
        Long userId = (Long) auth.getPrincipal();
        aiService.deleteHistory(userId, id);
        return ResponseEntity.noContent().build();
    }
}
