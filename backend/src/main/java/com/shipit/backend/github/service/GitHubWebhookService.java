package com.shipit.backend.github.service;

import com.shipit.backend.common.exception.AppException;
import com.shipit.backend.github.dto.GitHubPushPayloadDto;
import com.shipit.backend.github.dto.GitHubWebhookResponse;
import com.shipit.backend.github.entity.GitHubWebhookDelivery;
import com.shipit.backend.github.entity.GitHubWebhookDeliveryStatus;
import com.shipit.backend.github.repository.GitHubWebhookDeliveryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
public class GitHubWebhookService {

    private final GitHubWebhookSignatureService signatureService;
    private final GitHubWebhookDeliveryRepository deliveryRepository;
    private final GitHubPushEventProcessor pushEventProcessor;
    private final ObjectMapper objectMapper;

    @Transactional
    public GitHubWebhookResponse processWebhook(
            String rawPayload,
            String signatureHeader,
            String deliveryId,
            String eventType
    ) {
        if (deliveryId == null || deliveryId.isBlank()) {
            throw new AppException(
                    "Missing X-GitHub-Delivery header",
                    HttpStatus.BAD_REQUEST,
                    "MISSING_DELIVERY_HEADER"
            );
        }
        if (eventType == null || eventType.isBlank()) {
            throw new AppException(
                    "Missing X-GitHub-Event header",
                    HttpStatus.BAD_REQUEST,
                    "MISSING_EVENT_HEADER"
            );
        }

        // 1. Verify HMAC-SHA256 signature
        signatureService.verifySignature(rawPayload, signatureHeader);

        // 2. Check for duplicate delivery
        if (deliveryRepository.existsByDeliveryId(deliveryId)) {
            log.info("Duplicate GitHub webhook delivery ignored: {}", deliveryId);
            return GitHubWebhookResponse.builder()
                    .deliveryId(deliveryId)
                    .eventType(eventType)
                    .status("DUPLICATE_IGNORED")
                    .message("Delivery already received and processed")
                    .build();
        }

        // 3. Record delivery as RECEIVED
        String payloadHash = signatureService.computePayloadHash(rawPayload);
        GitHubWebhookDelivery delivery = GitHubWebhookDelivery.builder()
                .deliveryId(deliveryId)
                .eventType(eventType)
                .payloadHash(payloadHash)
                .status(GitHubWebhookDeliveryStatus.RECEIVED)
                .build();
        delivery = deliveryRepository.save(delivery);

        // 4. Route and process event
        try {
            switch (eventType.trim().toLowerCase()) {
                case "ping" -> {
                    delivery.setStatus(GitHubWebhookDeliveryStatus.PROCESSED);
                    delivery.setProcessedAt(Instant.now());
                    deliveryRepository.save(delivery);

                    return GitHubWebhookResponse.builder()
                            .deliveryId(deliveryId)
                            .eventType(eventType)
                            .status("PONG")
                            .message("Webhook ping received successfully")
                            .build();
                }
                case "push" -> {
                    GitHubPushPayloadDto pushPayload = objectMapper.readValue(rawPayload, GitHubPushPayloadDto.class);
                    var result = pushEventProcessor.processPushEvent(pushPayload);

                    delivery.setStatus(result.processed()
                            ? GitHubWebhookDeliveryStatus.PROCESSED
                            : GitHubWebhookDeliveryStatus.IGNORED);
                    delivery.setErrorMessage(result.message());
                    delivery.setProcessedAt(Instant.now());
                    deliveryRepository.save(delivery);

                    return GitHubWebhookResponse.builder()
                            .deliveryId(deliveryId)
                            .eventType(eventType)
                            .status(result.processed() ? "PROCESSED" : "IGNORED")
                            .message(result.message())
                            .build();
                }
                default -> {
                    delivery.setStatus(GitHubWebhookDeliveryStatus.IGNORED);
                    delivery.setErrorMessage("Unsupported event type: " + eventType);
                    delivery.setProcessedAt(Instant.now());
                    deliveryRepository.save(delivery);

                    return GitHubWebhookResponse.builder()
                            .deliveryId(deliveryId)
                            .eventType(eventType)
                            .status("IGNORED")
                            .message("Event type not handled: " + eventType)
                            .build();
                }
            }
        } catch (Exception e) {
            log.error("Failed to process webhook delivery {}: {}", deliveryId, e.getMessage(), e);
            delivery.setStatus(GitHubWebhookDeliveryStatus.FAILED);
            delivery.setErrorMessage(e.getMessage());
            delivery.setProcessedAt(Instant.now());
            deliveryRepository.save(delivery);

            throw new AppException(
                    "Failed to process webhook: " + e.getMessage(),
                    e,
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "WEBHOOK_PROCESSING_ERROR"
            );
        }
    }
}
