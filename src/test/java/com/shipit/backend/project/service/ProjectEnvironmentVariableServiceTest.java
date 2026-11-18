package com.shipit.backend.project.service;

import com.shipit.backend.common.exception.BadRequestException;
import com.shipit.backend.common.exception.ResourceNotFoundException;
import com.shipit.backend.project.dto.BatchEnvironmentVariablesRequest;
import com.shipit.backend.project.dto.EnvironmentVariableRequest;
import com.shipit.backend.project.dto.EnvironmentVariableResponse;
import com.shipit.backend.project.entity.Project;
import com.shipit.backend.project.entity.ProjectEnvironmentVariable;
import com.shipit.backend.project.entity.ProjectStatus;
import com.shipit.backend.project.repository.ProjectEnvironmentVariableRepository;
import com.shipit.backend.project.repository.ProjectRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProjectEnvironmentVariableServiceTest {

    @Mock
    private ProjectEnvironmentVariableRepository environmentVariableRepository;

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private EncryptionService encryptionService;

    @InjectMocks
    private ProjectEnvironmentVariableService variableService;

    private UUID userId;
    private UUID projectId;
    private Project testProject;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        projectId = UUID.randomUUID();

        testProject = Project.builder()
                .id(projectId)
                .name("My App")
                .deploymentBranch("main")
                .status(ProjectStatus.ACTIVE)
                .build();
    }

    @Test
    @DisplayName("getProjectEnvironmentVariables - Masks secret values with ******** while exposing plaintext")
    void getProjectEnvironmentVariables_Success_MasksSecrets() {
        ProjectEnvironmentVariable v1 = ProjectEnvironmentVariable.builder()
                .id(UUID.randomUUID())
                .project(testProject)
                .key("PORT")
                .value("8080")
                .isSecret(false)
                .build();

        ProjectEnvironmentVariable v2 = ProjectEnvironmentVariable.builder()
                .id(UUID.randomUUID())
                .project(testProject)
                .key("DB_PASSWORD")
                .value("v1:Base64CiphertextEnvelope==")
                .isSecret(true)
                .build();

        when(projectRepository.findByIdAndUserId(projectId, userId)).thenReturn(Optional.of(testProject));
        when(environmentVariableRepository.findAllByProjectId(projectId)).thenReturn(List.of(v1, v2));

        List<EnvironmentVariableResponse> responses = variableService.getProjectEnvironmentVariables(userId, projectId);

        assertThat(responses).hasSize(2);

        EnvironmentVariableResponse r1 = responses.get(0);
        assertThat(r1.getKey()).isEqualTo("PORT");
        assertThat(r1.getValue()).isEqualTo("8080");
        assertThat(r1.isSecret()).isFalse();

        EnvironmentVariableResponse r2 = responses.get(1);
        assertThat(r2.getKey()).isEqualTo("DB_PASSWORD");
        assertThat(r2.getValue()).isEqualTo(EnvironmentVariableResponse.MASKED_VALUE);
        assertThat(r2.isSecret()).isTrue();
    }

    @Test
    @DisplayName("getProjectEnvironmentVariable - Returns masked value for secret variable")
    void getProjectEnvironmentVariable_Secret_ReturnsMasked() {
        ProjectEnvironmentVariable v = ProjectEnvironmentVariable.builder()
                .id(UUID.randomUUID())
                .project(testProject)
                .key("API_SECRET")
                .value("v1:EncryptedValue==")
                .isSecret(true)
                .build();

        when(projectRepository.findByIdAndUserId(projectId, userId)).thenReturn(Optional.of(testProject));
        when(environmentVariableRepository.findByProjectIdAndKey(projectId, "API_SECRET")).thenReturn(Optional.of(v));

        EnvironmentVariableResponse response = variableService.getProjectEnvironmentVariable(userId, projectId, "API_SECRET");

        assertThat(response).isNotNull();
        assertThat(response.getKey()).isEqualTo("API_SECRET");
        assertThat(response.getValue()).isEqualTo(EnvironmentVariableResponse.MASKED_VALUE);
        assertThat(response.isSecret()).isTrue();
    }

    @Test
    @DisplayName("getProjectEnvironmentVariable - Throws ResourceNotFoundException when not found")
    void getProjectEnvironmentVariable_NotFound_ThrowsException() {
        when(projectRepository.findByIdAndUserId(projectId, userId)).thenReturn(Optional.of(testProject));
        when(environmentVariableRepository.findByProjectIdAndKey(projectId, "UNKNOWN")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> variableService.getProjectEnvironmentVariable(userId, projectId, "UNKNOWN"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Environment variable not found with key: UNKNOWN");
    }

    @Test
    @DisplayName("upsertEnvironmentVariable - Encrypts secret value at rest and returns masked response")
    void upsertEnvironmentVariable_NewSecret_EncryptsAtRest() {
        EnvironmentVariableRequest request = EnvironmentVariableRequest.builder()
                .key("JWT_SECRET")
                .value("rawSecretValue123")
                .isSecret(true)
                .build();

        when(projectRepository.findByIdAndUserId(projectId, userId)).thenReturn(Optional.of(testProject));
        when(encryptionService.encrypt(projectId, "JWT_SECRET", "rawSecretValue123"))
                .thenReturn("v1:EncryptedJwtSecretEnvelope==");
        when(environmentVariableRepository.findByProjectIdAndKey(projectId, "JWT_SECRET")).thenReturn(Optional.empty());
        when(environmentVariableRepository.save(any(ProjectEnvironmentVariable.class))).thenAnswer(i -> {
            ProjectEnvironmentVariable pev = i.getArgument(0);
            pev.setId(UUID.randomUUID());
            return pev;
        });

        EnvironmentVariableResponse response = variableService.upsertEnvironmentVariable(userId, projectId, request);

        assertThat(response).isNotNull();
        assertThat(response.getKey()).isEqualTo("JWT_SECRET");
        assertThat(response.getValue()).isEqualTo(EnvironmentVariableResponse.MASKED_VALUE);
        assertThat(response.isSecret()).isTrue();

        ArgumentCaptor<ProjectEnvironmentVariable> captor = ArgumentCaptor.forClass(ProjectEnvironmentVariable.class);
        verify(environmentVariableRepository).save(captor.capture());
        ProjectEnvironmentVariable saved = captor.getValue();
        assertThat(saved.getValue()).isEqualTo("v1:EncryptedJwtSecretEnvelope==");
        assertThat(saved.isSecret()).isTrue();
    }

    @Test
    @DisplayName("upsertEnvironmentVariable - Stores plaintext when isSecret is false")
    void upsertEnvironmentVariable_Plaintext_StoresRaw() {
        EnvironmentVariableRequest request = EnvironmentVariableRequest.builder()
                .key("LOG_LEVEL")
                .value("DEBUG")
                .isSecret(false)
                .build();

        when(projectRepository.findByIdAndUserId(projectId, userId)).thenReturn(Optional.of(testProject));
        when(environmentVariableRepository.findByProjectIdAndKey(projectId, "LOG_LEVEL")).thenReturn(Optional.empty());
        when(environmentVariableRepository.save(any(ProjectEnvironmentVariable.class))).thenAnswer(i -> i.getArgument(0));

        EnvironmentVariableResponse response = variableService.upsertEnvironmentVariable(userId, projectId, request);

        assertThat(response.getKey()).isEqualTo("LOG_LEVEL");
        assertThat(response.getValue()).isEqualTo("DEBUG");
        assertThat(response.isSecret()).isFalse();

        verify(encryptionService, never()).encrypt(any(), any(), any());
    }

    @Test
    @DisplayName("upsertEnvironmentVariable - Updates existing variable correctly")
    void upsertEnvironmentVariable_Existing_UpdatesValue() {
        ProjectEnvironmentVariable existing = ProjectEnvironmentVariable.builder()
                .id(UUID.randomUUID())
                .project(testProject)
                .key("PORT")
                .value("8080")
                .isSecret(false)
                .build();

        EnvironmentVariableRequest request = EnvironmentVariableRequest.builder()
                .key("PORT")
                .value("9000")
                .isSecret(false)
                .build();

        when(projectRepository.findByIdAndUserId(projectId, userId)).thenReturn(Optional.of(testProject));
        when(environmentVariableRepository.findByProjectIdAndKey(projectId, "PORT")).thenReturn(Optional.of(existing));
        when(environmentVariableRepository.save(existing)).thenReturn(existing);

        EnvironmentVariableResponse response = variableService.upsertEnvironmentVariable(userId, projectId, request);

        assertThat(response.getValue()).isEqualTo("9000");
        assertThat(existing.getValue()).isEqualTo("9000");
    }

    @Test
    @DisplayName("batchUpsertEnvironmentVariables - Successfully upserts multiple variables")
    void batchUpsertEnvironmentVariables_Success() {
        BatchEnvironmentVariablesRequest request = BatchEnvironmentVariablesRequest.builder()
                .variables(List.of(
                        EnvironmentVariableRequest.builder().key("NODE_ENV").value("production").isSecret(false).build(),
                        EnvironmentVariableRequest.builder().key("API_TOKEN").value("secretToken").isSecret(true).build()
                ))
                .build();

        when(projectRepository.findByIdAndUserId(projectId, userId)).thenReturn(Optional.of(testProject));
        when(encryptionService.encrypt(projectId, "API_TOKEN", "secretToken")).thenReturn("v1:EncryptedToken==");
        when(environmentVariableRepository.findByProjectIdAndKey(projectId, "NODE_ENV")).thenReturn(Optional.empty());
        when(environmentVariableRepository.findByProjectIdAndKey(projectId, "API_TOKEN")).thenReturn(Optional.empty());
        when(environmentVariableRepository.save(any(ProjectEnvironmentVariable.class))).thenAnswer(i -> i.getArgument(0));

        List<EnvironmentVariableResponse> responses = variableService.batchUpsertEnvironmentVariables(userId, projectId, request);

        assertThat(responses).hasSize(2);
        assertThat(responses.get(0).getKey()).isEqualTo("NODE_ENV");
        assertThat(responses.get(0).getValue()).isEqualTo("production");
        assertThat(responses.get(1).getKey()).isEqualTo("API_TOKEN");
        assertThat(responses.get(1).getValue()).isEqualTo(EnvironmentVariableResponse.MASKED_VALUE);

        verify(environmentVariableRepository, times(2)).save(any(ProjectEnvironmentVariable.class));
    }

    @Test
    @DisplayName("batchUpsertEnvironmentVariables - Duplicate keys in request throws BadRequestException")
    void batchUpsertEnvironmentVariables_DuplicateKeys_ThrowsBadRequestException() {
        BatchEnvironmentVariablesRequest request = BatchEnvironmentVariablesRequest.builder()
                .variables(List.of(
                        EnvironmentVariableRequest.builder().key("DB_NAME").value("shipit").build(),
                        EnvironmentVariableRequest.builder().key("DB_NAME").value("shipit_test").build()
                ))
                .build();

        when(projectRepository.findByIdAndUserId(projectId, userId)).thenReturn(Optional.of(testProject));

        assertThatThrownBy(() -> variableService.batchUpsertEnvironmentVariables(userId, projectId, request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Duplicate environment variable key in batch: DB_NAME");

        verify(environmentVariableRepository, never()).save(any());
    }

    @Test
    @DisplayName("deleteEnvironmentVariable - Successfully deletes variable")
    void deleteEnvironmentVariable_Success() {
        ProjectEnvironmentVariable variable = ProjectEnvironmentVariable.builder()
                .id(UUID.randomUUID())
                .project(testProject)
                .key("PORT")
                .value("8080")
                .build();

        when(projectRepository.findByIdAndUserId(projectId, userId)).thenReturn(Optional.of(testProject));
        when(environmentVariableRepository.findByProjectIdAndKey(projectId, "PORT")).thenReturn(Optional.of(variable));

        variableService.deleteEnvironmentVariable(userId, projectId, "PORT");

        verify(environmentVariableRepository).delete(variable);
    }

    @Test
    @DisplayName("deleteEnvironmentVariable - Throws ResourceNotFoundException when variable does not exist")
    void deleteEnvironmentVariable_NotFound_ThrowsException() {
        when(projectRepository.findByIdAndUserId(projectId, userId)).thenReturn(Optional.of(testProject));
        when(environmentVariableRepository.findByProjectIdAndKey(projectId, "UNKNOWN")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> variableService.deleteEnvironmentVariable(userId, projectId, "UNKNOWN"))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(environmentVariableRepository, never()).delete(any());
    }

    @Test
    @DisplayName("getDecryptedEnvironmentVariablesForDeployment - Decrypts secrets and leaves plaintext values for deployment execution")
    void getDecryptedEnvironmentVariablesForDeployment_DecryptsSecrets() {
        ProjectEnvironmentVariable v1 = ProjectEnvironmentVariable.builder()
                .id(UUID.randomUUID())
                .project(testProject)
                .key("PORT")
                .value("8080")
                .isSecret(false)
                .build();

        ProjectEnvironmentVariable v2 = ProjectEnvironmentVariable.builder()
                .id(UUID.randomUUID())
                .project(testProject)
                .key("DB_PASS")
                .value("v1:EncryptedPass==")
                .isSecret(true)
                .build();

        when(projectRepository.existsById(projectId)).thenReturn(true);
        when(environmentVariableRepository.findAllByProjectId(projectId)).thenReturn(List.of(v1, v2));
        when(encryptionService.decrypt(projectId, "DB_PASS", "v1:EncryptedPass==")).thenReturn("decryptedPassword123");

        Map<String, String> result = variableService.getDecryptedEnvironmentVariablesForDeployment(projectId);

        assertThat(result).hasSize(2);
        assertThat(result).containsEntry("PORT", "8080");
        assertThat(result).containsEntry("DB_PASS", "decryptedPassword123");
    }

    @Test
    @DisplayName("verifyProjectOwnership - Throws ResourceNotFoundException when project does not belong to user")
    void verifyProjectOwnership_NotFound_ThrowsException() {
        when(projectRepository.findByIdAndUserId(projectId, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> variableService.getProjectEnvironmentVariables(userId, projectId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Project not found with id: " + projectId);
    }
}
