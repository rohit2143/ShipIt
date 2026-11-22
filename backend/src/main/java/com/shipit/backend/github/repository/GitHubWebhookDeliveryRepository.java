package com.shipit.backend.github.repository;

import com.shipit.backend.github.entity.GitHubWebhookDelivery;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface GitHubWebhookDeliveryRepository extends JpaRepository<GitHubWebhookDelivery, UUID> {

    boolean existsByDeliveryId(String deliveryId);

    Optional<GitHubWebhookDelivery> findByDeliveryId(String deliveryId);
}
