package com.shipit.backend.job.entity;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

public enum JobStatus {
    PENDING,
    RUNNING,
    COMPLETED,
    FAILED,
    CANCELLED;

    private static final Set<JobStatus> TERMINAL_STATUSES = EnumSet.of(COMPLETED, FAILED, CANCELLED);

    private static final Set<JobStatus> ACTIVE_STATUSES = EnumSet.of(PENDING, RUNNING);

    private static final Map<JobStatus, Set<JobStatus>> VALID_TRANSITIONS = Map.of(
            PENDING, EnumSet.of(RUNNING, CANCELLED, FAILED),
            RUNNING, EnumSet.of(COMPLETED, FAILED, CANCELLED, PENDING),
            COMPLETED, Collections.emptySet(),
            FAILED, Collections.emptySet(),
            CANCELLED, Collections.emptySet()
    );

    public boolean isTerminal() {
        return TERMINAL_STATUSES.contains(this);
    }

    public boolean isActive() {
        return ACTIVE_STATUSES.contains(this);
    }

    public boolean canTransitionTo(JobStatus target) {
        if (target == null) {
            return false;
        }
        return VALID_TRANSITIONS.getOrDefault(this, Collections.emptySet()).contains(target);
    }
}
