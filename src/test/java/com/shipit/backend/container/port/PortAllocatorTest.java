package com.shipit.backend.container.port;

import com.shipit.backend.container.config.ContainerProperties;
import com.shipit.backend.container.exception.ContainerException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.*;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PortAllocatorTest {

    private ContainerProperties properties;
    private PortAllocator portAllocator;

    @BeforeEach
    void setUp() {
        properties = new ContainerProperties();
        properties.setMinPort(15000);
        properties.setMaxPort(15010);
        portAllocator = new PortAllocator(properties);
    }

    @Test
    @DisplayName("allocatePort - Successfully allocates port within configured range")
    void allocatePort_Success() {
        int port = portAllocator.allocatePort();

        assertThat(port).isBetween(15000, 15010);
        assertThat(portAllocator.isPortAllocated(port)).isTrue();
        assertThat(portAllocator.getAllocatedPortCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("allocatePort - Multiple allocations produce unique sequential ports")
    void allocatePort_MultipleUnique() {
        int port1 = portAllocator.allocatePort();
        int port2 = portAllocator.allocatePort();
        int port3 = portAllocator.allocatePort();

        Set<Integer> allocated = Set.of(port1, port2, port3);
        assertThat(allocated).hasSize(3);
        assertThat(portAllocator.getAllocatedPortCount()).isEqualTo(3);
    }

    @Test
    @DisplayName("releasePort - Frees allocated port for reuse")
    void releasePort_Success() {
        int port = portAllocator.allocatePort();
        assertThat(portAllocator.isPortAllocated(port)).isTrue();

        portAllocator.releasePort(port);
        assertThat(portAllocator.isPortAllocated(port)).isFalse();
        assertThat(portAllocator.getAllocatedPortCount()).isEqualTo(0);
    }

    @Test
    @DisplayName("releasePort - Releasing unallocated port does not throw or corrupt state")
    void releasePort_UnallocatedDoesNotThrow() {
        portAllocator.releasePort(9999);
        assertThat(portAllocator.getAllocatedPortCount()).isEqualTo(0);
    }

    @Test
    @DisplayName("allocatePort - Throws INVALID_PORT_RANGE when configuration is invalid")
    void allocatePort_InvalidRange() {
        properties.setMinPort(20000);
        properties.setMaxPort(10000);

        assertThatThrownBy(() -> portAllocator.allocatePort())
                .isInstanceOf(ContainerException.class)
                .hasMessageContaining("Invalid container port range");

        properties.setMinPort(-1);
        properties.setMaxPort(1000);

        assertThatThrownBy(() -> portAllocator.allocatePort())
                .isInstanceOf(ContainerException.class)
                .hasMessageContaining("Invalid container port range");
    }

    @Test
    @DisplayName("allocatePort - Throws PORT_EXHAUSTION when all ports in range are occupied")
    void allocatePort_PortExhaustion() {
        properties.setMinPort(16000);
        properties.setMaxPort(16002); // 3 total ports

        int p1 = portAllocator.allocatePort();
        int p2 = portAllocator.allocatePort();
        int p3 = portAllocator.allocatePort();

        assertThat(Set.of(p1, p2, p3)).hasSize(3);

        assertThatThrownBy(() -> portAllocator.allocatePort())
                .isInstanceOf(ContainerException.class)
                .hasMessageContaining("exhausted");
    }

    @Test
    @DisplayName("allocatePort - Skips ports that are externally occupied on the host")
    void allocatePort_SkipsOccupiedPort() throws IOException {
        properties.setMinPort(17000);
        properties.setMaxPort(17005);

        // Bind port 17000 externally
        try (ServerSocket socket = new ServerSocket(17000)) {
            socket.setReuseAddress(true);

            int allocatedPort = portAllocator.allocatePort();

            // Should have skipped 17000 and allocated 17001
            assertThat(allocatedPort).isNotEqualTo(17000);
            assertThat(allocatedPort).isBetween(17001, 17005);
        }
    }

    @Test
    @DisplayName("allocatePort - Thread-safe concurrent port allocations produce zero duplicates")
    void allocatePort_ConcurrentAllocations() throws InterruptedException {
        properties.setMinPort(18000);
        properties.setMaxPort(18050);

        int threadCount = 20;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        Set<Integer> allocated = Collections.synchronizedSet(new HashSet<>());
        List<Future<?>> futures = new ArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            futures.add(executor.submit(() -> {
                try {
                    startLatch.await();
                    int port = portAllocator.allocatePort();
                    allocated.add(port);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }));
        }

        startLatch.countDown();
        executor.shutdown();
        assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();

        for (Future<?> future : futures) {
            try {
                future.get();
            } catch (ExecutionException e) {
                throw new AssertionError("Concurrent allocation failed", e.getCause());
            }
        }

        assertThat(allocated).hasSize(threadCount);
        assertThat(portAllocator.getAllocatedPortCount()).isEqualTo(threadCount);
    }

    @Test
    @DisplayName("reset - Clears all allocated ports and resets state")
    void reset_ClearsAllocations() {
        portAllocator.allocatePort();
        portAllocator.allocatePort();
        assertThat(portAllocator.getAllocatedPortCount()).isEqualTo(2);

        portAllocator.reset();
        assertThat(portAllocator.getAllocatedPortCount()).isEqualTo(0);
    }
}
