package com.localuz.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.localuz.service.*;
import com.localuz.service.RecurringBillingReservationService.Candidate;
import com.localuz.service.dto.RecurringReconciliationResult;
import com.localuz.service.dto.RecurringReconciliationResult.Outcome;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

class RecurringBillingReconciliationSchedulerTest {
    private final RecurringBillingReconciliationProperties config=new RecurringBillingReconciliationProperties();
    private final MercadoPagoProperties provider=new MercadoPagoProperties();
    private final RecurringBillingReservationService reservations=mock(RecurringBillingReservationService.class);
    private final RecurringBillingReconciliationService service=mock(RecurringBillingReconciliationService.class);
    private final Instant now=Instant.parse("2026-11-01T12:00:00Z");
    private final Clock clock=Clock.fixed(now, ZoneOffset.UTC);
    private final RecurringReconciliationCircuitBreaker breaker=new RecurringReconciliationCircuitBreaker(clock);
    private final RecurringBillingReconciliationScheduler scheduler=
        new RecurringBillingReconciliationScheduler(config,provider,reservations,service,breaker,clock);
    private void enable() { config.setEnabled(true); provider.setEnabled(true); provider.setAccessToken("fake-test-only"); }
    private ListAppender<ILoggingEvent> logAppender;

    @BeforeEach void attachLogAppender() {
        logAppender = new ListAppender<>();
        logAppender.start();
        ((ch.qos.logback.classic.Logger) LoggerFactory.getLogger(RecurringBillingReconciliationScheduler.class)).addAppender(logAppender);
    }
    @AfterEach void detachLogAppender() {
        ((ch.qos.logback.classic.Logger) LoggerFactory.getLogger(RecurringBillingReconciliationScheduler.class)).detachAppender(logAppender);
    }
    private java.util.List<String> loggedMessages() {
        return logAppender.list.stream().map(ILoggingEvent::getFormattedMessage).collect(java.util.stream.Collectors.toList());
    }

    @Test void providerFailureLogsASafeCategoryAndHttpStatusWithoutTheExceptionMessage() {
        enable();
        when(reservations.candidates(any(),any(),anyInt(),anyInt())).thenReturn(List.of(new Candidate(1L,"a")));
        when(service.reconcile(any(),any())).thenReturn(
            new RecurringReconciliationResult(1L, Outcome.PROVIDER_FAILURE, 0, 0, 0, 1, "HTTP_404", 404));
        scheduler.reconcileSubscriptions();
        assertThat(loggedMessages()).anySatisfy(message -> {
            assertThat(message).contains("outcome=PROVIDER_FAILURE", "failureCategory=HTTP_404", "failureHttpStatus=404", "providerErrorCode=null");
            assertThat(message).doesNotContain("Bearer", "access_token", "provider-secret-body");
        });
    }

    @Test void providerFailureLogsTheSafeStructuredProviderCodeWhenPresent() {
        enable();
        when(reservations.candidates(any(),any(),anyInt(),anyInt())).thenReturn(List.of(new Candidate(1L,"a")));
        when(service.reconcile(any(),any())).thenReturn(
            new RecurringReconciliationResult(1L, Outcome.PROVIDER_FAILURE, 0, 0, 0, 1, "HTTP_400", 400, "bad_request, PA400"));
        scheduler.reconcileSubscriptions();
        assertThat(loggedMessages()).anySatisfy(message -> {
            assertThat(message).contains("outcome=PROVIDER_FAILURE", "failureCategory=HTTP_400", "failureHttpStatus=400", "providerErrorCode=bad_request, PA400");
            assertThat(message).doesNotContain("Bearer", "access_token", "payer", "@");
        });
    }

    @Test void completeOutcomeLogsNullFailureFieldsRatherThanFabricatingACategory() {
        enable();
        when(reservations.candidates(any(),any(),anyInt(),anyInt())).thenReturn(List.of(new Candidate(1L,"a")));
        when(service.reconcile(any(),any())).thenReturn(new RecurringReconciliationResult(1L, Outcome.COMPLETE, 0, 0, 0, 1));
        scheduler.reconcileSubscriptions();
        assertThat(loggedMessages()).anySatisfy(message -> assertThat(message).contains("failureCategory=null", "failureHttpStatus=null", "providerErrorCode=null"));
    }

    @Test void disabledByDefaultDoesNothing() {
        scheduler.reconcileSubscriptions(); verifyNoInteractions(reservations,service);
    }
    @Test void disabledProviderDoesNothing() {
        config.setEnabled(true); scheduler.reconcileSubscriptions(); verifyNoInteractions(reservations,service);
    }
    @Test void batchSizeIsEnforcedEvenForUnexpectedLargerCandidateList() {
        enable(); config.setBatchSize(1);
        when(reservations.candidates(any(),any(),anyInt(),eq(1))).thenReturn(List.of(new Candidate(1L,"a"),new Candidate(2L,"b")));
        when(service.reconcile(any(),any())).thenReturn(new RecurringReconciliationResult(1L,Outcome.COMPLETE,0,0,0,0));
        scheduler.reconcileSubscriptions(); verify(service,times(1)).reconcile(any(),any());
    }
    @Test void rateLimitStopsRemainingCandidatesAndOpensTheBreaker() {
        enable();
        when(reservations.candidates(any(),any(),anyInt(),anyInt())).thenReturn(List.of(new Candidate(1L,"a"),new Candidate(2L,"b")));
        when(service.reconcile(any(),any())).thenAnswer(call->{
            call.<RecurringReconciliationBudget>getArgument(1).stopForRateLimit(null);
            return new RecurringReconciliationResult(1L,Outcome.RATE_LIMITED,0,0,0,1);
        });
        assertThat(breaker.isOpen()).isFalse();
        scheduler.reconcileSubscriptions();
        verify(service,times(1)).reconcile(any(),any());
        assertThat(breaker.isOpen()).isTrue();
    }
    @Test void oneOperationalFailureDoesNotAbortOthers() {
        enable();
        when(reservations.candidates(any(),any(),anyInt(),anyInt())).thenReturn(List.of(new Candidate(1L,"a"),new Candidate(2L,"b")));
        when(service.reconcile(any(),any())).thenThrow(new IllegalStateException("fake"));
        scheduler.reconcileSubscriptions(); verify(service,times(2)).reconcile(any(),any());
    }
    @Test void openBreakerSkipsTheWholeCycleWithoutTouchingReservationsOrProvider() {
        enable();
        breaker.openFor(java.time.Duration.ofMinutes(15));
        scheduler.reconcileSubscriptions();
        verifyNoInteractions(reservations,service);
    }
    @Test void breakerClosesAgainAfterTheCooldownElapses() {
        enable();
        var mutableClock = new MutableClock(now);
        var sharedBreaker = new RecurringReconciliationCircuitBreaker(mutableClock);
        var mutableScheduler = new RecurringBillingReconciliationScheduler(config,provider,reservations,service,sharedBreaker,mutableClock);
        sharedBreaker.openFor(java.time.Duration.ofSeconds(60));
        when(reservations.candidates(any(),any(),anyInt(),anyInt())).thenReturn(List.of());
        mutableScheduler.reconcileSubscriptions();
        verifyNoInteractions(reservations); // still within the cooldown
        mutableClock.set(now.plusSeconds(61));
        mutableScheduler.reconcileSubscriptions();
        verify(reservations).candidates(any(),any(),anyInt(),anyInt());
    }
    @Test void validProviderRetryAfterIsRespectedAndClampedToTheConfiguredCeiling() {
        enable(); config.setRateLimitDefaultCooldownSeconds(60); config.setRateLimitMaxCooldownSeconds(300);
        when(reservations.candidates(any(),any(),anyInt(),anyInt())).thenReturn(List.of(new Candidate(1L,"a")));
        when(service.reconcile(any(),any())).thenAnswer(call->{
            call.<RecurringReconciliationBudget>getArgument(1).stopForRateLimit(3600); // provider asked for 1h
            return new RecurringReconciliationResult(1L,Outcome.RATE_LIMITED,0,0,0,1);
        });
        scheduler.reconcileSubscriptions();
        assertThat(breaker.cooldownUntil()).isEqualTo(now.plusSeconds(300)); // clamped, not the raw 3600
    }
    @Test void missingRetryAfterUsesTheConfiguredDefaultCooldown() {
        enable(); config.setRateLimitDefaultCooldownSeconds(120);
        when(reservations.candidates(any(),any(),anyInt(),anyInt())).thenReturn(List.of(new Candidate(1L,"a")));
        when(service.reconcile(any(),any())).thenAnswer(call->{
            call.<RecurringReconciliationBudget>getArgument(1).stopForRateLimit(null);
            return new RecurringReconciliationResult(1L,Outcome.RATE_LIMITED,0,0,0,1);
        });
        scheduler.reconcileSubscriptions();
        assertThat(breaker.cooldownUntil()).isEqualTo(now.plusSeconds(120));
    }
    @Test void cancelledTerminalHorizonAndCurrentInstantArePassedToCandidateSelection() {
        enable(); config.setCancelledTerminalHorizonDays(45); config.setMinIntervalMinutes(30);
        when(reservations.candidates(any(),any(),anyInt(),anyInt())).thenReturn(List.of());
        scheduler.reconcileSubscriptions();
        verify(reservations).candidates(now.minusSeconds(30*60), now, 45, config.getBatchSize());
    }

    private static final class MutableClock extends Clock {
        private final AtomicReference<Instant> instant;
        MutableClock(Instant initial) { this.instant = new AtomicReference<>(initial); }
        void set(Instant value) { instant.set(value); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { throw new UnsupportedOperationException(); }
        @Override public Instant instant() { return instant.get(); }
    }
}
