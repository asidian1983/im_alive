package com.project.repository;

import com.project.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;

import com.project.domain.User.AuthProvider;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    Optional<User> findByProviderAndProviderId(AuthProvider provider, String providerId);

    boolean existsByEmail(String email);
}
