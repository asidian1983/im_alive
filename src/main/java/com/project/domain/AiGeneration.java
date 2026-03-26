package com.project.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "ai_generations", indexes = {
        @Index(name = "idx_ai_gen_user_id", columnList = "user_id"),
        @Index(name = "idx_ai_gen_created", columnList = "created_at DESC")
})
public class AiGeneration {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String prompt;

    @Column(columnDefinition = "TEXT")
    private String result;

    @Column(nullable = false, length = 50)
    private String model;

    @Column(name = "tokens_used")
    private Integer tokensUsed;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    protected AiGeneration() {}

    public AiGeneration(User user, String prompt, String result, String model, Integer tokensUsed) {
        this.user = user;
        this.prompt = prompt;
        this.result = result;
        this.model = model;
        this.tokensUsed = tokensUsed;
    }

    public Long getId() { return id; }
    public User getUser() { return user; }
    public String getPrompt() { return prompt; }
    public String getResult() { return result; }
    public String getModel() { return model; }
    public Integer getTokensUsed() { return tokensUsed; }
    public LocalDateTime getCreatedAt() { return createdAt; }

    public void setResult(String result) { this.result = result; }
    public void setTokensUsed(Integer tokensUsed) { this.tokensUsed = tokensUsed; }
}
