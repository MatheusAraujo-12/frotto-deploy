package com.localuz.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.localuz.domain.User;
import com.localuz.repository.AuthorityRepository;
import com.localuz.repository.UserRepository;
import com.localuz.service.dto.AdminUserDTO;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * The avatar key (image_url) is only changed by MeService upload/remove: the generic account endpoints
 * (/api/register, /api/account, admin user management) can no longer make the backend treat an arbitrary key or
 * URL as a stored file.
 */
class UserServiceImageUrlProtectionTest {

    private static final String ATTACK = "../../etc/passwd";
    private static final String STORED_KEY = "avatars/2026/10/3f2a8c1e-0b6d-4c5e-9a1f-2b3c4d5e6f70.png";

    private UserRepository userRepository;
    private UserService service;
    private User existing;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        AuthorityRepository authorityRepository = mock(AuthorityRepository.class);
        when(authorityRepository.findById(anyString())).thenReturn(Optional.empty());
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        when(passwordEncoder.encode(any())).thenReturn("hash");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        service = new UserService(userRepository, passwordEncoder, authorityRepository, new ConcurrentMapCacheManager());

        existing = new User();
        existing.setId(7L);
        existing.setLogin("owner@frotto.test");
        existing.setEmail("owner@frotto.test");
        existing.setImageUrl(STORED_KEY);
        when(userRepository.findOneByLogin("owner@frotto.test")).thenReturn(Optional.of(existing));
        when(userRepository.findById(7L)).thenReturn(Optional.of(existing));
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void accountUpdateNeverChangesTheAvatarKey() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("owner@frotto.test", "x"));

        service.updateUser("Ana", "Silva", "owner@frotto.test", "pt-br");

        assertThat(existing.getFirstName()).isEqualTo("Ana");
        assertThat(existing.getImageUrl()).isEqualTo(STORED_KEY);
    }

    @Test
    void registrationIgnoresClientSuppliedImageUrl() {
        when(userRepository.findOneByEmailIgnoreCase(anyString())).thenReturn(Optional.empty());
        AdminUserDTO dto = new AdminUserDTO();
        dto.setEmail("new@frotto.test");
        dto.setImageUrl(ATTACK);

        User created = service.registerUser(dto, "secret-password");

        assertThat(created.getImageUrl()).isNull();
    }

    @Test
    void adminCreateIgnoresClientSuppliedImageUrl() {
        AdminUserDTO dto = new AdminUserDTO();
        dto.setLogin("new-admin-made@frotto.test");
        dto.setImageUrl("https://evil.example/avatar.png");

        assertThat(service.createUser(dto).getImageUrl()).isNull();
    }

    @Test
    void adminUpdateKeepsTheStoredAvatarKey() {
        AdminUserDTO dto = new AdminUserDTO();
        dto.setId(7L);
        dto.setLogin("owner@frotto.test");
        dto.setImageUrl(ATTACK);
        dto.setAuthorities(Set.of());

        service.updateUser(dto);

        assertThat(existing.getImageUrl()).isEqualTo(STORED_KEY);
    }
}
