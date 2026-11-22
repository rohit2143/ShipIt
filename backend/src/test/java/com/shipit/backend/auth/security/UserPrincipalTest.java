package com.shipit.backend.auth.security;

import com.shipit.backend.user.entity.Role;
import com.shipit.backend.user.entity.User;
import com.shipit.backend.user.entity.UserStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class UserPrincipalTest {

    @Test
    @DisplayName("Should build UserPrincipal from User entity with appropriate authorities")
    void shouldBuildUserPrincipal() {
        UUID userId = UUID.randomUUID();
        User user = User.builder()
                .id(userId)
                .name("Ellen Ripley")
                .email("ripley@weyland.corp")
                .role(Role.ROLE_ADMIN)
                .status(UserStatus.ACTIVE)
                .emailVerified(true)
                .build();

        UserPrincipal principal = UserPrincipal.create(user, "encoded_hash_123");

        assertThat(principal.getId()).isEqualTo(userId);
        assertThat(principal.getName()).isEqualTo("Ellen Ripley");
        assertThat(principal.getUsername()).isEqualTo("ripley@weyland.corp");
        assertThat(principal.getPassword()).isEqualTo("encoded_hash_123");
        assertThat(principal.isAccountNonLocked()).isTrue();
        assertThat(principal.isEnabled()).isTrue();

        assertThat(principal.getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_ADMIN");
    }

    @Test
    @DisplayName("Should report account locked when user status is SUSPENDED")
    void shouldReportSuspendedUserAsLocked() {
        User user = User.builder()
                .id(UUID.randomUUID())
                .name("Suspended User")
                .email("locked@shipit.dev")
                .role(Role.ROLE_USER)
                .status(UserStatus.SUSPENDED)
                .build();

        UserPrincipal principal = UserPrincipal.create(user);

        assertThat(principal.isAccountNonLocked()).isFalse();
        assertThat(principal.isEnabled()).isFalse();
    }
}
