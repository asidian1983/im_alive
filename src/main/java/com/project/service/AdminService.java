package com.project.service;

import com.project.common.exception.NotFoundException;
import com.project.domain.AdminAuditLog;
import com.project.domain.AiJobStatus;
import com.project.domain.User;
import com.project.dto.*;
import com.project.repository.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminService {

    private final UserRepository userRepository;
    private final AiGenerationRepository aiGenerationRepository;
    private final AiJobRepository aiJobRepository;
    private final AdminAuditLogRepository auditLogRepository;

    public AdminService(UserRepository userRepository,
                        AiGenerationRepository aiGenerationRepository,
                        AiJobRepository aiJobRepository,
                        AdminAuditLogRepository auditLogRepository) {
        this.userRepository = userRepository;
        this.aiGenerationRepository = aiGenerationRepository;
        this.aiJobRepository = aiJobRepository;
        this.auditLogRepository = auditLogRepository;
    }

    @Transactional(readOnly = true)
    public PageResponse<UserResponse> getUsers(int page, int size) {
        Page<User> users = userRepository.findAll(PageRequest.of(page, size));
        return PageResponse.from(users, UserResponse::from);
    }

    @Transactional(readOnly = true)
    public UserResponse getUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User"));
        return UserResponse.from(user);
    }

    @Transactional
    public UserResponse updateUser(Long adminId, Long userId, AdminUserUpdateRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User"));
        User admin = userRepository.findById(adminId)
                .orElseThrow(() -> new NotFoundException("Admin"));

        StringBuilder details = new StringBuilder();

        if (request.name() != null && !request.name().isBlank()) {
            details.append("name: ").append(user.getName()).append(" -> ").append(request.name()).append("; ");
            user.setName(request.name());
        }

        if (request.role() != null && !request.role().isBlank()) {
            User.Role newRole = User.Role.valueOf(request.role().toUpperCase());
            details.append("role: ").append(user.getRole()).append(" -> ").append(newRole).append("; ");
            user.setRole(newRole);
        }

        auditLogRepository.save(new AdminAuditLog(
                admin, "UPDATE", "USER", userId, details.toString()
        ));

        return UserResponse.from(user);
    }

    @Transactional
    public void deleteUser(Long adminId, Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User"));
        User admin = userRepository.findById(adminId)
                .orElseThrow(() -> new NotFoundException("Admin"));

        auditLogRepository.save(new AdminAuditLog(
                admin, "DELETE", "USER", userId, "email: " + user.getEmail()
        ));

        userRepository.delete(user);
    }

    @Transactional(readOnly = true)
    public AdminStatsResponse getStats() {
        long totalUsers = userRepository.count();
        long totalGenerations = aiGenerationRepository.count();
        long totalTokensUsed = aiGenerationRepository.sumTotalTokensUsed();
        long activeJobs = aiJobRepository.countByStatus(AiJobStatus.QUEUED)
                + aiJobRepository.countByStatus(AiJobStatus.PROCESSING);

        return new AdminStatsResponse(totalUsers, totalGenerations, totalTokensUsed, activeJobs);
    }
}
