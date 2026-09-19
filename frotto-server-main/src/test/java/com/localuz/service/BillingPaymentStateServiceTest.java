package com.localuz.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.localuz.domain.BillingCheckout;
import com.localuz.domain.Plan;
import com.localuz.domain.Subscription;
import com.localuz.domain.User;
import com.localuz.domain.enumeration.BillingCheckoutStatus;
import com.localuz.domain.enumeration.BillingCycle;
import com.localuz.domain.enumeration.PlanCode;
import com.localuz.domain.enumeration.SubscriptionSource;
import com.localuz.domain.enumeration.SubscriptionStatus;
import com.localuz.repository.BillingCheckoutRepository;
import com.localuz.repository.SubscriptionRepository;
import com.localuz.service.dto.BillingPaymentStateDTO;
import com.localuz.service.dto.FinancialCoverageEvaluation;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class BillingPaymentStateServiceTest {
    private static final Instant NOW = Instant.parse("2026-09-18T12:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    private SubscriptionRepository subscriptions; private BillingCheckoutRepository checkouts;
    private SubscriptionFinancialCoverageService financialCoverage;
    private RecurringSubscriptionGuardService recurringSubscriptionGuard;
    private BillingPaymentStateService service; private User user;
    @BeforeEach void setUp(){
        subscriptions=mock(SubscriptionRepository.class);checkouts=mock(BillingCheckoutRepository.class);
        financialCoverage=mock(SubscriptionFinancialCoverageService.class);
        recurringSubscriptionGuard=new RecurringSubscriptionGuardService(subscriptions,financialCoverage,CLOCK);
        service=new BillingPaymentStateService(subscriptions,checkouts,financialCoverage,recurringSubscriptionGuard);user=new User();user.setId(42L);
    }

    private void stubProviderRows(Subscription... rows){
        when(subscriptions.findByUserIdAndSource(42L,SubscriptionSource.PAYMENT_PROVIDER)).thenReturn(List.of(rows));
    }

    @ParameterizedTest @MethodSource("subscriptionStatuses")
    void exposesEveryPaymentProviderStatusEvenWhenItIsNotEffective(SubscriptionStatus status){
        Subscription paid=subscription(status);
        stubProviderRows(paid);
        stubTerminalCoverage(paid,status,false);
        when(financialCoverage.evaluate(paid)).thenReturn(new FinancialCoverageEvaluation(false,FinancialCoverageEvaluation.CommercialState.AWAITING_PAYMENT,null,null,null,null,FinancialCoverageEvaluation.Reason.NO_INVOICE));
        BillingPaymentStateDTO result=service.getState(user);
        assertThat(result.getPaymentProviderSubscription().getStatus()).isEqualTo(status);
        assertThat(result.getPaymentProviderSubscription().getPlanCode()).isEqualTo(PlanCode.BRONZE);
        assertThat(result.getPaymentProviderSubscription().getBillingCycle()).isEqualTo(BillingCycle.MONTHLY);
    }

    @Test void statusActiveWithoutFinancialEvidenceIsNotReportedAsCovered(){
        // Reproduces the staging observation: Subscription.status=ACTIVE but zero BillingInvoice/PaymentAttempt.
        Subscription paid=subscription(SubscriptionStatus.ACTIVE);
        stubProviderRows(paid);
        when(financialCoverage.evaluate(paid)).thenReturn(new FinancialCoverageEvaluation(false,FinancialCoverageEvaluation.CommercialState.AWAITING_PAYMENT,null,null,null,null,FinancialCoverageEvaluation.Reason.NO_INVOICE));
        BillingPaymentStateDTO result=service.getState(user);
        assertThat(result.getPaymentProviderSubscription().getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(result.getPaymentProviderSubscription().isFinanciallyCovered()).isFalse();
    }

    /**
     * 5G.9 section B: canCancel answers "can this remote contract be cancelled", never "does it
     * currently grant paid access" - reproduces the staging bug where an ACTIVE-but-financially-
     * unproven subscription made BillingMeDTO fall back to FREE and hid the cancel action.
     */
    @ParameterizedTest @MethodSource("cancellableStatuses")
    void canCancelReflectsCancellationEligibilityIndependentlyOfFinancialCoverage(SubscriptionStatus status, boolean expectedCanCancel){
        Subscription paid=subscription(status);
        stubProviderRows(paid);
        stubTerminalCoverage(paid,status,false);
        when(financialCoverage.evaluate(paid)).thenReturn(new FinancialCoverageEvaluation(false,FinancialCoverageEvaluation.CommercialState.AWAITING_PAYMENT,null,null,null,null,FinancialCoverageEvaluation.Reason.NO_INVOICE));
        BillingPaymentStateDTO result=service.getState(user);
        assertThat(result.getPaymentProviderSubscription().isCanCancel()).isEqualTo(expectedCanCancel);
        assertThat(result.getPaymentProviderSubscription().isFinanciallyCovered()).isFalse();
    }

    @Test void canCancelIsFalseWhenThereIsNoPaymentProviderSubscriptionAtAll(){
        BillingPaymentStateDTO result=service.getState(user);
        assertThat(result.getPaymentProviderSubscription()).isNull();
    }

    @Test void exposesCancellationStateAndCurrentPeriodEndFromTheRawSubscriptionRow(){
        Subscription paid=subscription(SubscriptionStatus.ACTIVE);
        paid.setCancelAtPeriodEnd(true);
        Instant periodEnd=Instant.parse("2026-10-01T12:00:00Z");
        paid.setCurrentPeriodEnd(periodEnd);
        stubProviderRows(paid);
        when(financialCoverage.evaluate(paid)).thenReturn(new FinancialCoverageEvaluation(false,FinancialCoverageEvaluation.CommercialState.AWAITING_PAYMENT,null,null,null,null,FinancialCoverageEvaluation.Reason.NO_INVOICE));
        BillingPaymentStateDTO result=service.getState(user);
        assertThat(result.getPaymentProviderSubscription().getCancellationState()).isEqualTo(com.localuz.service.dto.SubscriptionCancellationState.PENDING_CONFIRMATION);
        assertThat(result.getPaymentProviderSubscription().getCurrentPeriodEnd()).isEqualTo(periodEnd);
    }

    /**
     * 5G.9 section 3: a user can accumulate more than one PAYMENT_PROVIDER row over time. The
     * still-chargeable one (found via RecurringSubscriptionGuardService#isStillChargeable, the
     * exact same rule that blocks a second checkout) must always win over a merely more-recently-
     * started but already-closed row - "most recent by startDate" alone must never decide this.
     */
    @Test void picksTheStillChargeableRowEvenWhenAnAlreadyClosedRowStartedMoreRecently(){
        Subscription oldButChargeable=subscription(SubscriptionStatus.ACTIVE);
        oldButChargeable.setId(1L);
        oldButChargeable.setStartDate(NOW.minusSeconds(86400));
        Subscription newerButClosed=subscription(SubscriptionStatus.CANCELED);
        newerButClosed.setId(2L);
        newerButClosed.setStartDate(NOW); // sorts after oldButChargeable by startDate
        newerButClosed.setCanceledAt(NOW.minusSeconds(60));
        when(financialCoverage.evaluate(newerButClosed,NOW)).thenReturn(new FinancialCoverageEvaluation(false,FinancialCoverageEvaluation.CommercialState.CANCELED,null,null,null,null,FinancialCoverageEvaluation.Reason.RENEWAL_STOPPED));
        stubProviderRows(oldButChargeable,newerButClosed);
        when(financialCoverage.evaluate(oldButChargeable)).thenReturn(new FinancialCoverageEvaluation(false,FinancialCoverageEvaluation.CommercialState.AWAITING_PAYMENT,null,null,null,null,FinancialCoverageEvaluation.Reason.NO_INVOICE));

        BillingPaymentStateDTO result=service.getState(user);

        assertThat(result.getPaymentProviderSubscription().getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(result.getPaymentProviderSubscription().isCanCancel()).isTrue();
    }

    /** When NO row is currently chargeable, falls back to the most recently started row for display only - canCancel stays false regardless of which one is shown. */
    @Test void fallsBackToMostRecentlyStartedRowForDisplayWhenNoneIsChargeable(){
        Subscription oldest=subscription(SubscriptionStatus.CANCELED);
        oldest.setId(1L);
        oldest.setStartDate(NOW.minusSeconds(172800));
        oldest.setCanceledAt(NOW.minusSeconds(90000));
        Subscription mostRecent=subscription(SubscriptionStatus.EXPIRED);
        mostRecent.setId(2L);
        mostRecent.setStartDate(NOW.minusSeconds(86400));
        when(financialCoverage.evaluate(oldest,NOW)).thenReturn(new FinancialCoverageEvaluation(false,FinancialCoverageEvaluation.CommercialState.CANCELED,null,null,null,null,FinancialCoverageEvaluation.Reason.RENEWAL_STOPPED));
        when(financialCoverage.evaluate(mostRecent,NOW)).thenReturn(new FinancialCoverageEvaluation(false,FinancialCoverageEvaluation.CommercialState.EXPIRED,null,null,null,null,FinancialCoverageEvaluation.Reason.PERIOD_EXPIRED));
        stubProviderRows(oldest,mostRecent);
        when(financialCoverage.evaluate(mostRecent)).thenReturn(new FinancialCoverageEvaluation(false,FinancialCoverageEvaluation.CommercialState.EXPIRED,null,null,null,null,FinancialCoverageEvaluation.Reason.PERIOD_EXPIRED));

        BillingPaymentStateDTO result=service.getState(user);

        assertThat(result.getPaymentProviderSubscription().getStatus()).isEqualTo(SubscriptionStatus.EXPIRED);
        assertThat(result.getPaymentProviderSubscription().isCanCancel()).isFalse();
    }

    static Stream<org.junit.jupiter.params.provider.Arguments> cancellableStatuses(){
        return Stream.of(
            org.junit.jupiter.params.provider.Arguments.of(SubscriptionStatus.ACTIVE,true),
            org.junit.jupiter.params.provider.Arguments.of(SubscriptionStatus.PAST_DUE,true),
            org.junit.jupiter.params.provider.Arguments.of(SubscriptionStatus.PAUSED,true),
            org.junit.jupiter.params.provider.Arguments.of(SubscriptionStatus.CANCELED,false),
            org.junit.jupiter.params.provider.Arguments.of(SubscriptionStatus.EXPIRED,false)
        );
    }

    @Test void statusActiveWithApprovedEvidenceIsReportedAsCovered(){
        Subscription paid=subscription(SubscriptionStatus.ACTIVE);
        stubProviderRows(paid);
        when(financialCoverage.evaluate(paid)).thenReturn(new FinancialCoverageEvaluation(true,FinancialCoverageEvaluation.CommercialState.ACTIVE,99L,Instant.parse("2026-08-28T00:00:00Z"),Instant.parse("2026-09-28T00:00:00Z"),null,FinancialCoverageEvaluation.Reason.PAID));
        BillingPaymentStateDTO result=service.getState(user);
        assertThat(result.getPaymentProviderSubscription().isFinanciallyCovered()).isTrue();
    }

    @Test void financialCoverageIsNeverEvaluatedWhenThereIsNoPaymentProviderSubscription(){
        BillingPaymentStateDTO result=service.getState(user);
        assertThat(result.getPaymentProviderSubscription()).isNull();
        org.mockito.Mockito.verifyNoInteractions(financialCoverage);
    }

    @ParameterizedTest @MethodSource("checkoutStatuses")
    void exposesRelevantLatestCheckoutStates(BillingCheckoutStatus status){
        BillingCheckout checkout=checkout(status);when(checkouts.findFirstByUserIdOrderByCreatedAtDesc(42L)).thenReturn(Optional.of(checkout));
        BillingPaymentStateDTO result=service.getState(user);
        assertThat(result.getLatestCheckout().getStatus()).isEqualTo(status);
        assertThat(result.getLatestCheckout().getPlanCode()).isEqualTo(PlanCode.BRONZE);
        assertThat(result.getLatestCheckout().getCreatedAt()).isEqualTo(Instant.parse("2026-08-28T12:00:00Z"));
    }

    @Test void returnsNullSectionsWhenThereIsNoPaymentHistory(){BillingPaymentStateDTO result=service.getState(user);assertThat(result.getPaymentProviderSubscription()).isNull();assertThat(result.getLatestCheckout()).isNull();}

    @Test void contractDoesNotExposeProviderOrOwnershipIdentifiers(){
        List<String> fields=Stream.of(BillingPaymentStateDTO.class,BillingPaymentStateDTO.PaymentProviderSubscription.class,BillingPaymentStateDTO.LatestCheckout.class).flatMap(type->Stream.of(type.getDeclaredFields())).map(field->field.getName()).toList();
        // checkoutUrl is intentionally exposed since it is the resumable checkout's own init_point,
        // the same URL createCheckout already returns for the browser to redirect to.
        assertThat(fields).doesNotContain("providerSubscriptionId","externalSubscriptionId","externalReference","idempotencyKey","userId");
    }

    @ParameterizedTest @MethodSource("resumeEligibility")
    void computesCanResumeFromStatusAndInitPointPresence(BillingCheckoutStatus status, boolean hasInitPoint, boolean expectedCanResume){
        BillingCheckout checkout=checkout(status);
        if(hasInitPoint) checkout.setInitPoint("https://mp.test/checkout/resume");
        when(checkouts.findFirstByUserIdOrderByCreatedAtDesc(42L)).thenReturn(Optional.of(checkout));
        BillingPaymentStateDTO result=service.getState(user);
        assertThat(result.getLatestCheckout().isCanResume()).isEqualTo(expectedCanResume);
        assertThat(result.getLatestCheckout().getCheckoutUrl()).isEqualTo(expectedCanResume?"https://mp.test/checkout/resume":null);
    }

    @Test void neverExposesAnotherUsersCheckoutUrl(){
        User userA=new User();userA.setId(1L);User userB=new User();userB.setId(2L);
        BillingCheckout checkoutA=checkout(BillingCheckoutStatus.PROVIDER_PENDING);checkoutA.setInitPoint("https://mp.test/user-a");
        BillingCheckout checkoutB=checkout(BillingCheckoutStatus.PROVIDER_PENDING);checkoutB.setInitPoint("https://mp.test/user-b");
        when(checkouts.findFirstByUserIdOrderByCreatedAtDesc(1L)).thenReturn(Optional.of(checkoutA));
        when(checkouts.findFirstByUserIdOrderByCreatedAtDesc(2L)).thenReturn(Optional.of(checkoutB));

        BillingPaymentStateDTO resultA=service.getState(userA);
        BillingPaymentStateDTO resultB=service.getState(userB);

        assertThat(resultA.getLatestCheckout().getCheckoutUrl()).isEqualTo("https://mp.test/user-a");
        assertThat(resultB.getLatestCheckout().getCheckoutUrl()).isEqualTo("https://mp.test/user-b");
        org.mockito.Mockito.verify(checkouts).findFirstByUserIdOrderByCreatedAtDesc(1L);
        org.mockito.Mockito.verify(checkouts).findFirstByUserIdOrderByCreatedAtDesc(2L);
        org.mockito.Mockito.verifyNoMoreInteractions(checkouts);
    }

    static Stream<org.junit.jupiter.params.provider.Arguments> resumeEligibility(){
        return Stream.of(
            org.junit.jupiter.params.provider.Arguments.of(BillingCheckoutStatus.CREATED,true,true),
            org.junit.jupiter.params.provider.Arguments.of(BillingCheckoutStatus.CREATED,false,false),
            org.junit.jupiter.params.provider.Arguments.of(BillingCheckoutStatus.PROVIDER_PENDING,true,true),
            org.junit.jupiter.params.provider.Arguments.of(BillingCheckoutStatus.PROVIDER_PENDING,false,false),
            org.junit.jupiter.params.provider.Arguments.of(BillingCheckoutStatus.PROVIDER_UNKNOWN,true,true),
            org.junit.jupiter.params.provider.Arguments.of(BillingCheckoutStatus.PROVIDER_UNKNOWN,false,false),
            org.junit.jupiter.params.provider.Arguments.of(BillingCheckoutStatus.AUTHORIZED,true,false),
            org.junit.jupiter.params.provider.Arguments.of(BillingCheckoutStatus.FAILED,true,false),
            org.junit.jupiter.params.provider.Arguments.of(BillingCheckoutStatus.EXPIRED,true,false),
            org.junit.jupiter.params.provider.Arguments.of(BillingCheckoutStatus.CANCELED,true,false)
        );
    }

    static Stream<SubscriptionStatus> subscriptionStatuses(){return Stream.of(SubscriptionStatus.ACTIVE,SubscriptionStatus.PAST_DUE,SubscriptionStatus.PAUSED,SubscriptionStatus.CANCELED);}
    static Stream<BillingCheckoutStatus> checkoutStatuses(){return Stream.of(BillingCheckoutStatus.PROVIDER_PENDING,BillingCheckoutStatus.PROVIDER_UNKNOWN,BillingCheckoutStatus.FAILED,BillingCheckoutStatus.AUTHORIZED,BillingCheckoutStatus.CANCELED);}
    private Subscription subscription(SubscriptionStatus status){Subscription value=new Subscription();value.setStatus(status);value.setPlan(plan());value.setBillingCycle(BillingCycle.MONTHLY);value.setStartDate(NOW);return value;}
    private BillingCheckout checkout(BillingCheckoutStatus status){BillingCheckout value=new BillingCheckout();value.setStatus(status);value.setPlan(plan());value.setCreatedAt(Instant.parse("2026-08-28T12:00:00Z"));return value;}
    private Plan plan(){Plan value=new Plan();value.setCode(PlanCode.BRONZE);return value;}
    /** Stubs the 2-arg financialCoverage.evaluate(subscription, now) the guard consults ONLY for CANCELED/EXPIRED statuses (ACTIVE/PAST_DUE/PAUSED short-circuit before ever calling it). */
    private void stubTerminalCoverage(Subscription subscription, SubscriptionStatus status, boolean covered){
        if(status==SubscriptionStatus.CANCELED||status==SubscriptionStatus.EXPIRED){
            when(financialCoverage.evaluate(subscription,NOW)).thenReturn(new FinancialCoverageEvaluation(covered,FinancialCoverageEvaluation.CommercialState.CANCELED,null,null,null,null,FinancialCoverageEvaluation.Reason.RENEWAL_STOPPED));
        }
    }
}
