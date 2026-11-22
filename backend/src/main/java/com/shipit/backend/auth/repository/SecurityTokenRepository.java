package com.shipit.backend.auth.repository;

import com.shipit.backend.auth.entity.SecurityToken;
import com.shipit.backend.auth.entity.SecurityTokenType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SecurityTokenRepository extends JpaRepository<SecurityToken, UUID> {

    Optional<SecurityToken> findByTokenHashAndTokenType(String tokenHash, SecurityTokenType tokenType);

    @Modifying
    @Query("UPDATE SecurityToken s SET s.usedAt = :usedAt WHERE s.user.id = :userId AND s.tokenType = :tokenType AND s.usedAt IS NULL")
    int invalidateAllActiveByUserIdAndType(@Param("userId") UUID userId,
                                          @Param("tokenType") SecurityTokenType tokenType,
                                          @Param("usedAt") Instant usedAt);
}
