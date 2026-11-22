package com.shipit.backend.deployment.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shipit.backend.auth.security.UserPrincipal;
import com.shipit.backend.common.exception.BadRequestException;
import com.shipit.backend.common.exception.ConflictException;
import com.shipit.backend.common.exception.GlobalExceptionHandler;
import com.shipit.backend.common.exception.ResourceNotFoundException;
import com.shipit.backend.deployment.dto.DeploymentResponse;
import com.shipit.backend.deployment.dto.DeploymentSummaryResponse;
import com.shipit.backend.deployment.dto.TriggerDeploymentRequest;
import com.shipit.backend.deployment.entity.DeploymentStatus;
import com.shipit.backend.deployment.entity.DeploymentTrigger;
import com.shipit.backend.deployment.service.DeploymentService;
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
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class DeploymentControllerTest {

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private DeploymentService deploymentService;

    @InjectMocks
    private DeploymentController deploymentController;

    private User sampleUser;
    private UserPrincipal samplePrincipal;
    private UUID sampleUserId;
    private UUID sampleProjectId;
    private UUID sampleDeploymentId;

    @BeforeEach
    void setUp() {
        sampleUserId = UUID.randomUUID();
        sampleProjectId = UUID.randomUUID();
        sampleDeploymentId = UUID.randomUUID();

        sampleUser = User.builder()
                .id(sampleUserId)
                .name("Developer")
                .email("dev@shipit.dev")
                .role(Role.ROLE_USER)
                .build();

        samplePrincipal = UserPrincipal.create(sampleUser, "encoded-pwd");

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

        mockMvc = MockMvcBuilders.standaloneSetup(deploymentController)
                .setCustomArgumentResolvers(authResolver, new PageableHandlerMethodArgumentResolver())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("POST /api/v1/projects/{projectId}/deployments - Success returns 201 Created")
    void triggerDeployment_Success() throws Exception {
        TriggerDeploymentRequest request = TriggerDeploymentRequest.builder()
                .branch("release/v1")
                .commitSha("abc123def456")
                .commitMessage("feat: release v1")
                .build();

        DeploymentResponse response = DeploymentResponse.builder()
                .id(sampleDeploymentId)
                .projectId(sampleProjectId)
                .projectName("Sample Project")
                .status(DeploymentStatus.QUEUED)
                .trigger(DeploymentTrigger.MANUAL)
                .commitBranch("release/v1")
                .commitSha("abc123def456")
                .commitAuthor("Developer")
                .queuedAt(Instant.now())
                .build();

        when(deploymentService.triggerManualDeployment(eq(sampleUserId), eq(sampleProjectId), any(TriggerDeploymentRequest.class)))
                .thenReturn(response);

        mockMvc.perform(post("/api/v1/projects/{projectId}/deployments", sampleProjectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Deployment triggered successfully"))
                .andExpect(jsonPath("$.data.id").value(sampleDeploymentId.toString()))
                .andExpect(jsonPath("$.data.status").value("QUEUED"))
                .andExpect(jsonPath("$.data.trigger").value("MANUAL"))
                .andExpect(jsonPath("$.data.commitBranch").value("release/v1"));
    }

    @Test
    @DisplayName("POST /api/v1/projects/{projectId}/deployments - Success with no body returns 201 Created")
    void triggerDeployment_Success_NoBody() throws Exception {
        DeploymentResponse response = DeploymentResponse.builder()
                .id(sampleDeploymentId)
                .projectId(sampleProjectId)
                .status(DeploymentStatus.QUEUED)
                .trigger(DeploymentTrigger.MANUAL)
                .commitBranch("main")
                .queuedAt(Instant.now())
                .build();

        when(deploymentService.triggerManualDeployment(eq(sampleUserId), eq(sampleProjectId), any()))
                .thenReturn(response);

        mockMvc.perform(post("/api/v1/projects/{projectId}/deployments", sampleProjectId))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(sampleDeploymentId.toString()))
                .andExpect(jsonPath("$.data.status").value("QUEUED"));
    }

    @Test
    @DisplayName("POST /api/v1/projects/{projectId}/deployments - Project not found returns 404 Not Found")
    void triggerDeployment_ProjectNotFound_Returns404() throws Exception {
        when(deploymentService.triggerManualDeployment(eq(sampleUserId), eq(sampleProjectId), any()))
                .thenThrow(new ResourceNotFoundException("Project not found with id: " + sampleProjectId));

        mockMvc.perform(post("/api/v1/projects/{projectId}/deployments", sampleProjectId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    @DisplayName("POST /api/v1/projects/{projectId}/deployments - Project not active returns 400 Bad Request")
    void triggerDeployment_ProjectNotActive_Returns400() throws Exception {
        when(deploymentService.triggerManualDeployment(eq(sampleUserId), eq(sampleProjectId), any()))
                .thenThrow(new BadRequestException("Cannot deploy project with status: ARCHIVED", "PROJECT_NOT_ACTIVE"));

        mockMvc.perform(post("/api/v1/projects/{projectId}/deployments", sampleProjectId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("PROJECT_NOT_ACTIVE"));
    }

    @Test
    @DisplayName("GET /api/v1/projects/{projectId}/deployments - Success returns 200 OK with PageResponse")
    void getProjectDeployments_Success() throws Exception {
        DeploymentSummaryResponse summary = DeploymentSummaryResponse.builder()
                .id(sampleDeploymentId)
                .projectId(sampleProjectId)
                .status(DeploymentStatus.SUCCESS)
                .trigger(DeploymentTrigger.MANUAL)
                .commitBranch("main")
                .commitSha("abc123456789")
                .commitAuthor("Developer")
                .queuedAt(Instant.now())
                .startedAt(Instant.now())
                .finishedAt(Instant.now().plusSeconds(45))
                .durationSeconds(45L)
                .build();

        when(deploymentService.getProjectDeployments(eq(sampleUserId), eq(sampleProjectId), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(summary)));

        mockMvc.perform(get("/api/v1/projects/{projectId}/deployments", sampleProjectId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content", hasSize(1)))
                .andExpect(jsonPath("$.data.content[0].id").value(sampleDeploymentId.toString()))
                .andExpect(jsonPath("$.data.content[0].status").value("SUCCESS"))
                .andExpect(jsonPath("$.data.content[0].durationSeconds").value(45))
                .andExpect(jsonPath("$.data.totalElements").value(1));
    }

    @Test
    @DisplayName("GET /api/v1/projects/{projectId}/deployments - Project not found returns 404 Not Found")
    void getProjectDeployments_ProjectNotFound_Returns404() throws Exception {
        when(deploymentService.getProjectDeployments(eq(sampleUserId), eq(sampleProjectId), any(Pageable.class)))
                .thenThrow(new ResourceNotFoundException("Project not found with id: " + sampleProjectId));

        mockMvc.perform(get("/api/v1/projects/{projectId}/deployments", sampleProjectId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    @DisplayName("GET /api/v1/deployments/{id} - Success returns 200 OK with details")
    void getDeployment_Success() throws Exception {
        DeploymentResponse response = DeploymentResponse.builder()
                .id(sampleDeploymentId)
                .projectId(sampleProjectId)
                .projectName("Sample Project")
                .status(DeploymentStatus.HEALTH_CHECKING)
                .trigger(DeploymentTrigger.MANUAL)
                .commitBranch("main")
                .commitSha("commitSha123")
                .queuedAt(Instant.now())
                .build();

        when(deploymentService.getDeployment(sampleUserId, sampleDeploymentId)).thenReturn(response);

        mockMvc.perform(get("/api/v1/deployments/{id}", sampleDeploymentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(sampleDeploymentId.toString()))
                .andExpect(jsonPath("$.data.status").value("HEALTH_CHECKING"));
    }

    @Test
    @DisplayName("GET /api/v1/deployments/{id} - Not found returns 404 Not Found")
    void getDeployment_NotFound() throws Exception {
        when(deploymentService.getDeployment(sampleUserId, sampleDeploymentId))
                .thenThrow(new ResourceNotFoundException("Deployment not found with id: " + sampleDeploymentId));

        mockMvc.perform(get("/api/v1/deployments/{id}", sampleDeploymentId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    @DisplayName("POST /api/v1/deployments/{id}/cancel - Success returns 200 OK with cancelled deployment")
    void cancelDeployment_Success() throws Exception {
        DeploymentResponse response = DeploymentResponse.builder()
                .id(sampleDeploymentId)
                .projectId(sampleProjectId)
                .status(DeploymentStatus.CANCELLED)
                .finishedAt(Instant.now())
                .build();

        when(deploymentService.cancelDeployment(sampleUserId, sampleDeploymentId)).thenReturn(response);

        mockMvc.perform(post("/api/v1/deployments/{id}/cancel", sampleDeploymentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Deployment cancelled successfully"))
                .andExpect(jsonPath("$.data.id").value(sampleDeploymentId.toString()))
                .andExpect(jsonPath("$.data.status").value("CANCELLED"));
    }

    @Test
    @DisplayName("POST /api/v1/deployments/{id}/cancel - Conflict throws 409 Conflict")
    void cancelDeployment_Conflict_Returns409() throws Exception {
        when(deploymentService.cancelDeployment(sampleUserId, sampleDeploymentId))
                .thenThrow(new ConflictException("Deployment is already in terminal state: SUCCESS", "DEPLOYMENT_ALREADY_TERMINAL"));

        mockMvc.perform(post("/api/v1/deployments/{id}/cancel", sampleDeploymentId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("DEPLOYMENT_ALREADY_TERMINAL"));
    }

    @Test
    @DisplayName("POST /api/v1/deployments/{id}/retry - Success returns 201 Created with new deployment")
    void retryDeployment_Success() throws Exception {
        UUID newDeploymentId = UUID.randomUUID();
        DeploymentResponse response = DeploymentResponse.builder()
                .id(newDeploymentId)
                .projectId(sampleProjectId)
                .status(DeploymentStatus.QUEUED)
                .trigger(DeploymentTrigger.RETRY)
                .commitBranch("main")
                .commitMessage("Retry: Failed build")
                .queuedAt(Instant.now())
                .build();

        when(deploymentService.retryDeployment(sampleUserId, sampleDeploymentId)).thenReturn(response);

        mockMvc.perform(post("/api/v1/deployments/{id}/retry", sampleDeploymentId))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Deployment retried successfully"))
                .andExpect(jsonPath("$.data.id").value(newDeploymentId.toString()))
                .andExpect(jsonPath("$.data.status").value("QUEUED"))
                .andExpect(jsonPath("$.data.trigger").value("RETRY"));
    }

    @Test
    @DisplayName("POST /api/v1/deployments/{id}/retry - Non-retryable status returns 400 Bad Request")
    void retryDeployment_NonRetryable_Returns400() throws Exception {
        when(deploymentService.retryDeployment(sampleUserId, sampleDeploymentId))
                .thenThrow(new BadRequestException("Only failed or cancelled deployments can be retried", "DEPLOYMENT_NOT_RETRYABLE"));

        mockMvc.perform(post("/api/v1/deployments/{id}/retry", sampleDeploymentId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("DEPLOYMENT_NOT_RETRYABLE"));
    }
}
