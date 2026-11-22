package com.shipit.backend.deployment.entity;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

public enum DeploymentStatus {
    QUEUED,
    CLONING,
    DETECTING,
    BUILDING,
    STARTING,
    HEALTH_CHECKING,
    SUCCESS,
    FAILED,
    CANCELLED;

    private static final Set<DeploymentStatus> TERMINAL_STATUSES = EnumSet.of(SUCCESS, FAILED, CANCELLED);

    private static final Set<DeploymentStatus> IN_PROGRESS_STATUSES = EnumSet.of(
            QUEUED, CLONING, DETECTING, BUILDING, STARTING, HEALTH_CHECKING
    );

    private static final Map<DeploymentStatus, Set<DeploymentStatus>> VALID_TRANSITIONS = Map.of(
            QUEUED, EnumSet.of(CLONING, CANCELLED, FAILED),
            CLONING, EnumSet.of(DETECTING, CANCELLED, FAILED),
            DETECTING, EnumSet.of(BUILDING, FAILED),
            BUILDING, EnumSet.of(STARTING, FAILED),
            STARTING, EnumSet.of(HEALTH_CHECKING, FAILED),
            HEALTH_CHECKING, EnumSet.of(SUCCESS, FAILED),
            SUCCESS, Collections.emptySet(),
            FAILED, Collections.emptySet(),
            CANCELLED, Collections.emptySet()
    );

    public boolean isTerminal() {
        return TERMINAL_STATUSES.contains(this);
    }

    public boolean isInProgress() {
        return IN_PROGRESS_STATUSES.contains(this);
    }

    public boolean canTransitionTo(DeploymentStatus target) {
        if (target == null) {
            return false;
        }
        return VALID_TRANSITIONS.getOrDefault(this, Collections.emptySet()).contains(target);
    }
}
