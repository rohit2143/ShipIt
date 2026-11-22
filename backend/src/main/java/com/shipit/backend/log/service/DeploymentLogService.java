package com.shipit.backend.log.service;

import com.shipit.backend.build.logging.LogSource;
import com.shipit.backend.common.exception.ResourceNotFoundException;
import com.shipit.backend.deployment.entity.Deployment;
import com.shipit.backend.deployment.entity.DeploymentStatus;
import com.shipit.backend.deployment.repository.DeploymentRepository;
import com.shipit.backend.log.broadcaster.DeploymentLogBroadcaster;
import com.shipit.backend.log.dto.DeploymentLogDto;
import com.shipit.backend.log.dto.DeploymentLogsPageResponse;
import com.shipit.backend.log.entity.DeploymentLog;
import com.shipit.backend.log.entity.DeploymentStage;
import com.shipit.backend.log.repository.DeploymentLogRepository;
import com.shipit.backend.log.security.LogRedactor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Service orchestrating deployment log sanitization, database persistence,
 * historical retrieval, and real-time streaming dispatch.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeploymentLogService {

    private final DeploymentLogRepository logRepository;
    private final DeploymentRepository deploymentRepository;
    private final LogRedactor logRedactor;
    private final DeploymentLogBroadcaster broadcaster;

    /**
     * In-memory atomic sequence counter per active deployment to avoid DB roundtrips for sequence generation.
     */
    private final Map<UUID, AtomicLong> sequenceTracker = new ConcurrentHashMap<>();

    /**
     * Sanitizes, persists, and broadcasts an individual log line for a deployment.
     */
    @Transactional
    public DeploymentLogDto appendLog(
            UUID deploymentId,
            DeploymentStage stage,
            LogSource source,
            String rawMessage,
            Collection<String> sensitiveValues
    ) {
        if (deploymentId == null || rawMessage == null) {
            return null;
        }

        Deployment deploymentRef = deploymentRepository.getReferenceById(deploymentId);
        long sequenceNumber = getNextSequenceNumber(deploymentId);

        String redactedMessage = logRedactor.redact(rawMessage, sensitiveValues);

        DeploymentLog entity = DeploymentLog.builder()
                .deployment(deploymentRef)
                .sequenceNumber(sequenceNumber)
                .stage(stage != null ? stage : DeploymentStage.SYSTEM)
                .source(source != null ? source : LogSource.SYSTEM)
                .message(redactedMessage)
                .timestamp(Instant.now())
                .build();

        DeploymentLog saved = logRepository.save(entity);
        DeploymentLogDto dto = DeploymentLogDto.fromEntity(saved);

        // Broadcast to live streaming listeners
        broadcaster.broadcastLog(deploymentId, dto);

        return dto;
    }

    /**
     * Appends a batch of log lines for a deployment.
     */
    @Transactional
    public List<DeploymentLogDto> appendLogs(
            UUID deploymentId,
            DeploymentStage stage,
            LogSource source,
            List<String> rawMessages,
            Collection<String> sensitiveValues
    ) {
        if (deploymentId == null || rawMessages == null || rawMessages.isEmpty()) {
            return Collections.emptyList();
        }

        Deployment deploymentRef = deploymentRepository.getReferenceById(deploymentId);
        List<DeploymentLog> entities = new ArrayList<>(rawMessages.size());
        List<DeploymentLogDto> dtos = new ArrayList<>(rawMessages.size());

        for (String raw : rawMessages) {
            if (raw == null) {
                continue;
            }
            long sequenceNumber = getNextSequenceNumber(deploymentId);
            String redacted = logRedactor.redact(raw, sensitiveValues);

            DeploymentLog entity = DeploymentLog.builder()
                    .deployment(deploymentRef)
                    .sequenceNumber(sequenceNumber)
                    .stage(stage != null ? stage : DeploymentStage.SYSTEM)
                    .source(source != null ? source : LogSource.SYSTEM)
                    .message(redacted)
                    .timestamp(Instant.now())
                    .build();

            entities.add(entity);
        }

        List<DeploymentLog> savedEntities = logRepository.saveAll(entities);
        for (DeploymentLog saved : savedEntities) {
            DeploymentLogDto dto = DeploymentLogDto.fromEntity(saved);
            dtos.add(dto);
            broadcaster.broadcastLog(deploymentId, dto);
        }

        return dtos;
    }

    /**
     * Records and broadcasts a deployment state transition.
     */
    public void recordStatusChange(UUID deploymentId, DeploymentStatus status, String message) {
        if (deploymentId == null || status == null) {
            return;
        }

        broadcaster.broadcastStatus(deploymentId, status.name(), message);

        if (status.isTerminal()) {
            broadcaster.broadcastComplete(deploymentId, status.name());
            sequenceTracker.remove(deploymentId);
        }
    }

    /**
     * Retrieves paginated logs for a deployment, verifying that the requesting user owns the deployment.
     */
    @Transactional(readOnly = true)
    public DeploymentLogsPageResponse getLogs(
            UUID userId,
            UUID deploymentId,
            DeploymentStage stage,
            Long sinceSequenceNumber,
            int page,
            int size
    ) {
        if (userId == null) {
            throw new IllegalArgumentException("userId must not be null");
        }
        if (deploymentId == null) {
            throw new IllegalArgumentException("deploymentId must not be null");
        }

        verifyDeploymentAccess(deploymentId, userId);

        int safePage = Math.max(0, page);
        int safeSize = (size <= 0 || size > 500) ? 100 : size;
        Pageable pageable = PageRequest.of(safePage, safeSize);

        List<DeploymentLog> logs;
        if (stage != null) {
            logs = logRepository.findByDeploymentIdAndStageOrderBySequenceNumberAsc(deploymentId, stage, pageable);
        } else if (sinceSequenceNumber != null) {
            logs = logRepository.findByDeploymentIdAndSequenceNumberGreaterThanOrderBySequenceNumberAsc(
                    deploymentId, sinceSequenceNumber, pageable
            );
        } else {
            logs = logRepository.findByDeploymentIdOrderBySequenceNumberAsc(deploymentId, pageable);
        }

        List<DeploymentLogDto> dtos = logs.stream()
                .map(DeploymentLogDto::fromEntity)
                .toList();

        long totalLines = (stage != null)
                ? logRepository.countByDeploymentIdAndStage(deploymentId, stage)
                : logRepository.countByDeploymentId(deploymentId);

        long maxSeq = logRepository.findMaxSequenceNumberByDeploymentId(deploymentId);
        boolean hasMore = (long) (safePage + 1) * safeSize < totalLines;

        return DeploymentLogsPageResponse.builder()
                .deploymentId(deploymentId)
                .logs(dtos)
                .totalLines(totalLines)
                .maxSequenceNumber(maxSeq)
                .page(safePage)
                .size(safeSize)
                .hasMore(hasMore)
                .build();
    }

    /**
     * Retrieves full logs as a formatted plain-text block for log download.
     */
    @Transactional(readOnly = true)
    public String getFullLogsAsString(UUID userId, UUID deploymentId) {
        if (userId == null || deploymentId == null) {
            throw new IllegalArgumentException("userId and deploymentId must not be null");
        }

        Deployment deployment = verifyDeploymentAccess(deploymentId, userId);
        List<DeploymentLog> logs = logRepository.findByDeploymentIdOrderBySequenceNumberAsc(deploymentId);

        if (logs.isEmpty()) {
            if (deployment.getStatus() == DeploymentStatus.QUEUED) {
                return "Deployment is currently queued. Execution logs are not yet available.";
            }
            return String.format("No logs recorded for deployment %s.", deploymentId);
        }

        StringBuilder sb = new StringBuilder();
        for (DeploymentLog entry : logs) {
            sb.append(entry.toFormattedString()).append(System.lineSeparator());
        }
        return sb.toString();
    }

    /**
     * Connects an authenticated user to the live SSE stream for a deployment.
     */
    @Transactional(readOnly = true)
    public SseEmitter subscribeToStream(UUID userId, UUID deploymentId, Long sinceSequenceNumber) {
        if (userId == null || deploymentId == null) {
            throw new IllegalArgumentException("userId and deploymentId must not be null");
        }

        Deployment deployment = verifyDeploymentAccess(deploymentId, userId);

        // If deployment is already terminal (finished), replay DB logs and complete immediately
        if (deployment.getStatus().isTerminal()) {
            SseEmitter terminalEmitter = new SseEmitter(DeploymentLogBroadcaster.DEFAULT_SSE_TIMEOUT_MS);
            try {
                terminalEmitter.send(SseEmitter.event()
                        .name("connected")
                        .data(Map.of(
                                "deploymentId", deploymentId.toString(),
                                "status", deployment.getStatus().name(),
                                "message", "Deployment is in terminal state " + deployment.getStatus()
                        )));

                List<DeploymentLog> logs = (sinceSequenceNumber != null)
                        ? logRepository.findByDeploymentIdAndSequenceNumberGreaterThanOrderBySequenceNumberAsc(
                        deploymentId, sinceSequenceNumber, PageRequest.of(0, 1000))
                        : logRepository.findByDeploymentIdOrderBySequenceNumberAsc(deploymentId);

                for (DeploymentLog l : logs) {
                    terminalEmitter.send(SseEmitter.event()
                            .name("log")
                            .data(DeploymentLogDto.fromEntity(l)));
                }

                terminalEmitter.send(SseEmitter.event()
                        .name("complete")
                        .data(Map.of(
                                "deploymentId", deploymentId.toString(),
                                "finalStatus", deployment.getStatus().name()
                        )));

                terminalEmitter.complete();
            } catch (IOException e) {
                log.debug("Error streaming terminal logs to client: {}", e.getMessage());
            }
            return terminalEmitter;
        }

        return broadcaster.subscribe(deploymentId, sinceSequenceNumber);
    }

    /**
     * Purges logs and caches for a deployment.
     */
    @Transactional
    public void cleanupDeploymentLogs(UUID deploymentId) {
        if (deploymentId == null) {
            return;
        }
        logRepository.deleteByDeploymentId(deploymentId);
        broadcaster.cleanupDeployment(deploymentId);
        sequenceTracker.remove(deploymentId);
    }

    private Deployment verifyDeploymentAccess(UUID deploymentId, UUID userId) {
        return deploymentRepository.findByIdAndUserId(deploymentId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Deployment not found with id: " + deploymentId));
    }

    private long getNextSequenceNumber(UUID deploymentId) {
        AtomicLong counter = sequenceTracker.computeIfAbsent(deploymentId, id -> {
            long currentMax = logRepository.findMaxSequenceNumberByDeploymentId(id);
            return new AtomicLong(currentMax);
        });
        return counter.incrementAndGet();
    }
}
