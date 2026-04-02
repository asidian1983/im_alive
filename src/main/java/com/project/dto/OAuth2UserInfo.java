package com.project.dto;

import com.project.domain.User.AuthProvider;

import java.util.Map;

public record OAuth2UserInfo(
        String id,
        String email,
        String name,
        AuthProvider provider
) {

    public static OAuth2UserInfo fromGoogle(Map<String, Object> attributes) {
        return new OAuth2UserInfo(
                (String) attributes.get("sub"),
                (String) attributes.get("email"),
                (String) attributes.get("name"),
                AuthProvider.GOOGLE
        );
    }

    public static OAuth2UserInfo fromGithub(Map<String, Object> attributes) {
        String name = (String) attributes.get("name");
        if (name == null) {
            name = (String) attributes.get("login");
        }
        return new OAuth2UserInfo(
                String.valueOf(attributes.get("id")),
                (String) attributes.get("email"),
                name,
                AuthProvider.GITHUB
        );
    }
}
