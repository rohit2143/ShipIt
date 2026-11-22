package com.shipit.backend.container.port;

import com.shipit.backend.container.config.ContainerProperties;
import com.shipit.backend.container.exception.ContainerException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Thread-safe host port allocator for deployed application containers.
 * <p>
 * Ensures host ports assigned to containers are:
 * 1. Within the configured port range [minPort, maxPort].
 * 2. Not already reserved by another active container.
 * 3. Not actively bound by another process on the host operating system.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PortAllocator {

    private final ContainerProperties properties;
    private final Set<Integer> allocatedPorts = Collections.synchronizedSet(new HashSet<>());
    private final ReentrantLock lock = new ReentrantLock();

    private int nextPortCandidate = -1;

    /**
     * Allocates the next available collision-free host port.
     *
     * @return Allocated host port number.
     * @throws ContainerException if all ports in the configured range are exhausted.
     */
    public int allocatePort() {
        lock.lock();
        try {
            int minPort = properties.getMinPort();
            int maxPort = properties.getMaxPort();

            if (minPort <= 0 || maxPort > 65535 || minPort > maxPort) {
                throw new ContainerException(
                        String.format("Invalid container port range: [%d, %d]", minPort, maxPort),
                        "INVALID_PORT_RANGE"
                );
            }

            int totalPorts = maxPort - minPort + 1;
            if (nextPortCandidate < minPort || nextPortCandidate > maxPort) {
                nextPortCandidate = minPort;
            }

            for (int i = 0; i < totalPorts; i++) {
                int candidate = nextPortCandidate;
                nextPortCandidate = (candidate == maxPort) ? minPort : candidate + 1;

                if (!allocatedPorts.contains(candidate) && isSocketAvailable(candidate)) {
                    allocatedPorts.add(candidate);
                    log.info("Allocated host port {} for container deployment", candidate);
                    return candidate;
                }
            }

            log.error("Port exhaustion: all {} ports in range [{}-{}] are occupied", totalPorts, minPort, maxPort);
            throw new ContainerException(
                    String.format("All container host ports in range [%d, %d] are exhausted", minPort, maxPort),
                    "PORT_EXHAUSTION"
            );
        } finally {
            lock.unlock();
        }
    }

    /**
     * Releases a previously allocated port back to the free pool.
     *
     * @param port Port number to release.
     */
    public void releasePort(int port) {
        lock.lock();
        try {
            if (allocatedPorts.remove(port)) {
                log.info("Released host port {}", port);
            } else {
                log.debug("Attempted to release unallocated port {}", port);
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * Checks if a port is currently tracked as allocated by the allocator.
     */
    public boolean isPortAllocated(int port) {
        return allocatedPorts.contains(port);
    }

    /**
     * Returns the total count of currently allocated ports.
     */
    public int getAllocatedPortCount() {
        return allocatedPorts.size();
    }

    /**
     * Probes the operating system socket layer to verify the port is free to bind.
     */
    protected boolean isSocketAvailable(int port) {
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            serverSocket.setReuseAddress(true);
            return true;
        } catch (IOException e) {
            log.debug("Port {} is bound by external process or unavailable: {}", port, e.getMessage());
            return false;
        }
    }

    /**
     * Resets the allocated ports tracker (primarily for testing).
     */
    public void reset() {
        lock.lock();
        try {
            allocatedPorts.clear();
            nextPortCandidate = -1;
        } finally {
            lock.unlock();
        }
    }
}
