package com.shipit.backend.log.broadcaster;

import com.shipit.backend.log.config.LogProperties;
import com.shipit.backend.log.dto.DeploymentLogDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * Thread-safe multi-client Server-Sent Events (SSE) broadcaster for live deployment logs.
 * <p>
 * Manages active web client subscriptions, replay ring-buffers for late-joining subscribers,
 * periodic keep-alive heartbeats, and graceful connection termination.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DeploymentLogBroadcaster {

    public static final long DEFAULT_SSE_TIMEOUT_MS = 30 * 60 * 1000L; // 30 minutes

    private final LogProperties logProperties;

    /**
     * Map of deploymentId -> Set of active SseEmitter subscribers.
     */
    private final Map<UUID, Set<SseEmitter>> subscriberMap = new ConcurrentHashMap<>();

    /**
     * In-memory replay ring-buffer of recent logs per deployment for late subscribers.
     */
    private final Map<UUID, Deque<DeploymentLogDto>> replayBufferMap = new ConcurrentHashMap<>();

    /**
     * Subscribes a client to the real-time log event stream of a deployment.
     *
     * @param deploymentId ID of the deployment.
     * @param sinceSequenceNumber Optional cursor: only replay logs strictly after this sequence number.
     * @return Initialized SseEmitter instance.
     */
    public SseEmitter subscribe(UUID deploymentId, Long sinceSequenceNumber) {
        if (deploymentId == null) {
            throw new IllegalArgumentException("deploymentId must not be null");
        }

        SseEmitter emitter = new SseEmitter(DEFAULT_SSE_TIMEOUT_MS);
        Set<SseEmitter> emitters = subscriberMap.computeIfAbsent(deploymentId, k -> new CopyOnWriteArraySet<>());
        emitters.add(emitter);

        Runnable cleanup = () -> removeEmitter(deploymentId, emitter);
        emitter.onCompletion(cleanup);
        emitter.onTimeout(cleanup);
        emitter.onError(e -> cleanup.run());

        // Send initial connected handshake event
        try {
            emitter.send(SseEmitter.event()
                    .name("connected")
                    .data(Map.of(
                            "deploymentId", deploymentId.toString(),
                            "message", "Subscribed to live deployment log stream"
                    )));
        } catch (IOException e) {
            log.debug("Failed to send connected handshake to client for deployment {}: {}", deploymentId, e.getMessage());
            removeEmitter(deploymentId, emitter);
            return emitter;
        }

        // Replay backlog of stored logs to the new client
        replayBacklog(deploymentId, emitter, sinceSequenceNumber);

        log.debug("Client subscribed to deployment {} stream (active listeners: {})", deploymentId, emitters.size());
        return emitter;
    }

    /**
     * Broadcasts a single log line to all active subscribers and caches it in the replay buffer.
     */
    public void broadcastLog(UUID deploymentId, DeploymentLogDto logEntry) {
        if (deploymentId == null || logEntry == null) {
            return;
        }

        // Cache in replay buffer
        int maxReplay = logProperties != null ? logProperties.getMaxReplayLines() : 1000;
        Deque<DeploymentLogDto> buffer = replayBufferMap.computeIfAbsent(deploymentId, k -> new ArrayDeque<>());
        synchronized (buffer) {
            if (buffer.size() >= maxReplay) {
                buffer.pollFirst();
            }
            buffer.addLast(logEntry);
        }

        // Dispatch to active listeners
        Set<SseEmitter> emitters = subscriberMap.get(deploymentId);
        if (emitters == null || emitters.isEmpty()) {
            return;
        }

        List<SseEmitter> deadEmitters = new ArrayList<>();
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event()
                        .name("log")
                        .data(logEntry));
            } catch (Exception e) {
                deadEmitters.add(emitter);
            }
        }

        for (SseEmitter dead : deadEmitters) {
            removeEmitter(deploymentId, dead);
        }
    }

    /**
     * Broadcasts a deployment status update event (e.g. state transitions).
     */
    public void broadcastStatus(UUID deploymentId, String status, String message) {
        if (deploymentId == null) {
            return;
        }

        Set<SseEmitter> emitters = subscriberMap.get(deploymentId);
        if (emitters == null || emitters.isEmpty()) {
            return;
        }

        Map<String, Object> payload = Map.of(
                "deploymentId", deploymentId.toString(),
                "status", status != null ? status : "",
                "message", message != null ? message : ""
        );

        List<SseEmitter> deadEmitters = new ArrayList<>();
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event()
                        .name("status")
                        .data(payload));
            } catch (Exception e) {
                deadEmitters.add(emitter);
            }
        }

        for (SseEmitter dead : deadEmitters) {
            removeEmitter(deploymentId, dead);
        }
    }

    /**
     * Broadcasts terminal completion event and cleanly closes all active client connections.
     */
    public void broadcastComplete(UUID deploymentId, String finalStatus) {
        if (deploymentId == null) {
            return;
        }

        Set<SseEmitter> emitters = subscriberMap.remove(deploymentId);
        if (emitters == null || emitters.isEmpty()) {
            return;
        }

        Map<String, Object> payload = Map.of(
                "deploymentId", deploymentId.toString(),
                "finalStatus", finalStatus != null ? finalStatus : "FINISHED"
        );

        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event()
                        .name("complete")
                        .data(payload));
                emitter.complete();
            } catch (Exception ignored) {
                // Connection already dropped
            }
        }
        log.debug("Completed and closed {} stream connections for deployment {}", emitters.size(), deploymentId);
    }

    /**
     * Replays prior in-memory buffered logs to a late-joining client.
     */
    private void replayBacklog(UUID deploymentId, SseEmitter emitter, Long sinceSequenceNumber) {
        Deque<DeploymentLogDto> buffer = replayBufferMap.get(deploymentId);
        if (buffer == null) {
            return;
        }

        List<DeploymentLogDto> snapshot;
        synchronized (buffer) {
            snapshot = new ArrayList<>(buffer);
        }

        for (DeploymentLogDto entry : snapshot) {
            if (sinceSequenceNumber == null || entry.getSequenceNumber() > sinceSequenceNumber) {
                try {
                    emitter.send(SseEmitter.event()
                            .name("log")
                            .data(entry));
                } catch (IOException e) {
                    removeEmitter(deploymentId, emitter);
                    return;
                }
            }
        }
    }

    private void removeEmitter(UUID deploymentId, SseEmitter emitter) {
        Set<SseEmitter> emitters = subscriberMap.get(deploymentId);
        if (emitters != null) {
            emitters.remove(emitter);
            if (emitters.isEmpty()) {
                subscriberMap.remove(deploymentId);
            }
        }
    }

    /**
     * Periodic keep-alive ping emitted to prevent intermediate network timeouts.
     */
    @Scheduled(fixedDelayString = "${shipit.logs.stream-heartbeat-seconds:15}000")
    public void sendHeartbeats() {
        if (subscriberMap.isEmpty()) {
            return;
        }

        for (Map.Entry<UUID, Set<SseEmitter>> entry : subscriberMap.entrySet()) {
            List<SseEmitter> deadEmitters = new ArrayList<>();
            for (SseEmitter emitter : entry.getValue()) {
                try {
                    emitter.send(SseEmitter.event().comment("ping"));
                } catch (Exception e) {
                    deadEmitters.add(emitter);
                }
            }
            for (SseEmitter dead : deadEmitters) {
                removeEmitter(entry.getKey(), dead);
            }
        }
    }

    /**
     * Returns the count of active listeners for a deployment.
     */
    public int getSubscriberCount(UUID deploymentId) {
        Set<SseEmitter> set = subscriberMap.get(deploymentId);
        return set != null ? set.size() : 0;
    }

    /**
     * Returns an unmodifiable snapshot of buffered logs for a deployment.
     */
    public List<DeploymentLogDto> getReplayBacklog(UUID deploymentId) {
        Deque<DeploymentLogDto> buffer = replayBufferMap.get(deploymentId);
        if (buffer == null) {
            return Collections.emptyList();
        }
        synchronized (buffer) {
            return new ArrayList<>(buffer);
        }
    }

    /**
     * Cleans up all resources associated with a finished deployment.
     */
    public void cleanupDeployment(UUID deploymentId) {
        subscriberMap.remove(deploymentId);
        replayBufferMap.remove(deploymentId);
    }

    /**
     * Resets broadcaster state (for testing).
     */
    public void reset() {
        subscriberMap.clear();
        replayBufferMap.clear();
    }
}
