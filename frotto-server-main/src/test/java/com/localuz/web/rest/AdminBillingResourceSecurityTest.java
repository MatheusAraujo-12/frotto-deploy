package com.localuz.web.rest;

import static org.assertj.core.api.Assertions.assertThat;

import com.localuz.security.AuthoritiesConstants;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

/**
 * Structural safety net for "every /api/admin/billing/** endpoint requires ROLE_ADMIN",
 * verified via reflection rather than a live Spring Security filter chain.
 *
 * This is NOT a substitute for a real MockMvc/@SpringBootTest security test proving a
 * ROLE_USER caller actually gets rejected end-to-end - it only catches "someone added a new
 * admin method and forgot the annotation" or "someone removed the class-level @PreAuthorize".
 * A true integration test wasn't added in this stage: this project has zero existing
 * @SpringBootTest/MockMvc tests, and the Testcontainers/MySQL infra it would need
 * (src/test/resources/testcontainers.properties) requires Docker, which is not running in
 * this environment. See the Billing Etapa 3 report, section "Testes", for the recommendation
 * to add that integration test as a follow-up once Docker/CI can run it reliably.
 *
 * Independently of this test: "/api/admin/**" -> hasAuthority(ROLE_ADMIN) is already enforced
 * by SecurityConfiguration's URL-pattern matcher (unchanged by Etapa 3), which is a second,
 * separate enforcement layer this test does not cover either.
 */
class AdminBillingResourceSecurityTest {

    @Test
    void classIsAnnotatedWithRoleAdminPreAuthorize() {
        PreAuthorize annotation = AdminBillingResource.class.getAnnotation(PreAuthorize.class);

        assertThat(annotation).isNotNull();
        assertThat(annotation.value()).contains(AuthoritiesConstants.ADMIN);
    }

    @Test
    void everyPublicEndpointMethodIsCoveredByTheClassLevelAnnotationOrItsOwn() {
        boolean classIsProtected = AdminBillingResource.class.isAnnotationPresent(PreAuthorize.class);

        for (Method method : AdminBillingResource.class.getDeclaredMethods()) {
            if (!Modifier.isPublic(method.getModifiers())) {
                continue;
            }
            boolean methodIsProtected = method.isAnnotationPresent(PreAuthorize.class);
            assertThat(classIsProtected || methodIsProtected)
                .as("AdminBillingResource#%s must be covered by @PreAuthorize(ROLE_ADMIN), directly or via the class", method.getName())
                .isTrue();
        }
    }
}
