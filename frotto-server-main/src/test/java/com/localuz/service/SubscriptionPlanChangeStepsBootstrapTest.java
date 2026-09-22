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
 * SubscriptionPlanChangeSteps has two constructors (the public production one and a
 * package-private one taking a Clock for tests), the same shape that previously caused a real
 * staging crash loop for SubscriptionCancellationSteps/BillingAutoReconciliationScheduler
 * (BeanCreationException / NoSuchMethodException(&lt;init&gt;())) because Spring cannot pick a
 * candidate constructor among several unannotated ones. This boots a real (minimal, DB-free)
 * Spring context and lets Spring's own constructor resolution build the bean, instead of just
 * grepping the source for "@Autowired".
 */
class SubscriptionPlanChangeStepsBootstrapTest {

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
        RecurringSubscriptionGuardService recurringSubscriptionGuardService() {
            return mock(RecurringSubscriptionGuardService.class);
        }

        @Bean
        SubscriptionFinancialCoverageService subscriptionFinancialCoverageService() {
            return mock(SubscriptionFinancialCoverageService.class);
        }
    }

    @Test
    void springCanInstantiateStepsThroughItsOwnConstructorResolution() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.register(CollaboratorBeans.class, SubscriptionPlanChangeSteps.class);

            assertThatCode(context::refresh).doesNotThrowAnyException();
            assertThat(context.getBean(SubscriptionPlanChangeSteps.class)).isNotNull();
        }
    }
}
