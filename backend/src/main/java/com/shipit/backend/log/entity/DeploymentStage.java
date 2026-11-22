package com.shipit.backend.log.entity;

/**
 * Represents the execution stage associated with a deployment log entry.
 */
public enum DeploymentStage {
    CLONING,
    DETECTING,
    BUILDING,
    STARTING,
    HEALTH_CHECKING,
    CONTAINER,
    SYSTEM;

    /**
     * Checks if this stage is part of the build and preparation phase.
     */
    public boolean isBuildPhase() {
        return this == CLONING || this == DETECTING || this == BUILDING;
    }

    /**
     * Checks if this stage is part of the container startup and verification phase.
     */
    public boolean isRuntimePhase() {
        return this == STARTING || this == HEALTH_CHECKING || this == CONTAINER;
    }
}
