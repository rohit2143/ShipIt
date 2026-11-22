package com.shipit.backend.auth.security;

import com.shipit.backend.auth.service.JwtService;
import com.shipit.backend.config.JwtProperties;
import com.shipit.backend.user.entity.Role;
import com.shipit.backend.user.entity.User;
import com.shipit.backend.user.entity.UserStatus;
import com.shipit.backend.user.repository.UserRepository;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class SecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private JwtProperties jwtProperties;

    @Test
    @DisplayName("Should return 401 Unauthorized when accessing protected endpoint without token")
    void shouldReturn401WhenNoTokenProvided() throws Exception {
        mockMvc.perform(get("/api/v1/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("Should return 401 Unauthorized when accessing protected endpoint with invalid JWT")
    void shouldReturn401WhenInvalidJwtProvided() throws Exception {
        mockMvc.perform(get("/api/v1/users/me")
                        .header("Authorization", "Bearer invalid.malformed.jwt"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("Should return 401 Unauthorized when accessing protected endpoint with expired JWT")
    void shouldReturn401WhenExpiredJwtProvided() throws Exception {
        SecretKey key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(jwtProperties.getSecret()));
        String expiredToken = Jwts.builder()
                .subject(UUID.randomUUID().toString())
                .claim("email", "expired@shipit.dev")
                .claim("role", "ROLE_USER")
                .issuedAt(Date.from(Instant.now().minusSeconds(3600)))
                .expiration(Date.from(Instant.now().minusSeconds(1800)))
                .signWith(key)
                .compact();

        mockMvc.perform(get("/api/v1/users/me")
                        .header("Authorization", "Bearer " + expiredToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("Should allow access to protected endpoint when valid JWT is provided")
    void shouldAllowAccessWithValidJwt() throws Exception {
        User testUser = userRepository.save(User.builder()
                .name("Security Integration Tester")
                .email("sec-integration@shipit.dev")
                .role(Role.ROLE_USER)
                .status(UserStatus.ACTIVE)
                .emailVerified(true)
                .build());

        String validToken = jwtService.generateAccessToken(testUser);

        mockMvc.perform(get("/api/v1/users/me")
                        .header("Authorization", "Bearer " + validToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(testUser.getId().toString()))
                .andExpect(jsonPath("$.data.email").value("sec-integration@shipit.dev"));
    }

    @Test
    @DisplayName("Should allow anonymous access to public auth endpoints without 401")
    void shouldAllowAnonymousAccessToPublicAuthEndpoints() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("Should allow anonymous access to Actuator health endpoint")
    void shouldAllowAnonymousAccessToActuatorHealth() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }
}
