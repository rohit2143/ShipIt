package com.shipit.backend.project.service;

import com.shipit.backend.common.exception.BadRequestException;
import com.shipit.backend.common.exception.ResourceNotFoundException;
import com.shipit.backend.project.dto.BatchEnvironmentVariablesRequest;
import com.shipit.backend.project.dto.EnvironmentVariableRequest;
import com.shipit.backend.project.dto.EnvironmentVariableResponse;
import com.shipit.backend.project.entity.Project;
import com.shipit.backend.project.entity.ProjectEnvironmentVariable;
import com.shipit.backend.project.repository.ProjectEnvironmentVariableRepository;
import com.shipit.backend.project.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProjectEnvironmentVariableService {

    private final ProjectEnvironmentVariableRepository environmentVariableRepository;
    private final ProjectRepository projectRepository;
    private final EncryptionService encryptionService;

    @Transactional(readOnly = true)
    public List<EnvironmentVariableResponse> getProjectEnvironmentVariables(UUID userId, UUID projectId) {
        log.debug("Fetching environment variables for project id: {} user: {}", projectId, userId);
        verifyProjectOwnership(userId, projectId);

        return environmentVariableRepository.findAllByProjectId(projectId).stream()
                .map(EnvironmentVariableResponse::fromEntity)
                .toList();
    }

    @Transactional(readOnly = true)
    public EnvironmentVariableResponse getProjectEnvironmentVariable(UUID userId, UUID projectId, String key) {
        log.debug("Fetching environment variable key: {} for project id: {} user: {}", key, projectId, userId);
        verifyProjectOwnership(userId, projectId);

        ProjectEnvironmentVariable variable = environmentVariableRepository.findByProjectIdAndKey(projectId, key)
                .orElseThrow(() -> new ResourceNotFoundException("Environment variable not found with key: " + key));

        return EnvironmentVariableResponse.fromEntity(variable);
    }

    @Transactional
    public EnvironmentVariableResponse upsertEnvironmentVariable(UUID userId, UUID projectId, EnvironmentVariableRequest request) {
        log.info("Upserting environment variable key: {} for project id: {} user: {}", request.getKey(), projectId, userId);
        Project project = verifyProjectOwnership(userId, projectId);

        String key = request.getKey().trim();
        String rawValue = request.getValue();
        boolean isSecret = Boolean.TRUE.equals(request.getIsSecret());

        String storageValue = isSecret
                ? encryptionService.encrypt(projectId, key, rawValue)
                : rawValue;

        ProjectEnvironmentVariable variable = environmentVariableRepository.findByProjectIdAndKey(projectId, key)
                .map(existing -> {
                    existing.setValue(storageValue);
                    existing.setSecret(isSecret);
                    return existing;
                })
                .orElseGet(() -> ProjectEnvironmentVariable.builder()
                        .project(project)
                        .key(key)
                        .value(storageValue)
                        .isSecret(isSecret)
                        .build());

        ProjectEnvironmentVariable saved = environmentVariableRepository.save(variable);
        log.info("Successfully persisted environment variable key: {} (isSecret: {}) for project: {}", key, isSecret, projectId);

        return EnvironmentVariableResponse.fromEntity(saved);
    }

    @Transactional
    public List<EnvironmentVariableResponse> batchUpsertEnvironmentVariables(
            UUID userId, UUID projectId, BatchEnvironmentVariablesRequest request
    ) {
        log.info("Batch upserting {} environment variables for project id: {} user: {}",
                request.getVariables().size(), projectId, userId);
        Project project = verifyProjectOwnership(userId, projectId);

        Set<String> seenKeys = new HashSet<>();
        for (EnvironmentVariableRequest varReq : request.getVariables()) {
            if (!seenKeys.add(varReq.getKey())) {
                throw new BadRequestException("Duplicate environment variable key in batch: " + varReq.getKey(), "DUPLICATE_ENV_VAR_KEY");
            }
        }

        List<ProjectEnvironmentVariable> savedVariables = new ArrayList<>();
        for (EnvironmentVariableRequest varReq : request.getVariables()) {
            String key = varReq.getKey().trim();
            String rawValue = varReq.getValue();
            boolean isSecret = Boolean.TRUE.equals(varReq.getIsSecret());

            String storageValue = isSecret
                    ? encryptionService.encrypt(projectId, key, rawValue)
                    : rawValue;

            ProjectEnvironmentVariable variable = environmentVariableRepository.findByProjectIdAndKey(projectId, key)
                    .map(existing -> {
                        existing.setValue(storageValue);
                        existing.setSecret(isSecret);
                        return existing;
                    })
                    .orElseGet(() -> ProjectEnvironmentVariable.builder()
                            .project(project)
                            .key(key)
                            .value(storageValue)
                            .isSecret(isSecret)
                            .build());

            savedVariables.add(environmentVariableRepository.save(variable));
        }

        log.info("Successfully batch upserted {} environment variables for project: {}", savedVariables.size(), projectId);
        return savedVariables.stream()
                .map(EnvironmentVariableResponse::fromEntity)
                .toList();
    }

    @Transactional
    public void deleteEnvironmentVariable(UUID userId, UUID projectId, String key) {
        log.info("Deleting environment variable key: {} for project id: {} user: {}", key, projectId, userId);
        verifyProjectOwnership(userId, projectId);

        ProjectEnvironmentVariable variable = environmentVariableRepository.findByProjectIdAndKey(projectId, key)
                .orElseThrow(() -> new ResourceNotFoundException("Environment variable not found with key: " + key));

        environmentVariableRepository.delete(variable);
        log.info("Successfully deleted environment variable key: {} for project: {}", key, projectId);
    }

    /**
     * Internal method strictly for deployment execution contexts.
     * Decrypts all secret environment variables and returns a plaintext map for Docker build/run injection.
     * Never expose this endpoint over public REST APIs.
     *
     * @param projectId Project UUID
     * @return Map of plaintext key-value pairs
     */
    @Transactional(readOnly = true)
    public Map<String, String> getDecryptedEnvironmentVariablesForDeployment(UUID projectId) {
        log.debug("Decrypting environment variables for deployment execution on project id: {}", projectId);

        if (!projectRepository.existsById(projectId)) {
            throw new ResourceNotFoundException("Project not found with id: " + projectId);
        }

        List<ProjectEnvironmentVariable> variables = environmentVariableRepository.findAllByProjectId(projectId);
        Map<String, String> decryptedMap = new LinkedHashMap<>();

        for (ProjectEnvironmentVariable variable : variables) {
            String value;
            if (variable.isSecret()) {
                value = encryptionService.decrypt(projectId, variable.getKey(), variable.getValue());
            } else {
                value = variable.getValue();
            }
            decryptedMap.put(variable.getKey(), value);
        }

        return Collections.unmodifiableMap(decryptedMap);
    }

    private Project verifyProjectOwnership(UUID userId, UUID projectId) {
        return projectRepository.findByIdAndUserId(projectId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Project not found with id: " + projectId));
    }
}
