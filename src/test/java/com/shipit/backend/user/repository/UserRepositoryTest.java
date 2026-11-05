package com.shipit.backend.user.repository;

import com.shipit.backend.auth.entity.AuthProvider;
import com.shipit.backend.auth.entity.UserAuthIdentity;
import com.shipit.backend.auth.repository.UserAuthIdentityRepository;
import com.shipit.backend.user.entity.Role;
import com.shipit.backend.user.entity.User;
import com.shipit.backend.user.entity.UserStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class UserRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserAuthIdentityRepository identityRepository;

    @Test
    @DisplayName("Should persist user and corresponding auth identity")
    void shouldPersistUserAndIdentity() {
        User user = User.builder()
                .name("Alex Engineer")
                .email("alex@shipit.dev")
                .role(Role.ROLE_USER)
                .status(UserStatus.ACTIVE)
                .emailVerified(true)
                .build();

        User savedUser = userRepository.save(user);
        assertThat(savedUser.getId()).isNotNull();
        assertThat(savedUser.getCreatedAt()).isNotNull();
        assertThat(savedUser.getUpdatedAt()).isNotNull();

        UserAuthIdentity identity = UserAuthIdentity.builder()
                .user(savedUser)
                .provider(AuthProvider.LOCAL)
                .providerUserId("alex@shipit.dev")
                .passwordHash("$2a$12$dummyHashedPassword")
                .build();

        UserAuthIdentity savedIdentity = identityRepository.save(identity);
        assertThat(savedIdentity.getId()).isNotNull();

        Optional<User> fetchedUser = userRepository.findByEmail("alex@shipit.dev");
        assertThat(fetchedUser).isPresent();
        assertThat(fetchedUser.get().getName()).isEqualTo("Alex Engineer");

        Optional<UserAuthIdentity> fetchedIdentity =
                identityRepository.findByProviderAndProviderUserId(AuthProvider.LOCAL, "alex@shipit.dev");
        assertThat(fetchedIdentity).isPresent();
        assertThat(fetchedIdentity.get().getUser().getId()).isEqualTo(savedUser.getId());
    }

    @Test
    @DisplayName("Should accurately report existsByEmail for existing and non-existing emails")
    void shouldCheckExistsByEmail() {
        User user = User.builder()
                .name("Check Exists")
                .email("check@shipit.dev")
                .role(Role.ROLE_USER)
                .status(UserStatus.ACTIVE)
                .emailVerified(true)
                .build();
        userRepository.saveAndFlush(user);

        assertThat(userRepository.existsByEmail("check@shipit.dev")).isTrue();
        assertThat(userRepository.existsByEmail("nonexistent@shipit.dev")).isFalse();
    }

    @Test
    @DisplayName("Should enforce unique database constraint on email column")
    void shouldEnforceUniqueEmailConstraint() {
        User user1 = User.builder()
                .name("First User")
                .email("duplicate@shipit.dev")
                .role(Role.ROLE_USER)
                .status(UserStatus.ACTIVE)
                .emailVerified(true)
                .build();
        userRepository.saveAndFlush(user1);

        User user2 = User.builder()
                .name("Second User")
                .email("duplicate@shipit.dev")
                .role(Role.ROLE_USER)
                .status(UserStatus.ACTIVE)
                .emailVerified(true)
                .build();

        assertThatThrownBy(() -> userRepository.saveAndFlush(user2))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
