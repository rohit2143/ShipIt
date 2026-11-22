package com.shipit.backend.build.controller;

import com.shipit.backend.auth.security.UserPrincipal;
import com.shipit.backend.build.dto.BuildLogsResponse;
import com.shipit.backend.build.service.BuildLogService;
import com.shipit.backend.common.exception.GlobalExceptionHandler;
import com.shipit.backend.common.exception.ResourceNotFoundException;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class DeploymentBuildLogsControllerTest {

    private MockMvc mockMvc;

    @Mock
    private BuildLogService buildLogService;

    @InjectMocks
    private DeploymentBuildLogsController controller;

    private UserPrincipal samplePrincipal;
    private UUID sampleUserId;
    private UUID sampleDeploymentId;

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
    @DisplayName("GET /api/v1/deployments/{id}/logs - Successfully retrieves build logs")
    void getBuildLogs_Success() throws Exception {
        String logContent = "Step 1: Cloning repository\nStep 2: Detecting project\nStep 3: Building image\n";
        BuildLogsResponse logsResponse = BuildLogsResponse.builder()
                .deploymentId(sampleDeploymentId)
                .logs(logContent)
                .lineCount(3)
                .build();

        when(buildLogService.getLogs(sampleUserId, sampleDeploymentId)).thenReturn(logsResponse);

        mockMvc.perform(get("/api/v1/deployments/{id}/logs", sampleDeploymentId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.deploymentId").value(sampleDeploymentId.toString()))
                .andExpect(jsonPath("$.data.logs").value(logContent))
                .andExpect(jsonPath("$.data.lineCount").value(3));

        verify(buildLogService).getLogs(sampleUserId, sampleDeploymentId);
    }

    @Test
    @DisplayName("GET /api/v1/deployments/{id}/logs - Deployment not found returns 404")
    void getBuildLogs_NotFound() throws Exception {
        when(buildLogService.getLogs(sampleUserId, sampleDeploymentId))
                .thenThrow(new ResourceNotFoundException("Deployment not found with id: " + sampleDeploymentId));

        mockMvc.perform(get("/api/v1/deployments/{id}/logs", sampleDeploymentId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Deployment not found with id: " + sampleDeploymentId));
    }
}
