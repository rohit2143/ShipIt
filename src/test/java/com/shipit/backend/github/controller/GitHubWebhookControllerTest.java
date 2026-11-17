package com.shipit.backend.github.controller;

import com.shipit.backend.common.exception.GlobalExceptionHandler;
import com.shipit.backend.github.dto.GitHubWebhookResponse;
import com.shipit.backend.github.service.GitHubWebhookService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class GitHubWebhookControllerTest {

    private MockMvc mockMvc;

    @Mock
    private GitHubWebhookService webhookService;

    @InjectMocks
    private GitHubWebhookController webhookController;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(webhookController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("Should receive webhook and return 200 OK with webhook response")
    void shouldHandleWebhook() throws Exception {
        String payload = "{\"ref\":\"refs/heads/main\"}";
        String signature = "sha256=1234567890abcdef";
        String deliveryId = "del-uuid-123";
        String eventType = "push";

        GitHubWebhookResponse response = GitHubWebhookResponse.builder()
                .deliveryId(deliveryId)
                .eventType(eventType)
                .status("PROCESSED")
                .message("Push processed for repository")
                .build();

        when(webhookService.processWebhook(eq(payload), eq(signature), eq(deliveryId), eq(eventType)))
                .thenReturn(response);

        mockMvc.perform(post("/api/v1/github/webhooks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Hub-Signature-256", signature)
                        .header("X-GitHub-Delivery", deliveryId)
                        .header("X-GitHub-Event", eventType)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.message", is("Webhook event processed")))
                .andExpect(jsonPath("$.data.deliveryId", is(deliveryId)))
                .andExpect(jsonPath("$.data.eventType", is("push")))
                .andExpect(jsonPath("$.data.status", is("PROCESSED")))
                .andExpect(jsonPath("$.data.message", is("Push processed for repository")));

        verify(webhookService, times(1))
                .processWebhook(eq(payload), eq(signature), eq(deliveryId), eq(eventType));
    }

    @Test
    @DisplayName("Should handle missing optional body gracefully as empty string")
    void shouldHandleEmptyBody() throws Exception {
        String signature = "sha256=1234567890abcdef";
        String deliveryId = "del-ping-123";
        String eventType = "ping";

        GitHubWebhookResponse response = GitHubWebhookResponse.builder()
                .deliveryId(deliveryId)
                .eventType(eventType)
                .status("PONG")
                .message("Webhook ping received successfully")
                .build();

        when(webhookService.processWebhook(eq(""), eq(signature), eq(deliveryId), eq(eventType)))
                .thenReturn(response);

        mockMvc.perform(post("/api/v1/github/webhooks")
                        .header("X-Hub-Signature-256", signature)
                        .header("X-GitHub-Delivery", deliveryId)
                        .header("X-GitHub-Event", eventType))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.status", is("PONG")));

        verify(webhookService, times(1))
                .processWebhook(eq(""), eq(signature), eq(deliveryId), eq(eventType));
    }
}
