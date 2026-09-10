package com.localuz.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.localuz.domain.BillingCheckout;
import com.localuz.domain.Subscription;
import com.localuz.domain.User;
import com.localuz.domain.enumeration.BillingCheckoutStatus;
import com.localuz.domain.enumeration.SubscriptionSource;
import com.localuz.domain.enumeration.SubscriptionStatus;
import com.localuz.repository.BillingCheckoutRepository;
import com.localuz.repository.SubscriptionRepository;
import com.localuz.repository.UserRepository;
import com.localuz.service.MercadoPagoWebhookProcessor;
import java.lang.reflect.Field;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * enabled/userLogin are normally set by @Value/Spring; set directly via reflection here since
 * this is a plain unit test with no Spring context (matching AdminBootstrapTest's style).
 */
class BillingReconciliationRunnerTest {

    private static final List<BillingCheckoutStatus> RECONCILABLE_STATUSES = List.of(
        BillingCheckoutStatus.CREATED,
        BillingCheckoutStatus.PROVIDER_PENDING,
        BillingCheckoutStatus.PROVIDER_UNKNOWN
    );

    private MercadoPagoProperties mercadoPagoProperties;
    private UserRepository userRepository;
    private BillingCheckoutRepository checkoutRepository;
    private SubscriptionRepository subscriptionRepository;
    private MercadoPagoWebhookProcessor processor;
    private BillingReconciliationRunner runner;

    @BeforeEach
    void setUp() {
        mercadoPagoProperties = new MercadoPagoProperties();
        mercadoPagoProperties.setTestMode(true);
        userRepository = mock(UserRepository.class);
        checkoutRepository = mock(BillingCheckoutRepository.class);
        subscriptionRepository = mock(SubscriptionRepository.class);
        processor = mock(MercadoPagoWebhookProcessor.class);
        runner = new BillingReconciliationRunner(mercadoPagoProperties, userRepository, checkoutRepository, subscriptionRepository, processor);
    }

    private void configure(boolean enabled, String userLogin) throws ReflectiveOperationException {
        setField("enabled", enabled);
        setField("userLogin", userLogin);
    }

    private void setField(String name, Object value) throws ReflectiveOperationException {
        Field field = BillingReconciliationRunner.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(runner, value);
    }

    private static User user() {
        User user = new User();
        user.setId(7L);
        user.setLogin("cliente");
        return user;
    }

    private static BillingCheckout checkout(Long id, String providerSubscriptionId, BillingCheckoutStatus status) {
        BillingCheckout checkout = new BillingCheckout();
        checkout.setId(id);
        checkout.setProviderSubscriptionId(providerSubscriptionId);
        checkout.setStatus(status);
        return checkout;
    }

    @Test
    void disabledMakesNoChangeAtAll() throws Exception {
        configure(false, "cliente");

        runner.run(null);

        verifyNoInteractions(userRepository, checkoutRepository, subscriptionRepository, processor);
    }

    @Test
    void refusesToRunWhenMercadoPagoTestModeIsNotEnabled() throws Exception {
        configure(true, "cliente");
        mercadoPagoProperties.setTestMode(false);

        runner.run(null);

        verifyNoInteractions(userRepository, checkoutRepository, subscriptionRepository, processor);
    }

    @Test
    void blankLoginFailsSafelyWithoutThrowing() throws Exception {
        configure(true, "   ");

        assertThatCode(() -> runner.run(null)).doesNotThrowAnyException();

        verifyNoInteractions(userRepository, checkoutRepository, subscriptionRepository, processor);
    }

    @Test
    void nonExistentUserDoesNotProcess() throws Exception {
        configure(true, "ghost");
        when(userRepository.findOneByLogin("ghost")).thenReturn(Optional.empty());

        runner.run(null);

        verifyNoInteractions(checkoutRepository, subscriptionRepository, processor);
    }

    @Test
    void userWithNoReconcilableCheckoutDoesNotProcess() throws Exception {
        configure(true, "cliente");
        when(userRepository.findOneByLogin("cliente")).thenReturn(Optional.of(user()));
        when(checkoutRepository.findFirstByUserIdAndStatusInOrderByCreatedAtDesc(7L, RECONCILABLE_STATUSES)).thenReturn(Optional.empty());

        runner.run(null);

        verifyNoInteractions(subscriptionRepository, processor);
    }

    @Test
    void checkoutWithoutProviderSubscriptionIdDoesNotProcess() throws Exception {
        configure(true, "cliente");
        when(userRepository.findOneByLogin("cliente")).thenReturn(Optional.of(user()));
        when(checkoutRepository.findFirstByUserIdAndStatusInOrderByCreatedAtDesc(7L, RECONCILABLE_STATUSES))
            .thenReturn(Optional.of(checkout(10L, null, BillingCheckoutStatus.PROVIDER_PENDING)));

        runner.run(null);

        verifyNoInteractions(subscriptionRepository, processor);
    }

    @Test
    void validPendingCheckoutCallsProcessorExactlyOnceWithPreapprovalTopicAndDeterministicRequestId() throws Exception {
        configure(true, "cliente");
        when(userRepository.findOneByLogin("cliente")).thenReturn(Optional.of(user()));
        when(checkoutRepository.findFirstByUserIdAndStatusInOrderByCreatedAtDesc(7L, RECONCILABLE_STATUSES))
            .thenReturn(Optional.of(checkout(10L, "db2848cec2884455a8e72089c9f8fbe2", BillingCheckoutStatus.PROVIDER_PENDING)));
        when(processor.process("manual-reconcile-10", "subscription_preapproval", "db2848cec2884455a8e72089c9f8fbe2"))
            .thenReturn(MercadoPagoWebhookProcessor.Result.PROCESSED);
        when(subscriptionRepository.findByUserIdAndSourceAndStatusIn(any(), any(), any())).thenReturn(Collections.emptyList());

        runner.run(null);

        ArgumentCaptor<String> requestId = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> topic = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> resourceId = ArgumentCaptor.forClass(String.class);
        verify(processor, times(1)).process(requestId.capture(), topic.capture(), resourceId.capture());
        assertThat(requestId.getValue()).isEqualTo("manual-reconcile-10");
        assertThat(topic.getValue()).isEqualTo("subscription_preapproval");
        assertThat(resourceId.getValue()).isEqualTo("db2848cec2884455a8e72089c9f8fbe2");
        verifyNoMoreInteractions(processor);
    }

    @Test
    void runnerNeverCallsAnyOtherProcessorMethodOrCreatesAResourceItself() throws Exception {
        // The runner only ever holds a MercadoPagoWebhookProcessor - it has no reference at all
        // to MercadoPagoClient, so it is structurally incapable of calling createPreapproval or
        // cancelPreapproval (those only exist on MercadoPagoClient, not on the processor).
        boolean hasMercadoPagoClientField = false;
        for (Field field : BillingReconciliationRunner.class.getDeclaredFields()) {
            if (field.getType().getSimpleName().equals("MercadoPagoClient")) {
                hasMercadoPagoClientField = true;
            }
        }
        assertThat(hasMercadoPagoClientField).isFalse();
    }

    @Test
    void secondRunReusesTheSameDeterministicRequestIdSoTheProcessorsOwnDedupApplies() throws Exception {
        configure(true, "cliente");
        when(userRepository.findOneByLogin("cliente")).thenReturn(Optional.of(user()));
        when(checkoutRepository.findFirstByUserIdAndStatusInOrderByCreatedAtDesc(7L, RECONCILABLE_STATUSES))
            .thenReturn(Optional.of(checkout(10L, "db2848cec2884455a8e72089c9f8fbe2", BillingCheckoutStatus.PROVIDER_PENDING)));
        when(processor.process("manual-reconcile-10", "subscription_preapproval", "db2848cec2884455a8e72089c9f8fbe2"))
            .thenReturn(MercadoPagoWebhookProcessor.Result.PROCESSED)
            .thenReturn(MercadoPagoWebhookProcessor.Result.DUPLICATE);
        when(subscriptionRepository.findByUserIdAndSourceAndStatusIn(any(), any(), any())).thenReturn(Collections.emptyList());

        runner.run(null);
        runner.run(null);

        verify(processor, times(2)).process("manual-reconcile-10", "subscription_preapproval", "db2848cec2884455a8e72089c9f8fbe2");
    }

    @Test
    void logsWhetherAPaymentProviderSubscriptionIsActiveWithoutTouchingItsPrecedence() throws Exception {
        configure(true, "cliente");
        when(userRepository.findOneByLogin("cliente")).thenReturn(Optional.of(user()));
        when(checkoutRepository.findFirstByUserIdAndStatusInOrderByCreatedAtDesc(7L, RECONCILABLE_STATUSES))
            .thenReturn(Optional.of(checkout(10L, "db2848cec2884455a8e72089c9f8fbe2", BillingCheckoutStatus.PROVIDER_PENDING)));
        when(processor.process(anyString(), anyString(), anyString())).thenReturn(MercadoPagoWebhookProcessor.Result.PROCESSED);
        Subscription active = new Subscription();
        active.setSource(SubscriptionSource.PAYMENT_PROVIDER);
        active.setStatus(SubscriptionStatus.ACTIVE);
        when(
            subscriptionRepository.findByUserIdAndSourceAndStatusIn(7L, SubscriptionSource.PAYMENT_PROVIDER, List.of(SubscriptionStatus.ACTIVE, SubscriptionStatus.PAST_DUE))
        ).thenReturn(List.of(active));

        assertThatCode(() -> runner.run(null)).doesNotThrowAnyException();

        // Only reads the Subscription table for logging purposes - never writes to it, so the
        // ADMIN_GRANT > PAYMENT_PROVIDER > GRANDFATHERED precedence in SubscriptionService is
        // untouched by this runner.
        verify(subscriptionRepository, never()).save(any());
    }

    @Test
    void processorFailureIsLoggedAndDoesNotPropagate() throws Exception {
        configure(true, "cliente");
        when(userRepository.findOneByLogin("cliente")).thenReturn(Optional.of(user()));
        when(checkoutRepository.findFirstByUserIdAndStatusInOrderByCreatedAtDesc(7L, RECONCILABLE_STATUSES))
            .thenReturn(Optional.of(checkout(10L, "db2848cec2884455a8e72089c9f8fbe2", BillingCheckoutStatus.PROVIDER_PENDING)));
        when(processor.process(anyString(), anyString(), anyString())).thenThrow(new RuntimeException("provider unreachable"));

        assertThatCode(() -> runner.run(null)).doesNotThrowAnyException();
    }

    @Test
    void loginIsNormalizedToLowercaseAndTrimmedBeforeLookup() throws Exception {
        configure(true, "  ClienteVIP  ");
        when(userRepository.findOneByLogin("clientevip")).thenReturn(Optional.of(user()));
        when(checkoutRepository.findFirstByUserIdAndStatusInOrderByCreatedAtDesc(7L, RECONCILABLE_STATUSES)).thenReturn(Optional.empty());

        runner.run(null);

        verify(userRepository).findOneByLogin("clientevip");
    }
}
