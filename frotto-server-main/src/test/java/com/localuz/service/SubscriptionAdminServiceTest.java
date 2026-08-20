package com.localuz.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.localuz.domain.Plan;
import com.localuz.domain.Subscription;
import com.localuz.domain.User;
import com.localuz.domain.enumeration.PlanCode;
import com.localuz.domain.enumeration.SubscriptionSource;
import com.localuz.domain.enumeration.SubscriptionStatus;
import com.localuz.repository.CarRepository;
import com.localuz.repository.PlanRepository;
import com.localuz.repository.SubscriptionRepository;
import com.localuz.repository.UserRepository;
import com.localuz.service.dto.GrantPlanRequestDTO;
import com.localuz.web.rest.errors.BadRequestAlertException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

class SubscriptionAdminServiceTest {

    private static final List<SubscriptionStatus> OPEN_STATUSES = List.of(SubscriptionStatus.ACTIVE, SubscriptionStatus.PAST_DUE);

    private SubscriptionRepository subscriptionRepository;
    private PlanRepository planRepository;
    private UserRepository userRepository;
    private CarRepository carRepository;
    private SubscriptionAdminService subscriptionAdminService;

    private User targetUser;
    private User admin;

    @BeforeEach
    void setUp() {
        subscriptionRepository = Mockito.mock(SubscriptionRepository.class);
        planRepository = Mockito.mock(PlanRepository.class);
        userRepository = Mockito.mock(UserRepository.class);
        carRepository = Mockito.mock(CarRepository.class);
        subscriptionAdminService = new SubscriptionAdminService(subscriptionRepository, planRepository, userRepository, carRepository);

        targetUser = new User();
        targetUser.setId(123L);
        admin = new User();
        admin.setId(1L);

        when(subscriptionRepository.save(any(Subscription.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(subscriptionRepository.findByUserIdAndSourceAndStatusIn(123L, SubscriptionSource.ADMIN_GRANT, OPEN_STATUSES))
            .thenReturn(List.of());
    }

    private static Plan plan(PlanCode code, boolean active) {
        Plan plan = new Plan();
        plan.setId(10L);
        plan.setCode(code);
        plan.setName(code.name());
        plan.setActive(active);
        return plan;
    }

    private static GrantPlanRequestDTO request(Long userId, PlanCode planCode, Instant expiresAt, String reason) {
        GrantPlanRequestDTO dto = new GrantPlanRequestDTO();
        dto.setUserId(userId);
        dto.setPlanCode(planCode);
        dto.setExpiresAt(expiresAt);
        dto.setReason(reason);
        return dto;
    }

    @Test
    void grantsAPermanentPlanWithFullAuditTrail() {
        when(userRepository.findById(123L)).thenReturn(Optional.of(targetUser));
        when(planRepository.findByCode(PlanCode.GOLD)).thenReturn(Optional.of(plan(PlanCode.GOLD, true)));
        when(carRepository.countByUserIdAndActiveTrue(123L)).thenReturn(4L);

        Subscription grant = subscriptionAdminService.grantPlan(request(123L, PlanCode.GOLD, null, "Parceiro comercial"), admin);

        assertThat(grant.getUser()).isEqualTo(targetUser);
        assertThat(grant.getPlan().getCode()).isEqualTo(PlanCode.GOLD);
        assertThat(grant.getSource()).isEqualTo(SubscriptionSource.ADMIN_GRANT);
        assertThat(grant.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(grant.getContractedPrice()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(grant.getContractedVehicleCount()).isEqualTo(4);
        assertThat(grant.getGrantedBy()).isEqualTo(admin);
        assertThat(grant.getGrantedAt()).isNotNull();
        assertThat(grant.getGrantReason()).isEqualTo("Parceiro comercial");
        assertThat(grant.getGrantExpiresAt()).isNull();
    }

    @Test
    void grantsATemporaryPlanWithAFutureExpiry() {
        when(userRepository.findById(123L)).thenReturn(Optional.of(targetUser));
        when(planRepository.findByCode(PlanCode.GOLD)).thenReturn(Optional.of(plan(PlanCode.GOLD, true)));
        when(carRepository.countByUserIdAndActiveTrue(123L)).thenReturn(0L);

        Instant future = Instant.now().plusSeconds(86_400);
        Subscription grant = subscriptionAdminService.grantPlan(request(123L, PlanCode.GOLD, future, "Trial"), admin);

        assertThat(grant.getGrantExpiresAt()).isEqualTo(future);
    }

    @Test
    void newGrantClosesAConflictingPreviousAdminGrantWithoutDeletingIt() {
        Subscription previousGrant = new Subscription();
        previousGrant.setId(99L);
        previousGrant.setStatus(SubscriptionStatus.ACTIVE);
        previousGrant.setSource(SubscriptionSource.ADMIN_GRANT);
        when(userRepository.findById(123L)).thenReturn(Optional.of(targetUser));
        when(planRepository.findByCode(PlanCode.PLATINUM)).thenReturn(Optional.of(plan(PlanCode.PLATINUM, true)));
        when(subscriptionRepository.findByUserIdAndSourceAndStatusIn(123L, SubscriptionSource.ADMIN_GRANT, OPEN_STATUSES))
            .thenReturn(List.of(previousGrant));
        when(carRepository.countByUserIdAndActiveTrue(123L)).thenReturn(0L);

        subscriptionAdminService.grantPlan(request(123L, PlanCode.PLATINUM, null, null), admin);

        ArgumentCaptor<Subscription> saved = ArgumentCaptor.forClass(Subscription.class);
        Mockito.verify(subscriptionRepository, Mockito.times(2)).save(saved.capture());
        Subscription closedPrevious = saved.getAllValues().get(0);
        assertThat(closedPrevious).isSameAs(previousGrant);
        assertThat(closedPrevious.getStatus()).isEqualTo(SubscriptionStatus.CANCELED);
        assertThat(closedPrevious.getCanceledAt()).isNotNull();
    }

    @Test
    void twoConflictingAdminGrantsAreBothClosedByANewGrant() {
        // Etapa 3 (revisão) scenario F: two ACTIVE ADMIN_GRANT rows for the same user must not
        // remain simultaneously "current" after a new grant is made.
        Subscription grantA = new Subscription();
        grantA.setId(91L);
        grantA.setStatus(SubscriptionStatus.ACTIVE);
        grantA.setSource(SubscriptionSource.ADMIN_GRANT);
        Subscription grantB = new Subscription();
        grantB.setId(92L);
        grantB.setStatus(SubscriptionStatus.PAST_DUE);
        grantB.setSource(SubscriptionSource.ADMIN_GRANT);
        when(userRepository.findById(123L)).thenReturn(Optional.of(targetUser));
        when(planRepository.findByCode(PlanCode.PLATINUM)).thenReturn(Optional.of(plan(PlanCode.PLATINUM, true)));
        when(subscriptionRepository.findByUserIdAndSourceAndStatusIn(123L, SubscriptionSource.ADMIN_GRANT, OPEN_STATUSES))
            .thenReturn(List.of(grantA, grantB));
        when(carRepository.countByUserIdAndActiveTrue(123L)).thenReturn(0L);

        subscriptionAdminService.grantPlan(request(123L, PlanCode.PLATINUM, null, null), admin);

        assertThat(grantA.getStatus()).isEqualTo(SubscriptionStatus.CANCELED);
        assertThat(grantB.getStatus()).isEqualTo(SubscriptionStatus.CANCELED);
        Mockito.verify(subscriptionRepository, Mockito.times(3)).save(any(Subscription.class)); // grantA + grantB + the new one
    }

    @Test
    void grantingAPlanNeverTouchesAPaymentProviderSubscription() {
        // Etapa 3 (revisão) scenario G: granting a plan must never cancel/query a
        // PAYMENT_PROVIDER (or GRANDFATHERED) subscription as a side effect - only the
        // source=ADMIN_GRANT lookup is ever made.
        when(userRepository.findById(123L)).thenReturn(Optional.of(targetUser));
        when(planRepository.findByCode(PlanCode.FROTTA)).thenReturn(Optional.of(plan(PlanCode.FROTTA, true)));
        when(carRepository.countByUserIdAndActiveTrue(123L)).thenReturn(0L);

        subscriptionAdminService.grantPlan(request(123L, PlanCode.FROTTA, null, "Cortesia temporária"), admin);

        Mockito.verify(subscriptionRepository).findByUserIdAndSourceAndStatusIn(123L, SubscriptionSource.ADMIN_GRANT, OPEN_STATUSES);
        Mockito.verify(subscriptionRepository, Mockito.never()).findByUserIdAndStatusInOrderByStartDateDesc(Mockito.anyLong(), Mockito.anyList());
        Mockito.verify(subscriptionRepository, Mockito.times(1)).save(any(Subscription.class)); // only the new grant itself
    }

    @Test
    void rejectsUnknownUser() {
        when(userRepository.findById(123L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> subscriptionAdminService.grantPlan(request(123L, PlanCode.GOLD, null, null), admin))
            .isInstanceOf(BadRequestAlertException.class);
    }

    @Test
    void rejectsUnknownPlan() {
        when(userRepository.findById(123L)).thenReturn(Optional.of(targetUser));
        when(planRepository.findByCode(PlanCode.GOLD)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> subscriptionAdminService.grantPlan(request(123L, PlanCode.GOLD, null, null), admin))
            .isInstanceOf(BadRequestAlertException.class);
    }

    @Test
    void rejectsInactivePlan() {
        when(userRepository.findById(123L)).thenReturn(Optional.of(targetUser));
        when(planRepository.findByCode(PlanCode.GOLD)).thenReturn(Optional.of(plan(PlanCode.GOLD, false)));

        assertThatThrownBy(() -> subscriptionAdminService.grantPlan(request(123L, PlanCode.GOLD, null, null), admin))
            .isInstanceOf(BadRequestAlertException.class);
    }

    @Test
    void rejectsGrantingFreeAsAnAdminGrant() {
        when(userRepository.findById(123L)).thenReturn(Optional.of(targetUser));
        when(planRepository.findByCode(PlanCode.FREE)).thenReturn(Optional.of(plan(PlanCode.FREE, true)));

        assertThatThrownBy(() -> subscriptionAdminService.grantPlan(request(123L, PlanCode.FREE, null, null), admin))
            .isInstanceOf(BadRequestAlertException.class);
    }

    @Test
    void rejectsAnExpiresAtInThePast() {
        when(userRepository.findById(123L)).thenReturn(Optional.of(targetUser));
        when(planRepository.findByCode(PlanCode.GOLD)).thenReturn(Optional.of(plan(PlanCode.GOLD, true)));

        Instant past = Instant.now().minusSeconds(60);

        assertThatThrownBy(() -> subscriptionAdminService.grantPlan(request(123L, PlanCode.GOLD, past, null), admin))
            .isInstanceOf(BadRequestAlertException.class);
    }

    @Test
    void revokeMarksAnActiveGrantCanceled() {
        Subscription grant = new Subscription();
        grant.setId(55L);
        grant.setStatus(SubscriptionStatus.ACTIVE);
        grant.setSource(SubscriptionSource.ADMIN_GRANT);
        when(subscriptionRepository.findById(55L)).thenReturn(Optional.of(grant));

        Subscription revoked = subscriptionAdminService.revokeGrant(55L);

        assertThat(revoked.getStatus()).isEqualTo(SubscriptionStatus.CANCELED);
        assertThat(revoked.getCanceledAt()).isNotNull();
    }

    @Test
    void revokeIsIdempotentAndDoesNotOverwriteTheOriginalCanceledAt() {
        Instant originalCanceledAt = Instant.parse("2026-01-01T00:00:00Z");
        Subscription alreadyRevoked = new Subscription();
        alreadyRevoked.setId(55L);
        alreadyRevoked.setStatus(SubscriptionStatus.CANCELED);
        alreadyRevoked.setSource(SubscriptionSource.ADMIN_GRANT);
        alreadyRevoked.setCanceledAt(originalCanceledAt);
        when(subscriptionRepository.findById(55L)).thenReturn(Optional.of(alreadyRevoked));

        Subscription result = subscriptionAdminService.revokeGrant(55L);

        assertThat(result.getCanceledAt()).isEqualTo(originalCanceledAt);
        Mockito.verify(subscriptionRepository, Mockito.never()).save(any(Subscription.class));
    }

    @Test
    void revokeRejectsANonAdminGrantSubscription() {
        Subscription paymentProviderSubscription = new Subscription();
        paymentProviderSubscription.setId(55L);
        paymentProviderSubscription.setStatus(SubscriptionStatus.ACTIVE);
        paymentProviderSubscription.setSource(SubscriptionSource.PAYMENT_PROVIDER);
        when(subscriptionRepository.findById(55L)).thenReturn(Optional.of(paymentProviderSubscription));

        assertThatThrownBy(() -> subscriptionAdminService.revokeGrant(55L)).isInstanceOf(BadRequestAlertException.class);
    }

    @Test
    void revokeRejectsAnUnknownSubscriptionId() {
        when(subscriptionRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> subscriptionAdminService.revokeGrant(404L)).isInstanceOf(BadRequestAlertException.class);
    }

    @Test
    void historyIsNeverDeletedOnlyCanceled() {
        // Documents the "no physical DELETE" requirement: revoke only ever calls save(), never
        // a delete/remove method on the repository.
        Subscription grant = new Subscription();
        grant.setId(55L);
        grant.setStatus(SubscriptionStatus.ACTIVE);
        grant.setSource(SubscriptionSource.ADMIN_GRANT);
        when(subscriptionRepository.findById(55L)).thenReturn(Optional.of(grant));

        subscriptionAdminService.revokeGrant(55L);

        Mockito.verify(subscriptionRepository, Mockito.never()).delete(any(Subscription.class));
        Mockito.verify(subscriptionRepository, Mockito.never()).deleteById(Mockito.anyLong());
    }
}
