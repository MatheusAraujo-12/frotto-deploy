package com.localuz.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.localuz.domain.Authority;
import com.localuz.domain.User;
import com.localuz.repository.AuthorityRepository;
import com.localuz.repository.UserRepository;
import com.localuz.security.AuthoritiesConstants;
import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

/**
 * enabled/login are normally set by @Value/Spring; set directly via reflection here since this
 * is a plain unit test with no Spring context (matching this project's existing test style).
 */
class AdminBootstrapTest {

    private UserRepository userRepository;
    private AuthorityRepository authorityRepository;
    private AdminBootstrap adminBootstrap;

    @BeforeEach
    void setUp() {
        userRepository = Mockito.mock(UserRepository.class);
        authorityRepository = Mockito.mock(AuthorityRepository.class);
        adminBootstrap = new AdminBootstrap(userRepository, authorityRepository);
    }

    private void configure(boolean enabled, String login) throws ReflectiveOperationException {
        setField("enabled", enabled);
        setField("login", login);
    }

    private void setField(String name, Object value) throws ReflectiveOperationException {
        Field field = AdminBootstrap.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(adminBootstrap, value);
    }

    private static Authority authority(String name) {
        Authority authority = new Authority();
        authority.setName(name);
        return authority;
    }

    private static User userWithAuthorities(String... authorityNames) {
        User user = new User();
        user.setId(7L);
        user.setLogin("cliente");
        Set<Authority> authorities = new HashSet<>();
        for (String name : authorityNames) {
            authorities.add(authority(name));
        }
        user.setAuthorities(authorities);
        return user;
    }

    private static Set<String> authorityNamesOf(User user) {
        return user.getAuthorities().stream().map(Authority::getName).collect(Collectors.toSet());
    }

    @Test
    void disabledMakesNoChangeAtAll() throws Exception {
        configure(false, "cliente");

        adminBootstrap.run(null);

        Mockito.verifyNoInteractions(userRepository, authorityRepository);
    }

    @Test
    void enabledPromotesAnExistingUserToAdmin() throws Exception {
        configure(true, "cliente");
        User user = userWithAuthorities(AuthoritiesConstants.USER);
        when(userRepository.findOneWithAuthoritiesByLogin("cliente")).thenReturn(Optional.of(user));
        when(authorityRepository.findById(AuthoritiesConstants.ADMIN)).thenReturn(Optional.of(authority(AuthoritiesConstants.ADMIN)));

        adminBootstrap.run(null);

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        Mockito.verify(userRepository).save(saved.capture());
        assertThat(authorityNamesOf(saved.getValue())).contains(AuthoritiesConstants.ADMIN);
    }

    @Test
    void roleUserIsNeverRemovedWhenPromoting() throws Exception {
        configure(true, "cliente");
        User user = userWithAuthorities(AuthoritiesConstants.USER);
        when(userRepository.findOneWithAuthoritiesByLogin("cliente")).thenReturn(Optional.of(user));
        when(authorityRepository.findById(AuthoritiesConstants.ADMIN)).thenReturn(Optional.of(authority(AuthoritiesConstants.ADMIN)));

        adminBootstrap.run(null);

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        Mockito.verify(userRepository).save(saved.capture());
        assertThat(authorityNamesOf(saved.getValue())).contains(AuthoritiesConstants.USER, AuthoritiesConstants.ADMIN);
    }

    @Test
    void alreadyAdminIsIdempotentAndNeverSaves() throws Exception {
        configure(true, "cliente");
        User user = userWithAuthorities(AuthoritiesConstants.USER, AuthoritiesConstants.ADMIN);
        when(userRepository.findOneWithAuthoritiesByLogin("cliente")).thenReturn(Optional.of(user));

        adminBootstrap.run(null);

        Mockito.verify(userRepository, Mockito.never()).save(any(User.class));
        Mockito.verifyNoInteractions(authorityRepository);
    }

    @Test
    void doesNotDuplicateTheAuthorityEvenIfCalledTwice() throws Exception {
        configure(true, "cliente");
        User user = userWithAuthorities(AuthoritiesConstants.USER);
        when(userRepository.findOneWithAuthoritiesByLogin("cliente")).thenReturn(Optional.of(user));
        when(authorityRepository.findById(AuthoritiesConstants.ADMIN)).thenReturn(Optional.of(authority(AuthoritiesConstants.ADMIN)));

        adminBootstrap.run(null);
        // Second run: same user now already has ROLE_ADMIN from the first run's mutation.
        adminBootstrap.run(null);

        Mockito.verify(userRepository, Mockito.times(1)).save(any(User.class));
        assertThat(authorityNamesOf(user).stream().filter(AuthoritiesConstants.ADMIN::equals).count()).isEqualTo(1);
    }

    @Test
    void nonExistentLoginFailsSafelyWithoutThrowing() throws Exception {
        configure(true, "ghost");
        when(userRepository.findOneWithAuthoritiesByLogin("ghost")).thenReturn(Optional.empty());

        assertThatCode(() -> adminBootstrap.run(null)).doesNotThrowAnyException();

        Mockito.verify(userRepository, Mockito.never()).save(any(User.class));
        Mockito.verifyNoInteractions(authorityRepository);
    }

    @Test
    void blankLoginFailsSafelyWithoutThrowing() throws Exception {
        configure(true, "  ");

        assertThatCode(() -> adminBootstrap.run(null)).doesNotThrowAnyException();

        Mockito.verifyNoInteractions(userRepository, authorityRepository);
    }

    @Test
    void loginIsNormalizedToLowercaseAndTrimmedBeforeLookup() throws Exception {
        configure(true, "  ClienteVIP  ");
        User user = userWithAuthorities(AuthoritiesConstants.USER);
        when(userRepository.findOneWithAuthoritiesByLogin("clientevip")).thenReturn(Optional.of(user));
        when(authorityRepository.findById(AuthoritiesConstants.ADMIN)).thenReturn(Optional.of(authority(AuthoritiesConstants.ADMIN)));

        adminBootstrap.run(null);

        Mockito.verify(userRepository).findOneWithAuthoritiesByLogin("clientevip");
        Mockito.verify(userRepository).save(any(User.class));
    }
}
