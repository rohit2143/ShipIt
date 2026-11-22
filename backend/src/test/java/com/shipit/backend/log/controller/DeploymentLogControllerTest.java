package com.shipit.backend.log.controller;

import com.shipit.backend.auth.security.UserPrincipal;
import com.shipit.backend.build.logging.LogSource;
import com.shipit.backend.common.exception.GlobalExceptionHandler;
import com.shipit.backend.common.exception.ResourceNotFoundException;
import com.shipit.backend.log.dto.DeploymentLogDto;
import com.shipit.backend.log.dto.DeploymentLogsPageResponse;
import com.shipit.backend.log.entity.DeploymentStage;
import com.shipit.backend.log.service.DeploymentLogService;
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
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class DeploymentLogControllerTest {

    private MockMvc mockMvc;

    @Mock
    private DeploymentLogService logService;

    @InjectMocks
    private DeploymentLogController controller;

    private UserPrincipal samplePrincipal;
    private UUID sampleUserId;
    private UUID sampleDeploymentId;

    @BeforeEach
    void setUp() {
        sampleUserId = UUID.randomUUID();
        sampleDeploymentId = UUID.randomUUID();

        User user = User.builder()
                .id(sampleUserId)
                .name("Log Tester")
                .email("tester@shipit.dev")
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
    @DisplayName("GET /api/v1/deployments/{id}/logs/history - Returns paginated logs")
    void getHistoricalLogs_Success() throws Exception {
        DeploymentLogDto dto = DeploymentLogDto.builder()
                .sequenceNumber(1L)
                .stage(DeploymentStage.BUILDING)
                .source(LogSource.STDOUT)
                .message("Building container...")
                .timestamp(Instant.now())
                .build();

        DeploymentLogsPageResponse response = DeploymentLogsPageResponse.builder()
                .deploymentId(sampleDeploymentId)
                .logs(List.of(dto))
                .totalLines(1L)
                .maxSequenceNumber(1L)
                .page(0)
                .size(100)
                .hasMore(false)
                .build();

        when(logService.getLogs(eq(sampleUserId), eq(sampleDeploymentId), eq(null), eq(null), eq(0), eq(100)))
                .thenReturn(response);

        mockMvc.perform(get("/api/v1/deployments/{id}/logs/history", sampleDeploymentId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.deploymentId").value(sampleDeploymentId.toString()))
                .andExpect(jsonPath("$.data.logs[0].message").value("Building container..."))
                .andExpect(jsonPath("$.data.totalLines").value(1));

        verify(logService).getLogs(sampleUserId, sampleDeploymentId, null, null, 0, 100);
    }

    @Test
    @DisplayName("GET /api/v1/deployments/{id}/logs/history - Passes stage and since filtering")
    void getHistoricalLogs_WithFilters() throws Exception {
        DeploymentLogsPageResponse response = DeploymentLogsPageResponse.builder()
                .deploymentId(sampleDeploymentId)
                .logs(List.of())
                .totalLines(0L)
                .maxSequenceNumber(0L)
                .page(1)
                .size(20)
                .hasMore(false)
                .build();

        when(logService.getLogs(eq(sampleUserId), eq(sampleDeploymentId), eq(DeploymentStage.BUILDING), eq(5L), eq(1), eq(20)))
                .thenReturn(response);

        mockMvc.perform(get("/api/v1/deployments/{id}/logs/history", sampleDeploymentId)
                        .param("stage", "BUILDING")
                        .param("since", "5")
                        .param("page", "1")
                        .param("size", "20")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(logService).getLogs(sampleUserId, sampleDeploymentId, DeploymentStage.BUILDING, 5L, 1, 20);
    }

    @Test
    @DisplayName("GET /api/v1/deployments/{id}/logs/history - Returns 404 when deployment not found")
    void getHistoricalLogs_NotFound() throws Exception {
        when(logService.getLogs(eq(sampleUserId), eq(sampleDeploymentId), any(), any(), anyInt(), anyInt()))
                .thenThrow(new ResourceNotFoundException("Deployment not found"));

        mockMvc.perform(get("/api/v1/deployments/{id}/logs/history", sampleDeploymentId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    @DisplayName("GET /api/v1/deployments/{id}/logs/stream - Returns SSE stream")
    void streamDeploymentLogs_Success() throws Exception {
        SseEmitter emitter = new SseEmitter();
        when(logService.subscribeToStream(sampleUserId, sampleDeploymentId, null)).thenReturn(emitter);

        mockMvc.perform(get("/api/v1/deployments/{id}/logs/stream", sampleDeploymentId)
                        .accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(status().isOk());

        verify(logService).subscribeToStream(sampleUserId, sampleDeploymentId, null);
    }

    @Test
    @DisplayName("GET /api/v1/deployments/{id}/logs/download - Returns plain text attachment")
    void downloadFullLogs_Success() throws Exception {
        String logText = "[SYSTEM] Clone started\n[BUILD] Build finished\n";
        when(logService.getFullLogsAsString(sampleUserId, sampleDeploymentId)).thenReturn(logText);

        mockMvc.perform(get("/api/v1/deployments/{id}/logs/download", sampleDeploymentId))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"deployment-" + sampleDeploymentId + ".log\""))
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_PLAIN))
                .andExpect(content().string(logText));

        verify(logService).getFullLogsAsString(sampleUserId, sampleDeploymentId);
    }
}
