package com.shipit.backend.github.repository;

import com.shipit.backend.github.entity.GitHubInstallationState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface GitHubInstallationStateRepository extends JpaRepository<GitHubInstallationState, UUID> {

    Optional<GitHubInstallationState> findByStateHash(String stateHash);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE GitHubInstallationState s SET s.used = true " +
           "WHERE s.stateHash = :stateHash " +
           "AND s.user.id = :userId " +
           "AND s.used = false " +
           "AND s.expiresAt > :now")
    int consumeStateAtomically(@Param("stateHash") String stateHash,
                               @Param("userId") UUID userId,
                               @Param("now") Instant now);

    @Modifying
    @Query("DELETE FROM GitHubInstallationState s WHERE s.expiresAt < :now")
    void deleteExpiredStates(@Param("now") Instant now);
}
