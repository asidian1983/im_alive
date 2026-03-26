package com.project.repository;

import com.project.domain.AiJob;
import com.project.domain.AiJobStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AiJobRepository extends JpaRepository<AiJob, Long> {

    Optional<AiJob> findByIdAndUserId(Long id, Long userId);

    long countByStatus(AiJobStatus status);
}
