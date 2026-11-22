package com.shipit.backend.user.service;

import com.shipit.backend.common.exception.ConflictException;
import com.shipit.backend.common.exception.ResourceNotFoundException;
import com.shipit.backend.user.entity.Role;
import com.shipit.backend.user.entity.User;
import com.shipit.backend.user.entity.UserStatus;
import com.shipit.backend.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    @Transactional
    public User createUser(String name, String email, Role role, UserStatus status, boolean emailVerified) {
        String normalizedEmail = normalizeEmail(email);

        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new ConflictException("An account with this email already exists");
        }

        User user = User.builder()
                .name(name.trim())
                .email(normalizedEmail)
                .role(role != null ? role : Role.ROLE_USER)
                .status(status != null ? status : UserStatus.ACTIVE)
                .emailVerified(emailVerified)
                .build();

        log.info("Creating new user with email: {}", normalizedEmail);
        return userRepository.save(user);
    }

    @Transactional(readOnly = true)
    public Optional<User> findByEmail(String email) {
        return userRepository.findByEmail(normalizeEmail(email));
    }

    @Transactional(readOnly = true)
    public User getById(UUID id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", id));
    }

    @Transactional(readOnly = true)
    public boolean existsByEmail(String email) {
        return userRepository.existsByEmail(normalizeEmail(email));
    }

    public static String normalizeEmail(String email) {
        return email != null ? email.trim().toLowerCase() : null;
    }
}
