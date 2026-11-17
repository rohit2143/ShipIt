package com.shipit.backend.github.controller;

import com.shipit.backend.common.response.ApiResponse;
import com.shipit.backend.github.dto.GitHubWebhookResponse;
import com.shipit.backend.github.service.GitHubWebhookService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/v1/github/webhooks")
@RequiredArgsConstructor
public class GitHubWebhookController {

    private final GitHubWebhookService webhookService;

    @PostMapping
    public ResponseEntity<ApiResponse<GitHubWebhookResponse>> handleWebhook(
            @RequestBody(required = false) String rawPayload,
            @RequestHeader(value = "X-Hub-Signature-256", required = false) String signatureHeader,
            @RequestHeader(value = "X-GitHub-Delivery", required = false) String deliveryId,
            @RequestHeader(value = "X-GitHub-Event", required = false) String eventType
    ) {
        log.info("Received GitHub webhook: event='{}', deliveryId='{}'", eventType, deliveryId);

        String payload = rawPayload != null ? rawPayload : "";
        GitHubWebhookResponse response =
                webhookService.processWebhook(payload, signatureHeader, deliveryId, eventType);

        return ResponseEntity.ok(ApiResponse.success("Webhook event processed", response));
    }
}
