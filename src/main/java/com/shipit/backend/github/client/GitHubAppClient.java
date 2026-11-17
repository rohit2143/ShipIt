package com.shipit.backend.github.client;

import com.shipit.backend.common.exception.AppException;
import com.shipit.backend.common.exception.ResourceNotFoundException;
import com.shipit.backend.common.exception.UnauthorizedException;
import com.shipit.backend.config.GitHubAppProperties;
import com.shipit.backend.github.dto.GitHubBranchDto;
import com.shipit.backend.github.dto.GitHubInstallationDto;
import com.shipit.backend.github.dto.GitHubInstallationRepositoriesDto;
import com.shipit.backend.github.dto.GitHubInstallationTokenResponse;
import com.shipit.backend.github.dto.GitHubRepoDto;
import com.shipit.backend.github.service.GitHubAppAuthService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;

@Slf4j
@Component
public class GitHubAppClient {

    private final GitHubAppProperties properties;
    private final GitHubAppAuthService authService;
    private final RestClient restClient;

    @org.springframework.beans.factory.annotation.Autowired
    public GitHubAppClient(GitHubAppProperties properties, GitHubAppAuthService authService) {
        this(properties, authService, RestClient.builder().build());
    }

    public GitHubAppClient(GitHubAppProperties properties, GitHubAppAuthService authService, RestClient restClient) {
        this.properties = properties;
        this.authService = authService;
        this.restClient = restClient;
    }

    public GitHubInstallationDto getInstallation(Long installationId) {
        String jwt = authService.generateAppJwt();
        String url = properties.getApiBaseUrl() + "/app/installations/" + installationId;

        log.debug("Fetching GitHub App installation {} metadata", installationId);

        return restClient.get()
                .uri(url)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwt)
                .header(HttpHeaders.ACCEPT, "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .retrieve()
                .onStatus(status -> status.value() == 404, (req, resp) -> {
                    throw new ResourceNotFoundException(
                            "GitHub App installation " + installationId + " was not found on GitHub",
                            "INSTALLATION_NOT_FOUND"
                    );
                })
                .onStatus(status -> status.value() == 401 || status.value() == 403, (req, resp) -> {
                    throw new UnauthorizedException(
                            "GitHub App authentication failed for installation " + installationId,
                            "GITHUB_APP_AUTH_FAILED"
                    );
                })
                .onStatus(HttpStatusCode::isError, (req, resp) -> {
                    throw new AppException(
                            "Failed to fetch GitHub installation metadata: HTTP " + resp.getStatusCode(),
                            HttpStatus.valueOf(resp.getStatusCode().value()),
                            "GITHUB_API_ERROR"
                    );
                })
                .body(GitHubInstallationDto.class);
    }

    public GitHubInstallationTokenResponse createInstallationAccessToken(Long installationId) {
        String jwt = authService.generateAppJwt();
        String url = properties.getApiBaseUrl() + "/app/installations/" + installationId + "/access_tokens";

        log.debug("Requesting short-lived installation access token for installation {}", installationId);

        return restClient.post()
                .uri(url)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwt)
                .header(HttpHeaders.ACCEPT, "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .contentType(MediaType.APPLICATION_JSON)
                .retrieve()
                .onStatus(status -> status.value() == 404, (req, resp) -> {
                    throw new ResourceNotFoundException(
                            "GitHub App installation " + installationId + " was not found on GitHub",
                            "INSTALLATION_NOT_FOUND"
                    );
                })
                .onStatus(status -> status.value() == 401 || status.value() == 403, (req, resp) -> {
                    throw new UnauthorizedException(
                            "GitHub App authentication failed for installation " + installationId,
                            "GITHUB_APP_AUTH_FAILED"
                    );
                })
                .onStatus(HttpStatusCode::isError, (req, resp) -> {
                    throw new AppException(
                            "Failed to generate GitHub installation access token: HTTP " + resp.getStatusCode(),
                            HttpStatus.valueOf(resp.getStatusCode().value()),
                            "GITHUB_API_ERROR"
                    );
                })
                .body(GitHubInstallationTokenResponse.class);
    }

    public GitHubInstallationRepositoriesDto getInstallationRepositories(String installationToken, int page, int perPage) {
        String url = properties.getApiBaseUrl() + "/installation/repositories?page=" + page + "&per_page=" + perPage;

        log.debug("Fetching installation repositories (page={}, perPage={})", page, perPage);

        return restClient.get()
                .uri(url)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + installationToken)
                .header(HttpHeaders.ACCEPT, "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .retrieve()
                .onStatus(status -> status.value() == 401 || status.value() == 403, (req, resp) -> {
                    throw new UnauthorizedException("GitHub installation token is invalid or expired", "GITHUB_TOKEN_EXPIRED");
                })
                .onStatus(HttpStatusCode::isError, (req, resp) -> {
                    throw new AppException(
                            "Failed to fetch installation repositories: HTTP " + resp.getStatusCode(),
                            HttpStatus.valueOf(resp.getStatusCode().value()),
                            "GITHUB_API_ERROR"
                    );
                })
                .body(GitHubInstallationRepositoriesDto.class);
    }

    public GitHubRepoDto getRepository(String installationToken, String owner, String repo) {
        String url = properties.getApiBaseUrl() + "/repos/" + owner + "/" + repo;

        log.debug("Fetching repository {}/{}", owner, repo);

        return restClient.get()
                .uri(url)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + installationToken)
                .header(HttpHeaders.ACCEPT, "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .retrieve()
                .onStatus(status -> status.value() == 404, (req, resp) -> {
                    throw new ResourceNotFoundException("GitHub repository " + owner + "/" + repo + " not found", "REPOSITORY_NOT_FOUND");
                })
                .onStatus(status -> status.value() == 401 || status.value() == 403, (req, resp) -> {
                    throw new UnauthorizedException("Unauthorized access to repository " + owner + "/" + repo, "GITHUB_REPO_ACCESS_DENIED");
                })
                .onStatus(HttpStatusCode::isError, (req, resp) -> {
                    throw new AppException(
                            "Failed to fetch repository metadata: HTTP " + resp.getStatusCode(),
                            HttpStatus.valueOf(resp.getStatusCode().value()),
                            "GITHUB_API_ERROR"
                    );
                })
                .body(GitHubRepoDto.class);
    }

    public GitHubRepoDto getRepositoryById(String installationToken, Long githubRepoId) {
        String url = properties.getApiBaseUrl() + "/repositories/" + githubRepoId;

        log.debug("Fetching repository by GitHub ID {}", githubRepoId);

        return restClient.get()
                .uri(url)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + installationToken)
                .header(HttpHeaders.ACCEPT, "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .retrieve()
                .onStatus(status -> status.value() == 404, (req, resp) -> {
                    throw new ResourceNotFoundException("GitHub repository " + githubRepoId + " not found", "REPOSITORY_NOT_FOUND");
                })
                .onStatus(status -> status.value() == 401 || status.value() == 403, (req, resp) -> {
                    throw new UnauthorizedException("Unauthorized access to repository " + githubRepoId, "GITHUB_REPO_ACCESS_DENIED");
                })
                .onStatus(HttpStatusCode::isError, (req, resp) -> {
                    throw new AppException(
                            "Failed to fetch repository metadata: HTTP " + resp.getStatusCode(),
                            HttpStatus.valueOf(resp.getStatusCode().value()),
                            "GITHUB_API_ERROR"
                    );
                })
                .body(GitHubRepoDto.class);
    }

    public List<GitHubBranchDto> getRepositoryBranches(String installationToken, String owner, String repo, int page, int perPage) {
        String url = properties.getApiBaseUrl() + "/repos/" + owner + "/" + repo + "/branches?page=" + page + "&per_page=" + perPage;

        log.debug("Fetching branches for repository {}/{} (page={}, perPage={})", owner, repo, page, perPage);

        return restClient.get()
                .uri(url)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + installationToken)
                .header(HttpHeaders.ACCEPT, "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .retrieve()
                .onStatus(status -> status.value() == 404, (req, resp) -> {
                    throw new ResourceNotFoundException("GitHub repository " + owner + "/" + repo + " not found", "REPOSITORY_NOT_FOUND");
                })
                .onStatus(status -> status.value() == 401 || status.value() == 403, (req, resp) -> {
                    throw new UnauthorizedException("Unauthorized access to repository branches", "GITHUB_REPO_ACCESS_DENIED");
                })
                .onStatus(HttpStatusCode::isError, (req, resp) -> {
                    throw new AppException(
                            "Failed to fetch repository branches: HTTP " + resp.getStatusCode(),
                            HttpStatus.valueOf(resp.getStatusCode().value()),
                            "GITHUB_API_ERROR"
                    );
                })
                .body(new ParameterizedTypeReference<List<GitHubBranchDto>>() {});
    }

    public GitHubBranchDto getRepositoryBranch(String installationToken, String owner, String repo, String branch) {
        String url = properties.getApiBaseUrl() + "/repos/" + owner + "/" + repo + "/branches/" + branch;

        log.debug("Fetching branch {} for repository {}/{}", branch, owner, repo);

        return restClient.get()
                .uri(url)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + installationToken)
                .header(HttpHeaders.ACCEPT, "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .retrieve()
                .onStatus(status -> status.value() == 404, (req, resp) -> {
                    throw new ResourceNotFoundException("Branch " + branch + " not found in repository " + owner + "/" + repo, "BRANCH_NOT_FOUND");
                })
                .onStatus(status -> status.value() == 401 || status.value() == 403, (req, resp) -> {
                    throw new UnauthorizedException("Unauthorized access to branch " + branch, "GITHUB_REPO_ACCESS_DENIED");
                })
                .onStatus(HttpStatusCode::isError, (req, resp) -> {
                    throw new AppException(
                            "Failed to fetch branch metadata: HTTP " + resp.getStatusCode(),
                            HttpStatus.valueOf(resp.getStatusCode().value()),
                            "GITHUB_API_ERROR"
                    );
                })
                .body(GitHubBranchDto.class);
    }
}
