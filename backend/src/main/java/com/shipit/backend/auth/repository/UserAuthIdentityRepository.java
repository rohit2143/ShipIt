package com.shipit.backend.auth.repository;

import com.shipit.backend.auth.entity.AuthProvider;
import com.shipit.backend.auth.entity.UserAuthIdentity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserAuthIdentityRepository extends JpaRepository<UserAuthIdentity, UUID> {

    Optional<UserAuthIdentity> findByProviderAndProviderUserId(AuthProvider provider, String providerUserId);

    Optional<UserAuthIdentity> findByUserIdAndProvider(UUID userId, AuthProvider provider);

    List<UserAuthIdentity> findAllByUserId(UUID userId);

    boolean existsByProviderAndProviderUserId(AuthProvider provider, String providerUserId);

    boolean existsByUserIdAndProvider(UUID userId, AuthProvider provider);

    long countByUserId(UUID userId);
}
