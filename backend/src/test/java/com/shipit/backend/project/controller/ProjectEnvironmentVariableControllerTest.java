package com.shipit.backend.project.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shipit.backend.auth.security.UserPrincipal;
import com.shipit.backend.common.exception.GlobalExceptionHandler;
import com.shipit.backend.common.exception.ResourceNotFoundException;
import com.shipit.backend.project.dto.BatchEnvironmentVariablesRequest;
import com.shipit.backend.project.dto.EnvironmentVariableRequest;
import com.shipit.backend.project.dto.EnvironmentVariableResponse;
import com.shipit.backend.project.service.ProjectEnvironmentVariableService;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class ProjectEnvironmentVariableControllerTest {

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @Mock
    private ProjectEnvironmentVariableService environmentVariableService;

    @InjectMocks
    private ProjectEnvironmentVariableController controller;

    private User sampleUser;
    private UserPrincipal samplePrincipal;
    private UUID sampleUserId;
    private UUID sampleProjectId;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();

        sampleUserId = UUID.randomUUID();
        sampleProjectId = UUID.randomUUID();

        sampleUser = User.builder()
                .id(sampleUserId)
                .name("Developer")
                .email("dev@shipit.dev")
                .role(Role.ROLE_USER)
                .build();

        samplePrincipal = UserPrincipal.create(sampleUser, "encoded-pwd");

        HandlerMethodArgumentResolver argumentResolver = new HandlerMethodArgumentResolver() {
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
                .setCustomArgumentResolvers(argumentResolver)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("GET /api/v1/projects/{projectId}/env - Success returns list of masked variables")
    void getEnvironmentVariables_Success() throws Exception {
        EnvironmentVariableResponse var1 = EnvironmentVariableResponse.builder()
                .id(UUID.randomUUID())
                .key("PORT")
                .value("8080")
                .isSecret(false)
                .createdAt(Instant.now())
                .build();

        EnvironmentVariableResponse var2 = EnvironmentVariableResponse.builder()
                .id(UUID.randomUUID())
                .key("DATABASE_PASSWORD")
                .value("********")
                .isSecret(true)
                .createdAt(Instant.now())
                .build();

        when(environmentVariableService.getProjectEnvironmentVariables(sampleUserId, sampleProjectId))
                .thenReturn(List.of(var1, var2));

        mockMvc.perform(get("/api/v1/projects/{projectId}/env", sampleProjectId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data", hasSize(2)))
                .andExpect(jsonPath("$.data[0].key").value("PORT"))
                .andExpect(jsonPath("$.data[0].value").value("8080"))
                .andExpect(jsonPath("$.data[0].isSecret").value(false))
                .andExpect(jsonPath("$.data[1].key").value("DATABASE_PASSWORD"))
                .andExpect(jsonPath("$.data[1].value").value("********"))
                .andExpect(jsonPath("$.data[1].isSecret").value(true));
    }

    @Test
    @DisplayName("GET /api/v1/projects/{projectId}/env/{key} - Success returns single masked variable")
    void getEnvironmentVariable_Success() throws Exception {
        EnvironmentVariableResponse response = EnvironmentVariableResponse.builder()
                .id(UUID.randomUUID())
                .key("JWT_SECRET")
                .value("********")
                .isSecret(true)
                .createdAt(Instant.now())
                .build();

        when(environmentVariableService.getProjectEnvironmentVariable(sampleUserId, sampleProjectId, "JWT_SECRET"))
                .thenReturn(response);

        mockMvc.perform(get("/api/v1/projects/{projectId}/env/{key}", sampleProjectId, "JWT_SECRET"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.key").value("JWT_SECRET"))
                .andExpect(jsonPath("$.data.value").value("********"))
                .andExpect(jsonPath("$.data.isSecret").value(true));
    }

    @Test
    @DisplayName("GET /api/v1/projects/{projectId}/env/{key} - Not found returns 404 Not Found")
    void getEnvironmentVariable_NotFound() throws Exception {
        when(environmentVariableService.getProjectEnvironmentVariable(sampleUserId, sampleProjectId, "UNKNOWN"))
                .thenThrow(new ResourceNotFoundException("Environment variable not found with key: UNKNOWN"));

        mockMvc.perform(get("/api/v1/projects/{projectId}/env/{key}", sampleProjectId, "UNKNOWN"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    @DisplayName("POST /api/v1/projects/{projectId}/env - Success single upsert returns 200 OK")
    void upsertEnvironmentVariable_Success() throws Exception {
        EnvironmentVariableRequest request = EnvironmentVariableRequest.builder()
                .key("DB_USER")
                .value("postgres")
                .isSecret(false)
                .build();

        EnvironmentVariableResponse response = EnvironmentVariableResponse.builder()
                .id(UUID.randomUUID())
                .key("DB_USER")
                .value("postgres")
                .isSecret(false)
                .createdAt(Instant.now())
                .build();

        when(environmentVariableService.upsertEnvironmentVariable(eq(sampleUserId), eq(sampleProjectId), any(EnvironmentVariableRequest.class)))
                .thenReturn(response);

        mockMvc.perform(post("/api/v1/projects/{projectId}/env", sampleProjectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Environment variable saved successfully"))
                .andExpect(jsonPath("$.data.key").value("DB_USER"))
                .andExpect(jsonPath("$.data.value").value("postgres"));
    }

    @Test
    @DisplayName("POST /api/v1/projects/{projectId}/env - Invalid key format returns 400 Bad Request")
    void upsertEnvironmentVariable_InvalidKey_Returns400() throws Exception {
        EnvironmentVariableRequest request = EnvironmentVariableRequest.builder()
                .key("invalid-key-lowercase")
                .value("value")
                .build();

        mockMvc.perform(post("/api/v1/projects/{projectId}/env", sampleProjectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));

        verify(environmentVariableService, never()).upsertEnvironmentVariable(any(), any(), any());
    }

    @Test
    @DisplayName("PUT /api/v1/projects/{projectId}/env/batch - Success batch upsert returns 200 OK")
    void batchUpsertEnvironmentVariables_PUT_Success() throws Exception {
        BatchEnvironmentVariablesRequest request = BatchEnvironmentVariablesRequest.builder()
                .variables(List.of(
                        EnvironmentVariableRequest.builder().key("KEY_A").value("valA").isSecret(false).build(),
                        EnvironmentVariableRequest.builder().key("KEY_B").value("valB").isSecret(true).build()
                ))
                .build();

        EnvironmentVariableResponse resA = EnvironmentVariableResponse.builder()
                .id(UUID.randomUUID()).key("KEY_A").value("valA").isSecret(false).build();
        EnvironmentVariableResponse resB = EnvironmentVariableResponse.builder()
                .id(UUID.randomUUID()).key("KEY_B").value("********").isSecret(true).build();

        when(environmentVariableService.batchUpsertEnvironmentVariables(eq(sampleUserId), eq(sampleProjectId), any(BatchEnvironmentVariablesRequest.class)))
                .thenReturn(List.of(resA, resB));

        mockMvc.perform(put("/api/v1/projects/{projectId}/env/batch", sampleProjectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Environment variables saved successfully"))
                .andExpect(jsonPath("$.data", hasSize(2)))
                .andExpect(jsonPath("$.data[0].key").value("KEY_A"))
                .andExpect(jsonPath("$.data[1].value").value("********"));
    }

    @Test
    @DisplayName("POST /api/v1/projects/{projectId}/env/batch - Success batch upsert via POST returns 200 OK")
    void batchUpsertEnvironmentVariables_POST_Success() throws Exception {
        BatchEnvironmentVariablesRequest request = BatchEnvironmentVariablesRequest.builder()
                .variables(List.of(
                        EnvironmentVariableRequest.builder().key("KEY_C").value("valC").isSecret(false).build()
                ))
                .build();

        EnvironmentVariableResponse resC = EnvironmentVariableResponse.builder()
                .id(UUID.randomUUID()).key("KEY_C").value("valC").isSecret(false).build();

        when(environmentVariableService.batchUpsertEnvironmentVariables(eq(sampleUserId), eq(sampleProjectId), any(BatchEnvironmentVariablesRequest.class)))
                .thenReturn(List.of(resC));

        mockMvc.perform(post("/api/v1/projects/{projectId}/env/batch", sampleProjectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].key").value("KEY_C"));
    }

    @Test
    @DisplayName("PUT /api/v1/projects/{projectId}/env/batch - Empty list returns 400 Bad Request")
    void batchUpsertEnvironmentVariables_EmptyList_Returns400() throws Exception {
        BatchEnvironmentVariablesRequest request = BatchEnvironmentVariablesRequest.builder()
                .variables(Collections.emptyList())
                .build();

        mockMvc.perform(put("/api/v1/projects/{projectId}/env/batch", sampleProjectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));

        verify(environmentVariableService, never()).batchUpsertEnvironmentVariables(any(), any(), any());
    }

    @Test
    @DisplayName("DELETE /api/v1/projects/{projectId}/env/{key} - Success returns 200 OK with message")
    void deleteEnvironmentVariable_Success() throws Exception {
        doNothing().when(environmentVariableService).deleteEnvironmentVariable(sampleUserId, sampleProjectId, "PORT");

        mockMvc.perform(delete("/api/v1/projects/{projectId}/env/{key}", sampleProjectId, "PORT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Environment variable deleted successfully"));

        verify(environmentVariableService).deleteEnvironmentVariable(sampleUserId, sampleProjectId, "PORT");
    }

    @Test
    @DisplayName("DELETE /api/v1/projects/{projectId}/env/{key} - Not found returns 404 Not Found")
    void deleteEnvironmentVariable_NotFound() throws Exception {
        doThrow(new ResourceNotFoundException("Environment variable not found with key: UNKNOWN"))
                .when(environmentVariableService).deleteEnvironmentVariable(sampleUserId, sampleProjectId, "UNKNOWN");

        mockMvc.perform(delete("/api/v1/projects/{projectId}/env/{key}", sampleProjectId, "UNKNOWN"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }
}
