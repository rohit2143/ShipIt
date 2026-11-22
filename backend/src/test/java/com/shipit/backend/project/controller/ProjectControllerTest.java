package com.shipit.backend.project.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shipit.backend.auth.security.UserPrincipal;
import com.shipit.backend.common.exception.ConflictException;
import com.shipit.backend.common.exception.GlobalExceptionHandler;
import com.shipit.backend.common.exception.ResourceNotFoundException;
import com.shipit.backend.project.dto.CreateProjectRequest;
import com.shipit.backend.project.dto.ProjectResponse;
import com.shipit.backend.project.dto.ProjectSummaryResponse;
import com.shipit.backend.project.dto.UpdateProjectRequest;
import com.shipit.backend.project.entity.ProjectStatus;
import com.shipit.backend.project.service.ProjectService;
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
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class ProjectControllerTest {

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @Mock
    private ProjectService projectService;

    @InjectMocks
    private ProjectController projectController;

    private User sampleUser;
    private UserPrincipal samplePrincipal;
    private UUID sampleUserId;
    private UUID sampleProjectId;
    private UUID sampleRepoId;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();

        sampleUserId = UUID.randomUUID();
        sampleProjectId = UUID.randomUUID();
        sampleRepoId = UUID.randomUUID();

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

        mockMvc = MockMvcBuilders.standaloneSetup(projectController)
                .setCustomArgumentResolvers(argumentResolver)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("POST /api/v1/projects - Success returns 201 Created")
    void createProject_Success() throws Exception {
        CreateProjectRequest request = CreateProjectRequest.builder()
                .repositoryId(sampleRepoId)
                .name("My App")
                .port(8080)
                .build();

        ProjectResponse response = ProjectResponse.builder()
                .id(sampleProjectId)
                .name("My App")
                .deploymentBranch("main")
                .port(8080)
                .autoDeploy(true)
                .status(ProjectStatus.ACTIVE)
                .createdAt(Instant.now())
                .build();

        when(projectService.createProject(eq(sampleUserId), any(CreateProjectRequest.class)))
                .thenReturn(response);

        mockMvc.perform(post("/api/v1/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Project created successfully"))
                .andExpect(jsonPath("$.data.id").value(sampleProjectId.toString()))
                .andExpect(jsonPath("$.data.name").value("My App"))
                .andExpect(jsonPath("$.data.port").value(8080));
    }

    @Test
    @DisplayName("POST /api/v1/projects - Validation failure on empty name returns 400 Bad Request")
    void createProject_EmptyName_Returns400() throws Exception {
        CreateProjectRequest request = CreateProjectRequest.builder()
                .repositoryId(sampleRepoId)
                .name("")
                .build();

        mockMvc.perform(post("/api/v1/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));

        verify(projectService, never()).createProject(any(), any());
    }

    @Test
    @DisplayName("POST /api/v1/projects - Validation failure on invalid port returns 400 Bad Request")
    void createProject_InvalidPort_Returns400() throws Exception {
        CreateProjectRequest request = CreateProjectRequest.builder()
                .repositoryId(sampleRepoId)
                .name("Valid Name")
                .port(70000)
                .build();

        mockMvc.perform(post("/api/v1/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));

        verify(projectService, never()).createProject(any(), any());
    }

    @Test
    @DisplayName("POST /api/v1/projects - Conflict on duplicate name returns 409 Conflict")
    void createProject_DuplicateName_Returns409() throws Exception {
        CreateProjectRequest request = CreateProjectRequest.builder()
                .repositoryId(sampleRepoId)
                .name("Existing App")
                .build();

        when(projectService.createProject(eq(sampleUserId), any(CreateProjectRequest.class)))
                .thenThrow(new ConflictException("Project with name 'Existing App' already exists for this user", "PROJECT_NAME_EXISTS"));

        mockMvc.perform(post("/api/v1/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("PROJECT_NAME_EXISTS"));
    }

    @Test
    @DisplayName("POST /api/v1/projects - Repository not found returns 404 Not Found")
    void createProject_RepoNotFound_Returns404() throws Exception {
        CreateProjectRequest request = CreateProjectRequest.builder()
                .repositoryId(sampleRepoId)
                .name("App")
                .build();

        when(projectService.createProject(eq(sampleUserId), any(CreateProjectRequest.class)))
                .thenThrow(new ResourceNotFoundException("Repository not found or access denied"));

        mockMvc.perform(post("/api/v1/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    @DisplayName("GET /api/v1/projects - Success returns 200 OK with list of projects")
    void getUserProjects_Success() throws Exception {
        ProjectSummaryResponse summary = ProjectSummaryResponse.builder()
                .id(sampleProjectId)
                .name("My App")
                .deploymentBranch("main")
                .port(8080)
                .autoDeploy(true)
                .status(ProjectStatus.ACTIVE)
                .repositoryFullName("dev/my-app")
                .build();

        when(projectService.getUserProjects(sampleUserId)).thenReturn(List.of(summary));

        mockMvc.perform(get("/api/v1/projects"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].id").value(sampleProjectId.toString()))
                .andExpect(jsonPath("$.data[0].name").value("My App"))
                .andExpect(jsonPath("$.data[0].repositoryFullName").value("dev/my-app"));
    }

    @Test
    @DisplayName("GET /api/v1/projects/{id} - Success returns 200 OK with project details")
    void getProject_Success() throws Exception {
        ProjectResponse response = ProjectResponse.builder()
                .id(sampleProjectId)
                .name("My App")
                .deploymentBranch("main")
                .port(8080)
                .autoDeploy(true)
                .status(ProjectStatus.ACTIVE)
                .environmentVariablesCount(3)
                .build();

        when(projectService.getProject(sampleUserId, sampleProjectId)).thenReturn(response);

        mockMvc.perform(get("/api/v1/projects/{id}", sampleProjectId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(sampleProjectId.toString()))
                .andExpect(jsonPath("$.data.name").value("My App"))
                .andExpect(jsonPath("$.data.environmentVariablesCount").value(3));
    }

    @Test
    @DisplayName("GET /api/v1/projects/{id} - Not found returns 404 Not Found")
    void getProject_NotFound() throws Exception {
        when(projectService.getProject(sampleUserId, sampleProjectId))
                .thenThrow(new ResourceNotFoundException("Project not found with id: " + sampleProjectId));

        mockMvc.perform(get("/api/v1/projects/{id}", sampleProjectId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    @DisplayName("PUT /api/v1/projects/{id} - Success returns 200 OK with updated project")
    void updateProject_Success() throws Exception {
        UpdateProjectRequest request = UpdateProjectRequest.builder()
                .name("Updated App")
                .port(9090)
                .autoDeploy(false)
                .build();

        ProjectResponse response = ProjectResponse.builder()
                .id(sampleProjectId)
                .name("Updated App")
                .port(9090)
                .autoDeploy(false)
                .status(ProjectStatus.ACTIVE)
                .build();

        when(projectService.updateProject(eq(sampleUserId), eq(sampleProjectId), any(UpdateProjectRequest.class)))
                .thenReturn(response);

        mockMvc.perform(put("/api/v1/projects/{id}", sampleProjectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Project updated successfully"))
                .andExpect(jsonPath("$.data.name").value("Updated App"))
                .andExpect(jsonPath("$.data.port").value(9090));
    }

    @Test
    @DisplayName("PUT /api/v1/projects/{id} - Invalid port returns 400 Bad Request")
    void updateProject_InvalidPort_Returns400() throws Exception {
        UpdateProjectRequest request = UpdateProjectRequest.builder()
                .port(0)
                .build();

        mockMvc.perform(put("/api/v1/projects/{id}", sampleProjectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));

        verify(projectService, never()).updateProject(any(), any(), any());
    }

    @Test
    @DisplayName("DELETE /api/v1/projects/{id} - Success returns 200 OK with message")
    void deleteProject_Success() throws Exception {
        doNothing().when(projectService).deleteProject(sampleUserId, sampleProjectId);

        mockMvc.perform(delete("/api/v1/projects/{id}", sampleProjectId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Project deleted successfully"));

        verify(projectService).deleteProject(sampleUserId, sampleProjectId);
    }

    @Test
    @DisplayName("DELETE /api/v1/projects/{id} - Not found returns 404 Not Found")
    void deleteProject_NotFound() throws Exception {
        doThrow(new ResourceNotFoundException("Project not found with id: " + sampleProjectId))
                .when(projectService).deleteProject(sampleUserId, sampleProjectId);

        mockMvc.perform(delete("/api/v1/projects/{id}", sampleProjectId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }
}
