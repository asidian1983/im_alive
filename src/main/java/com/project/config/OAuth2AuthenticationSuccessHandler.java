package com.project.config;

import com.project.domain.RefreshToken;
import com.project.domain.User;
import com.project.infra.JwtProvider;
import com.project.repository.RefreshTokenRepository;
import com.project.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.UUID;

@Component
public class OAuth2AuthenticationSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final JwtProvider jwtProvider;
    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final String redirectUri;
    private final long refreshExpiration;

    public OAuth2AuthenticationSuccessHandler(
            JwtProvider jwtProvider,
            UserRepository userRepository,
            RefreshTokenRepository refreshTokenRepository,
            @Value("${oauth2.redirect-uri}") String redirectUri,
            @Value("${jwt.refresh-expiration}") long refreshExpiration) {
        this.jwtProvider = jwtProvider;
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.redirectUri = redirectUri;
        this.refreshExpiration = refreshExpiration;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException {
        OAuth2User oAuth2User = (OAuth2User) authentication.getPrincipal();
        Long userId = oAuth2User.getAttribute("id");

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalStateException("User not found after OAuth2 login"));

        String accessToken = jwtProvider.generateToken(user.getId(), user.getEmail(), user.getRole().name());

        refreshTokenRepository.deleteByUserId(user.getId());
        String refreshTokenValue = UUID.randomUUID().toString();
        LocalDateTime expiresAt = LocalDateTime.now().plusSeconds(refreshExpiration / 1000);
        RefreshToken refreshToken = new RefreshToken(refreshTokenValue, user, expiresAt);
        refreshTokenRepository.save(refreshToken);

        String targetUrl = UriComponentsBuilder.fromUriString(redirectUri)
                .queryParam("accessToken", accessToken)
                .queryParam("refreshToken", refreshTokenValue)
                .build().toUriString();

        getRedirectStrategy().sendRedirect(request, response, targetUrl);
    }
}
