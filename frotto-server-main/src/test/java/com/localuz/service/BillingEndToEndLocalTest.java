package com.localuz.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.localuz.config.MercadoPagoProperties;
import com.localuz.domain.BillingCheckout;
import com.localuz.domain.Plan;
import com.localuz.domain.Subscription;
import com.localuz.domain.User;
import com.localuz.domain.enumeration.BillingCheckoutStatus;
import com.localuz.domain.enumeration.PlanCode;
import com.localuz.domain.enumeration.SubscriptionSource;
import com.localuz.domain.enumeration.SubscriptionStatus;
import com.localuz.repository.BillingCheckoutRepository;
import com.localuz.repository.CarRepository;
import com.localuz.repository.MercadoPagoWebhookEventRepository;
import com.localuz.repository.PlanRepository;
import com.localuz.repository.SubscriptionRepository;
import com.localuz.repository.UserRepository;
import com.localuz.service.dto.BillingMeDTO;
import com.localuz.service.dto.BillingPaymentStateDTO;
import com.localuz.service.dto.MercadoPagoPreapproval;
import com.localuz.service.dto.PricingResult;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class BillingEndToEndLocalTest {

    @Test
    void freeToBronzeCheckoutWebhookAndReadModelsUseOnlyFakeProvider() {
        User user = new User(); user.setId(7L); user.setEmail("local@example.test");
        Plan free = plan(PlanCode.FREE, 0, 2); Plan bronze = plan(PlanCode.BRONZE, 3, 10);
        AtomicReference<BillingCheckout> checkoutState = new AtomicReference<>();
        AtomicReference<Subscription> subscriptionState = new AtomicReference<>();
        BillingCheckoutRepository checkouts = mock(BillingCheckoutRepository.class);
        SubscriptionRepository subscriptions = mock(SubscriptionRepository.class);
        PlanRepository plans = mock(PlanRepository.class); PricingService pricing = mock(PricingService.class);
        CarRepository cars = mock(CarRepository.class); UserRepository users = mock(UserRepository.class);
        MercadoPagoClient provider = mock(MercadoPagoClient.class);
        MercadoPagoWebhookEventRepository events = mock(MercadoPagoWebhookEventRepository.class);
        MercadoPagoProperties properties = new MercadoPagoProperties(); properties.setEnabled(true); properties.setAccessToken("fake-only"); properties.setBackUrl("https://local.test/menu/meu-plano");
        when(users.findByIdForBillingCheckoutLock(7L)).thenReturn(Optional.of(user)); when(cars.countByUserIdAndActiveTrue(7L)).thenReturn(2L);
        when(plans.findByCode(PlanCode.FREE)).thenReturn(Optional.of(free)); when(plans.findByCode(PlanCode.BRONZE)).thenReturn(Optional.of(bronze));
        when(pricing.resolvePlanForVehicleCount(2)).thenReturn(free); when(pricing.calculatePriceForPlan(PlanCode.BRONZE,2)).thenReturn(new PricingResult(PlanCode.BRONZE,"Bronze",2,new BigDecimal("29.90"),List.of()));
        when(checkouts.save(any())).thenAnswer(call->{BillingCheckout value=call.getArgument(0);if(value.getId()==null)value.setId(41L);checkoutState.set(value);return value;});
        when(provider.createPreapproval(any(),any())).thenAnswer(call->{com.localuz.service.dto.MercadoPagoPreapprovalRequest request=call.getArgument(0);return new MercadoPagoPreapproval("pre-local","pending",request.getExternalReference(),"https://fake.test/checkout");});
        BillingCheckoutService checkoutService = new BillingCheckoutService(properties,pricing,plans,checkouts,provider,cars,users);
        BillingCheckout created = checkoutService.createCheckout(user,PlanCode.BRONZE);
        assertThat(created.getStatus()).isEqualTo(BillingCheckoutStatus.PROVIDER_PENDING);
        when(checkouts.findByProviderSubscriptionId("pre-local")).thenAnswer(call->Optional.ofNullable(checkoutState.get()));
        when(checkouts.findByExternalReference(created.getExternalReference())).thenAnswer(call->Optional.ofNullable(checkoutState.get()));
        when(provider.getPreapproval("pre-local")).thenReturn(new MercadoPagoPreapproval("pre-local","authorized",created.getExternalReference(),null,Instant.parse("2026-08-28T00:00:00Z"),Instant.parse("2026-09-28T00:00:00Z"),Instant.parse("2026-08-28T01:00:00Z")));
        when(subscriptions.findByExternalProviderAndExternalSubscriptionId("MERCADO_PAGO","pre-local")).thenAnswer(call->Optional.ofNullable(subscriptionState.get()));
        when(subscriptions.save(any())).thenAnswer(call->{Subscription value=call.getArgument(0);subscriptionState.set(value);return value;});
        MercadoPagoWebhookProcessor processor = new MercadoPagoWebhookProcessor(provider,checkouts,subscriptions,events);
        assertThat(processor.process("request-local","subscription_preapproval","pre-local")).isEqualTo(MercadoPagoWebhookProcessor.Result.PROCESSED);
        Subscription paid = subscriptionState.get();
        assertThat(paid.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE); assertThat(paid.getSource()).isEqualTo(SubscriptionSource.PAYMENT_PROVIDER);
        assertThat(paid.getContractedPrice()).isEqualByComparingTo("29.90"); assertThat(paid.getContractedVehicleCount()).isEqualTo(2);
        when(subscriptions.findFirstByUserIdAndSourceOrderByStartDateDesc(7L,SubscriptionSource.PAYMENT_PROVIDER)).thenReturn(Optional.of(paid));
        when(checkouts.findFirstByUserIdOrderByCreatedAtDesc(7L)).thenReturn(Optional.of(created));
        BillingPaymentStateDTO paymentState = new BillingPaymentStateService(subscriptions,checkouts).getState(user);
        assertThat(paymentState.getPaymentProviderSubscription().getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(paymentState.getLatestCheckout().getStatus()).isEqualTo(BillingCheckoutStatus.AUTHORIZED);
        when(subscriptions.findByUserIdAndStatusInOrderByStartDateDesc(any(),any())).thenReturn(List.of(paid));
        SubscriptionService subscriptionService = new SubscriptionService(subscriptions,plans);
        BillingMeDTO me = BillingMeDTO.from(new EntitlementService(subscriptionService,cars,pricing).getSnapshot(user));
        assertThat(me.getPlanCode()).isEqualTo(PlanCode.BRONZE); assertThat(me.getSubscriptionStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
    }

    private Plan plan(PlanCode code,int min,Integer max){Plan plan=new Plan();plan.setCode(code);plan.setName(code.name());plan.setMinVehicles(min);plan.setMaxVehicles(max);plan.setActive(true);plan.setMonthlyBasePrice(BigDecimal.ZERO);return plan;}
}
