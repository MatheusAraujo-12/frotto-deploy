package com.localuz.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.localuz.domain.Plan;
import com.localuz.domain.Subscription;
import com.localuz.domain.User;
import com.localuz.domain.enumeration.PlanCode;
import com.localuz.domain.enumeration.SubscriptionSource;
import com.localuz.domain.enumeration.SubscriptionStatus;
import com.localuz.repository.SubscriptionRepository;
import com.localuz.repository.UserRepository;
import com.localuz.service.dto.MercadoPagoPreapproval;
import com.localuz.web.rest.errors.BadRequestAlertException;
import com.localuz.web.rest.errors.BillingCancellationProviderRejectedException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mockito;

class SubscriptionCancellationServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-10T12:00:00Z");
    private static final Instant PERIOD_END = NOW.plusSeconds(10 * 24 * 3600L);

    private UserRepository userRepository;
    private SubscriptionRepository subscriptionRepository;
    private MercadoPagoClient client;
    private RecurringSubscriptionGuardService recurringSubscriptionGuard;
    private SubscriptionCancellationSteps steps;
    private SubscriptionCancellationService service;
    private User user;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        subscriptionRepository = mock(SubscriptionRepository.class);
        client = mock(MercadoPagoClient.class);
        recurringSubscriptionGuard = mock(RecurringSubscriptionGuardService.class);
        steps = Mockito.spy(new SubscriptionCancellationSteps(userRepository, subscriptionRepository, client, recurringSubscriptionGuard, Clock.fixed(NOW, ZoneOffset.UTC)));
        service = new SubscriptionCancellationService(steps, client);

        user = new User();
        user.setId(1L);
        when(userRepository.findByIdForBillingCheckoutLock(1L)).thenReturn(Optional.of(user));
    }

    private static Subscription subscription(Long id, SubscriptionStatus status, boolean cancelAtPeriodEnd, Instant periodEnd, String externalId) {
        Subscription subscription = new Subscription();
        subscription.setId(id);
        Plan plan = new Plan();
        plan.setCode(PlanCode.SILVER);
        subscription.setPlan(plan);
        subscription.setStatus(status);
        subscription.setSource(SubscriptionSource.PAYMENT_PROVIDER);
        subscription.setCancelAtPeriodEnd(cancelAtPeriodEnd);
        subscription.setCurrentPeriodEnd(periodEnd);
        subscription.setExternalProvider("MERCADO_PAGO");
        subscription.setExternalSubscriptionId(externalId);
        subscription.setContractedPrice(java.math.BigDecimal.TEN);
        return subscription;
    }

    private void stubFindById(Subscription subscription) {
        when(subscriptionRepository.findById(subscription.getId())).thenReturn(Optional.of(subscription));
    }

    @Test
    void activeSubscriptionCancelsSuccessfully() {
        Subscription subscription = subscription(10L, SubscriptionStatus.ACTIVE, false, PERIOD_END, "pre-1");
        when(subscriptionRepository.findByUserIdAndSourceAndStatusIn(1L, SubscriptionSource.PAYMENT_PROVIDER, SubscriptionCancellationSteps.CANCELLABLE_STATUSES))
            .thenReturn(List.of(subscription));
        stubFindById(subscription);
        when(client.cancelPreapproval(eq("pre-1"), anyString())).thenReturn(new MercadoPagoPreapproval("pre-1", "cancelled", "ref", null));

        Subscription result = service.cancel(user);

        assertThat(result.getCancelAtPeriodEnd()).isTrue();
        assertThat(result.getCanceledAt()).isNotNull();
        assertThat(result.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(result.getCurrentPeriodEnd()).isEqualTo(PERIOD_END);
    }

    @Test
    void pastDueSubscriptionCancelsSuccessfully() {
        Subscription subscription = subscription(11L, SubscriptionStatus.PAST_DUE, false, PERIOD_END, "pre-2");
        when(subscriptionRepository.findByUserIdAndSourceAndStatusIn(1L, SubscriptionSource.PAYMENT_PROVIDER, SubscriptionCancellationSteps.CANCELLABLE_STATUSES))
            .thenReturn(List.of(subscription));
        stubFindById(subscription);
        when(client.cancelPreapproval(eq("pre-2"), anyString())).thenReturn(new MercadoPagoPreapproval("pre-2", "canceled", "ref", null));

        Subscription result = service.cancel(user);

        assertThat(result.getCancelAtPeriodEnd()).isTrue();
        assertThat(result.getStatus()).isEqualTo(SubscriptionStatus.PAST_DUE);
    }

    /**
     * 5G.9 section B: a PAUSED remote contract must be cancellable through Frotto too - Mercado
     * Pago's own docs (Subscription management / Gerenciamento de assinaturas) describe pausing,
     * reactivating and cancelling a preapproval as independent PUT operations with no documented
     * precondition that a paused contract must be reactivated first, and cancelPreapproval already
     * sends a plain PUT status=cancelled regardless of current status.
     */
    @Test
    void pausedSubscriptionCancelsSuccessfully() {
        Subscription subscription = subscription(26L, SubscriptionStatus.PAUSED, false, PERIOD_END, "pre-paused");
        when(subscriptionRepository.findByUserIdAndSourceAndStatusIn(1L, SubscriptionSource.PAYMENT_PROVIDER, SubscriptionCancellationSteps.CANCELLABLE_STATUSES))
            .thenReturn(List.of(subscription));
        stubFindById(subscription);
        when(client.cancelPreapproval(eq("pre-paused"), anyString())).thenReturn(new MercadoPagoPreapproval("pre-paused", "cancelled", "ref", null));

        Subscription result = service.cancel(user);

        assertThat(result.getCancelAtPeriodEnd()).isTrue();
        assertThat(result.getCanceledAt()).isNotNull();
        assertThat(result.getStatus()).isEqualTo(SubscriptionStatus.PAUSED);
    }

    @Test
    void providerRespondingWithANonTerminalStatusIsNotConfirmedLocally() {
        Subscription subscription = subscription(12L, SubscriptionStatus.ACTIVE, false, PERIOD_END, "pre-3");
        when(subscriptionRepository.findByUserIdAndSourceAndStatusIn(1L, SubscriptionSource.PAYMENT_PROVIDER, SubscriptionCancellationSteps.CANCELLABLE_STATUSES))
            .thenReturn(List.of(subscription));
        stubFindById(subscription);
        when(client.cancelPreapproval(eq("pre-3"), anyString())).thenReturn(new MercadoPagoPreapproval("pre-3", "authorized", "ref", null));
        when(client.getPreapproval("pre-3")).thenReturn(new MercadoPagoPreapproval("pre-3", "authorized", "ref", null));

        assertThatThrownBy(() -> service.cancel(user)).isInstanceOf(BillingCancellationProviderRejectedException.class);

        assertThat(subscription.getCancelAtPeriodEnd()).isFalse();
    }

    @Test
    void confirmedCancellationIsIdempotentAndNeverCallsTheProviderAgain() {
        // true + canceledAt!=null == CONFIRMED - the only state that is a true no-op.
        Subscription subscription = subscription(13L, SubscriptionStatus.ACTIVE, true, PERIOD_END, "pre-4");
        subscription.setCanceledAt(NOW.minusSeconds(60));
        when(subscriptionRepository.findByUserIdAndSourceAndStatusIn(1L, SubscriptionSource.PAYMENT_PROVIDER, SubscriptionCancellationSteps.CANCELLABLE_STATUSES))
            .thenReturn(List.of(subscription));

        Subscription result = service.cancel(user);

        assertThat(result.getCancelAtPeriodEnd()).isTrue();
        assertThat(result.getCanceledAt()).isNotNull();
        Mockito.verifyNoInteractions(client);
        verify(subscriptionRepository, never()).save(any());
    }

    /**
     * true + canceledAt==null == PENDING_CONFIRMATION - must NOT be treated as a no-op. A retry
     * (manual, or this same request after an earlier ambiguous failure) has to be able to drive
     * it all the way to CONFIRMED by calling the provider again.
     */
    @Test
    void pendingConfirmationIsNotANoOpAndARetryCanReachConfirmed() {
        Subscription subscription = subscription(24L, SubscriptionStatus.ACTIVE, true, PERIOD_END, "pre-14");
        assertThat(subscription.getCanceledAt()).isNull();
        when(subscriptionRepository.findByUserIdAndSourceAndStatusIn(1L, SubscriptionSource.PAYMENT_PROVIDER, SubscriptionCancellationSteps.CANCELLABLE_STATUSES))
            .thenReturn(List.of(subscription));
        stubFindById(subscription);
        when(client.cancelPreapproval(eq("pre-14"), anyString())).thenReturn(new MercadoPagoPreapproval("pre-14", "cancelled", "ref", null));

        Subscription result = service.cancel(user);

        Mockito.verify(client).cancelPreapproval(eq("pre-14"), anyString());
        assertThat(result.getCancelAtPeriodEnd()).isTrue();
        assertThat(result.getCanceledAt()).isNotNull();
    }

    @Test
    void pendingConfirmationRetryReusesTheSameDeterministicIdempotencyKeyAsTheFirstAttempt() {
        Subscription subscription = subscription(25L, SubscriptionStatus.ACTIVE, true, PERIOD_END, "pre-15");
        when(subscriptionRepository.findByUserIdAndSourceAndStatusIn(1L, SubscriptionSource.PAYMENT_PROVIDER, SubscriptionCancellationSteps.CANCELLABLE_STATUSES))
            .thenReturn(List.of(subscription));
        stubFindById(subscription);
        when(client.cancelPreapproval(anyString(), anyString())).thenReturn(new MercadoPagoPreapproval("pre-15", "cancelled", "ref", null));

        service.cancel(user);

        ArgumentCaptor<String> idempotencyKeyCaptor = ArgumentCaptor.forClass(String.class);
        Mockito.verify(client).cancelPreapproval(eq("pre-15"), idempotencyKeyCaptor.capture());
        assertThat(idempotencyKeyCaptor.getValue()).isEqualTo("cancel-pre-15");
    }

    @Test
    void noPaymentProviderSubscriptionIsAControlledError() {
        when(subscriptionRepository.findByUserIdAndSourceAndStatusIn(1L, SubscriptionSource.PAYMENT_PROVIDER, SubscriptionCancellationSteps.CANCELLABLE_STATUSES))
            .thenReturn(List.of());

        assertThatThrownBy(() -> service.cancel(user)).isInstanceOf(BadRequestAlertException.class);
        Mockito.verifyNoInteractions(client);
    }

    @Test
    void missingExternalSubscriptionIdFailsBeforeTheProviderIsEverCalled() {
        Subscription subscription = subscription(14L, SubscriptionStatus.ACTIVE, false, PERIOD_END, null);
        when(subscriptionRepository.findByUserIdAndSourceAndStatusIn(1L, SubscriptionSource.PAYMENT_PROVIDER, SubscriptionCancellationSteps.CANCELLABLE_STATUSES))
            .thenReturn(List.of(subscription));

        assertThatThrownBy(() -> service.cancel(user)).isInstanceOf(BadRequestAlertException.class);
        Mockito.verifyNoInteractions(client);
    }

    @Test
    void missingCurrentPeriodEndFailsBeforeTheProviderIsEverCalled() {
        Subscription subscription = subscription(15L, SubscriptionStatus.ACTIVE, false, null, "pre-5");
        when(subscriptionRepository.findByUserIdAndSourceAndStatusIn(1L, SubscriptionSource.PAYMENT_PROVIDER, SubscriptionCancellationSteps.CANCELLABLE_STATUSES))
            .thenReturn(List.of(subscription));

        assertThatThrownBy(() -> service.cancel(user)).isInstanceOf(BadRequestAlertException.class);
        Mockito.verifyNoInteractions(client);
    }

    @Test
    void providerTimeoutOrFiveHundredNeverProducesAFalseSuccess() {
        Subscription subscription = subscription(16L, SubscriptionStatus.ACTIVE, false, PERIOD_END, "pre-6");
        when(subscriptionRepository.findByUserIdAndSourceAndStatusIn(1L, SubscriptionSource.PAYMENT_PROVIDER, SubscriptionCancellationSteps.CANCELLABLE_STATUSES))
            .thenReturn(List.of(subscription));
        stubFindById(subscription);
        when(client.cancelPreapproval(eq("pre-6"), anyString())).thenThrow(new MercadoPagoException("timeout", true));
        // The confirming GET is also ambiguous - genuinely unknown whether the provider cancelled.
        when(client.getPreapproval("pre-6")).thenThrow(new MercadoPagoException("timeout", true));

        assertThatThrownBy(() -> service.cancel(user)).isInstanceOf(MercadoPagoException.class);

        // Never flipped to false on a guess, and never silently reported as a definite success either.
        assertThat(subscription.getCancelAtPeriodEnd()).isTrue();
        assertThat(subscription.getCanceledAt()).isNull();
    }

    @Test
    void definiteProviderRejectionRollsBackTheLocalIntent() {
        Subscription subscription = subscription(17L, SubscriptionStatus.ACTIVE, false, PERIOD_END, "pre-7");
        when(subscriptionRepository.findByUserIdAndSourceAndStatusIn(1L, SubscriptionSource.PAYMENT_PROVIDER, SubscriptionCancellationSteps.CANCELLABLE_STATUSES))
            .thenReturn(List.of(subscription));
        stubFindById(subscription);
        when(client.cancelPreapproval(eq("pre-7"), anyString())).thenThrow(new MercadoPagoException("rejected", false));

        assertThatThrownBy(() -> service.cancel(user)).isInstanceOf(BillingCancellationProviderRejectedException.class);

        assertThat(subscription.getCancelAtPeriodEnd()).isFalse();
        Mockito.verify(client, never()).getPreapproval(anyString());
    }

    @Test
    void adminGrantIsNeverQueriedOrTouchedByCancellation() {
        Subscription subscription = subscription(18L, SubscriptionStatus.ACTIVE, false, PERIOD_END, "pre-8");
        when(subscriptionRepository.findByUserIdAndSourceAndStatusIn(eq(1L), eq(SubscriptionSource.PAYMENT_PROVIDER), any()))
            .thenReturn(List.of(subscription));
        stubFindById(subscription);
        when(client.cancelPreapproval(eq("pre-8"), anyString())).thenReturn(new MercadoPagoPreapproval("pre-8", "cancelled", "ref", null));

        service.cancel(user);

        ArgumentCaptor<SubscriptionSource> sourceCaptor = ArgumentCaptor.forClass(SubscriptionSource.class);
        verify(subscriptionRepository).findByUserIdAndSourceAndStatusIn(eq(1L), sourceCaptor.capture(), any());
        assertThat(sourceCaptor.getValue()).isEqualTo(SubscriptionSource.PAYMENT_PROVIDER);
        verify(subscriptionRepository, never()).findByUserIdAndSourceAndStatusIn(eq(1L), eq(SubscriptionSource.ADMIN_GRANT), any());
    }

    @Test
    void currentPeriodEndIsNeverModifiedByCancellation() {
        Subscription subscription = subscription(19L, SubscriptionStatus.ACTIVE, false, PERIOD_END, "pre-9");
        when(subscriptionRepository.findByUserIdAndSourceAndStatusIn(1L, SubscriptionSource.PAYMENT_PROVIDER, SubscriptionCancellationSteps.CANCELLABLE_STATUSES))
            .thenReturn(List.of(subscription));
        stubFindById(subscription);
        when(client.cancelPreapproval(eq("pre-9"), anyString())).thenReturn(new MercadoPagoPreapproval("pre-9", "cancelled", "ref", null));

        Subscription result = service.cancel(user);

        assertThat(result.getCurrentPeriodEnd()).isEqualTo(PERIOD_END);
    }

    @Test
    void statusStaysActiveThroughoutThePaidPeriod() {
        Subscription subscription = subscription(20L, SubscriptionStatus.ACTIVE, false, PERIOD_END, "pre-10");
        when(subscriptionRepository.findByUserIdAndSourceAndStatusIn(1L, SubscriptionSource.PAYMENT_PROVIDER, SubscriptionCancellationSteps.CANCELLABLE_STATUSES))
            .thenReturn(List.of(subscription));
        stubFindById(subscription);
        when(client.cancelPreapproval(eq("pre-10"), anyString())).thenReturn(new MercadoPagoPreapproval("pre-10", "cancelled", "ref", null));

        Subscription result = service.cancel(user);

        assertThat(result.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
    }

    /**
     * The core race-elimination proof: markIntent's commit of cancelAtPeriodEnd=true must happen
     * strictly BEFORE the provider is ever called, so a webhook racing in - however fast Mercado
     * Pago responds - can never observe cancelAtPeriodEnd=false for a Frotto-initiated cancel.
     */
    @Test
    void intentIsCommittedBeforeTheProviderIsEverCalled() {
        Subscription subscription = subscription(21L, SubscriptionStatus.ACTIVE, false, PERIOD_END, "pre-11");
        when(subscriptionRepository.findByUserIdAndSourceAndStatusIn(1L, SubscriptionSource.PAYMENT_PROVIDER, SubscriptionCancellationSteps.CANCELLABLE_STATUSES))
            .thenReturn(List.of(subscription));
        stubFindById(subscription);
        when(client.cancelPreapproval(eq("pre-11"), anyString())).thenReturn(new MercadoPagoPreapproval("pre-11", "cancelled", "ref", null));

        service.cancel(user);

        InOrder order = Mockito.inOrder(subscriptionRepository, client);
        order.verify(subscriptionRepository).save(Mockito.argThat(saved -> Boolean.TRUE.equals(saved.getCancelAtPeriodEnd())));
        order.verify(client).cancelPreapproval(eq("pre-11"), anyString());
    }

    @Test
    void concurrentCallsForTheSameUserSerializeThroughTheExistingBillingCheckoutLock() {
        Subscription subscription = subscription(22L, SubscriptionStatus.ACTIVE, false, PERIOD_END, "pre-12");
        when(subscriptionRepository.findByUserIdAndSourceAndStatusIn(1L, SubscriptionSource.PAYMENT_PROVIDER, SubscriptionCancellationSteps.CANCELLABLE_STATUSES))
            .thenReturn(List.of(subscription));
        stubFindById(subscription);
        when(client.cancelPreapproval(eq("pre-12"), anyString())).thenReturn(new MercadoPagoPreapproval("pre-12", "cancelled", "ref", null));

        service.cancel(user);
        service.cancel(user);

        verify(userRepository, times(2)).findByIdForBillingCheckoutLock(1L);
        // Second call observed cancelAtPeriodEnd already true (set by the first) and never called the provider again.
        verify(client, times(1)).cancelPreapproval(anyString(), anyString());
    }

    @Test
    void idempotencyKeyContainsNoSecretOrPersonalData() {
        Subscription subscription = subscription(23L, SubscriptionStatus.ACTIVE, false, PERIOD_END, "pre-13");
        when(subscriptionRepository.findByUserIdAndSourceAndStatusIn(1L, SubscriptionSource.PAYMENT_PROVIDER, SubscriptionCancellationSteps.CANCELLABLE_STATUSES))
            .thenReturn(List.of(subscription));
        stubFindById(subscription);
        when(client.cancelPreapproval(anyString(), anyString())).thenReturn(new MercadoPagoPreapproval("pre-13", "cancelled", "ref", null));

        service.cancel(user);

        ArgumentCaptor<String> idempotencyKeyCaptor = ArgumentCaptor.forClass(String.class);
        verify(client).cancelPreapproval(anyString(), idempotencyKeyCaptor.capture());
        assertThat(idempotencyKeyCaptor.getValue()).isEqualTo("cancel-pre-13").doesNotContain("@", "token", "secret", "password");
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({"ACTIVE,400", "PAST_DUE,400", "ACTIVE,409", "PAST_DUE,409"})
    void conclusiveRejectionRollsBackPendingIntentAndPreservesSubscription(SubscriptionStatus status, int httpStatus) {
        Subscription subscription = subscription(31L, status, true, PERIOD_END, "pre-rejected");
        Plan plan = subscription.getPlan();
        when(subscriptionRepository.findByUserIdAndSourceAndStatusIn(1L, SubscriptionSource.PAYMENT_PROVIDER, SubscriptionCancellationSteps.CANCELLABLE_STATUSES)).thenReturn(List.of(subscription));
        stubFindById(subscription);
        when(client.cancelPreapproval(anyString(), anyString())).thenThrow(new MercadoPagoException("Provider rejected cancellation", false, httpStatus, null, "raw provider data"));
        assertThatThrownBy(() -> service.cancel(user)).isInstanceOf(BillingCancellationProviderRejectedException.class);
        verify(steps).rollbackIntent(31L);
        assertThat(subscription.getCancelAtPeriodEnd()).isFalse();
        assertThat(subscription.getCanceledAt()).isNull();
        assertThat(subscription.getStatus()).isEqualTo(status);
        assertThat(subscription.getPlan()).isSameAs(plan);
        assertThat(subscription.getCurrentPeriodEnd()).isEqualTo(PERIOD_END);
        assertThat(com.localuz.service.dto.SubscriptionCancellationState.from(subscription.getCancelAtPeriodEnd(), subscription.getCanceledAt())).isEqualTo(com.localuz.service.dto.SubscriptionCancellationState.NONE);
        verify(client, never()).getPreapproval(anyString());
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({"timeout,canceled", "500,canceled", "timeout,cancelled", "500,cancelled", "503,cancelled", "timeout,authorized", "500,authorized", "503,authorized", "timeout,failure", "500,failure", "503,failure"})
    void ambiguousPutUsesOneAuthoritativeGet(String failure, String getResult) {
        Subscription subscription = subscription(32L, SubscriptionStatus.ACTIVE, false, PERIOD_END, "pre-ambiguous");
        when(subscriptionRepository.findByUserIdAndSourceAndStatusIn(1L, SubscriptionSource.PAYMENT_PROVIDER, SubscriptionCancellationSteps.CANCELLABLE_STATUSES)).thenReturn(List.of(subscription));
        stubFindById(subscription);
        when(client.cancelPreapproval(anyString(), anyString())).thenThrow(new MercadoPagoException(failure, true, "timeout".equals(failure) ? null : Integer.valueOf(failure), null, null));
        if ("failure".equals(getResult)) when(client.getPreapproval("pre-ambiguous")).thenThrow(new MercadoPagoException("GET failed", false, 400, null, null));
        else when(client.getPreapproval("pre-ambiguous")).thenReturn(new MercadoPagoPreapproval("pre-ambiguous", getResult, "ref", null));
        if ("canceled".equals(getResult) || "cancelled".equals(getResult)) {
            assertThat(service.cancel(user).getCanceledAt()).isNotNull();
        } else {
            assertThatThrownBy(() -> service.cancel(user)).isInstanceOf("failure".equals(getResult) ? MercadoPagoException.class : BillingCancellationProviderRejectedException.class);
            assertThat(subscription.getCanceledAt()).isNull();
        }
        assertThat(subscription.getCancelAtPeriodEnd()).isEqualTo(!"authorized".equals(getResult));
        assertThat(subscription.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(subscription.getCurrentPeriodEnd()).isEqualTo(PERIOD_END);
        verify(client).getPreapproval("pre-ambiguous");
        verify(steps, never()).rollbackIntent(any());
    }

    @Test
    void unconfirmedResolutionFailureLogsSafeFieldsButNeverTheRawProviderMessage() {
        String distinctiveSecretLookingMessage = "provider rejected token Bearer APP_USR-abc123secret for user someone@example.com";
        Subscription subscription = subscription(34L, SubscriptionStatus.ACTIVE, true, PERIOD_END, "pre-confirm-fail");
        stubFindById(subscription);
        when(client.getPreapproval("pre-confirm-fail"))
            .thenThrow(new MercadoPagoException(distinctiveSecretLookingMessage, false, 400, "bad_request", distinctiveSecretLookingMessage));

        ListAppender<ILoggingEvent> logAppender = new ListAppender<>();
        logAppender.start();
        ch.qos.logback.classic.Logger stepsLogger = (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(SubscriptionCancellationSteps.class);
        stepsLogger.addAppender(logAppender);
        try {
            assertThatThrownBy(() -> steps.resolveAfterUnconfirmedResponse(34L)).isInstanceOf(MercadoPagoException.class);
        } finally {
            stepsLogger.detachAppender(logAppender);
        }

        List<String> loggedMessages = logAppender.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
        assertThat(loggedMessages).isNotEmpty();
        for (String message : loggedMessages) {
            assertThat(message).doesNotContain(distinctiveSecretLookingMessage).doesNotContain("someone@example.com").doesNotContain("APP_USR-abc123secret");
        }
        assertThat(loggedMessages).anyMatch(
            message -> message.contains("subscriptionId=34") && message.contains("HTTP_400") && message.contains("bad_request")
        );
    }

    // --- 5G.11: canonical "canceled"/"cancelled" normalization (explicit, beyond the incidental
    // coverage already in other tests above). --------------------------------------------------

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"canceled", "cancelled"})
    void putResponseAcceptsBothCanonicalSpellingsAsConfirmedWithoutAConfirmingGet(String status) {
        Subscription subscription = subscription(60L, SubscriptionStatus.ACTIVE, false, PERIOD_END, "pre-spelling");
        when(subscriptionRepository.findByUserIdAndSourceAndStatusIn(1L, SubscriptionSource.PAYMENT_PROVIDER, SubscriptionCancellationSteps.CANCELLABLE_STATUSES))
            .thenReturn(List.of(subscription));
        stubFindById(subscription);
        when(client.cancelPreapproval(eq("pre-spelling"), anyString())).thenReturn(new MercadoPagoPreapproval("pre-spelling", status, "ref", null));

        Subscription result = service.cancel(user);

        assertThat(result.getCanceledAt()).isNotNull();
        assertThat(result.getCancelAtPeriodEnd()).isTrue();
        verify(client, never()).getPreapproval(anyString());
    }

    // --- 5G.11: historical pre-5G.9 duplicate PAYMENT_PROVIDER subscriptions - the confirmed
    // staging incident (cancelling only one of two ACTIVE rows left the other silently chargeable,
    // and the OTHER one's genuine rejection was surfaced as if the whole cancellation failed). ---

    @Test
    void twoHistoricalActiveSubscriptionsAreBothCancelledLeavingNoHiddenChargeableRecurrence() {
        Subscription older = subscription(40L, SubscriptionStatus.ACTIVE, false, PERIOD_END, "pre-old");
        older.setStartDate(NOW.minusSeconds(86400 * 30));
        Subscription newer = subscription(41L, SubscriptionStatus.ACTIVE, false, PERIOD_END, "pre-new");
        newer.setStartDate(NOW.minusSeconds(86400));
        // Repository order deliberately does not match start-date order - selection must not rely on it.
        when(subscriptionRepository.findByUserIdAndSourceAndStatusIn(1L, SubscriptionSource.PAYMENT_PROVIDER, SubscriptionCancellationSteps.CANCELLABLE_STATUSES))
            .thenReturn(List.of(older, newer));
        stubFindById(older);
        stubFindById(newer);
        when(client.cancelPreapproval(eq("pre-old"), anyString())).thenReturn(new MercadoPagoPreapproval("pre-old", "cancelled", "ref", null));
        when(client.cancelPreapproval(eq("pre-new"), anyString())).thenReturn(new MercadoPagoPreapproval("pre-new", "cancelled", "ref", null));

        Subscription primary = service.cancel(user);

        // The most-recently-started row is "the" subscription reported back to the caller.
        assertThat(primary.getId()).isEqualTo(41L);
        assertThat(primary.getCanceledAt()).isNotNull();
        // The historical duplicate was ALSO cancelled - never left hidden and still chargeable.
        assertThat(older.getCanceledAt()).isNotNull();
        verify(client).cancelPreapproval(eq("pre-old"), anyString());
        verify(client).cancelPreapproval(eq("pre-new"), anyString());
    }

    @Test
    void alreadyCanceledSubscriptionIsExcludedAndOnlyTheStillActiveOneReceivesAPut() {
        // A subscription with local status=CANCELED never matches CANCELLABLE_STATUSES in the
        // first place (the query itself excludes it) - it must never receive a redundant PUT.
        Subscription stillActive = subscription(44L, SubscriptionStatus.ACTIVE, false, PERIOD_END, "pre-active-only");
        when(subscriptionRepository.findByUserIdAndSourceAndStatusIn(1L, SubscriptionSource.PAYMENT_PROVIDER, SubscriptionCancellationSteps.CANCELLABLE_STATUSES))
            .thenReturn(List.of(stillActive));
        stubFindById(stillActive);
        when(client.cancelPreapproval(eq("pre-active-only"), anyString())).thenReturn(new MercadoPagoPreapproval("pre-active-only", "cancelled", "ref", null));

        service.cancel(user);

        verify(client, times(1)).cancelPreapproval(anyString(), anyString());
        verify(client).cancelPreapproval(eq("pre-active-only"), anyString());
    }

    @Test
    void partialFailureAcrossTwoHistoricalSubscriptionsPreservesEachConfirmedStateAndSurfacesTheResidual() {
        Subscription primarySubscription = subscription(42L, SubscriptionStatus.ACTIVE, false, PERIOD_END, "pre-primary");
        primarySubscription.setStartDate(NOW.minusSeconds(86400));
        Subscription staleSubscription = subscription(43L, SubscriptionStatus.ACTIVE, false, PERIOD_END, "pre-stale");
        staleSubscription.setStartDate(NOW.minusSeconds(86400 * 30));
        when(subscriptionRepository.findByUserIdAndSourceAndStatusIn(1L, SubscriptionSource.PAYMENT_PROVIDER, SubscriptionCancellationSteps.CANCELLABLE_STATUSES))
            .thenReturn(List.of(primarySubscription, staleSubscription));
        stubFindById(primarySubscription);
        stubFindById(staleSubscription);
        when(client.cancelPreapproval(eq("pre-primary"), anyString())).thenReturn(new MercadoPagoPreapproval("pre-primary", "cancelled", "ref", null));
        when(client.cancelPreapproval(eq("pre-stale"), anyString())).thenThrow(new MercadoPagoException("rejected", false));

        // Must NOT throw: the primary (most-recently-started) subscription succeeded.
        Subscription result = service.cancel(user);

        assertThat(result.getId()).isEqualTo(42L);
        assertThat(result.getCanceledAt()).isNotNull(); // primary confirmed
        assertThat(staleSubscription.getCanceledAt()).isNull(); // never fabricate a confirmation for the failed sibling
        assertThat(staleSubscription.getCancelAtPeriodEnd()).isFalse(); // a definite rejection still rolls back that row's own intent, exactly like a solo cancellation would

        when(subscriptionRepository.findByUserIdAndSource(1L, SubscriptionSource.PAYMENT_PROVIDER))
            .thenReturn(List.of(primarySubscription, staleSubscription));
        when(recurringSubscriptionGuard.isStillChargeable(primarySubscription)).thenReturn(false);
        when(recurringSubscriptionGuard.isStillChargeable(staleSubscription)).thenReturn(true);
        assertThat(service.hasResidualActiveContract(user)).isTrue();
    }

    @Test
    void ambiguousSiblingFailureIsResolvedIndependentlyAndNeverMasksThePrimaryResult() {
        Subscription primarySubscription = subscription(45L, SubscriptionStatus.ACTIVE, false, PERIOD_END, "pre-primary-2");
        primarySubscription.setStartDate(NOW.minusSeconds(86400));
        Subscription ambiguousSibling = subscription(46L, SubscriptionStatus.ACTIVE, false, PERIOD_END, "pre-ambiguous-sibling");
        ambiguousSibling.setStartDate(NOW.minusSeconds(86400 * 30));
        when(subscriptionRepository.findByUserIdAndSourceAndStatusIn(1L, SubscriptionSource.PAYMENT_PROVIDER, SubscriptionCancellationSteps.CANCELLABLE_STATUSES))
            .thenReturn(List.of(primarySubscription, ambiguousSibling));
        stubFindById(primarySubscription);
        stubFindById(ambiguousSibling);
        when(client.cancelPreapproval(eq("pre-primary-2"), anyString())).thenReturn(new MercadoPagoPreapproval("pre-primary-2", "cancelled", "ref", null));
        when(client.cancelPreapproval(eq("pre-ambiguous-sibling"), anyString())).thenThrow(new MercadoPagoException("timeout", true));
        when(client.getPreapproval("pre-ambiguous-sibling")).thenReturn(new MercadoPagoPreapproval("pre-ambiguous-sibling", "cancelled", "ref", null));

        Subscription result = service.cancel(user);

        assertThat(result.getId()).isEqualTo(45L);
        assertThat(result.getCanceledAt()).isNotNull();
        // The ambiguous sibling's confirming GET found it genuinely cancelled - confirmed too, not left pending.
        assertThat(ambiguousSibling.getCanceledAt()).isNotNull();
    }

    @Test
    void oneRowMissingProviderDataIsSkippedWithoutBlockingItsStillProcessableSibling() {
        Subscription blocked = subscription(47L, SubscriptionStatus.ACTIVE, false, null, "pre-blocked"); // no currentPeriodEnd
        blocked.setStartDate(NOW.minusSeconds(86400));
        Subscription processable = subscription(48L, SubscriptionStatus.ACTIVE, false, PERIOD_END, "pre-processable");
        processable.setStartDate(NOW.minusSeconds(86400 * 30));
        when(subscriptionRepository.findByUserIdAndSourceAndStatusIn(1L, SubscriptionSource.PAYMENT_PROVIDER, SubscriptionCancellationSteps.CANCELLABLE_STATUSES))
            .thenReturn(List.of(blocked, processable));
        stubFindById(processable);
        when(client.cancelPreapproval(eq("pre-processable"), anyString())).thenReturn(new MercadoPagoPreapproval("pre-processable", "cancelled", "ref", null));

        Subscription result = service.cancel(user);

        // blocked is the primary (most recently started) but cannot be processed - it is returned
        // untouched (still not cancelled), while its processable sibling is still cancelled.
        assertThat(result.getId()).isEqualTo(47L);
        assertThat(result.getCanceledAt()).isNull();
        assertThat(processable.getCanceledAt()).isNotNull();
        verify(client, never()).cancelPreapproval(eq("pre-blocked"), anyString());
    }

    @Test
    void cancellationForOneUserNeverQueriesOrTouchesAnotherUsersSubscriptions() {
        User otherUser = new User();
        otherUser.setId(2L);
        when(userRepository.findByIdForBillingCheckoutLock(2L)).thenReturn(Optional.of(otherUser));
        Subscription mine = subscription(49L, SubscriptionStatus.ACTIVE, false, PERIOD_END, "pre-mine");
        when(subscriptionRepository.findByUserIdAndSourceAndStatusIn(1L, SubscriptionSource.PAYMENT_PROVIDER, SubscriptionCancellationSteps.CANCELLABLE_STATUSES))
            .thenReturn(List.of(mine));
        when(subscriptionRepository.findByUserIdAndSourceAndStatusIn(2L, SubscriptionSource.PAYMENT_PROVIDER, SubscriptionCancellationSteps.CANCELLABLE_STATUSES))
            .thenReturn(List.of());
        stubFindById(mine);
        when(client.cancelPreapproval(eq("pre-mine"), anyString())).thenReturn(new MercadoPagoPreapproval("pre-mine", "cancelled", "ref", null));

        Subscription result = service.cancel(user);
        assertThatThrownBy(() -> service.cancel(otherUser)).isInstanceOf(BadRequestAlertException.class);

        assertThat(result.getId()).isEqualTo(49L);
        assertThat(result.getCanceledAt()).isNotNull();
        verify(userRepository).findByIdForBillingCheckoutLock(1L);
        verify(userRepository).findByIdForBillingCheckoutLock(2L);
        verify(subscriptionRepository).findByUserIdAndSourceAndStatusIn(eq(1L), eq(SubscriptionSource.PAYMENT_PROVIDER), any());
        verify(subscriptionRepository).findByUserIdAndSourceAndStatusIn(eq(2L), eq(SubscriptionSource.PAYMENT_PROVIDER), any());
        verify(client, times(1)).cancelPreapproval(anyString(), anyString());
    }

    @Test
    void hasResidualActiveContractReflectsFreshStateNotStaleOutcomes() {
        when(subscriptionRepository.findByUserIdAndSourceAndStatusIn(1L, SubscriptionSource.PAYMENT_PROVIDER, SubscriptionCancellationSteps.CANCELLABLE_STATUSES))
            .thenReturn(List.of());
        // No cancellable rows at all -> nothing chargeable remains.
        when(subscriptionRepository.findByUserIdAndSource(1L, SubscriptionSource.PAYMENT_PROVIDER)).thenReturn(List.of());
        assertThat(service.hasResidualActiveContract(user)).isFalse();
    }

    @Test
    void authoritativeRejectionCommitsRollbackThroughTransactionProxy() {
        Subscription subscription = subscription(33L, SubscriptionStatus.ACTIVE, true, PERIOD_END, "pre-transaction");
        stubFindById(subscription);
        when(client.getPreapproval("pre-transaction")).thenReturn(new MercadoPagoPreapproval("pre-transaction", "authorized", "ref", null));
        org.springframework.transaction.PlatformTransactionManager manager = mock(org.springframework.transaction.PlatformTransactionManager.class);
        org.springframework.transaction.TransactionStatus transaction = new org.springframework.transaction.support.SimpleTransactionStatus();
        when(manager.getTransaction(any())).thenReturn(transaction);
        org.springframework.aop.framework.ProxyFactory factory = new org.springframework.aop.framework.ProxyFactory(
            new SubscriptionCancellationSteps(userRepository, subscriptionRepository, client, recurringSubscriptionGuard, Clock.fixed(NOW, ZoneOffset.UTC)));
        org.springframework.transaction.interceptor.TransactionInterceptor interceptor = new org.springframework.transaction.interceptor.TransactionInterceptor();
        interceptor.setTransactionManager(manager);
        interceptor.setTransactionAttributeSource(new org.springframework.transaction.annotation.AnnotationTransactionAttributeSource());
        factory.addAdvice(interceptor);
        SubscriptionCancellationSteps proxy = (SubscriptionCancellationSteps) factory.getProxy();
        assertThatThrownBy(() -> proxy.resolveAfterUnconfirmedResponse(33L)).isInstanceOf(BillingCancellationProviderRejectedException.class);
        verify(manager).commit(transaction);
        verify(manager, never()).rollback(any());
        assertThat(subscription.getCancelAtPeriodEnd()).isFalse();
    }

}
