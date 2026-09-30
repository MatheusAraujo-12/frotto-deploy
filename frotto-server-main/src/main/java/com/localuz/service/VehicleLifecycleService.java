package com.localuz.service;

import com.localuz.domain.Car;
import com.localuz.domain.Subscription;
import com.localuz.domain.User;
import com.localuz.domain.enumeration.SubscriptionSource;
import com.localuz.repository.CarRepository;
import com.localuz.repository.SubscriptionRepository;
import com.localuz.repository.SubscriptionPlanUpgradeRepository;
import com.localuz.repository.UserRepository;
import com.localuz.web.rest.errors.BadRequestAlertException;
import com.localuz.web.rest.errors.BillingPlanUpgradeInProgressException;
import com.localuz.web.rest.errors.VehicleLimitReachedException;
import java.time.Instant;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Account lock is held through the caller's fleet mutation transaction. Never calls a provider. */
@Service
@Transactional
public class VehicleLifecycleService {
    private final UserRepository users;
    private final CarRepository cars;
    private final SubscriptionRepository subscriptions;
    private final SubscriptionPlanUpgradeRepository upgrades;
    private final EntitlementService entitlements;

    public VehicleLifecycleService(UserRepository users, CarRepository cars, SubscriptionRepository subscriptions,
        SubscriptionPlanUpgradeRepository upgrades, EntitlementService entitlements) {
        this.users = users; this.cars = cars; this.subscriptions = subscriptions;
        this.upgrades = upgrades; this.entitlements = entitlements;
    }

    public void lockMutation(User owner, boolean adding) {
        lockOwner(owner);
        long count = cars.countBillableByUserId(owner.getId());
        for (Subscription subscription : subscriptions.findByUserIdAndSource(owner.getId(), SubscriptionSource.PAYMENT_PROVIDER)) {
            if (!upgrades.findBySubscriptionIdAndStatusIn(subscription.getId(), SubscriptionPlanChangeSteps.OPEN_UPGRADE_STATUSES).isEmpty()) {
                throw new BillingPlanUpgradeInProgressException();
            }
            if (adding && subscription.getPendingPlan() != null && subscription.getPendingPlan().getMaxVehicles() != null
                && count >= subscription.getPendingPlan().getMaxVehicles()) {
                throw new BadRequestAlertException(
                    "Há um downgrade agendado incompatível com essa quantidade de veículos. Desfaça o downgrade antes de adicionar este veículo.",
                    "car", "BILLING_PENDING_DOWNGRADE_FLEET_LIMIT");
            }
        }
        if (adding) {
            var snapshot = entitlements.getSnapshot(owner);
            if (!snapshot.isCanAddVehicle()) {
                throw new VehicleLimitReachedException(snapshot.getCurrentPlan().getCode(), count,
                    snapshot.getVehicleLimit(), snapshot.getCurrentPlan().getCode() == com.localuz.domain.enumeration.PlanCode.PLATINUM
                        ? com.localuz.domain.enumeration.PlanCode.FROTTA : snapshot.getRequiredPlan().getCode());
            }
        }
    }

    public void lockOwner(User owner) {
        users.findByIdForBillingCheckoutLock(owner.getId()).orElseThrow();
    }

    public void validatePlate(User owner, String plate, Long exceptId) {
        String normalized = normalizePlate(plate);
        if (normalized.isBlank()) return;
        for (Car existing : cars.findAllByUserId(owner.getId())) {
            if (existing.getId().equals(exceptId) || !normalizePlate(existing.getPlate()).equals(normalized)) continue;
            if (Boolean.TRUE.equals(existing.getDeleted())) {
                throw new BadRequestAlertException(
                    "Este veículo já foi excluído anteriormente. Para restaurá-lo, entre em contato com o suporte.",
                    "car", "VEHICLE_PREVIOUSLY_DELETED");
            }
            throw new BadRequestAlertException("Já existe um veículo com esta placa nesta conta.", "car", "VEHICLE_PLATE_EXISTS");
        }
    }

    public static String normalizePlate(String plate) {
        return plate == null ? "" : plate.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
    }

    public static void requireOperational(Car car) {
        if (car != null && Boolean.TRUE.equals(car.getDeleted())) {
            throw new BadRequestAlertException("Veículo excluído: o histórico está disponível somente para consulta.", "car", "VEHICLE_DELETED");
        }
    }

    public Car restore(Long id, User administrator, String reason) {
        if (reason == null || reason.isBlank() || reason.trim().length() > 500) {
            throw new BadRequestAlertException("Informe o motivo da restauração (até 500 caracteres).", "car", "RESTORE_REASON_REQUIRED");
        }
        Car car = cars.findById(id).orElseThrow(() -> new BadRequestAlertException("Veículo não encontrado.", "car", "idnotfound"));
        lockMutation(car.getUser(), true);
        car = cars.findByIdForUpdate(id).orElseThrow();
        if (!Boolean.TRUE.equals(car.getDeleted())) {
            throw new BadRequestAlertException("O veículo não está excluído.", "car", "VEHICLE_NOT_DELETED");
        }
        validatePlate(car.getUser(), car.getPlate(), car.getId());
        car.setDeleted(false); car.setActive(true);
        car.setRestoredAt(Instant.now()); car.setRestoredByUserId(administrator.getId()); car.setRestoreReason(reason.trim());
        return cars.saveAndFlush(car);
    }
}
