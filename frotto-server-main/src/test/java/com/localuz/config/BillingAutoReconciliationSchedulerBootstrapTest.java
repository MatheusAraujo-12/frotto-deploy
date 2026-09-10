package com.localuz.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;

import com.localuz.repository.BillingCheckoutRepository;
import com.localuz.service.MercadoPagoWebhookProcessor;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Regression test for a real staging crash loop: BillingAutoReconciliationScheduler had two
 * constructors (the public production one and a package-private one taking a Clock for tests)
 * with neither annotated @Autowired. Spring's AutowiredAnnotationBeanPostProcessor cannot pick a
 * candidate constructor among several unannotated ones, falls back to a no-arg constructor that
 * doesn't exist, and bean creation fails with NoSuchMethodException(<init>()) - during
 * ApplicationContext startup, before the billing.reconciliation.auto.enabled flag is even read,
 * which is why the flag being false did not prevent the crash.
 *
 * A source-text search for "@Autowired" would not have caught this (it would also "pass" if the
 * annotation were on the wrong constructor, or if a third constructor were added later without
 * one). This instead registers the real class - not a hand-written @Bean factory method, which
 * would just call `new BillingAutoReconciliationScheduler(...)` directly and never exercise
 * constructor resolution at all - on a real (minimal, DB-free) Spring ApplicationContext and lets
 * Spring's own AutowiredAnnotationBeanPostProcessor pick and invoke the constructor, exactly like
 * component-scanning does during full application startup.
 */
class BillingAutoReconciliationSchedulerBootstrapTest {

    @Configuration
    static class CollaboratorBeans {
        @Bean
        MercadoPagoProperties mercadoPagoProperties() {
            return new MercadoPagoProperties();
        }

        @Bean
        BillingCheckoutRepository billingCheckoutRepository() {
            return mock(BillingCheckoutRepository.class);
        }

        @Bean
        MercadoPagoWebhookProcessor mercadoPagoWebhookProcessor() {
            return mock(MercadoPagoWebhookProcessor.class);
        }
    }

    @Test
    void springCanInstantiateTheSchedulerThroughItsOwnConstructorResolution() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.register(CollaboratorBeans.class, BillingAutoReconciliationScheduler.class);

            assertThatCode(context::refresh).doesNotThrowAnyException();
            assertThat(context.getBean(BillingAutoReconciliationScheduler.class)).isNotNull();
        }
    }

    @Test
    void hasNoArtificialNoArgConstructor() {
        boolean hasNoArgConstructor = Arrays.stream(BillingAutoReconciliationScheduler.class.getDeclaredConstructors()).anyMatch(
            constructor -> constructor.getParameterCount() == 0
        );
        assertThat(hasNoArgConstructor).isFalse();
    }
}
