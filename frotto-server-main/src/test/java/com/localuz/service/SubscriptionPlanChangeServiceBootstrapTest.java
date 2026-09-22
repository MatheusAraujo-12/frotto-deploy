package com.localuz.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;

import com.localuz.repository.CarRepository;
import com.localuz.repository.PlanRepository;
import com.localuz.repository.SubscriptionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Same dual-constructor Spring wiring concern as SubscriptionPlanChangeStepsBootstrapTest - see its javadoc. */
class SubscriptionPlanChangeServiceBootstrapTest {

    @Configuration
    static class CollaboratorBeans {
        @Bean
        SubscriptionPlanChangeSteps subscriptionPlanChangeSteps() {
            return mock(SubscriptionPlanChangeSteps.class);
        }

        @Bean
        SubscriptionCancellationService subscriptionCancellationService() {
            return mock(SubscriptionCancellationService.class);
        }

        @Bean
        MercadoPagoClient mercadoPagoClient() {
            return mock(MercadoPagoClient.class);
        }

        @Bean
        PricingService pricingService() {
            return mock(PricingService.class);
        }

        @Bean
        PlanRepository planRepository() {
            return mock(PlanRepository.class);
        }

        @Bean
        SubscriptionRepository subscriptionRepository() {
            return mock(SubscriptionRepository.class);
        }

        @Bean
        CarRepository carRepository() {
            return mock(CarRepository.class);
        }
    }

    @Test
    void springCanInstantiateTheServiceThroughItsOwnConstructorResolution() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.register(CollaboratorBeans.class, SubscriptionPlanChangeService.class);

            assertThatCode(context::refresh).doesNotThrowAnyException();
            assertThat(context.getBean(SubscriptionPlanChangeService.class)).isNotNull();
        }
    }
}
