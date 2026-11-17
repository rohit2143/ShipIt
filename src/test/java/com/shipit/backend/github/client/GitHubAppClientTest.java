package com.shipit.backend.github.client;

import com.shipit.backend.common.exception.AppException;
import com.shipit.backend.common.exception.ResourceNotFoundException;
import com.shipit.backend.common.exception.UnauthorizedException;
import com.shipit.backend.config.GitHubAppProperties;
import com.shipit.backend.github.dto.*;
import com.shipit.backend.github.service.GitHubAppAuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@ExtendWith(MockitoExtension.class)
class GitHubAppClientTest {

    @Mock
    private GitHubAppAuthService authService;

    private GitHubAppProperties properties;
    private MockRestServiceServer mockServer;
    private GitHubAppClient client;

    @BeforeEach
    void setUp() {
        properties = new GitHubAppProperties();
        properties.setApiBaseUrl("https://api.github.com");

        RestClient.Builder builder = RestClient.builder();
        mockServer = MockRestServiceServer.bindTo(builder).build();
        RestClient restClient = builder.build();

        client = new GitHubAppClient(properties, authService, restClient);
    }

    @Test
    @DisplayName("Should fetch installation metadata successfully")
    void shouldFetchInstallationMetadata() {
        Long installationId = 123456L;
        when(authService.generateAppJwt()).thenReturn("mock-app-jwt");

        String jsonResponse = """
                {
                    "id": 123456,
                    "account": {
                        "id": 78910,
                        "login": "octocat-org",
                        "type": "Organization",
                        "avatar_url": "https://avatars.githubusercontent.com/u/78910"
                    },
                    "repository_selection": "selected",
                    "permissions": {
                        "contents": "read",
                        "metadata": "read"
                    },
                    "suspended_at": null
                }
                """;

        mockServer.expect(requestTo("https://api.github.com/app/installations/123456"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", "Bearer mock-app-jwt"))
                .andExpect(header("Accept", "application/vnd.github+json"))
                .andExpect(header("X-GitHub-Api-Version", "2022-11-28"))
                .andRespond(withSuccess(jsonResponse, MediaType.APPLICATION_JSON));

        GitHubInstallationDto dto = client.getInstallation(installationId);

        mockServer.verify();
        assertThat(dto).isNotNull();
        assertThat(dto.getId()).isEqualTo(123456L);
        assertThat(dto.getAccount()).isNotNull();
        assertThat(dto.getAccount().getId()).isEqualTo(78910L);
        assertThat(dto.getAccount().getLogin()).isEqualTo("octocat-org");
        assertThat(dto.getAccount().getType()).isEqualTo("Organization");
        assertThat(dto.getAccount().getAvatarUrl()).isEqualTo("https://avatars.githubusercontent.com/u/78910");
        assertThat(dto.getRepositorySelection()).isEqualTo("selected");
        assertThat(dto.getPermissions()).containsEntry("contents", "read");
        assertThat(dto.getSuspendedAt()).isNull();
    }

    @Test
    @DisplayName("Should throw ResourceNotFoundException when installation is not found (404)")
    void shouldThrowNotFoundWhenInstallationNotFound() {
        Long installationId = 999999L;
        when(authService.generateAppJwt()).thenReturn("mock-app-jwt");

        mockServer.expect(requestTo("https://api.github.com/app/installations/999999"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThatThrownBy(() -> client.getInstallation(installationId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("999999 was not found")
                .extracting("errorCode")
                .isEqualTo("INSTALLATION_NOT_FOUND");

        mockServer.verify();
    }

    @Test
    @DisplayName("Should throw UnauthorizedException when GitHub App authentication fails (401)")
    void shouldThrowUnauthorizedWhenAuthFails() {
        Long installationId = 123456L;
        when(authService.generateAppJwt()).thenReturn("invalid-jwt");

        mockServer.expect(requestTo("https://api.github.com/app/installations/123456"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        assertThatThrownBy(() -> client.getInstallation(installationId))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("authentication failed")
                .extracting("errorCode")
                .isEqualTo("GITHUB_APP_AUTH_FAILED");

        mockServer.verify();
    }

    @Test
    @DisplayName("Should generate installation access token successfully")
    void shouldGenerateInstallationAccessToken() {
        Long installationId = 123456L;
        when(authService.generateAppJwt()).thenReturn("mock-app-jwt");

        String jsonResponse = """
                {
                    "token": "ghs_16C7e42F292c6912E7710c838347Ae178B4a",
                    "expires_at": "2026-11-06T02:00:00Z",
                    "permissions": {
                        "contents": "read"
                    },
                    "repository_selection": "all"
                }
                """;

        mockServer.expect(requestTo("https://api.github.com/app/installations/123456/access_tokens"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer mock-app-jwt"))
                .andExpect(header("Accept", "application/vnd.github+json"))
                .andExpect(header("X-GitHub-Api-Version", "2022-11-28"))
                .andRespond(withSuccess(jsonResponse, MediaType.APPLICATION_JSON));

        GitHubInstallationTokenResponse response = client.createInstallationAccessToken(installationId);

        mockServer.verify();
        assertThat(response).isNotNull();
        assertThat(response.getToken()).isEqualTo("ghs_16C7e42F292c6912E7710c838347Ae178B4a");
        assertThat(response.getExpiresAt()).isEqualTo(Instant.parse("2026-11-06T02:00:00Z"));
        assertThat(response.getRepositorySelection()).isEqualTo("all");
        assertThat(response.getPermissions()).containsEntry("contents", "read");
    }

    @Test
    @DisplayName("Should throw AppException on server error (500)")
    void shouldThrowAppExceptionOnServerError() {
        Long installationId = 123456L;
        when(authService.generateAppJwt()).thenReturn("mock-app-jwt");

        mockServer.expect(requestTo("https://api.github.com/app/installations/123456/access_tokens"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        assertThatThrownBy(() -> client.createInstallationAccessToken(installationId))
                .isInstanceOf(AppException.class)
                .extracting("errorCode")
                .isEqualTo("GITHUB_API_ERROR");

        mockServer.verify();
    }

    @Test
    @DisplayName("Should fetch installation repositories successfully")
    void shouldFetchInstallationRepositories() {
        String token = "ghs_installation_token";
        String json = """
                {
                    "total_count": 1,
                    "repositories": [
                        {
                            "id": 98765,
                            "name": "my-app",
                            "full_name": "octocat/my-app",
                            "private": true,
                            "html_url": "https://github.com/octocat/my-app",
                            "clone_url": "https://github.com/octocat/my-app.git",
                            "default_branch": "main",
                            "owner": {
                                "id": 1001,
                                "login": "octocat"
                            }
                        }
                    ]
                }
                """;

        mockServer.expect(requestTo("https://api.github.com/installation/repositories?page=1&per_page=30"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", "Bearer " + token))
                .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));

        GitHubInstallationRepositoriesDto result = client.getInstallationRepositories(token, 1, 30);

        mockServer.verify();
        assertThat(result).isNotNull();
        assertThat(result.getTotalCount()).isEqualTo(1);
        assertThat(result.getRepositories()).hasSize(1);
        assertThat(result.getRepositories().get(0).getName()).isEqualTo("my-app");
        assertThat(result.getRepositories().get(0).isPrivate()).isTrue();
    }

    @Test
    @DisplayName("Should fetch repository by owner and name successfully")
    void shouldFetchRepositoryByOwnerAndName() {
        String token = "ghs_installation_token";
        String json = """
                {
                    "id": 98765,
                    "name": "my-app",
                    "full_name": "octocat/my-app",
                    "private": false,
                    "html_url": "https://github.com/octocat/my-app",
                    "clone_url": "https://github.com/octocat/my-app.git",
                    "default_branch": "main"
                }
                """;

        mockServer.expect(requestTo("https://api.github.com/repos/octocat/my-app"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", "Bearer " + token))
                .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));

        GitHubRepoDto repo = client.getRepository(token, "octocat", "my-app");

        mockServer.verify();
        assertThat(repo).isNotNull();
        assertThat(repo.getId()).isEqualTo(98765L);
        assertThat(repo.getFullName()).isEqualTo("octocat/my-app");
    }

    @Test
    @DisplayName("Should fetch repository branches successfully")
    void shouldFetchRepositoryBranches() {
        String token = "ghs_installation_token";
        String json = """
                [
                    {
                        "name": "main",
                        "commit": {
                            "sha": "6dcb09b5b57875f334f61aebed695e2e4193db5e",
                            "url": "https://api.github.com/repos/octocat/my-app/commits/6dcb09b5b57875f334f61aebed695e2e4193db5e"
                        },
                        "protected": true
                    },
                    {
                        "name": "feature-x",
                        "commit": {
                            "sha": "7a9b09b5b57875f334f61aebed695e2e4193db5f",
                            "url": "https://api.github.com/repos/octocat/my-app/commits/7a9b09b5b57875f334f61aebed695e2e4193db5f"
                        },
                        "protected": false
                    }
                ]
                """;

        mockServer.expect(requestTo("https://api.github.com/repos/octocat/my-app/branches?page=1&per_page=100"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", "Bearer " + token))
                .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));

        List<GitHubBranchDto> branches = client.getRepositoryBranches(token, "octocat", "my-app", 1, 100);

        mockServer.verify();
        assertThat(branches).hasSize(2);
        assertThat(branches.get(0).getName()).isEqualTo("main");
        assertThat(branches.get(0).getCommit().getSha()).isEqualTo("6dcb09b5b57875f334f61aebed695e2e4193db5e");
        assertThat(branches.get(0).isProtected()).isTrue();
        assertThat(branches.get(1).getName()).isEqualTo("feature-x");
    }

    @Test
    @DisplayName("Should throw ResourceNotFoundException when repository does not exist")
    void shouldThrowNotFoundWhenRepoDoesNotExist() {
        String token = "ghs_installation_token";

        mockServer.expect(requestTo("https://api.github.com/repos/octocat/nonexistent"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThatThrownBy(() -> client.getRepository(token, "octocat", "nonexistent"))
                .isInstanceOf(ResourceNotFoundException.class)
                .extracting("errorCode")
                .isEqualTo("REPOSITORY_NOT_FOUND");

        mockServer.verify();
    }
}
