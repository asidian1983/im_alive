package com.project.repository;

import com.project.domain.AiGeneration;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface AiGenerationRepository extends JpaRepository<AiGeneration, Long> {

    Page<AiGeneration> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    @Query("SELECT COALESCE(SUM(g.tokensUsed), 0) FROM AiGeneration g")
    long sumTotalTokensUsed();

    long countByUserId(Long userId);
}
