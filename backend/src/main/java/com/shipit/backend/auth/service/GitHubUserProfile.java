package com.shipit.backend.auth.service;

public record GitHubUserProfile(
        String id,
        String name,
        String email,
        String avatarUrl
) {
}
