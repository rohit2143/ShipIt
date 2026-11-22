package com.shipit.backend.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OAuthLoginRequest {

    @NotBlank(message = "OAuth authorization code is required")
    private String code;

    private String redirectUri;
}
