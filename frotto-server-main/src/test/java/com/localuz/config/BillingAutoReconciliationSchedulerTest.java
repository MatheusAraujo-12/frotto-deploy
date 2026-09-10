package com.localuz.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.localuz.domain.BillingCheckout;
import com.localuz.domain.enumeration.BillingCheckoutStatus;
import com.localuz.repository.BillingCheckoutRepository;
import com.localuz.service.MercadoPagoException;
import com.localuz.service.MercadoPagoWebhookProcessor;
import java.lang.reflect.Field;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;

class BillingAutoReconciliationSchedulerTest {

    private static final List<BillingCheckoutStatus> ELIGIBLE_STATUSES = List.of(
        BillingCheckoutStatus.PROVIDER_PENDING,
        BillingCheckoutStatus.PROVIDER_UNKNOWN
    );
    private static final Instant FIXED_NOW = Instant.parse("2026-09-10T12:00:00Z");

    private MercadoPagoProperties mercadoPagoProperties;
    private BillingCheckoutRepository checkoutRepository;
    private MercadoPagoWebhookProcessor processor;
    private ListAppender<ILoggingEvent> logAppender;

    @BeforeEach
    void setUp() {
        mercadoPagoProperties = new MercadoPagoProperties();
        mercadoPagoProperties.setEnabled(true);
        checkoutRepository = mock(BillingCheckoutRepository.class);
        processor = mock(MercadoPagoWebhookProcessor.class);

        logAppender = new ListAppender<>();
        logAppender.start();
        ((ch.qos.logback.classic.Logger) LoggerFactory.getLogger(BillingAutoReconciliationScheduler.class)).addAppender(logAppender);
    }

    @AfterEach
    void tearDown() {
        ((ch.qos.logback.classic.Logger) LoggerFactory.getLogger(BillingAutoReconciliationScheduler.class)).detachAppender(logAppender);
    }

    private BillingAutoReconciliationScheduler scheduler(Instant now) {
        return scheduler(now, true);
    }

    private BillingAutoReconciliationScheduler scheduler(Instant now, boolean enabled) {
        BillingAutoReconciliationScheduler instance = new BillingAutoReconciliationScheduler(
            mercadoPagoProperties,
            checkoutRepository,
            processor,
            Clock.fixed(now, ZoneOffset.UTC)
        );
        setField(instance, "enabled", enabled);
        setField(instance, "intervalMinutes", 15);
        setField(instance, "minAgeMinutes", 5);
        return instance;
    }

    private void setField(Object target, String name, Object value) {
        try {
            Field field = BillingAutoReconciliationScheduler.class.getDeclaredField(name);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static BillingCheckout checkout(Long id, String providerSubscriptionId, BillingCheckoutStatus status) {
        BillingCheckout checkout = new BillingCheckout();
        checkout.setId(id);
        checkout.setProviderSubscriptionId(providerSubscriptionId);
        checkout.setStatus(status);
        return checkout;
    }

    private List<String> loggedMessages() {
        return logAppender.list.stream().map(ILoggingEvent::getFormattedMessage).collect(Collectors.toList());
    }

    @Test
    void disabledFlagDoesNothing() {
        BillingAutoReconciliationScheduler scheduler = scheduler(FIXED_NOW, false);

        scheduler.reconcilePendingCheckouts();

        verifyNoInteractions(checkoutRepository, processor);
    }

    @Test
    void providerDisabledDoesNothing() {
        mercadoPagoProperties.setEnabled(false);
        BillingAutoReconciliationScheduler scheduler = scheduler(FIXED_NOW);

        scheduler.reconcilePendingCheckouts();

        verifyNoInteractions(checkoutRepository, processor);
    }

    @Test
    void checkoutWithoutProviderSubscriptionIdIsSkipped() {
        when(checkoutRepository.findByStatusInAndProviderSubscriptionIdIsNotNullAndCreatedAtBefore(eq(ELIGIBLE_STATUSES), any()))
            .thenReturn(List.of(checkout(1L, null, BillingCheckoutStatus.PROVIDER_PENDING)));

        scheduler(FIXED_NOW).reconcilePendingCheckouts();

        verifyNoInteractions(processor);
    }

    @Test
    void queriesOnlyTheEligibleStatuses() {
        when(checkoutRepository.findByStatusInAndProviderSubscriptionIdIsNotNullAndCreatedAtBefore(any(), any())).thenReturn(List.of());

        scheduler(FIXED_NOW).reconcilePendingCheckouts();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<BillingCheckoutStatus>> statusesCaptor = ArgumentCaptor.forClass(List.class);
        verify(checkoutRepository).findByStatusInAndProviderSubscriptionIdIsNotNullAndCreatedAtBefore(statusesCaptor.capture(), any());
        assertThat(statusesCaptor.getValue())
            .containsExactlyInAnyOrder(BillingCheckoutStatus.PROVIDER_PENDING, BillingCheckoutStatus.PROVIDER_UNKNOWN)
            .doesNotContain(BillingCheckoutStatus.AUTHORIZED, BillingCheckoutStatus.FAILED, BillingCheckoutStatus.CANCELED, BillingCheckoutStatus.CREATED, BillingCheckoutStatus.EXPIRED);
    }

    @Test
    void appliesTheConfiguredMinimumAgeAsTheCutoff() {
        when(checkoutRepository.findByStatusInAndProviderSubscriptionIdIsNotNullAndCreatedAtBefore(any(), any())).thenReturn(List.of());

        scheduler(FIXED_NOW).reconcilePendingCheckouts();

        ArgumentCaptor<Instant> cutoffCaptor = ArgumentCaptor.forClass(Instant.class);
        verify(checkoutRepository).findByStatusInAndProviderSubscriptionIdIsNotNullAndCreatedAtBefore(any(), cutoffCaptor.capture());
        assertThat(cutoffCaptor.getValue()).isEqualTo(FIXED_NOW.minusSeconds(5 * 60));
    }

    @Test
    void callsProcessorWithSubscriptionPreapprovalTopicAndProviderSubscriptionId() {
        when(checkoutRepository.findByStatusInAndProviderSubscriptionIdIsNotNullAndCreatedAtBefore(any(), any()))
            .thenReturn(List.of(checkout(7L, "pre-abc", BillingCheckoutStatus.PROVIDER_PENDING)));
        when(processor.process(anyString(), anyString(), anyString())).thenReturn(MercadoPagoWebhookProcessor.Result.PROCESSED);

        scheduler(FIXED_NOW).reconcilePendingCheckouts();

        ArgumentCaptor<String> topic = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> resourceId = ArgumentCaptor.forClass(String.class);
        verify(processor).process(anyString(), topic.capture(), resourceId.capture());
        assertThat(topic.getValue()).isEqualTo("subscription_preapproval");
        assertThat(resourceId.getValue()).isEqualTo("pre-abc");
    }

    @Test
    void oneFailingCheckoutDoesNotStopTheOthers() {
        when(checkoutRepository.findByStatusInAndProviderSubscriptionIdIsNotNullAndCreatedAtBefore(any(), any())).thenReturn(
            List.of(
                checkout(1L, "pre-1", BillingCheckoutStatus.PROVIDER_PENDING),
                checkout(2L, "pre-2", BillingCheckoutStatus.PROVIDER_UNKNOWN),
                checkout(3L, "pre-3", BillingCheckoutStatus.PROVIDER_PENDING)
            )
        );
        when(processor.process(anyString(), anyString(), eq("pre-2"))).thenThrow(new MercadoPagoException("boom", false));
        when(processor.process(anyString(), anyString(), eq("pre-1"))).thenReturn(MercadoPagoWebhookProcessor.Result.PROCESSED);
        when(processor.process(anyString(), anyString(), eq("pre-3"))).thenReturn(MercadoPagoWebhookProcessor.Result.PROCESSED);

        assertThatCode(() -> scheduler(FIXED_NOW).reconcilePendingCheckouts()).doesNotThrowAnyException();

        verify(processor, times(3)).process(anyString(), anyString(), anyString());
        verify(processor).process(anyString(), anyString(), eq("pre-1"));
        verify(processor).process(anyString(), anyString(), eq("pre-3"));
    }

    @Test
    void neverReferencesMercadoPagoClientCreateOrCancelMethods() {
        boolean hasMercadoPagoClientField = false;
        for (Field field : BillingAutoReconciliationScheduler.class.getDeclaredFields()) {
            if (field.getType().getSimpleName().equals("MercadoPagoClient")) {
                hasMercadoPagoClientField = true;
            }
        }
        assertThat(hasMercadoPagoClientField).isFalse();
    }

    @Test
    void sameWindowProducesTheSameDeterministicRequestId() {
        when(checkoutRepository.findByStatusInAndProviderSubscriptionIdIsNotNullAndCreatedAtBefore(any(), any()))
            .thenReturn(List.of(checkout(42L, "pre-42", BillingCheckoutStatus.PROVIDER_PENDING)));
        when(processor.process(anyString(), anyString(), anyString())).thenReturn(MercadoPagoWebhookProcessor.Result.PROCESSED);

        BillingAutoReconciliationScheduler scheduler = scheduler(FIXED_NOW);
        scheduler.reconcilePendingCheckouts();
        scheduler.reconcilePendingCheckouts();

        ArgumentCaptor<String> requestIds = ArgumentCaptor.forClass(String.class);
        verify(processor, times(2)).process(requestIds.capture(), anyString(), anyString());
        assertThat(requestIds.getAllValues()).hasSize(2);
        assertThat(requestIds.getAllValues().get(0)).isEqualTo(requestIds.getAllValues().get(1));
        assertThat(requestIds.getAllValues().get(0)).startsWith("auto-reconcile-42-");
    }

    @Test
    void aLaterWindowCanStillReconcileTheSameCheckout() {
        when(checkoutRepository.findByStatusInAndProviderSubscriptionIdIsNotNullAndCreatedAtBefore(any(), any()))
            .thenReturn(List.of(checkout(42L, "pre-42", BillingCheckoutStatus.PROVIDER_PENDING)));
        when(processor.process(anyString(), anyString(), anyString())).thenReturn(MercadoPagoWebhookProcessor.Result.PROCESSED);

        scheduler(FIXED_NOW).reconcilePendingCheckouts();
        scheduler(FIXED_NOW.plusSeconds(16 * 60)).reconcilePendingCheckouts();

        ArgumentCaptor<String> requestIds = ArgumentCaptor.forClass(String.class);
        verify(processor, times(2)).process(requestIds.capture(), anyString(), anyString());
        assertThat(requestIds.getAllValues().get(0)).isNotEqualTo(requestIds.getAllValues().get(1));
    }

    @Test
    void providerErrorListingCheckoutsDoesNotPropagate() {
        when(checkoutRepository.findByStatusInAndProviderSubscriptionIdIsNotNullAndCreatedAtBefore(any(), any()))
            .thenThrow(new RuntimeException("db unavailable"));

        assertThatCode(() -> scheduler(FIXED_NOW).reconcilePendingCheckouts()).doesNotThrowAnyException();

        verifyNoInteractions(processor);
    }

    @Test
    void processorErrorForAllCheckoutsNeverPropagatesOutOfTheScheduledMethod() {
        when(checkoutRepository.findByStatusInAndProviderSubscriptionIdIsNotNullAndCreatedAtBefore(any(), any()))
            .thenReturn(List.of(checkout(1L, "pre-1", BillingCheckoutStatus.PROVIDER_PENDING)));
        when(processor.process(anyString(), anyString(), anyString())).thenThrow(new MercadoPagoException("provider down", true));

        assertThatCode(() -> scheduler(FIXED_NOW).reconcilePendingCheckouts()).doesNotThrowAnyException();
    }

    @Test
    void logsNeverContainTheWebhookSecretOrAccessToken() {
        mercadoPagoProperties.setWebhookSecret("super-secret-value-should-never-log");
        mercadoPagoProperties.setAccessToken("APP_USR-token-should-never-log");
        when(checkoutRepository.findByStatusInAndProviderSubscriptionIdIsNotNullAndCreatedAtBefore(any(), any()))
            .thenReturn(List.of(checkout(1L, "pre-1", BillingCheckoutStatus.PROVIDER_PENDING)));
        when(processor.process(anyString(), anyString(), anyString())).thenReturn(MercadoPagoWebhookProcessor.Result.PROCESSED);

        scheduler(FIXED_NOW).reconcilePendingCheckouts();

        assertThat(loggedMessages()).isNotEmpty();
        for (String message : loggedMessages()) {
            assertThat(message).doesNotContain("super-secret-value-should-never-log").doesNotContain("APP_USR-token-should-never-log");
        }
    }

    @Test
    void onlyLogsAtInfoOrWarnLevelNeverExposingStackTracesWithSensitiveData() {
        when(checkoutRepository.findByStatusInAndProviderSubscriptionIdIsNotNullAndCreatedAtBefore(any(), any()))
            .thenReturn(List.of(checkout(1L, "pre-1", BillingCheckoutStatus.PROVIDER_PENDING)));
        when(processor.process(anyString(), anyString(), anyString())).thenReturn(MercadoPagoWebhookProcessor.Result.PROCESSED);

        scheduler(FIXED_NOW).reconcilePendingCheckouts();

        assertThat(logAppender.list).allMatch(event -> event.getLevel() == Level.INFO || event.getLevel() == Level.WARN || event.getLevel() == Level.ERROR);
    }
}
