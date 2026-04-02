package com.project.service;

import com.project.domain.User;
import com.project.domain.User.AuthProvider;
import com.project.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CustomOAuth2UserServiceTest {

    @Mock private UserRepository userRepository;

    @Test
    void processOAuth2User_newUser_createsUser() throws Exception {
        User newUser = new User("test@gmail.com", "Test User", AuthProvider.GOOGLE, "google-123");
        // Use reflection to set id
        var idField = User.class.getDeclaredField("id");
        idField.setAccessible(true);
        idField.set(newUser, 1L);

        when(userRepository.findByProviderAndProviderId(AuthProvider.GOOGLE, "google-123"))
                .thenReturn(Optional.empty());
        when(userRepository.findByEmail("test@gmail.com")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenReturn(newUser);

        CustomOAuth2UserService service = new CustomOAuth2UserService(userRepository);

        // Call private method via reflection
        var method = CustomOAuth2UserService.class.getDeclaredMethod("processOAuth2User",
                Class.forName("com.project.dto.OAuth2UserInfo"));
        method.setAccessible(true);

        var userInfo = com.project.dto.OAuth2UserInfo.fromGoogle(Map.of(
                "sub", "google-123",
                "email", "test@gmail.com",
                "name", "Test User"
        ));

        User result = (User) method.invoke(service, userInfo);

        assertEquals("test@gmail.com", result.getEmail());
        assertEquals(AuthProvider.GOOGLE, result.getProvider());
        verify(userRepository).save(any(User.class));
    }

    @Test
    void processOAuth2User_existingOAuth2User_updatesName() throws Exception {
        User existingUser = new User("test@gmail.com", "Old Name", AuthProvider.GOOGLE, "google-123");
        var idField = User.class.getDeclaredField("id");
        idField.setAccessible(true);
        idField.set(existingUser, 1L);

        when(userRepository.findByProviderAndProviderId(AuthProvider.GOOGLE, "google-123"))
                .thenReturn(Optional.of(existingUser));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        CustomOAuth2UserService service = new CustomOAuth2UserService(userRepository);

        var method = CustomOAuth2UserService.class.getDeclaredMethod("processOAuth2User",
                Class.forName("com.project.dto.OAuth2UserInfo"));
        method.setAccessible(true);

        var userInfo = com.project.dto.OAuth2UserInfo.fromGoogle(Map.of(
                "sub", "google-123",
                "email", "test@gmail.com",
                "name", "New Name"
        ));

        User result = (User) method.invoke(service, userInfo);

        assertEquals("New Name", result.getName());
        verify(userRepository).save(existingUser);
    }

    @Test
    void processOAuth2User_existingLocalUser_linksAccount() throws Exception {
        User localUser = new User("test@gmail.com", "encoded", "Test User");
        var idField = User.class.getDeclaredField("id");
        idField.setAccessible(true);
        idField.set(localUser, 1L);

        when(userRepository.findByProviderAndProviderId(AuthProvider.GITHUB, "456"))
                .thenReturn(Optional.empty());
        when(userRepository.findByEmail("test@gmail.com")).thenReturn(Optional.of(localUser));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        CustomOAuth2UserService service = new CustomOAuth2UserService(userRepository);

        var method = CustomOAuth2UserService.class.getDeclaredMethod("processOAuth2User",
                Class.forName("com.project.dto.OAuth2UserInfo"));
        method.setAccessible(true);

        var userInfo = com.project.dto.OAuth2UserInfo.fromGithub(Map.of(
                "id", 456,
                "email", "test@gmail.com",
                "name", "Test User",
                "login", "testuser"
        ));

        User result = (User) method.invoke(service, userInfo);

        assertEquals(AuthProvider.GITHUB, result.getProvider());
        assertEquals("456", result.getProviderId());
        verify(userRepository).save(localUser);
    }
}
