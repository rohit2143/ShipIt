package com.shipit.backend.auth.service;

public record GoogleUserProfile(
        String id,
        String name,
        String email,
        String avatarUrl
) {
}
