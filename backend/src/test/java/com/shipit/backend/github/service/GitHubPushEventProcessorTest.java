package com.shipit.backend.github.service;

import com.shipit.backend.github.dto.GitHubPushCommitDto;
import com.shipit.backend.github.dto.GitHubPushPayloadDto;
import com.shipit.backend.github.dto.GitHubPushRepositoryDto;
import com.shipit.backend.github.dto.GitHubPushUserDto;
import com.shipit.backend.github.entity.GitHubRepository;
import com.shipit.backend.github.event.GitHubPushReceivedEvent;
import com.shipit.backend.github.repository.GitHubRepositoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GitHubPushEventProcessorTest {

    @Mock
    private GitHubRepositoryRepository repositoryRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private GitHubPushEventProcessor processor;

    @BeforeEach
    void setUp() {
        processor = new GitHubPushEventProcessor(repositoryRepository, eventPublisher);
    }

    @Test
    @DisplayName("Should process valid push event for connected repository and publish GitHubPushReceivedEvent")
    void shouldProcessPushForConnectedRepository() {
        Long githubRepoId = 1441936511L;
        UUID shipitRepoId = UUID.randomUUID();

        GitHubRepository connectedRepo = GitHubRepository.builder()
                .id(shipitRepoId)
                .githubRepoId(githubRepoId)
                .fullName("rohit2143/ShipIt")
                .cloneUrl("https://github.com/rohit2143/ShipIt.git")
                .defaultBranch("main")
                .build();

        when(repositoryRepository.findByGithubRepoId(githubRepoId)).thenReturn(Optional.of(connectedRepo));

        GitHubPushPayloadDto payload = GitHubPushPayloadDto.builder()
                .ref("refs/heads/main")
                .before("1111111111111111111111111111111111111111")
                .after("2222222222222222222222222222222222222222")
                .deleted(false)
                .repository(GitHubPushRepositoryDto.builder()
                        .id(githubRepoId)
                        .fullName("rohit2143/ShipIt")
                        .build())
                .pusher(GitHubPushUserDto.builder()
                        .name("rohit2143")
                        .email("rohit@shipit.dev")
                        .build())
                .headCommit(GitHubPushCommitDto.builder()
                        .id("2222222222222222222222222222222222222222")
                        .message("feat: implement webhooks")
                        .build())
                .build();

        var result = processor.processPushEvent(payload);

        assertThat(result.processed()).isTrue();
        assertThat(result.message()).contains("rohit2143/ShipIt");

        ArgumentCaptor<GitHubPushReceivedEvent> captor = ArgumentCaptor.forClass(GitHubPushReceivedEvent.class);
        verify(eventPublisher, times(1)).publishEvent(captor.capture());

        GitHubPushReceivedEvent capturedEvent = captor.getValue();
        assertThat(capturedEvent.getRepositoryId()).isEqualTo(shipitRepoId);
        assertThat(capturedEvent.getGithubRepoId()).isEqualTo(githubRepoId);
        assertThat(capturedEvent.getRepoFullName()).isEqualTo("rohit2143/ShipIt");
        assertThat(capturedEvent.getBranch()).isEqualTo("main");
        assertThat(capturedEvent.getCommitSha()).isEqualTo("2222222222222222222222222222222222222222");
        assertThat(capturedEvent.getCommitMessage()).isEqualTo("feat: implement webhooks");
        assertThat(capturedEvent.getPusherName()).isEqualTo("rohit2143");
        assertThat(capturedEvent.getPusherEmail()).isEqualTo("rohit@shipit.dev");
    }

    @Test
    @DisplayName("Should ignore push event when repository is not connected in ShipIt")
    void shouldIgnoreWhenRepoNotConnected() {
        Long githubRepoId = 999999999L;
        when(repositoryRepository.findByGithubRepoId(githubRepoId)).thenReturn(Optional.empty());

        GitHubPushPayloadDto payload = GitHubPushPayloadDto.builder()
                .ref("refs/heads/main")
                .after("abcdef1234567890abcdef1234567890abcdef12")
                .repository(GitHubPushRepositoryDto.builder()
                        .id(githubRepoId)
                        .fullName("unknown/repo")
                        .build())
                .build();

        var result = processor.processPushEvent(payload);

        assertThat(result.processed()).isFalse();
        assertThat(result.message()).contains("not connected");
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("Should ignore push event when branch is deleted")
    void shouldIgnoreWhenBranchDeleted() {
        GitHubPushPayloadDto payloadWithDeletedFlag = GitHubPushPayloadDto.builder()
                .ref("refs/heads/feature-branch")
                .deleted(true)
                .after("0000000000000000000000000000000000000000")
                .repository(GitHubPushRepositoryDto.builder()
                        .id(12345L)
                        .fullName("test/repo")
                        .build())
                .build();

        var result1 = processor.processPushEvent(payloadWithDeletedFlag);
        assertThat(result1.processed()).isFalse();
        assertThat(result1.message()).contains("deletion");

        GitHubPushPayloadDto payloadWithZeroSha = GitHubPushPayloadDto.builder()
                .ref("refs/heads/feature-branch")
                .deleted(false)
                .after("0000000000000000000000000000000000000000")
                .repository(GitHubPushRepositoryDto.builder()
                        .id(12345L)
                        .fullName("test/repo")
                        .build())
                .build();

        var result2 = processor.processPushEvent(payloadWithZeroSha);
        assertThat(result2.processed()).isFalse();
        assertThat(result2.message()).contains("deletion");

        verify(eventPublisher, never()).publishEvent(any());
        verify(repositoryRepository, never()).findByGithubRepoId(any());
    }

    @Test
    @DisplayName("Should ignore push event for tags or pull requests (non-branch refs)")
    void shouldIgnoreNonBranchRefs() {
        GitHubPushPayloadDto tagPayload = GitHubPushPayloadDto.builder()
                .ref("refs/tags/v1.0.0")
                .after("abcdef1234567890abcdef1234567890abcdef12")
                .repository(GitHubPushRepositoryDto.builder()
                        .id(12345L)
                        .fullName("test/repo")
                        .build())
                .build();

        var result = processor.processPushEvent(tagPayload);

        assertThat(result.processed()).isFalse();
        assertThat(result.message()).contains("Ignored ref");
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("Should handle null payload or repository safely without exception")
    void shouldHandleNullPayloadGracefully() {
        var result1 = processor.processPushEvent(null);
        assertThat(result1.processed()).isFalse();

        var result2 = processor.processPushEvent(GitHubPushPayloadDto.builder().build());
        assertThat(result2.processed()).isFalse();

        verify(eventPublisher, never()).publishEvent(any());
    }
}
