package com.project.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "ai_jobs", indexes = {
        @Index(name = "idx_ai_job_user_status", columnList = "user_id, status")
})
public class AiJob {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "generation_id")
    private AiGeneration generation;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AiJobStatus status = AiJobStatus.QUEUED;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String prompt;

    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "retry_count", nullable = false)
    private int retryCount = 0;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt = LocalDateTime.now();

    protected AiJob() {}

    public AiJob(User user, String prompt) {
        this.user = user;
        this.prompt = prompt;
    }

    public Long getId() { return id; }
    public User getUser() { return user; }
    public AiGeneration getGeneration() { return generation; }
    public AiJobStatus getStatus() { return status; }
    public String getPrompt() { return prompt; }
    public String getErrorMessage() { return errorMessage; }
    public int getRetryCount() { return retryCount; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }

    public void markProcessing() {
        this.status = AiJobStatus.PROCESSING;
        this.updatedAt = LocalDateTime.now();
    }

    public void markCompleted(AiGeneration generation) {
        this.status = AiJobStatus.COMPLETED;
        this.generation = generation;
        this.updatedAt = LocalDateTime.now();
    }

    public void markFailed(String errorMessage) {
        this.status = AiJobStatus.FAILED;
        this.errorMessage = errorMessage;
        this.updatedAt = LocalDateTime.now();
    }

    public void incrementRetry() {
        this.retryCount++;
        this.status = AiJobStatus.QUEUED;
        this.updatedAt = LocalDateTime.now();
    }
}
