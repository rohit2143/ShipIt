package com.shipit.backend.job.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DeploymentJobTest {

    @Test
    @DisplayName("Defaults: status PENDING, type DEPLOYMENT, priority 0, retryCount 0, maxRetries 3")
    void testDefaults() {
        DeploymentJob job = DeploymentJob.builder().build();

        assertThat(job.getStatus()).isEqualTo(JobStatus.PENDING);
        assertThat(job.getType()).isEqualTo(JobType.DEPLOYMENT);
        assertThat(job.getPriority()).isEqualTo(0);
        assertThat(job.getRetryCount()).isEqualTo(0);
        assertThat(job.getMaxRetries()).isEqualTo(3);
        assertThat(job.canRetry()).isTrue();
    }

    @Test
    @DisplayName("claim transitions to RUNNING and records workerId, claimedAt, and heartbeatAt")
    void testClaim() {
        DeploymentJob job = DeploymentJob.builder().status(JobStatus.PENDING).build();

        job.claim("worker-node-1");

        assertThat(job.getStatus()).isEqualTo(JobStatus.RUNNING);
        assertThat(job.getClaimedBy()).isEqualTo("worker-node-1");
        assertThat(job.getClaimedAt()).isNotNull();
        assertThat(job.getHeartbeatAt()).isEqualTo(job.getClaimedAt());
    }

    @Test
    @DisplayName("updateHeartbeat updates heartbeat timestamp for running job")
    void testUpdateHeartbeat() {
        DeploymentJob job = DeploymentJob.builder().status(JobStatus.RUNNING).build();
        Instant initial = Instant.now().minusSeconds(60);
        job.setHeartbeatAt(initial);

        job.updateHeartbeat();

        assertThat(job.getHeartbeatAt()).isAfter(initial);
    }

    @Test
    @DisplayName("updateHeartbeat throws exception if job is not RUNNING")
    void testUpdateHeartbeatInvalidStatus() {
        DeploymentJob job = DeploymentJob.builder().status(JobStatus.PENDING).build();

        assertThatThrownBy(job::updateHeartbeat)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cannot update heartbeat for job in status PENDING");
    }

    @Test
    @DisplayName("complete transitions to COMPLETED and sets finishedAt")
    void testComplete() {
        DeploymentJob job = DeploymentJob.builder().status(JobStatus.RUNNING).build();

        job.complete();

        assertThat(job.getStatus()).isEqualTo(JobStatus.COMPLETED);
        assertThat(job.getFinishedAt()).isNotNull();
    }

    @Test
    @DisplayName("fail transitions to FAILED, sanitizes error message, and sets finishedAt")
    void testFail() {
        DeploymentJob job = DeploymentJob.builder().status(JobStatus.RUNNING).build();
        String longError = "E".repeat(2500);

        job.fail(longError);

        assertThat(job.getStatus()).isEqualTo(JobStatus.FAILED);
        assertThat(job.getFinishedAt()).isNotNull();
        assertThat(job.getErrorMessage()).hasSize(2000);
    }

    @Test
    @DisplayName("cancel transitions to CANCELLED and sets finishedAt")
    void testCancel() {
        DeploymentJob job = DeploymentJob.builder().status(JobStatus.PENDING).build();

        job.cancel();

        assertThat(job.getStatus()).isEqualTo(JobStatus.CANCELLED);
        assertThat(job.getFinishedAt()).isNotNull();
    }

    @Test
    @DisplayName("scheduleRetry increments retryCount, resets claimedBy/claimedAt/heartbeatAt, and transitions to PENDING")
    void testScheduleRetry() {
        DeploymentJob job = DeploymentJob.builder()
                .status(JobStatus.RUNNING)
                .claimedBy("worker-1")
                .claimedAt(Instant.now())
                .heartbeatAt(Instant.now())
                .retryCount(1)
                .maxRetries(3)
                .build();

        job.scheduleRetry("Container crashed");

        assertThat(job.getStatus()).isEqualTo(JobStatus.PENDING);
        assertThat(job.getRetryCount()).isEqualTo(2);
        assertThat(job.getClaimedBy()).isNull();
        assertThat(job.getClaimedAt()).isNull();
        assertThat(job.getHeartbeatAt()).isNull();
        assertThat(job.getErrorMessage()).isEqualTo("Container crashed");
        assertThat(job.canRetry()).isTrue();
    }

    @Test
    @DisplayName("canRetry returns false when retryCount equals or exceeds maxRetries")
    void testCanRetryLimit() {
        DeploymentJob job = DeploymentJob.builder()
                .retryCount(3)
                .maxRetries(3)
                .build();

        assertThat(job.canRetry()).isFalse();
    }

    @Test
    @DisplayName("transitionTo throws IllegalStateException for invalid transitions")
    void testInvalidTransition() {
        DeploymentJob job = DeploymentJob.builder().status(JobStatus.COMPLETED).build();

        assertThatThrownBy(() -> job.transitionTo(JobStatus.RUNNING))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Invalid job transition from COMPLETED to RUNNING");
    }
}
