package com.shipit.backend.monitoring.controller;

import com.shipit.backend.auth.security.UserPrincipal;
import com.shipit.backend.common.exception.GlobalExceptionHandler;
import com.shipit.backend.common.exception.ResourceNotFoundException;
import com.shipit.backend.deployment.entity.DeploymentStatus;
import com.shipit.backend.deployment.entity.DeploymentTrigger;
import com.shipit.backend.monitoring.dto.DeploymentTimelineEntry;
import com.shipit.backend.monitoring.dto.ProjectDeploymentHistoryResponse;
import com.shipit.backend.monitoring.service.DeploymentHistoryService;
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

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class DeploymentHistoryControllerTest {

    private MockMvc mockMvc;

    @Mock
    private DeploymentHistoryService historyService;

    @InjectMocks
    private DeploymentHistoryController controller;

    private UserPrincipal samplePrincipal;
    private UUID sampleUserId;
    private UUID sampleProjectId;
    private UUID sampleDeploymentId;

    @BeforeEach
    void setUp() {
        sampleUserId = UUID.randomUUID();
        sampleProjectId = UUID.randomUUID();
        sampleDeploymentId = UUID.randomUUID();

        User user = User.builder()
                .id(sampleUserId)
                .name("History Tester")
                .email("history@shipit.dev")
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
    @DisplayName("GET /api/v1/projects/{projectId}/deployment-history - Returns project deployment metrics & timeline")
    void getProjectHistory_Success() throws Exception {
        ProjectDeploymentHistoryResponse response = ProjectDeploymentHistoryResponse.builder()
                .projectId(sampleProjectId)
                .projectName("demo-service")
                .totalDeployments(5L)
                .successfulDeployments(4L)
                .failedDeployments(1L)
                .successRatePercent(80.0)
                .averageDurationSeconds(35.5)
                .timeline(List.of())
                .page(0)
                .size(20)
                .hasMore(false)
                .build();

        when(historyService.getProjectDeploymentHistory(eq(sampleUserId), eq(sampleProjectId), eq(0), eq(20)))
                .thenReturn(response);

        mockMvc.perform(get("/api/v1/projects/{projectId}/deployment-history", sampleProjectId)
                        .param("page", "0")
                        .param("size", "20")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.projectId").value(sampleProjectId.toString()))
                .andExpect(jsonPath("$.data.totalDeployments").value(5))
                .andExpect(jsonPath("$.data.successRatePercent").value(80.0))
                .andExpect(jsonPath("$.data.averageDurationSeconds").value(35.5));

        verify(historyService).getProjectDeploymentHistory(sampleUserId, sampleProjectId, 0, 20);
    }

    @Test
    @DisplayName("GET /api/v1/projects/{projectId}/deployment-history - Returns 404 when project not found")
    void getProjectHistory_NotFound() throws Exception {
        when(historyService.getProjectDeploymentHistory(eq(sampleUserId), eq(sampleProjectId), anyInt(), anyInt()))
                .thenThrow(new ResourceNotFoundException("Project not found"));

        mockMvc.perform(get("/api/v1/projects/{projectId}/deployment-history", sampleProjectId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    @DisplayName("GET /api/v1/deployments/{id}/timeline - Returns deployment timeline details")
    void getDeploymentTimeline_Success() throws Exception {
        DeploymentTimelineEntry entry = DeploymentTimelineEntry.builder()
                .deploymentId(sampleDeploymentId)
                .status(DeploymentStatus.SUCCESS)
                .trigger(DeploymentTrigger.MANUAL)
                .commitSha("abc12345")
                .durationSeconds(42L)
                .build();

        when(historyService.getDeploymentTimelineDetails(sampleUserId, sampleDeploymentId)).thenReturn(entry);

        mockMvc.perform(get("/api/v1/deployments/{id}/timeline", sampleDeploymentId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.deploymentId").value(sampleDeploymentId.toString()))
                .andExpect(jsonPath("$.data.status").value("SUCCESS"))
                .andExpect(jsonPath("$.data.durationSeconds").value(42));

        verify(historyService).getDeploymentTimelineDetails(sampleUserId, sampleDeploymentId);
    }
}
