package com.shipit.backend.github.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GitHubWebhookResponse {

    private String deliveryId;
    private String eventType;
    private String status;
    private String message;
}
