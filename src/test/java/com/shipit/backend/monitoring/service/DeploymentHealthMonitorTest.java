package com.shipit.backend.monitoring.service;

import com.shipit.backend.container.config.ContainerProperties;
import com.shipit.backend.deployment.entity.Deployment;
import com.shipit.backend.deployment.entity.DeploymentStatus;
import com.shipit.backend.deployment.repository.DeploymentRepository;
import com.shipit.backend.monitoring.config.MonitoringProperties;
import com.shipit.backend.monitoring.model.AvailabilityStatus;
import com.shipit.backend.monitoring.model.ContainerMetrics;
import com.shipit.backend.monitoring.model.DeploymentHealthReport;
import com.shipit.backend.project.entity.Project;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DeploymentHealthMonitorTest {

    @Mock
    private DeploymentRepository deploymentRepository;

    @Mock
    private DockerStatsCollector dockerStatsCollector;

    private MonitoringProperties monitoringProperties;
    private ContainerProperties containerProperties;
    private TestableDeploymentHealthMonitor healthMonitor;

    private UUID deploymentId;
    private UUID projectId;
    private Deployment sampleDeployment;
    private ContainerMetrics runningMetrics;

    // Subclass overriding probeHttp for deterministic HTTP testing
    static class TestableDeploymentHealthMonitor extends DeploymentHealthMonitor {
        private ProbeResult nextProbeResult = new ProbeResult(true, 200, null);

        public TestableDeploymentHealthMonitor(
                DeploymentRepository deploymentRepository,
                DockerStatsCollector dockerStatsCollector,
                MonitoringProperties monitoringProperties,
                ContainerProperties containerProperties
        ) {
            super(deploymentRepository, dockerStatsCollector, monitoringProperties, containerProperties);
        }

        public void setNextProbeResult(ProbeResult result) {
            this.nextProbeResult = result;
        }

        @Override
        protected ProbeResult probeHttp(int port, String path, int timeoutSeconds) {
            return nextProbeResult;
        }
    }

    @BeforeEach
    void setUp() {
        deploymentId = UUID.randomUUID();
        projectId = UUID.randomUUID();

        monitoringProperties = new MonitoringProperties();
        monitoringProperties.setMaxUnhealthyThreshold(3);
        monitoringProperties.setHealthCheckTimeoutSeconds(2);

        containerProperties = new ContainerProperties();
        containerProperties.setHealthCheckPath("/");

        healthMonitor = new TestableDeploymentHealthMonitor(
                deploymentRepository,
                dockerStatsCollector,
                monitoringProperties,
                containerProperties
        );

        Project project = Project.builder().id(projectId).name("my-app").build();

        sampleDeployment = Deployment.builder()
                .id(deploymentId)
                .project(project)
                .status(DeploymentStatus.SUCCESS)
                .containerId("c-12345678")
                .containerPort(10050)
                .build();

        runningMetrics = ContainerMetrics.builder()
                .containerId("c-12345678")
                .status("running")
                .running(true)
                .cpuPercent(0.5)
                .memoryUsageFormatted("50MiB / 512MiB")
                .memoryUsageBytes(52428800L)
                .memoryLimitBytes(536870912L)
                .memoryPercent(9.76)
                .pidsCount(10)
                .build();
    }

    @Test
    @DisplayName("checkDeploymentHealth - Returns HEALTHY when container is running and HTTP probe returns 200")
    void checkDeploymentHealth_Healthy() {
        when(deploymentRepository.findById(deploymentId)).thenReturn(Optional.of(sampleDeployment));
        when(dockerStatsCollector.collectStats("c-12345678")).thenReturn(runningMetrics);
        healthMonitor.setNextProbeResult(new DeploymentHealthMonitor.ProbeResult(true, 200, null));

        DeploymentHealthReport report = healthMonitor.checkDeploymentHealth(deploymentId);

        assertThat(report.getAvailability()).isEqualTo(AvailabilityStatus.HEALTHY);
        assertThat(report.isHttpProbePassed()).isTrue();
        assertThat(report.isContainerRunning()).isTrue();
        assertThat(report.getHttpStatus()).isEqualTo(200);
        assertThat(report.getConsecutiveFailures()).isEqualTo(0);
        assertThat(report.getMetrics().getCpuPercent()).isEqualTo(0.5);
    }

    @Test
    @DisplayName("checkDeploymentHealth - Returns DEGRADED when HTTP probe fails below maxUnhealthyThreshold")
    void checkDeploymentHealth_Degraded() {
        when(deploymentRepository.findById(deploymentId)).thenReturn(Optional.of(sampleDeployment));
        when(dockerStatsCollector.collectStats("c-12345678")).thenReturn(runningMetrics);
        healthMonitor.setNextProbeResult(new DeploymentHealthMonitor.ProbeResult(false, 500, "Internal Server Error"));

        DeploymentHealthReport report = healthMonitor.checkDeploymentHealth(deploymentId);

        assertThat(report.getAvailability()).isEqualTo(AvailabilityStatus.DEGRADED);
        assertThat(report.isHttpProbePassed()).isFalse();
        assertThat(report.getConsecutiveFailures()).isEqualTo(1);
    }

    @Test
    @DisplayName("checkDeploymentHealth - Transitions to UNHEALTHY when failures reach threshold")
    void checkDeploymentHealth_Unhealthy_ThresholdReached() {
        when(deploymentRepository.findById(deploymentId)).thenReturn(Optional.of(sampleDeployment));
        when(dockerStatsCollector.collectStats("c-12345678")).thenReturn(runningMetrics);
        healthMonitor.setNextProbeResult(new DeploymentHealthMonitor.ProbeResult(false, 503, "Service Unavailable"));

        // Trigger 3 consecutive failures
        healthMonitor.checkDeploymentHealth(deploymentId); // 1
        healthMonitor.checkDeploymentHealth(deploymentId); // 2
        DeploymentHealthReport report = healthMonitor.checkDeploymentHealth(deploymentId); // 3

        assertThat(report.getAvailability()).isEqualTo(AvailabilityStatus.UNHEALTHY);
        assertThat(report.getConsecutiveFailures()).isEqualTo(3);
    }

    @Test
    @DisplayName("checkDeploymentHealth - Returns UNHEALTHY when container has stopped/crashed")
    void checkDeploymentHealth_ContainerStopped() {
        when(deploymentRepository.findById(deploymentId)).thenReturn(Optional.of(sampleDeployment));
        when(dockerStatsCollector.collectStats("c-12345678")).thenReturn(ContainerMetrics.stopped("c-12345678"));

        DeploymentHealthReport report = healthMonitor.checkDeploymentHealth(deploymentId);

        assertThat(report.getAvailability()).isEqualTo(AvailabilityStatus.UNHEALTHY);
        assertThat(report.isContainerRunning()).isFalse();
        assertThat(report.getDetails()).contains("not running");
    }

    @Test
    @DisplayName("checkDeploymentHealth - Returns STOPPED for deployment not in SUCCESS state")
    void checkDeploymentHealth_NotSuccess() {
        sampleDeployment.setStatus(DeploymentStatus.FAILED);
        when(deploymentRepository.findById(deploymentId)).thenReturn(Optional.of(sampleDeployment));

        DeploymentHealthReport report = healthMonitor.checkDeploymentHealth(deploymentId);

        assertThat(report.getAvailability()).isEqualTo(AvailabilityStatus.STOPPED);
        verifyNoInteractions(dockerStatsCollector);
    }

    @Test
    @DisplayName("getProjectActiveHealth - Returns STOPPED when project has no active running deployment")
    void getProjectActiveHealth_NoActiveDeployment() {
        when(deploymentRepository.findTopByProjectIdAndStatusOrderByCreatedAtDesc(projectId, DeploymentStatus.SUCCESS))
                .thenReturn(Optional.empty());

        DeploymentHealthReport report = healthMonitor.getProjectActiveHealth(projectId);

        assertThat(report.getAvailability()).isEqualTo(AvailabilityStatus.STOPPED);
        assertThat(report.getDetails()).contains("No active running deployment");
    }

    @Test
    @DisplayName("getProjectActiveHealth - Successfully queries active deployment for project")
    void getProjectActiveHealth_Found() {
        when(deploymentRepository.findTopByProjectIdAndStatusOrderByCreatedAtDesc(projectId, DeploymentStatus.SUCCESS))
                .thenReturn(Optional.of(sampleDeployment));
        when(deploymentRepository.findById(deploymentId)).thenReturn(Optional.of(sampleDeployment));
        when(dockerStatsCollector.collectStats("c-12345678")).thenReturn(runningMetrics);
        healthMonitor.setNextProbeResult(new DeploymentHealthMonitor.ProbeResult(true, 200, null));

        DeploymentHealthReport report = healthMonitor.getProjectActiveHealth(projectId);

        assertThat(report.getAvailability()).isEqualTo(AvailabilityStatus.HEALTHY);
        assertThat(report.getDeploymentId()).isEqualTo(deploymentId);
    }

    @Test
    @DisplayName("checkAllActiveDeployments - Background scheduler checks all active deployments")
    void checkAllActiveDeployments_Scheduled() {
        when(deploymentRepository.findByStatusAndContainerIdIsNotNull(DeploymentStatus.SUCCESS))
                .thenReturn(List.of(sampleDeployment));
        when(dockerStatsCollector.collectStats("c-12345678")).thenReturn(runningMetrics);
        healthMonitor.setNextProbeResult(new DeploymentHealthMonitor.ProbeResult(true, 200, null));

        healthMonitor.checkAllActiveDeployments();

        verify(dockerStatsCollector).collectStats("c-12345678");
    }
}
