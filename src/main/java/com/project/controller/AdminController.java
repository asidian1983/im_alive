package com.project.controller;

import com.project.dto.*;
import com.project.service.AdminService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final AdminService adminService;

    public AdminController(AdminService adminService) {
        this.adminService = adminService;
    }

    @GetMapping("/users")
    public ResponseEntity<PageResponse<UserResponse>> getUsers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(adminService.getUsers(page, size));
    }

    @GetMapping("/users/{id}")
    public ResponseEntity<UserResponse> getUser(@PathVariable Long id) {
        return ResponseEntity.ok(adminService.getUser(id));
    }

    @PatchMapping("/users/{id}")
    public ResponseEntity<UserResponse> updateUser(Authentication auth,
                                                    @PathVariable Long id,
                                                    @RequestBody AdminUserUpdateRequest request) {
        Long adminId = (Long) auth.getPrincipal();
        return ResponseEntity.ok(adminService.updateUser(adminId, id, request));
    }

    @DeleteMapping("/users/{id}")
    public ResponseEntity<Void> deleteUser(Authentication auth,
                                            @PathVariable Long id) {
        Long adminId = (Long) auth.getPrincipal();
        adminService.deleteUser(adminId, id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/stats")
    public ResponseEntity<AdminStatsResponse> getStats() {
        return ResponseEntity.ok(adminService.getStats());
    }
}
