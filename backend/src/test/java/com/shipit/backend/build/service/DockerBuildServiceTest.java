package com.shipit.backend.build.service;

import com.shipit.backend.build.config.BuildProperties;
import com.shipit.backend.build.exception.BuildException;
import com.shipit.backend.build.logging.BuildLogBuffer;
import com.shipit.backend.build.model.BuildPlan;
import com.shipit.backend.build.model.BuildResult;
import com.shipit.backend.build.model.ProjectType;
import com.shipit.backend.build.process.ProcessExecutor;
import com.shipit.backend.build.process.ProcessResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DockerBuildServiceTest {

    @TempDir
    Path tempDir;

    @Mock
    private ProcessExecutor processExecutor;

    @Mock
    private BuildProperties buildProperties;

    @InjectMocks
    private DockerBuildService dockerBuildService;

    private BuildPlan samplePlan;
    private Path sampleDockerfile;
    private BuildLogBuffer logBuffer;

    @BeforeEach
    void setUp() throws IOException {
        sampleDockerfile = tempDir.resolve("Dockerfile.shipit");
        Files.writeString(sampleDockerfile, "FROM alpine\n");
        logBuffer = new BuildLogBuffer(100, 200);

        samplePlan = BuildPlan.builder()
                .projectId(UUID.randomUUID())
                .deploymentId(UUID.randomUUID())
                .projectType(ProjectType.MAVEN_SPRING_BOOT)
                .rootDirectory("/")
                .dockerfilePath(sampleDockerfile.toAbsolutePath().toString())
                .port(8080)
                .imageTag("shipit/sample-app:abc1234")
                .build();
    }

    @Test
    @DisplayName("buildImage executes docker build with correct arguments and records success")
    void testBuildImage_Success() {
        when(buildProperties.getTimeoutSeconds()).thenReturn(600L);
        when(processExecutor.execute(anyList(), eq(tempDir), eq(logBuffer), eq(600L), anySet()))
                .thenReturn(ProcessResult.builder().exitCode(0).timedOut(false).build());

        BuildResult result = dockerBuildService.buildImage(tempDir, samplePlan, logBuffer);

        assertThat(result).isNotNull();
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getImageTag()).isEqualTo("shipit/sample-app:abc1234");
        assertThat(result.getStartedAt()).isNotNull();
        assertThat(result.getFinishedAt()).isNotNull();
        assertThat(result.getDurationSeconds()).isGreaterThanOrEqualTo(0);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<String>> cmdCaptor = ArgumentCaptor.forClass(List.class);
        verify(processExecutor).execute(cmdCaptor.capture(), eq(tempDir), eq(logBuffer), eq(600L), anySet());

        List<String> executedCmd = cmdCaptor.getValue();
        assertThat(executedCmd).containsExactly(
                "docker", "build",
                "--file", sampleDockerfile.toAbsolutePath().toString(),
                "--tag", "shipit/sample-app:abc1234",
                "."
        );

        assertThat(logBuffer.getAllLogsAsString()).contains("Successfully built Docker image 'shipit/sample-app:abc1234'");
    }

    @Test
    @DisplayName("buildImage throws BuildException when process returns non-zero exit code")
    void testBuildImage_NonZeroExit() {
        when(buildProperties.getTimeoutSeconds()).thenReturn(600L);
        when(processExecutor.execute(anyList(), eq(tempDir), eq(logBuffer), eq(600L), anySet()))
                .thenReturn(ProcessResult.builder().exitCode(1).timedOut(false).build());

        assertThatThrownBy(() -> dockerBuildService.buildImage(tempDir, samplePlan, logBuffer))
                .isInstanceOf(BuildException.class)
                .hasMessageContaining("Docker image build failed with exit code 1");

        assertThat(logBuffer.getAllLogsAsString()).contains("failed with exit code 1");
    }

    @Test
    @DisplayName("buildImage throws BuildException when process times out")
    void testBuildImage_Timeout() {
        when(buildProperties.getTimeoutSeconds()).thenReturn(300L);
        when(processExecutor.execute(anyList(), eq(tempDir), eq(logBuffer), eq(300L), anySet()))
                .thenReturn(ProcessResult.builder().exitCode(-1).timedOut(true).build());

        assertThatThrownBy(() -> dockerBuildService.buildImage(tempDir, samplePlan, logBuffer))
                .isInstanceOf(BuildException.class)
                .hasMessageContaining("Docker image build timed out after 300 seconds");

        assertThat(logBuffer.getAllLogsAsString()).contains("timed out after 300 seconds");
    }

    @Test
    @DisplayName("buildImage throws BuildException when Dockerfile is not found")
    void testBuildImage_MissingDockerfile() {
        samplePlan.setDockerfilePath(tempDir.resolve("non-existent-Dockerfile").toString());

        assertThatThrownBy(() -> dockerBuildService.buildImage(tempDir, samplePlan, logBuffer))
                .isInstanceOf(BuildException.class)
                .hasMessageContaining("Dockerfile not found");

        verifyNoInteractions(processExecutor);
    }

    @Test
    @DisplayName("buildImage validates non-null context and valid build plan")
    void testBuildImage_Validation() {
        assertThatThrownBy(() -> dockerBuildService.buildImage(null, samplePlan, logBuffer))
                .isInstanceOf(IllegalArgumentException.class);

        BuildPlan invalidPlan = BuildPlan.builder().build();
        assertThatThrownBy(() -> dockerBuildService.buildImage(tempDir, invalidPlan, logBuffer))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("imageExists returns true when docker image inspect succeeds")
    void testImageExists_True() {
        when(processExecutor.execute(List.of("docker", "image", "inspect", "shipit/app:tag"), null, null, 10, Set.of()))
                .thenReturn(ProcessResult.builder().exitCode(0).build());

        assertThat(dockerBuildService.imageExists("shipit/app:tag")).isTrue();
    }

    @Test
    @DisplayName("imageExists returns false when inspect fails or tag is blank")
    void testImageExists_False() {
        when(processExecutor.execute(anyList(), any(), any(), anyLong(), anySet()))
                .thenReturn(ProcessResult.builder().exitCode(1).build());

        assertThat(dockerBuildService.imageExists("shipit/app:nonexistent")).isFalse();
        assertThat(dockerBuildService.imageExists("")).isFalse();
        assertThat(dockerBuildService.imageExists(null)).isFalse();
    }

    @Test
    @DisplayName("removeImage invokes docker rmi command")
    void testRemoveImage() {
        dockerBuildService.removeImage("shipit/app:old", logBuffer);

        verify(processExecutor).execute(
                eq(List.of("docker", "rmi", "-f", "shipit/app:old")),
                isNull(),
                eq(logBuffer),
                eq(30L),
                eq(Set.of())
        );
    }
}
