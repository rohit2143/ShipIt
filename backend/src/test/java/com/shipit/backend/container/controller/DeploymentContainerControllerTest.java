package com.shipit.backend.container.controller;

import com.shipit.backend.auth.security.UserPrincipal;
import com.shipit.backend.common.exception.ConflictException;
import com.shipit.backend.common.exception.GlobalExceptionHandler;
import com.shipit.backend.common.exception.ResourceNotFoundException;
import com.shipit.backend.container.dto.ContainerLogsResponse;
import com.shipit.backend.container.model.ContainerInfo;
import com.shipit.backend.container.model.ContainerStatus;
import com.shipit.backend.container.service.ContainerManagementService;
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

import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class DeploymentContainerControllerTest {

    private MockMvc mockMvc;

    @Mock
    private ContainerManagementService containerManagementService;

    @InjectMocks
    private DeploymentContainerController controller;

    private UserPrincipal samplePrincipal;
    private UUID sampleUserId;
    private UUID sampleDeploymentId;
    private ContainerInfo sampleContainerInfo;

    @BeforeEach
    void setUp() {
        sampleUserId = UUID.randomUUID();
        sampleDeploymentId = UUID.randomUUID();

        User sampleUser = User.builder()
                .id(sampleUserId)
                .name("Developer")
                .email("dev@shipit.dev")
                .role(Role.ROLE_USER)
                .build();

        samplePrincipal = UserPrincipal.create(sampleUser, "encoded-pwd");

        sampleContainerInfo = ContainerInfo.builder()
                .containerId("c1234567890abcdef")
                .status(ContainerStatus.RUNNING)
                .hostPort(10050)
                .containerPort(8080)
                .running(true)
                .build();

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
    @DisplayName("GET /api/v1/deployments/{id}/container - Successfully inspects container")
    void inspectContainer_Success() throws Exception {
        when(containerManagementService.inspectDeploymentContainer(sampleUserId, sampleDeploymentId))
                .thenReturn(sampleContainerInfo);

        mockMvc.perform(get("/api/v1/deployments/{id}/container", sampleDeploymentId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.containerId").value("c1234567890abcdef"))
                .andExpect(jsonPath("$.data.status").value("RUNNING"))
                .andExpect(jsonPath("$.data.hostPort").value(10050))
                .andExpect(jsonPath("$.data.running").value(true));

        verify(containerManagementService).inspectDeploymentContainer(sampleUserId, sampleDeploymentId);
    }

    @Test
    @DisplayName("GET /api/v1/deployments/{id}/container - Container not found returns 404")
    void inspectContainer_NotFound() throws Exception {
        when(containerManagementService.inspectDeploymentContainer(sampleUserId, sampleDeploymentId))
                .thenThrow(new ResourceNotFoundException("No container is associated with deployment: " + sampleDeploymentId));

        mockMvc.perform(get("/api/v1/deployments/{id}/container", sampleDeploymentId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    @DisplayName("POST /api/v1/deployments/{id}/container/restart - Successfully restarts container")
    void restartContainer_Success() throws Exception {
        when(containerManagementService.restartDeploymentContainer(sampleUserId, sampleDeploymentId))
                .thenReturn(sampleContainerInfo);

        mockMvc.perform(post("/api/v1/deployments/{id}/container/restart", sampleDeploymentId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Container restarted successfully"))
                .andExpect(jsonPath("$.data.containerId").value("c1234567890abcdef"));

        verify(containerManagementService).restartDeploymentContainer(sampleUserId, sampleDeploymentId);
    }

    @Test
    @DisplayName("POST /api/v1/deployments/{id}/restart - Alias endpoint also restarts container")
    void restartContainer_Alias_Success() throws Exception {
        when(containerManagementService.restartDeploymentContainer(sampleUserId, sampleDeploymentId))
                .thenReturn(sampleContainerInfo);

        mockMvc.perform(post("/api/v1/deployments/{id}/restart", sampleDeploymentId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Container restarted successfully"));

        verify(containerManagementService).restartDeploymentContainer(sampleUserId, sampleDeploymentId);
    }

    @Test
    @DisplayName("POST /api/v1/deployments/{id}/container/stop - Successfully stops container")
    void stopContainer_Success() throws Exception {
        ContainerInfo stopped = ContainerInfo.builder()
                .containerId("c1234567890abcdef")
                .status(ContainerStatus.STOPPED)
                .running(false)
                .build();

        when(containerManagementService.stopDeploymentContainer(sampleUserId, sampleDeploymentId))
                .thenReturn(stopped);

        mockMvc.perform(post("/api/v1/deployments/{id}/container/stop", sampleDeploymentId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Container stopped successfully"))
                .andExpect(jsonPath("$.data.status").value("STOPPED"));

        verify(containerManagementService).stopDeploymentContainer(sampleUserId, sampleDeploymentId);
    }

    @Test
    @DisplayName("POST /api/v1/deployments/{id}/stop - Alias endpoint also stops container")
    void stopContainer_Alias_Success() throws Exception {
        ContainerInfo stopped = ContainerInfo.builder()
                .containerId("c1234567890abcdef")
                .status(ContainerStatus.STOPPED)
                .running(false)
                .build();

        when(containerManagementService.stopDeploymentContainer(sampleUserId, sampleDeploymentId))
                .thenReturn(stopped);

        mockMvc.perform(post("/api/v1/deployments/{id}/stop", sampleDeploymentId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(containerManagementService).stopDeploymentContainer(sampleUserId, sampleDeploymentId);
    }

    @Test
    @DisplayName("POST /api/v1/deployments/{id}/container/stop - When no container active returns 409 Conflict")
    void stopContainer_NoContainer_Conflict() throws Exception {
        when(containerManagementService.stopDeploymentContainer(sampleUserId, sampleDeploymentId))
                .thenThrow(new ConflictException("Cannot stop: no container is associated with deployment: " + sampleDeploymentId));

        mockMvc.perform(post("/api/v1/deployments/{id}/container/stop", sampleDeploymentId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_CONFLICT"));
    }

    @Test
    @DisplayName("GET /api/v1/deployments/{id}/container/logs - Successfully returns container logs")
    void getContainerLogs_Success() throws Exception {
        ContainerLogsResponse logsResponse = ContainerLogsResponse.builder()
                .deploymentId(sampleDeploymentId)
                .containerId("c1234567890abcdef")
                .lines(50)
                .logs("Line 1\nLine 2\n")
                .build();

        when(containerManagementService.getDeploymentContainerLogs(sampleUserId, sampleDeploymentId, 50))
                .thenReturn(logsResponse);

        mockMvc.perform(get("/api/v1/deployments/{id}/container/logs?lines=50", sampleDeploymentId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.containerId").value("c1234567890abcdef"))
                .andExpect(jsonPath("$.data.lines").value(50))
                .andExpect(jsonPath("$.data.logs").value("Line 1\nLine 2\n"));

        verify(containerManagementService).getDeploymentContainerLogs(sampleUserId, sampleDeploymentId, 50);
    }
}
