package com.shipit.backend.auth.security;

import com.shipit.backend.user.entity.Role;
import com.shipit.backend.user.entity.User;
import com.shipit.backend.user.entity.UserStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Getter
@Builder
@AllArgsConstructor
public class UserPrincipal implements UserDetails {

    private final UUID id;
    private final String name;
    private final String email;
    private final String password;
    private final Role role;
    private final UserStatus status;
    private final boolean emailVerified;
    private final Collection<? extends GrantedAuthority> authorities;

    public static UserPrincipal create(User user, String passwordHash) {
        List<GrantedAuthority> authorities = Collections.singletonList(
                new SimpleGrantedAuthority(user.getRole().name())
        );

        return UserPrincipal.builder()
                .id(user.getId())
                .name(user.getName())
                .email(user.getEmail())
                .password(passwordHash)
                .role(user.getRole())
                .status(user.getStatus())
                .emailVerified(user.isEmailVerified())
                .authorities(authorities)
                .build();
    }

    public static UserPrincipal create(User user) {
        return create(user, null);
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return status != UserStatus.SUSPENDED;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return status != UserStatus.SUSPENDED;
    }
}
