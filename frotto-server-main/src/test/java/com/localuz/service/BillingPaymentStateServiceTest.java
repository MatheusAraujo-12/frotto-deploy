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
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class BillingPaymentStateServiceTest {
    private SubscriptionRepository subscriptions; private BillingCheckoutRepository checkouts;
    private BillingPaymentStateService service; private User user;
    @BeforeEach void setUp(){subscriptions=mock(SubscriptionRepository.class);checkouts=mock(BillingCheckoutRepository.class);service=new BillingPaymentStateService(subscriptions,checkouts);user=new User();user.setId(42L);}

    @ParameterizedTest @MethodSource("subscriptionStatuses")
    void exposesEveryPaymentProviderStatusEvenWhenItIsNotEffective(SubscriptionStatus status){
        Subscription paid=subscription(status);when(subscriptions.findFirstByUserIdAndSourceOrderByStartDateDesc(42L,SubscriptionSource.PAYMENT_PROVIDER)).thenReturn(Optional.of(paid));
        BillingPaymentStateDTO result=service.getState(user);
        assertThat(result.getPaymentProviderSubscription().getStatus()).isEqualTo(status);
        assertThat(result.getPaymentProviderSubscription().getPlanCode()).isEqualTo(PlanCode.BRONZE);
        assertThat(result.getPaymentProviderSubscription().getBillingCycle()).isEqualTo(BillingCycle.MONTHLY);
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
    private Subscription subscription(SubscriptionStatus status){Subscription value=new Subscription();value.setStatus(status);value.setPlan(plan());value.setBillingCycle(BillingCycle.MONTHLY);return value;}
    private BillingCheckout checkout(BillingCheckoutStatus status){BillingCheckout value=new BillingCheckout();value.setStatus(status);value.setPlan(plan());value.setCreatedAt(Instant.parse("2026-08-28T12:00:00Z"));return value;}
    private Plan plan(){Plan value=new Plan();value.setCode(PlanCode.BRONZE);return value;}
}
