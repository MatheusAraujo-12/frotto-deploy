package com.localuz.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import com.localuz.domain.*;
import com.localuz.domain.enumeration.*;
import com.localuz.repository.*;
import com.localuz.service.dto.EntitlementSnapshot;
import com.localuz.web.rest.errors.*;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class VehicleLifecycleServiceTest {
    UserRepository users = mock(UserRepository.class);
    CarRepository cars = mock(CarRepository.class);
    SubscriptionRepository subscriptions = mock(SubscriptionRepository.class);
    SubscriptionPlanUpgradeRepository upgrades = mock(SubscriptionPlanUpgradeRepository.class);
    EntitlementService entitlements = mock(EntitlementService.class);
    VehicleLifecycleService service = new VehicleLifecycleService(users, cars, subscriptions, upgrades, entitlements);
    User owner = new User(); User admin = new User(); Car car = new Car(); Plan plan = new Plan();

    @BeforeEach void setup() {
        owner.setId(1L); admin.setId(9L); car.setId(2L); car.setUser(owner); car.setPlate("ABC-1234");
        plan.setCode(PlanCode.PLATINUM); plan.setMaxVehicles(100);
        when(users.findByIdForBillingCheckoutLock(1L)).thenReturn(Optional.of(owner));
        when(cars.findById(2L)).thenReturn(Optional.of(car));
        when(cars.findByIdForUpdate(2L)).thenReturn(Optional.of(car));
        when(cars.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));
        when(entitlements.getSnapshot(owner)).thenReturn(new EntitlementSnapshot(null, plan, plan, 31, 100, true, false));
    }

    @ParameterizedTest @EnumSource(CarAdminStatus.class)
    void everyOperationalStatusRemainsUsableWhenNotDeleted(CarAdminStatus status) {
        car.setAdminStatus(status); car.setActive(false); car.setDeleted(false);
        assertThatCode(() -> VehicleLifecycleService.requireOperational(car)).doesNotThrowAnyException();
    }

    @Test void deletedCarRejectsOperationalWrites() {
        car.setDeleted(true); car.setActive(true);
        assertThatThrownBy(() -> VehicleLifecycleService.requireOperational(car)).isInstanceOfSatisfying(BadRequestAlertException.class,
            failure -> assertThat(failure.getErrorKey()).isEqualTo("VEHICLE_DELETED"));
    }

    @Test void normalizationPreventsRecreatingDeletedPlate() {
        car.setDeleted(true); when(cars.findAllByUserId(1L)).thenReturn(List.of(car));
        assertThatThrownBy(() -> service.validatePlate(owner, " aBc 1234 ", null)).isInstanceOfSatisfying(BadRequestAlertException.class,
            failure -> assertThat(failure.getErrorKey()).isEqualTo("VEHICLE_PREVIOUSLY_DELETED"));
        verify(cars).findAllByUserId(1L);
    }

    @Test void administratorRestoresExistingIdentityWithAuditAndNoProviderDependency() {
        car.setDeleted(true); car.setActive(false); car.setDeletedByUserId(1L);
        Car restored = service.restore(2L, admin, "  Exclusão acidental  ");
        assertThat(restored).isSameAs(car);
        assertThat(restored.getDeleted()).isFalse(); assertThat(restored.getActive()).isTrue();
        assertThat(restored.getRestoredByUserId()).isEqualTo(9L); assertThat(restored.getRestoredAt()).isNotNull();
        assertThat(restored.getRestoreReason()).isEqualTo("Exclusão acidental");
        assertThat(restored.getDeletedByUserId()).isEqualTo(1L);
    }

    @Test void blankReasonDoesNotTouchTheDatabase() {
        assertThatThrownBy(() -> service.restore(2L, admin, "  ")).isInstanceOf(BadRequestAlertException.class);
        verifyNoInteractions(cars, users);
    }

    @Test void pendingDowngradePreventsIncompatibleAdditionAndRestore() {
        Subscription s = new Subscription(); s.setId(8L); Plan gold = new Plan(); gold.setMaxVehicles(30); s.setPendingPlan(gold);
        when(subscriptions.findByUserIdAndSource(1L, SubscriptionSource.PAYMENT_PROVIDER)).thenReturn(List.of(s));
        when(cars.countBillableByUserId(1L)).thenReturn(30L);
        assertThatThrownBy(() -> service.lockMutation(owner, true)).isInstanceOfSatisfying(BadRequestAlertException.class,
            failure -> assertThat(failure.getErrorKey()).isEqualTo("BILLING_PENDING_DOWNGRADE_FLEET_LIMIT"));
        assertThatCode(() -> service.lockMutation(owner, false)).doesNotThrowAnyException();
    }

    @Test void upgradeBlocksAllCountMutations() {
        Subscription s = new Subscription(); s.setId(8L);
        when(subscriptions.findByUserIdAndSource(1L, SubscriptionSource.PAYMENT_PROVIDER)).thenReturn(List.of(s));
        when(upgrades.findBySubscriptionIdAndStatusIn(eq(8L), any())).thenReturn(List.of(new SubscriptionPlanUpgrade()));
        assertThatThrownBy(() -> service.lockMutation(owner, true)).isInstanceOf(BillingPlanUpgradeInProgressException.class);
        assertThatThrownBy(() -> service.lockMutation(owner, false)).isInstanceOf(BillingPlanUpgradeInProgressException.class);
        car.setDeleted(true);
        assertThatThrownBy(() -> service.restore(2L, admin, "Acidente")).isInstanceOf(BillingPlanUpgradeInProgressException.class);
    }

    @Test void platinumAtOneHundredRequiresFrotta() {
        when(cars.countBillableByUserId(1L)).thenReturn(100L);
        when(entitlements.getSnapshot(owner)).thenReturn(new EntitlementSnapshot(null, plan, plan, 100, 100, false, true));
        assertThatThrownBy(() -> service.lockMutation(owner, true)).isInstanceOfSatisfying(VehicleLimitReachedException.class,
            failure -> assertThat(failure.getParameters().get("requiredPlan")).isEqualTo(PlanCode.FROTTA));
    }

    @Test void clientCannotSupplyDeletionOrRestorationAuditFields() throws Exception {
        Car payload = new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules().readValue(
            "{\"deleted\":true,\"deletedByUserId\":7,\"restoreReason\":\"spoof\",\"restoredByUserId\":8}", Car.class);
        assertThat(payload.getDeleted()).isFalse(); assertThat(payload.getDeletedByUserId()).isNull();
        assertThat(payload.getRestoreReason()).isNull(); assertThat(payload.getRestoredByUserId()).isNull();
    }
}
