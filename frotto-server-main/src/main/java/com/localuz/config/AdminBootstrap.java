package com.localuz.config;

import com.localuz.domain.Authority;
import com.localuz.domain.User;
import com.localuz.repository.AuthorityRepository;
import com.localuz.repository.UserRepository;
import com.localuz.security.AuthoritiesConstants;
import java.util.Locale;
import java.util.Optional;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Operational bootstrap for promoting ONE pre-existing user to ROLE_ADMIN from server-side
 * configuration only - never from an HTTP endpoint, never triggered by a request or JWT (see
 * the Billing Etapa 4A report for why: an admin-promotion endpoint is a privilege-escalation
 * surface by definition, and this project has no admin users yet to gate it with).
 *
 * Controlled entirely by admin.bootstrap.enabled (default false) and admin.bootstrap.login.
 * Both are read once at startup; there is no live/hot-reload behavior and none is needed - the
 * intended flow is: set the two env vars for one deploy, confirm the promotion in the logs,
 * then set ADMIN_BOOTSTRAP_ENABLED=false again (see section 5 of the report). jhi_authority
 * already seeds ROLE_ADMIN/ROLE_USER (config/liquibase/data/authority.csv, applied since the
 * very first migration) - this class only ever links an existing user to an existing
 * Authority row via jhi_user_authority; it never creates authorities, users, or touches
 * passwords.
 *
 * Idempotent by construction: re-running with the same login when the user already has
 * ROLE_ADMIN is a no-op (checked before writing, and User.authorities is a Set so a duplicate
 * link is structurally impossible even if that check were skipped).
 *
 * Failure mode when login is blank or doesn't resolve to an existing user: logged as an ERROR,
 * startup continues normally. Deliberately not a fail-fast (context-refresh-aborting) failure -
 * see the report for the reasoning: skipping the promotion is a fail-closed outcome (nobody
 * gets elevated), never fail-open, so there is no security downside to keeping the app running
 * and letting an operator fix the misconfigured login/enabled flag and redeploy.
 */
@Component
public class AdminBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

    @Value("${admin.bootstrap.enabled:false}")
    private boolean enabled;

    @Value("${admin.bootstrap.login:}")
    private String login;

    private final UserRepository userRepository;
    private final AuthorityRepository authorityRepository;

    public AdminBootstrap(UserRepository userRepository, AuthorityRepository authorityRepository) {
        this.userRepository = userRepository;
        this.authorityRepository = authorityRepository;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!enabled) {
            return;
        }
        if (StringUtils.isBlank(login)) {
            log.error("admin.bootstrap.enabled=true but admin.bootstrap.login is blank; skipping admin bootstrap.");
            return;
        }

        String normalizedLogin = login.trim().toLowerCase(Locale.ENGLISH);
        Optional<User> userOpt = userRepository.findOneWithAuthoritiesByLogin(normalizedLogin);
        if (userOpt.isEmpty()) {
            log.error("Admin bootstrap: no existing user found for login '{}'; skipping.", normalizedLogin);
            return;
        }

        User user = userOpt.get();
        boolean alreadyAdmin = user.getAuthorities().stream().map(Authority::getName).anyMatch(AuthoritiesConstants.ADMIN::equals);
        if (alreadyAdmin) {
            log.info("Admin bootstrap: user '{}' already has {}; no change made.", normalizedLogin, AuthoritiesConstants.ADMIN);
            return;
        }

        Authority adminAuthority = authorityRepository
            .findById(AuthoritiesConstants.ADMIN)
            .orElseThrow(() ->
                new IllegalStateException(AuthoritiesConstants.ADMIN + " is missing from jhi_authority - check the seed data")
            );
        user.getAuthorities().add(adminAuthority);
        userRepository.save(user);
        log.info("Admin bootstrap: granted {} to user '{}'.", AuthoritiesConstants.ADMIN, normalizedLogin);
    }
}
