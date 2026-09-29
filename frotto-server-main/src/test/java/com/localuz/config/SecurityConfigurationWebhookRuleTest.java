package com.localuz.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * Structural safety net for the {@code POST /api/webhooks/mercadopago} permitAll rule, verified
 * against the actual {@link SecurityConfiguration} source rather than a live Spring Security
 * filter chain.
 *
 * <p>This project cannot bootstrap any Spring {@code TestContext} in this environment - not just
 * {@code @SpringBootTest}: {@code @WebMvcTest} fails identically here with
 * {@code ClassNotFoundException: TestContainersSpringContextCustomizerFactory} (no Docker), and a
 * manually-constructed {@code HttpSecurity} outside a real {@code ApplicationContext} was tried
 * and produces {@code AuthorizationManager} decisions that don't reflect real Spring Boot wiring
 * (every request resolved to a {@code null} decision, including ones that must be denied) - not
 * trustworthy enough to assert on. See {@code AdminBillingResourceSecurityTest} for the same,
 * previously-documented limitation for {@code /api/admin/**}.
 *
 * <p>What this test protects against: someone widening the webhook rule to a broad wildcard
 * ({@code /api/webhooks/**} or {@code /api/**}), someone permitting other HTTP methods on the
 * same path, or someone moving the rule after the generic {@code "/api/**"} -&gt;
 * {@code authenticated()} catch-all. Spring Security's {@code authorizeHttpRequests()} matches
 * rules in declaration order - first match wins - so ordering here is load-bearing, not cosmetic:
 * this is exactly the property that was silently broken to cause the staging incident (the
 * webhook rejected with "Full authentication is required to access this resource" before ever
 * reaching {@code MercadoPagoWebhookResource#receive}).
 *
 * <p>{@link MercadoPagoWebhookResourceTest} independently proves the controller's own HMAC
 * rejection path (a 401 with an empty body from {@code MercadoPagoWebhookSignatureValidator}
 * returning false) is unrelated to and unaffected by this Spring Security rule.
 */
class SecurityConfigurationWebhookRuleTest {

    private static final Path SOURCE = Path.of("src/main/java/com/localuz/config/SecurityConfiguration.java");
    private static final String WEBHOOK_RULE = ".antMatchers(HttpMethod.POST, \"/api/webhooks/mercadopago\")";
    private static final String GENERIC_API_CATCH_ALL = ".antMatchers(\"/api/**\")";

    private String source() throws IOException {
        assertThat(Files.exists(SOURCE))
            .as("expected to find %s relative to the Maven module working directory", SOURCE)
            .isTrue();
        return Files.readString(SOURCE);
    }

    @Test
    void webhookPostRuleIsPresentAndExplicit() throws IOException {
        assertThat(source()).contains(WEBHOOK_RULE).contains(".permitAll()");
    }

    @Test
    void noBroadWildcardWasIntroducedForWebhooks() throws IOException {
        String content = source();
        assertThat(content).doesNotContain("\"/api/webhooks/**\"").doesNotContain("\"/api/webhooks\"");
    }

    @Test
    void webhookRuleIsDeclaredBeforeTheGenericApiCatchAll() throws IOException {
        String content = source();
        int webhookRuleIndex = content.indexOf(WEBHOOK_RULE);
        int catchAllIndex = content.indexOf(GENERIC_API_CATCH_ALL);

        assertThat(webhookRuleIndex).as("webhook rule must exist").isGreaterThanOrEqualTo(0);
        assertThat(catchAllIndex).as("generic /api/** catch-all must exist").isGreaterThanOrEqualTo(0);
        assertThat(webhookRuleIndex)
            .as(
                "the webhook permitAll rule must be declared before the generic /api/** " +
                "authenticated() rule: authorizeHttpRequests() evaluates rules in declaration " +
                "order and the first match wins, so a later position here would silently make " +
                "the webhook require authentication again"
            )
            .isLessThan(catchAllIndex);
    }

    @Test
    void onlyOnePermitAllRuleTargetsTheWebhookPath() throws IOException {
        Matcher matcher = Pattern.compile(Pattern.quote("\"/api/webhooks/mercadopago\"")).matcher(source());
        int occurrences = 0;
        while (matcher.find()) {
            occurrences++;
        }
        assertThat(occurrences)
            .as("the webhook path literal should appear exactly once - a single POST-only permitAll rule, not one per HTTP method")
            .isEqualTo(1);
    }

    @Test
    void genericApiCatchAllStillRequiresAuthentication() throws IOException {
        String content = source();
        int catchAllIndex = content.indexOf(GENERIC_API_CATCH_ALL);
        assertThat(catchAllIndex).isGreaterThanOrEqualTo(0);
        String afterCatchAll = content.substring(catchAllIndex, Math.min(content.length(), catchAllIndex + 120));
        assertThat(afterCatchAll).contains(".authenticated()");
    }
}
