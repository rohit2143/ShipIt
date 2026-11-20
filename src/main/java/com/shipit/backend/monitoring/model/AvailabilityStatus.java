package com.shipit.backend.monitoring.model;

/**
 * High-level availability status of a deployed application container.
 */
public enum AvailabilityStatus {
    HEALTHY,
    DEGRADED,
    UNHEALTHY,
    STOPPED,
    UNKNOWN;

    /**
     * Indicates whether the application is accessible to end users.
     */
    public boolean isAvailable() {
        return this == HEALTHY || this == DEGRADED;
    }

    /**
     * Indicates whether the deployment requires developer or administrative attention.
     */
    public boolean requiresAttention() {
        return this == DEGRADED || this == UNHEALTHY;
    }
}
