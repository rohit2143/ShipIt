package com.shipit.backend.job.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shipit.backend.auth.security.UserPrincipal;
import com.shipit.backend.common.exception.GlobalExceptionHandler;
import com.shipit.backend.common.exception.ResourceNotFoundException;
import com.shipit.backend.job.dto.JobQueueStatsResponse;
import com.shipit.backend.job.dto.JobResponse;
import com.shipit.backend.job.entity.JobStatus;
import com.shipit.backend.job.entity.JobType;
import com.shipit.backend.job.service.DeploymentJobService;
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
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class DeploymentJobControllerTest {

    private MockMvc mockMvc;

    @Mock
    private DeploymentJobService jobService;

    @InjectMocks
    private DeploymentJobController jobController;

    private UserPrincipal samplePrincipal;
    private UUID sampleUserId;
    private UUID sampleDeploymentId;
    private UUID sampleJobId;
    private JobResponse sampleJobResponse;

    @BeforeEach
    void setUp() {
        sampleUserId = UUID.randomUUID();
        sampleDeploymentId = UUID.randomUUID();
        sampleJobId = UUID.randomUUID();

        User sampleUser = User.builder()
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

        mockMvc = MockMvcBuilders.standaloneSetup(jobController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(authResolver)
                .build();

        sampleJobResponse = JobResponse.builder()
                .id(sampleJobId)
                .deploymentId(sampleDeploymentId)
                .projectId(UUID.randomUUID())
                .type(JobType.DEPLOYMENT)
                .status(JobStatus.RUNNING)
                .priority(0)
                .retryCount(0)
                .maxRetries(3)
                .claimedBy("worker-node-1")
                .queuedAt(Instant.now().minusSeconds(60))
                .claimedAt(Instant.now().minusSeconds(45))
                .heartbeatAt(Instant.now().minusSeconds(5))
                .durationSeconds(45L)
                .createdAt(Instant.now().minusSeconds(60))
                .updatedAt(Instant.now().minusSeconds(5))
                .build();
    }

    @Test
    @DisplayName("GET /api/v1/jobs/{id} returns job details when authorized")
    void testGetJob_Success() throws Exception {
        when(jobService.getJobForUser(eq(sampleUserId), eq(sampleJobId))).thenReturn(sampleJobResponse);

        mockMvc.perform(get("/api/v1/jobs/{id}", sampleJobId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(sampleJobId.toString()))
                .andExpect(jsonPath("$.data.deploymentId").value(sampleDeploymentId.toString()))
                .andExpect(jsonPath("$.data.status").value("RUNNING"))
                .andExpect(jsonPath("$.data.claimedBy").value("worker-node-1"))
                .andExpect(jsonPath("$.data.durationSeconds").value(45));

        verify(jobService).getJobForUser(sampleUserId, sampleJobId);
    }

    @Test
    @DisplayName("GET /api/v1/jobs/{id} returns 404 when job not found or unauthorized")
    void testGetJob_NotFound() throws Exception {
        when(jobService.getJobForUser(eq(sampleUserId), eq(sampleJobId)))
                .thenThrow(new ResourceNotFoundException("DeploymentJob not found with id: " + sampleJobId));

        mockMvc.perform(get("/api/v1/jobs/{id}", sampleJobId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("DeploymentJob not found with id: " + sampleJobId));
    }

    @Test
    @DisplayName("GET /api/v1/deployments/{deploymentId}/job returns latest job for deployment")
    void testGetLatestJobForDeployment_Success() throws Exception {
        when(jobService.getLatestJobForDeploymentAndUser(eq(sampleUserId), eq(sampleDeploymentId)))
                .thenReturn(sampleJobResponse);

        mockMvc.perform(get("/api/v1/deployments/{deploymentId}/job", sampleDeploymentId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(sampleJobId.toString()))
                .andExpect(jsonPath("$.data.status").value("RUNNING"));

        verify(jobService).getLatestJobForDeploymentAndUser(sampleUserId, sampleDeploymentId);
    }

    @Test
    @DisplayName("GET /api/v1/deployments/{deploymentId}/job returns 404 when no job found")
    void testGetLatestJobForDeployment_NotFound() throws Exception {
        when(jobService.getLatestJobForDeploymentAndUser(eq(sampleUserId), eq(sampleDeploymentId)))
                .thenThrow(new ResourceNotFoundException("No background job found for deployment: " + sampleDeploymentId));

        mockMvc.perform(get("/api/v1/deployments/{deploymentId}/job", sampleDeploymentId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("No background job found for deployment: " + sampleDeploymentId));
    }

    @Test
    @DisplayName("GET /api/v1/deployments/{deploymentId}/jobs returns list of all jobs")
    void testGetAllJobsForDeployment_Success() throws Exception {
        when(jobService.getAllJobsForDeploymentAndUser(eq(sampleUserId), eq(sampleDeploymentId)))
                .thenReturn(List.of(sampleJobResponse));

        mockMvc.perform(get("/api/v1/deployments/{deploymentId}/jobs", sampleDeploymentId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].id").value(sampleJobId.toString()));

        verify(jobService).getAllJobsForDeploymentAndUser(sampleUserId, sampleDeploymentId);
    }

    @Test
    @DisplayName("GET /api/v1/deployments/{deploymentId}/jobs returns empty list when no jobs exist")
    void testGetAllJobsForDeployment_EmptyList() throws Exception {
        when(jobService.getAllJobsForDeploymentAndUser(eq(sampleUserId), eq(sampleDeploymentId)))
                .thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/v1/deployments/{deploymentId}/jobs", sampleDeploymentId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data", hasSize(0)));
    }

    @Test
    @DisplayName("GET /api/v1/jobs/stats returns queue counters across statuses")
    void testGetQueueStats_Success() throws Exception {
        JobQueueStatsResponse stats = JobQueueStatsResponse.builder()
                .pendingCount(3)
                .runningCount(1)
                .completedCount(25)
                .failedCount(2)
                .cancelledCount(1)
                .totalCount(32)
                .build();

        when(jobService.getQueueStats()).thenReturn(stats);

        mockMvc.perform(get("/api/v1/jobs/stats")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.pendingCount").value(3))
                .andExpect(jsonPath("$.data.runningCount").value(1))
                .andExpect(jsonPath("$.data.completedCount").value(25))
                .andExpect(jsonPath("$.data.failedCount").value(2))
                .andExpect(jsonPath("$.data.cancelledCount").value(1))
                .andExpect(jsonPath("$.data.totalCount").value(32));

        verify(jobService).getQueueStats();
    }
}
