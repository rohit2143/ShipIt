package com.shipit.backend.container.model;

/**
 * Lifecycle states of an application container managed by ShipIt.
 */
public enum ContainerStatus {
    STARTING,
    RUNNING,
    HEALTHY,
    UNHEALTHY,
    STOPPED,
    EXITED,
    UNKNOWN;

    public boolean isActive() {
        return this == RUNNING || this == HEALTHY || this == STARTING;
    }
}
