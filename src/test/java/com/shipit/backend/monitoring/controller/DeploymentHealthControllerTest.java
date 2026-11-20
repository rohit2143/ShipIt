package com.shipit.backend.monitoring.controller;

import com.shipit.backend.auth.security.UserPrincipal;
import com.shipit.backend.common.exception.GlobalExceptionHandler;
import com.shipit.backend.deployment.entity.Deployment;
import com.shipit.backend.deployment.entity.DeploymentStatus;
import com.shipit.backend.deployment.repository.DeploymentRepository;
import com.shipit.backend.monitoring.model.AvailabilityStatus;
import com.shipit.backend.monitoring.model.ContainerMetrics;
import com.shipit.backend.monitoring.model.DeploymentHealthReport;
import com.shipit.backend.monitoring.service.DeploymentHealthMonitor;
import com.shipit.backend.project.entity.Project;
import com.shipit.backend.project.repository.ProjectRepository;
import com.shipit.backend.user.entity.Role;
import com.shipit.backend.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class DeploymentHealthControllerTest {

    private MockMvc mockMvc;

    @Mock
    private DeploymentHealthMonitor healthMonitor;

    @Mock
    private DeploymentRepository deploymentRepository;

    @Mock
    private ProjectRepository projectRepository;

    @InjectMocks
    private DeploymentHealthController controller;

    private UserPrincipal samplePrincipal;
    private UUID sampleUserId;
    private UUID sampleDeploymentId;
    private UUID sampleProjectId;

    @BeforeEach
    void setUp() {
        sampleUserId = UUID.randomUUID();
        sampleDeploymentId = UUID.randomUUID();
        sampleProjectId = UUID.randomUUID();

        User user = User.builder()
                .id(sampleUserId)
                .name("Health Tester")
                .email("health@shipit.dev")
                .role(Role.ROLE_USER)
                .build();
        samplePrincipal = UserPrincipal.create(user, "password123");

        HandlerMethodArgumentResolver authResolver = new HandlerMethodArgumentResolver() {
            @Override
            public boolean supportsParameter(MethodParameter parameter) {
                return parameter.getParameterType().isAssignableFrom(UserPrincipal.class);
            }

            @Override
            public Object resolveArgument(MethodParameter parameter,
                                          ModelAndViewContainer mavContainer,
                                          NativeWebRequest webRequest,
                                          WebDataBinderFactory binderFactory) {
                return samplePrincipal;
            }
        };

        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(authResolver)
                .build();
    }

    @Test
    @DisplayName("GET /api/v1/deployments/{id}/health - Returns live health report")
    void getDeploymentHealth_Success() throws Exception {
        Deployment deployment = Deployment.builder()
                .id(sampleDeploymentId)
                .status(DeploymentStatus.SUCCESS)
                .build();
        when(deploymentRepository.findByIdAndUserId(sampleDeploymentId, sampleUserId))
                .thenReturn(Optional.of(deployment));

        DeploymentHealthReport report = DeploymentHealthReport.builder()
                .deploymentId(sampleDeploymentId)
                .projectId(sampleProjectId)
                .containerId("c-123456")
                .hostPort(10050)
                .availability(AvailabilityStatus.HEALTHY)
                .httpStatus(200)
                .httpProbePassed(true)
                .containerRunning(true)
                .metrics(ContainerMetrics.builder().cpuPercent(0.5).memoryPercent(10.0).build())
                .details("Application healthy")
                .lastCheckedAt(Instant.now())
                .build();

        when(healthMonitor.checkDeploymentHealth(sampleDeploymentId)).thenReturn(report);

        mockMvc.perform(get("/api/v1/deployments/{id}/health", sampleDeploymentId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.deploymentId").value(sampleDeploymentId.toString()))
                .andExpect(jsonPath("$.data.availability").value("HEALTHY"))
                .andExpect(jsonPath("$.data.httpStatus").value(200))
                .andExpect(jsonPath("$.data.containerRunning").value(true));

        verify(healthMonitor).checkDeploymentHealth(sampleDeploymentId);
    }

    @Test
    @DisplayName("GET /api/v1/deployments/{id}/health - Returns 404 when deployment not found")
    void getDeploymentHealth_NotFound() throws Exception {
        when(deploymentRepository.findByIdAndUserId(sampleDeploymentId, sampleUserId))
                .thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/deployments/{id}/health", sampleDeploymentId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    @DisplayName("GET /api/v1/projects/{projectId}/health - Returns project active health report")
    void getProjectActiveHealth_Success() throws Exception {
        Project project = Project.builder().id(sampleProjectId).build();
        when(projectRepository.findByIdAndUserId(sampleProjectId, sampleUserId))
                .thenReturn(Optional.of(project));

        DeploymentHealthReport report = DeploymentHealthReport.builder()
                .projectId(sampleProjectId)
                .deploymentId(sampleDeploymentId)
                .availability(AvailabilityStatus.HEALTHY)
                .httpStatus(200)
                .build();

        when(healthMonitor.getProjectActiveHealth(sampleProjectId)).thenReturn(report);

        mockMvc.perform(get("/api/v1/projects/{projectId}/health", sampleProjectId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.projectId").value(sampleProjectId.toString()))
                .andExpect(jsonPath("$.data.availability").value("HEALTHY"));

        verify(healthMonitor).getProjectActiveHealth(sampleProjectId);
    }
}
