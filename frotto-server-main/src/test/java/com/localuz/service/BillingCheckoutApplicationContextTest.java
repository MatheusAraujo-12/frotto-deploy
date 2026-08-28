package com.localuz.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.localuz.config.MercadoPagoProperties;
import com.localuz.repository.BillingCheckoutRepository;
import com.localuz.repository.CarRepository;
import com.localuz.repository.PlanPricingTierRepository;
import com.localuz.repository.PlanRepository;
import com.localuz.repository.SubscriptionRepository;
import com.localuz.repository.UserRepository;
import com.localuz.web.rest.BillingResource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class BillingCheckoutApplicationContextTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withBean(MercadoPagoProperties.class)
        .withBean(ObjectMapper.class)
        .withBean(PlanRepository.class, () -> mock(PlanRepository.class))
        .withBean(PlanPricingTierRepository.class, () -> mock(PlanPricingTierRepository.class))
        .withBean(BillingCheckoutRepository.class, () -> mock(BillingCheckoutRepository.class))
        .withBean(SubscriptionRepository.class, () -> mock(SubscriptionRepository.class))
        .withBean(CarRepository.class, () -> mock(CarRepository.class))
        .withBean(UserRepository.class, () -> mock(UserRepository.class))
        .withBean(UserService.class, () -> mock(UserService.class))
        .withBean(EntitlementService.class, () -> mock(EntitlementService.class))
        .withBean(PricingService.class)
        .withBean(MercadoPagoHttpClient.class)
        .withBean(BillingCheckoutService.class)
        .withBean(BillingPaymentStateService.class);

    @Test
    void springCreatesBillingCheckoutServiceWithTheConcreteMercadoPagoClient() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(BillingCheckoutService.class);
            assertThat(context).hasSingleBean(MercadoPagoClient.class);
            assertThat(context.getBean(MercadoPagoClient.class)).isInstanceOf(MercadoPagoHttpClient.class);
        });
    }

    @Test
    void springInjectsBillingCheckoutServiceIntoBillingResource() {
        contextRunner.withBean(BillingResource.class).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(BillingResource.class);
            assertThat(context).hasSingleBean(BillingCheckoutService.class);
        });
    }
}
