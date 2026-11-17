package com.shipit.backend.github.service;

import com.shipit.backend.github.dto.GitHubPushPayloadDto;
import com.shipit.backend.github.entity.GitHubRepository;
import com.shipit.backend.github.event.GitHubPushReceivedEvent;
import com.shipit.backend.github.repository.GitHubRepositoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class GitHubPushEventProcessor {

    private static final String ZERO_SHA = "0000000000000000000000000000000000000000";
    private static final String REF_HEADS_PREFIX = "refs/heads/";

    private final GitHubRepositoryRepository repositoryRepository;
    private final ApplicationEventPublisher eventPublisher;

    public PushProcessingResult processPushEvent(GitHubPushPayloadDto payload) {
        if (payload == null || payload.getRepository() == null) {
            log.warn("Push event payload or repository is null; skipping");
            return new PushProcessingResult(false, "Invalid push payload");
        }

        String ref = payload.getRef();
        if (ref == null || !ref.startsWith(REF_HEADS_PREFIX)) {
            log.info("Push event ref '{}' is not a branch (tags or other refs ignored)", ref);
            return new PushProcessingResult(false, "Ignored ref: " + ref);
        }

        String branch = ref.substring(REF_HEADS_PREFIX.length());

        // Check if branch was deleted
        if (Boolean.TRUE.equals(payload.getDeleted()) || ZERO_SHA.equals(payload.getAfter())) {
            log.info("Branch '{}' in repository '{}' was deleted; ignoring push",
                    branch, payload.getRepository().getFullName());
            return new PushProcessingResult(false, "Branch deletion ignored");
        }

        Long githubRepoId = payload.getRepository().getId();
        Optional<GitHubRepository> optionalRepo = repositoryRepository.findByGithubRepoId(githubRepoId);

        if (optionalRepo.isEmpty()) {
            log.info("Repository '{}' (ID {}) is not connected in ShipIt; ignoring push event",
                    payload.getRepository().getFullName(), githubRepoId);
            return new PushProcessingResult(false, "Repository not connected in ShipIt");
        }

        GitHubRepository repo = optionalRepo.get();
        String commitSha = payload.getAfter();
        String commitMessage = payload.getHeadCommit() != null ? payload.getHeadCommit().getMessage() : "";
        String pusherName = payload.getPusher() != null ? payload.getPusher().getName() : "";
        String pusherEmail = payload.getPusher() != null ? payload.getPusher().getEmail() : "";

        GitHubPushReceivedEvent event = GitHubPushReceivedEvent.builder()
                .repositoryId(repo.getId())
                .githubRepoId(githubRepoId)
                .repoFullName(repo.getFullName())
                .cloneUrl(repo.getCloneUrl())
                .defaultBranch(repo.getDefaultBranch())
                .branch(branch)
                .commitSha(commitSha)
                .commitMessage(commitMessage)
                .pusherName(pusherName)
                .pusherEmail(pusherEmail)
                .timestamp(Instant.now())
                .build();

        eventPublisher.publishEvent(event);
        log.info("Dispatched GitHubPushReceivedEvent for repo '{}' on branch '{}', commit {}",
                repo.getFullName(), branch, commitSha);

        return new PushProcessingResult(true, "Push processed for repository: " + repo.getFullName());
    }

    public record PushProcessingResult(boolean processed, String message) {}
}
