package com.project.service;

import com.project.common.exception.NotFoundException;
import com.project.domain.AdminAuditLog;
import com.project.domain.AiJobStatus;
import com.project.domain.User;
import com.project.dto.AdminStatsResponse;
import com.project.dto.AdminUserUpdateRequest;
import com.project.dto.UserResponse;
import com.project.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private AiGenerationRepository aiGenerationRepository;
    @Mock private AiJobRepository aiJobRepository;
    @Mock private AdminAuditLogRepository auditLogRepository;

    private AdminService adminService;

    private User admin;
    private User targetUser;

    @BeforeEach
    void setUp() {
        adminService = new AdminService(userRepository, aiGenerationRepository,
                aiJobRepository, auditLogRepository);
        admin = new User("admin@test.com", "encoded", "Admin");
        admin.setRole(User.Role.ADMIN);
        targetUser = new User("user@test.com", "encoded", "Target");
    }

    // --- getUsers ---

    @Test
    void getUsers_success() {
        Page<User> page = new PageImpl<>(List.of(targetUser), PageRequest.of(0, 20), 1);
        when(userRepository.findAll(any(PageRequest.class))).thenReturn(page);

        var response = adminService.getUsers(0, 20);

        assertEquals(1, response.content().size());
        assertEquals("user@test.com", response.content().get(0).email());
    }

    @Test
    void getUsers_empty() {
        Page<User> empty = new PageImpl<>(List.of(), PageRequest.of(0, 20), 0);
        when(userRepository.findAll(any(PageRequest.class))).thenReturn(empty);

        var response = adminService.getUsers(0, 20);

        assertTrue(response.content().isEmpty());
    }

    // --- getUser ---

    @Test
    void getUser_success() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(targetUser));

        UserResponse response = adminService.getUser(1L);

        assertEquals("user@test.com", response.email());
    }

    @Test
    void getUser_notFound_throws() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> adminService.getUser(99L));
    }

    // --- updateUser ---

    @Test
    void updateUser_changeName() {
        when(userRepository.findById(2L)).thenReturn(Optional.of(targetUser));
        when(userRepository.findById(1L)).thenReturn(Optional.of(admin));
        when(auditLogRepository.save(any(AdminAuditLog.class))).thenAnswer(inv -> inv.getArgument(0));

        UserResponse response = adminService.updateUser(1L, 2L,
                new AdminUserUpdateRequest("NewName", null));

        assertEquals("NewName", response.name());
        verify(auditLogRepository).save(any(AdminAuditLog.class));
    }

    @Test
    void updateUser_changeRole() {
        when(userRepository.findById(2L)).thenReturn(Optional.of(targetUser));
        when(userRepository.findById(1L)).thenReturn(Optional.of(admin));
        when(auditLogRepository.save(any(AdminAuditLog.class))).thenAnswer(inv -> inv.getArgument(0));

        UserResponse response = adminService.updateUser(1L, 2L,
                new AdminUserUpdateRequest(null, "ADMIN"));

        assertEquals("ADMIN", response.role());
    }

    @Test
    void updateUser_changeNameAndRole() {
        when(userRepository.findById(2L)).thenReturn(Optional.of(targetUser));
        when(userRepository.findById(1L)).thenReturn(Optional.of(admin));
        when(auditLogRepository.save(any(AdminAuditLog.class))).thenAnswer(inv -> inv.getArgument(0));

        UserResponse response = adminService.updateUser(1L, 2L,
                new AdminUserUpdateRequest("NewName", "ADMIN"));

        assertEquals("NewName", response.name());
        assertEquals("ADMIN", response.role());
    }

    @Test
    void updateUser_targetNotFound_throws() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class,
                () -> adminService.updateUser(1L, 99L, new AdminUserUpdateRequest("Name", null)));
    }

    @Test
    void updateUser_adminNotFound_throws() {
        when(userRepository.findById(2L)).thenReturn(Optional.of(targetUser));
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class,
                () -> adminService.updateUser(99L, 2L, new AdminUserUpdateRequest("Name", null)));
    }

    @Test
    void updateUser_invalidRole_throws() {
        when(userRepository.findById(2L)).thenReturn(Optional.of(targetUser));
        when(userRepository.findById(1L)).thenReturn(Optional.of(admin));

        assertThrows(IllegalArgumentException.class,
                () -> adminService.updateUser(1L, 2L, new AdminUserUpdateRequest(null, "INVALID")));
    }

    // --- deleteUser ---

    @Test
    void deleteUser_success() {
        when(userRepository.findById(2L)).thenReturn(Optional.of(targetUser));
        when(userRepository.findById(1L)).thenReturn(Optional.of(admin));
        when(auditLogRepository.save(any(AdminAuditLog.class))).thenAnswer(inv -> inv.getArgument(0));

        adminService.deleteUser(1L, 2L);

        verify(userRepository).delete(targetUser);
        verify(auditLogRepository).save(any(AdminAuditLog.class));
    }

    @Test
    void deleteUser_targetNotFound_throws() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> adminService.deleteUser(1L, 99L));
        verify(userRepository, never()).delete(any());
    }

    // --- getStats ---

    @Test
    void getStats_success() {
        when(userRepository.count()).thenReturn(100L);
        when(aiGenerationRepository.count()).thenReturn(5000L);
        when(aiGenerationRepository.sumTotalTokensUsed()).thenReturn(250000L);
        when(aiJobRepository.countByStatus(AiJobStatus.QUEUED)).thenReturn(2L);
        when(aiJobRepository.countByStatus(AiJobStatus.PROCESSING)).thenReturn(1L);

        AdminStatsResponse stats = adminService.getStats();

        assertEquals(100, stats.totalUsers());
        assertEquals(5000, stats.totalGenerations());
        assertEquals(250000, stats.totalTokensUsed());
        assertEquals(3, stats.activeJobCount());
    }

    @Test
    void getStats_empty() {
        when(userRepository.count()).thenReturn(0L);
        when(aiGenerationRepository.count()).thenReturn(0L);
        when(aiGenerationRepository.sumTotalTokensUsed()).thenReturn(0L);
        when(aiJobRepository.countByStatus(AiJobStatus.QUEUED)).thenReturn(0L);
        when(aiJobRepository.countByStatus(AiJobStatus.PROCESSING)).thenReturn(0L);

        AdminStatsResponse stats = adminService.getStats();

        assertEquals(0, stats.totalUsers());
        assertEquals(0, stats.activeJobCount());
    }
}
