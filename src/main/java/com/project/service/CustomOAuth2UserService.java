package com.project.service;

import com.project.domain.User;
import com.project.domain.User.AuthProvider;
import com.project.dto.OAuth2UserInfo;
import com.project.repository.UserRepository;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.Map;

@Service
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    private final UserRepository userRepository;

    public CustomOAuth2UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    @Transactional
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User oAuth2User = super.loadUser(userRequest);

        String registrationId = userRequest.getClientRegistration().getRegistrationId();
        OAuth2UserInfo userInfo = extractUserInfo(registrationId, oAuth2User.getAttributes());

        User user = processOAuth2User(userInfo);

        return new DefaultOAuth2User(
                Collections.singleton(() -> "ROLE_" + user.getRole().name()),
                Map.of(
                        "id", user.getId(),
                        "email", user.getEmail(),
                        "name", user.getName(),
                        "role", user.getRole().name()
                ),
                "id"
        );
    }

    private OAuth2UserInfo extractUserInfo(String registrationId, Map<String, Object> attributes) {
        return switch (registrationId) {
            case "google" -> OAuth2UserInfo.fromGoogle(attributes);
            case "github" -> OAuth2UserInfo.fromGithub(attributes);
            default -> throw new OAuth2AuthenticationException("Unsupported provider: " + registrationId);
        };
    }

    private User processOAuth2User(OAuth2UserInfo userInfo) {
        return userRepository.findByProviderAndProviderId(userInfo.provider(), userInfo.id())
                .map(existingUser -> {
                    existingUser.setName(userInfo.name());
                    return userRepository.save(existingUser);
                })
                .orElseGet(() -> {
                    User existing = userRepository.findByEmail(userInfo.email()).orElse(null);
                    if (existing != null) {
                        existing.setProvider(userInfo.provider());
                        existing.setProviderId(userInfo.id());
                        return userRepository.save(existing);
                    }
                    User newUser = new User(
                            userInfo.email(),
                            userInfo.name(),
                            userInfo.provider(),
                            userInfo.id()
                    );
                    return userRepository.save(newUser);
                });
    }
}
