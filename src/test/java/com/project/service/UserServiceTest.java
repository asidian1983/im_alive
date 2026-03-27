package com.project.service;

import com.project.domain.User;
import com.project.dto.UpdateUserRequest;
import com.project.dto.UserResponse;
import com.project.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;

    private UserService userService;

    @BeforeEach
    void setUp() {
        userService = new UserService(userRepository, passwordEncoder);
    }

    // --- getUser ---

    @Test
    void getUser_success() {
        User user = new User("test@test.com", "encoded", "Test");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        UserResponse response = userService.getUser(1L);

        assertEquals("test@test.com", response.email());
        assertEquals("Test", response.name());
        assertEquals("USER", response.role());
    }

    @Test
    void getUser_notFound_throws() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> userService.getUser(99L));
    }

    // --- updateUser ---

    @Test
    void updateUser_nameOnly() {
        User user = new User("test@test.com", "encoded", "OldName");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        UserResponse response = userService.updateUser(1L, new UpdateUserRequest("NewName", null));

        assertEquals("NewName", response.name());
        verify(passwordEncoder, never()).encode(any());
    }

    @Test
    void updateUser_passwordOnly() {
        User user = new User("test@test.com", "encoded", "Test");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(passwordEncoder.encode("newpass123")).thenReturn("newEncoded");

        userService.updateUser(1L, new UpdateUserRequest(null, "newpass123"));

        assertEquals("newEncoded", user.getPassword());
    }

    @Test
    void updateUser_bothFields() {
        User user = new User("test@test.com", "encoded", "OldName");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(passwordEncoder.encode("newpass123")).thenReturn("newEncoded");

        UserResponse response = userService.updateUser(1L, new UpdateUserRequest("NewName", "newpass123"));

        assertEquals("NewName", response.name());
        assertEquals("newEncoded", user.getPassword());
    }

    @Test
    void updateUser_blankName_ignored() {
        User user = new User("test@test.com", "encoded", "OriginalName");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        userService.updateUser(1L, new UpdateUserRequest("  ", null));

        assertEquals("OriginalName", user.getName());
    }

    @Test
    void updateUser_notFound_throws() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class,
                () -> userService.updateUser(99L, new UpdateUserRequest("Name", null)));
    }

    // --- deleteUser ---

    @Test
    void deleteUser_success() {
        when(userRepository.existsById(1L)).thenReturn(true);

        userService.deleteUser(1L);

        verify(userRepository).deleteById(1L);
    }

    @Test
    void deleteUser_notFound_throws() {
        when(userRepository.existsById(99L)).thenReturn(false);

        assertThrows(IllegalArgumentException.class, () -> userService.deleteUser(99L));
        verify(userRepository, never()).deleteById(any());
    }
}
