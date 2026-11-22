package com.shipit.backend.github.service;

import com.shipit.backend.common.exception.AppException;
import com.shipit.backend.github.dto.GitHubPushPayloadDto;
import com.shipit.backend.github.dto.GitHubWebhookResponse;
import com.shipit.backend.github.entity.GitHubWebhookDelivery;
import com.shipit.backend.github.entity.GitHubWebhookDeliveryStatus;
import com.shipit.backend.github.repository.GitHubWebhookDeliveryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GitHubWebhookServiceTest {

    @Mock
    private GitHubWebhookSignatureService signatureService;

    @Mock
    private GitHubWebhookDeliveryRepository deliveryRepository;

    @Mock
    private GitHubPushEventProcessor pushEventProcessor;

    private ObjectMapper objectMapper;
    private GitHubWebhookService webhookService;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        webhookService = new GitHubWebhookService(
                signatureService,
                deliveryRepository,
                pushEventProcessor,
                objectMapper
        );
    }

    @Test
    @DisplayName("Should throw MISSING_DELIVERY_HEADER when delivery ID is missing or blank")
    void shouldThrowWhenDeliveryIdMissing() {
        assertThatThrownBy(() -> webhookService.processWebhook("{}", "sig", null, "push"))
                .isInstanceOf(AppException.class)
                .extracting("errorCode")
                .isEqualTo("MISSING_DELIVERY_HEADER");

        assertThatThrownBy(() -> webhookService.processWebhook("{}", "sig", "   ", "push"))
                .isInstanceOf(AppException.class)
                .extracting("errorCode")
                .isEqualTo("MISSING_DELIVERY_HEADER");
    }

    @Test
    @DisplayName("Should throw MISSING_EVENT_HEADER when event type is missing or blank")
    void shouldThrowWhenEventTypeMissing() {
        assertThatThrownBy(() -> webhookService.processWebhook("{}", "sig", "del-123", null))
                .isInstanceOf(AppException.class)
                .extracting("errorCode")
                .isEqualTo("MISSING_EVENT_HEADER");

        assertThatThrownBy(() -> webhookService.processWebhook("{}", "sig", "del-123", ""))
                .isInstanceOf(AppException.class)
                .extracting("errorCode")
                .isEqualTo("MISSING_EVENT_HEADER");
    }

    @Test
    @DisplayName("Should fail when signature verification fails")
    void shouldFailWhenSignatureVerificationFails() {
        doThrow(new AppException("Invalid signature", org.springframework.http.HttpStatus.UNAUTHORIZED, "INVALID_WEBHOOK_SIGNATURE"))
                .when(signatureService).verifySignature(any(), any());

        assertThatThrownBy(() -> webhookService.processWebhook("{}", "bad_sig", "del-123", "push"))
                .isInstanceOf(AppException.class)
                .extracting("errorCode")
                .isEqualTo("INVALID_WEBHOOK_SIGNATURE");

        verify(deliveryRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should detect duplicate delivery and return DUPLICATE_IGNORED without re-processing")
    void shouldReturnDuplicateIgnored() {
        String deliveryId = "del-dup-123";
        when(deliveryRepository.existsByDeliveryId(deliveryId)).thenReturn(true);

        GitHubWebhookResponse response =
                webhookService.processWebhook("{}", "sig", deliveryId, "push");

        assertThat(response).isNotNull();
        assertThat(response.getDeliveryId()).isEqualTo(deliveryId);
        assertThat(response.getStatus()).isEqualTo("DUPLICATE_IGNORED");
        assertThat(response.getMessage()).contains("already received");

        verify(deliveryRepository, never()).save(any());
        verify(pushEventProcessor, never()).processPushEvent(any());
    }

    @Test
    @DisplayName("Should process ping event successfully and return PONG")
    void shouldProcessPingEvent() {
        String deliveryId = "del-ping-123";
        String payload = "{\"zen\":\"Design for failure.\"}";
        when(deliveryRepository.existsByDeliveryId(deliveryId)).thenReturn(false);
        when(signatureService.computePayloadHash(payload)).thenReturn("hash123");

        GitHubWebhookDelivery delivery = GitHubWebhookDelivery.builder()
                .id(UUID.randomUUID())
                .deliveryId(deliveryId)
                .eventType("ping")
                .payloadHash("hash123")
                .status(GitHubWebhookDeliveryStatus.RECEIVED)
                .build();
        when(deliveryRepository.save(any(GitHubWebhookDelivery.class))).thenReturn(delivery);

        GitHubWebhookResponse response =
                webhookService.processWebhook(payload, "sig", deliveryId, "ping");

        assertThat(response).isNotNull();
        assertThat(response.getDeliveryId()).isEqualTo(deliveryId);
        assertThat(response.getStatus()).isEqualTo("PONG");
        assertThat(response.getMessage()).contains("ping received");

        verify(deliveryRepository, times(2)).save(any(GitHubWebhookDelivery.class));
        verify(pushEventProcessor, never()).processPushEvent(any());
    }

    @Test
    @DisplayName("Should process push event and update delivery status to PROCESSED")
    void shouldProcessPushEvent() {
        String deliveryId = "del-push-123";
        String payload = "{\"ref\":\"refs/heads/main\",\"after\":\"commit123\",\"repository\":{\"id\":1001,\"full_name\":\"user/repo\"}}";
        when(deliveryRepository.existsByDeliveryId(deliveryId)).thenReturn(false);
        when(signatureService.computePayloadHash(payload)).thenReturn("hash456");

        GitHubWebhookDelivery delivery = GitHubWebhookDelivery.builder()
                .id(UUID.randomUUID())
                .deliveryId(deliveryId)
                .eventType("push")
                .payloadHash("hash456")
                .status(GitHubWebhookDeliveryStatus.RECEIVED)
                .build();
        when(deliveryRepository.save(any(GitHubWebhookDelivery.class))).thenReturn(delivery);

        when(pushEventProcessor.processPushEvent(any(GitHubPushPayloadDto.class)))
                .thenReturn(new GitHubPushEventProcessor.PushProcessingResult(true, "Push processed for repository"));

        GitHubWebhookResponse response =
                webhookService.processWebhook(payload, "sig", deliveryId, "push");

        assertThat(response).isNotNull();
        assertThat(response.getDeliveryId()).isEqualTo(deliveryId);
        assertThat(response.getStatus()).isEqualTo("PROCESSED");
        assertThat(response.getMessage()).contains("Push processed");

        verify(pushEventProcessor, times(1)).processPushEvent(any(GitHubPushPayloadDto.class));
        verify(deliveryRepository, times(2)).save(any(GitHubWebhookDelivery.class));
    }

    @Test
    @DisplayName("Should handle unhandled event types gracefully and mark as IGNORED")
    void shouldHandleUnknownEvent() {
        String deliveryId = "del-unknown-123";
        String payload = "{\"action\":\"starred\"}";
        when(deliveryRepository.existsByDeliveryId(deliveryId)).thenReturn(false);
        when(signatureService.computePayloadHash(payload)).thenReturn("hash789");

        GitHubWebhookDelivery delivery = GitHubWebhookDelivery.builder()
                .id(UUID.randomUUID())
                .deliveryId(deliveryId)
                .eventType("watch")
                .payloadHash("hash789")
                .status(GitHubWebhookDeliveryStatus.RECEIVED)
                .build();
        when(deliveryRepository.save(any(GitHubWebhookDelivery.class))).thenReturn(delivery);

        GitHubWebhookResponse response =
                webhookService.processWebhook(payload, "sig", deliveryId, "watch");

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo("IGNORED");
        assertThat(response.getMessage()).contains("Event type not handled");

        verify(pushEventProcessor, never()).processPushEvent(any());
    }
}
