package com.localuz.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;

import com.localuz.repository.SubscriptionRepository;
import com.localuz.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * SubscriptionCancellationSteps has two constructors (the public production one and a
 * package-private one taking a Clock for tests), same shape as BillingAutoReconciliationScheduler
 * before its @Autowired fix - and that shape previously caused a real staging crash loop
 * (BeanCreationException / NoSuchMethodException(&lt;init&gt;())) because Spring cannot pick a
 * candidate constructor among several unannotated ones. This boots a real (minimal, DB-free)
 * Spring context and lets Spring's own constructor resolution build the bean, instead of just
 * grepping the source for "@Autowired".
 */
class SubscriptionCancellationStepsBootstrapTest {

    @Configuration
    static class CollaboratorBeans {
        @Bean
        UserRepository userRepository() {
            return mock(UserRepository.class);
        }

        @Bean
        SubscriptionRepository subscriptionRepository() {
            return mock(SubscriptionRepository.class);
        }

        @Bean
        MercadoPagoClient mercadoPagoClient() {
            return mock(MercadoPagoClient.class);
        }
    }

    @Test
    void springCanInstantiateStepsThroughItsOwnConstructorResolution() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.register(CollaboratorBeans.class, SubscriptionCancellationSteps.class);

            assertThatCode(context::refresh).doesNotThrowAnyException();
            assertThat(context.getBean(SubscriptionCancellationSteps.class)).isNotNull();
        }
    }
}
