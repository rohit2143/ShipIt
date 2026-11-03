package com.shipit.backend.user.controller;

import com.shipit.backend.auth.dto.OAuthLoginRequest;
import com.shipit.backend.auth.entity.AuthProvider;
import com.shipit.backend.auth.security.UserPrincipal;
import com.shipit.backend.auth.service.OAuthService;
import com.shipit.backend.common.exception.BadRequestException;
import com.shipit.backend.common.exception.ConflictException;
import com.shipit.backend.common.exception.GlobalExceptionHandler;
import com.shipit.backend.user.dto.IdentityResponse;
import com.shipit.backend.user.entity.Role;
import com.shipit.backend.user.entity.User;
import com.shipit.backend.user.entity.UserStatus;
import com.shipit.backend.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class UserControllerTest {

    private MockMvc mockMvc;

    @Mock
    private UserService userService;

    @Mock
    private OAuthService oAuthService;

    @InjectMocks
    private UserController userController;

    private User sampleUser;
    private UserPrincipal samplePrincipal;
    private UUID sampleUserId;

    @BeforeEach
    void setUp() {
        sampleUserId = UUID.randomUUID();

        sampleUser = User.builder()
                .id(sampleUserId)
                .name("Alice User")
                .email("alice@example.com")
                .role(Role.ROLE_USER)
                .status(UserStatus.ACTIVE)
                .emailVerified(true)
                .build();

        samplePrincipal = UserPrincipal.create(sampleUser, "encoded-pwd");

        HandlerMethodArgumentResolver argumentResolver = new HandlerMethodArgumentResolver() {
            @Override
            public boolean supportsParameter(MethodParameter parameter) {
                return parameter.getParameterType().isAssignableFrom(UserPrincipal.class);
            }

            @Override
            public Object resolveArgument(MethodParameter parameter,
                                          ModelAndViewContainer mavContainer,
                                          NativeWebRequest webRequest,
                                          WebDataBinderFactory binderFactory) {
                return samplePrincipal;
            }
        };

        mockMvc = MockMvcBuilders.standaloneSetup(userController)
                .setCustomArgumentResolvers(argumentResolver)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("Should return 200 OK with UserResponse for current authenticated user")
    void shouldGetCurrentUserSuccessfully() throws Exception {
        when(userService.getById(sampleUserId)).thenReturn(sampleUser);

        mockMvc.perform(get("/api/v1/users/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(sampleUserId.toString()))
                .andExpect(jsonPath("$.data.name").value("Alice User"))
                .andExpect(jsonPath("$.data.email").value("alice@example.com"));
    }

    @Test
    @DisplayName("Should return 200 OK with list of linked identities")
    void shouldGetIdentitiesSuccessfully() throws Exception {
        IdentityResponse localIdentity = IdentityResponse.builder()
                .id(UUID.randomUUID())
                .provider(AuthProvider.LOCAL)
                .providerUserId("alice@example.com")
                .createdAt(Instant.now())
                .build();

        IdentityResponse githubIdentity = IdentityResponse.builder()
                .id(UUID.randomUUID())
                .provider(AuthProvider.GITHUB)
                .providerUserId("gh-998877")
                .createdAt(Instant.now())
                .build();

        when(oAuthService.getUserIdentities(sampleUserId))
                .thenReturn(List.of(localIdentity, githubIdentity));

        mockMvc.perform(get("/api/v1/users/me/identities"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data", hasSize(2)))
                .andExpect(jsonPath("$.data[0].provider").value("LOCAL"))
                .andExpect(jsonPath("$.data[1].provider").value("GITHUB"));
    }

    @Test
    @DisplayName("Should return 200 OK when linking a GitHub account successfully")
    void shouldLinkGitHubAccountSuccessfully() throws Exception {
        String payload = """
                {
                    "code": "gh-valid-link-code",
                    "redirectUri": "http://localhost:5173/profile"
                }
                """;

        IdentityResponse linkedResponse = IdentityResponse.builder()
                .id(UUID.randomUUID())
                .provider(AuthProvider.GITHUB)
                .providerUserId("gh-12345")
                .createdAt(Instant.now())
                .build();

        when(oAuthService.linkGitHubAccount(eq(sampleUserId), eq("gh-valid-link-code"), eq("http://localhost:5173/profile")))
                .thenReturn(linkedResponse);

        mockMvc.perform(post("/api/v1/users/me/identities/github/link")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("GitHub account linked successfully"))
                .andExpect(jsonPath("$.data.provider").value("GITHUB"))
                .andExpect(jsonPath("$.data.providerUserId").value("gh-12345"));
    }

    @Test
    @DisplayName("Should return 409 Conflict when linking a GitHub account already bound to another user")
    void shouldRejectLinkingWhenGitHubAccountBoundToAnother() throws Exception {
        String payload = """
                {
                    "code": "gh-code-taken"
                }
                """;

        when(oAuthService.linkGitHubAccount(eq(sampleUserId), eq("gh-code-taken"), any()))
                .thenThrow(new ConflictException(
                        "This GitHub account is already linked to another ShipIt user.",
                        "GITHUB_ALREADY_LINKED"
                ));

        mockMvc.perform(post("/api/v1/users/me/identities/github/link")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("GITHUB_ALREADY_LINKED"));
    }

    @Test
    @DisplayName("Should return 200 OK when unlinking a secondary identity")
    void shouldUnlinkIdentitySuccessfully() throws Exception {
        doNothing().when(oAuthService).unlinkIdentity(sampleUserId, AuthProvider.GITHUB);

        mockMvc.perform(delete("/api/v1/users/me/identities/GITHUB"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Identity unlinked successfully"));

        verify(oAuthService).unlinkIdentity(sampleUserId, AuthProvider.GITHUB);
    }

    @Test
    @DisplayName("Should return 400 Bad Request when attempting to unlink sole authentication identity")
    void shouldRejectUnlinkingSoleIdentity() throws Exception {
        doThrow(new BadRequestException(
                "Cannot unlink your sole authentication method. Please add another identity or set a password first.",
                "CANNOT_UNLINK_SOLE_IDENTITY"
        )).when(oAuthService).unlinkIdentity(sampleUserId, AuthProvider.LOCAL);

        mockMvc.perform(delete("/api/v1/users/me/identities/LOCAL"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("CANNOT_UNLINK_SOLE_IDENTITY"));
    }
}
