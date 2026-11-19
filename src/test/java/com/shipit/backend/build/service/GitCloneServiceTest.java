package com.shipit.backend.build.service;

import com.shipit.backend.build.config.BuildProperties;
import com.shipit.backend.build.exception.BuildException;
import com.shipit.backend.build.logging.BuildLogBuffer;
import com.shipit.backend.build.process.ProcessExecutor;
import com.shipit.backend.build.process.ProcessResult;
import com.shipit.backend.build.workspace.WorkspaceManager;
import com.shipit.backend.deployment.entity.Deployment;
import com.shipit.backend.deployment.entity.DeploymentStatus;
import com.shipit.backend.github.entity.GitHubConnection;
import com.shipit.backend.github.entity.GitHubRepository;
import com.shipit.backend.github.service.GitHubInstallationTokenService;
import com.shipit.backend.project.entity.Project;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GitCloneServiceTest {

    @Mock
    private GitHubInstallationTokenService tokenService;

    @Mock
    private ProcessExecutor processExecutor;

    @Mock
    private WorkspaceManager workspaceManager;

    @Mock
    private BuildProperties buildProperties;

    @InjectMocks
    private GitCloneService gitCloneService;

    private Deployment sampleDeployment;
    private GitHubRepository sampleRepo;
    private GitHubConnection sampleConnection;
    private UUID deploymentId;
    private Path mockWorkspace;
    private BuildLogBuffer logBuffer;

    @BeforeEach
    void setUp() {
        deploymentId = UUID.randomUUID();
        mockWorkspace = Paths.get("/tmp/shipit-builds", deploymentId.toString(), "source");
        logBuffer = new BuildLogBuffer(100, 200);

        sampleConnection = GitHubConnection.builder()
                .id(UUID.randomUUID())
                .installationId(12345678L)
                .build();

        sampleRepo = GitHubRepository.builder()
                .id(UUID.randomUUID())
                .fullName("shipit-org/sample-app")
                .cloneUrl("https://github.com/shipit-org/sample-app.git")
                .defaultBranch("main")
                .connection(sampleConnection)
                .build();

        Project project = Project.builder()
                .id(UUID.randomUUID())
                .name("Sample App")
                .repository(sampleRepo)
                .build();

        sampleDeployment = Deployment.builder()
                .id(deploymentId)
                .project(project)
                .status(DeploymentStatus.QUEUED)
                .commitBranch("feature/v1")
                .commitSha("abc123def4567890abcdef1234567890abcdef12")
                .build();
    }

    @Test
    @DisplayName("buildAuthenticatedCloneUrl correctly formats URL with installation token")
    void testBuildAuthenticatedCloneUrl() {
        String url = gitCloneService.buildAuthenticatedCloneUrl(
                "https://github.com/shipit-org/sample-app.git",
                "ghs_TestToken123"
        );

        assertThat(url).isEqualTo("https://x-access-token:ghs_TestToken123@github.com/shipit-org/sample-app.git");
    }

    @Test
    @DisplayName("buildAuthenticatedCloneUrl validates blank inputs")
    void testBuildAuthenticatedCloneUrlValidation() {
        assertThatThrownBy(() -> gitCloneService.buildAuthenticatedCloneUrl("", "token"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> gitCloneService.buildAuthenticatedCloneUrl("https://github.com/repo.git", ""))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("cloneRepository successfully clones repository and checks out commit SHA")
    void testCloneRepository_Success_WithCommitSha() {
        when(tokenService.getInstallationToken(12345678L)).thenReturn("ghs_ValidToken");
        when(workspaceManager.createWorkspace(deploymentId)).thenReturn(mockWorkspace);
        when(buildProperties.getTimeoutSeconds()).thenReturn(600L);

        when(processExecutor.execute(anyList(), eq(mockWorkspace), eq(logBuffer), eq(600L), anySet()))
                .thenReturn(ProcessResult.builder().exitCode(0).timedOut(false).build());

        Path result = gitCloneService.cloneRepository(sampleDeployment, logBuffer);

        assertThat(result).isEqualTo(mockWorkspace);

        // Verify clone command
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<String>> cmdCaptor = ArgumentCaptor.forClass(List.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Set<String>> sensitiveCaptor = ArgumentCaptor.forClass(Set.class);

        verify(processExecutor, times(2)).execute(
                cmdCaptor.capture(),
                eq(mockWorkspace),
                eq(logBuffer),
                eq(600L),
                sensitiveCaptor.capture()
        );

        List<List<String>> allCmds = cmdCaptor.getAllValues();
        List<String> cloneCmd = allCmds.get(0);
        assertThat(cloneCmd).containsSubsequence("git", "clone", "--depth", "50", "--branch", "feature/v1");
        assertThat(cloneCmd.get(cloneCmd.size() - 2)).contains("x-access-token:ghs_ValidToken@github.com");

        List<String> checkoutCmd = allCmds.get(1);
        assertThat(checkoutCmd).containsExactly("git", "checkout", "abc123def4567890abcdef1234567890abcdef12");

        // Verify sensitive token masking
        assertThat(sensitiveCaptor.getValue()).contains("ghs_ValidToken");
    }

    @Test
    @DisplayName("cloneRepository executes only clone when commitSha is null or blank")
    void testCloneRepository_Success_NoCommitSha() {
        sampleDeployment.setCommitSha(null);
        sampleDeployment.setCommitBranch("main");

        when(tokenService.getInstallationToken(12345678L)).thenReturn("ghs_ValidToken");
        when(workspaceManager.createWorkspace(deploymentId)).thenReturn(mockWorkspace);
        when(buildProperties.getTimeoutSeconds()).thenReturn(600L);

        when(processExecutor.execute(anyList(), eq(mockWorkspace), eq(logBuffer), eq(600L), anySet()))
                .thenReturn(ProcessResult.builder().exitCode(0).timedOut(false).build());

        Path result = gitCloneService.cloneRepository(sampleDeployment, logBuffer);

        assertThat(result).isEqualTo(mockWorkspace);
        verify(processExecutor, times(1)).execute(anyList(), any(), any(), anyLong(), anySet());
    }

    @Test
    @DisplayName("cloneRepository throws BuildException when git clone fails")
    void testCloneRepository_CloneFails() {
        when(tokenService.getInstallationToken(12345678L)).thenReturn("ghs_ValidToken");
        when(workspaceManager.createWorkspace(deploymentId)).thenReturn(mockWorkspace);
        when(buildProperties.getTimeoutSeconds()).thenReturn(600L);

        when(processExecutor.execute(anyList(), eq(mockWorkspace), eq(logBuffer), eq(600L), anySet()))
                .thenReturn(ProcessResult.builder().exitCode(128).timedOut(false).build());

        assertThatThrownBy(() -> gitCloneService.cloneRepository(sampleDeployment, logBuffer))
                .isInstanceOf(BuildException.class)
                .hasMessageContaining("Git clone failed with exit code 128");
    }

    @Test
    @DisplayName("cloneRepository throws BuildException when git checkout fails")
    void testCloneRepository_CheckoutFails() {
        when(tokenService.getInstallationToken(12345678L)).thenReturn("ghs_ValidToken");
        when(workspaceManager.createWorkspace(deploymentId)).thenReturn(mockWorkspace);
        when(buildProperties.getTimeoutSeconds()).thenReturn(600L);

        when(processExecutor.execute(anyList(), eq(mockWorkspace), eq(logBuffer), eq(600L), anySet()))
                .thenReturn(ProcessResult.builder().exitCode(0).timedOut(false).build()) // clone success
                .thenReturn(ProcessResult.builder().exitCode(1).timedOut(false).build()); // checkout fails

        assertThatThrownBy(() -> gitCloneService.cloneRepository(sampleDeployment, logBuffer))
                .isInstanceOf(BuildException.class)
                .hasMessageContaining("Failed to checkout commit");
    }

    @Test
    @DisplayName("cloneRepository throws BuildException when token generation fails")
    void testCloneRepository_TokenFailure() {
        when(tokenService.getInstallationToken(12345678L)).thenReturn(null);

        assertThatThrownBy(() -> gitCloneService.cloneRepository(sampleDeployment, logBuffer))
                .isInstanceOf(BuildException.class)
                .hasMessageContaining("Failed to acquire GitHub installation token");

        verifyNoInteractions(workspaceManager);
        verifyNoInteractions(processExecutor);
    }

    @Test
    @DisplayName("cloneRepository validates non-null deployment and repository coordinates")
    void testCloneRepository_Validation() {
        assertThatThrownBy(() -> gitCloneService.cloneRepository(null, logBuffer))
                .isInstanceOf(IllegalArgumentException.class);

        Deployment noRepoDep = Deployment.builder()
                .id(UUID.randomUUID())
                .project(Project.builder().id(UUID.randomUUID()).repository(null).build())
                .build();
        assertThatThrownBy(() -> gitCloneService.cloneRepository(noRepoDep, logBuffer))
                .isInstanceOf(BuildException.class)
                .hasMessageContaining("does not have a linked GitHub repository");
    }
}
