package com.localuz.web.rest;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.localuz.domain.Car;
import com.localuz.domain.Plan;
import com.localuz.domain.User;
import com.localuz.domain.enumeration.PlanCode;
import com.localuz.repository.CarRepository;
import com.localuz.repository.DriverCarRepository;
import com.localuz.repository.InspectionRepository;
import com.localuz.repository.MaintenanceRepository;
import com.localuz.service.EntitlementService;
import com.localuz.service.UserService;
import com.localuz.service.dto.CarFormDTO;
import com.localuz.service.dto.EntitlementSnapshot;
import com.localuz.web.rest.errors.VehicleLimitReachedException;
import java.lang.reflect.Field;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/**
 * Etapa 3 enforcement scenarios (billing.enforcement.enabled) at the createCar entry point.
 * billingEnforcementEnabled is normally set by @Value/Spring; set directly via reflection here
 * since there is no Spring context in this test (matching this project's existing test style
 * of plain Mockito unit tests, not @WebMvcTest/@SpringBootTest).
 */
class CarResourceEnforcementTest {

    private CarRepository carRepository;
    private UserService userService;
    private EntitlementService entitlementService;
    private CarResource carResource;
    private User currentUser;

    @BeforeEach
    void setUp() {
        carRepository = Mockito.mock(CarRepository.class);
        userService = Mockito.mock(UserService.class);
        entitlementService = Mockito.mock(EntitlementService.class);
        carResource = new CarResource(
            carRepository,
            userService,
            Mockito.mock(DriverCarRepository.class),
            Mockito.mock(InspectionRepository.class),
            Mockito.mock(MaintenanceRepository.class),
            entitlementService
        );

        currentUser = new User();
        currentUser.setId(3L);
        when(userService.getUserWithAuthorities()).thenReturn(Optional.of(currentUser));
        when(carRepository.save(Mockito.any(Car.class))).thenAnswer(invocation -> {
            Car car = invocation.getArgument(0);
            car.setId(1L);
            return car;
        });
    }

    private void setEnforcementFlag(boolean enabled) throws ReflectiveOperationException {
        Field field = CarResource.class.getDeclaredField("billingEnforcementEnabled");
        field.setAccessible(true);
        field.set(carResource, enabled);
    }

    private static Plan plan(PlanCode code, Integer maxVehicles) {
        Plan plan = new Plan();
        plan.setCode(code);
        plan.setName(code.name());
        plan.setMaxVehicles(maxVehicles);
        return plan;
    }

    private static Car newCar() {
        Car car = new Car();
        car.setPlate("ABC1234");
        car.setCommissionPercent(0f);
        return car;
    }

    private void mockSnapshotAtLimit(PlanCode code, int limit) {
        Plan currentPlan = plan(code, limit);
        when(entitlementService.getSnapshot(currentUser))
            .thenReturn(new EntitlementSnapshot(null, currentPlan, currentPlan, limit, limit, false, true));
    }

    @Test
    void flagDisabledAllowsCreationRegardlessOfLimit() throws Exception {
        setEnforcementFlag(false);

        carResource.createCar(newCar());

        Mockito.verifyNoInteractions(entitlementService);
        Mockito.verify(carRepository).save(Mockito.any(Car.class));
    }

    @Test
    void flagEnabledAllowsCreationWhenUnderLimit() throws Exception {
        setEnforcementFlag(true);
        Plan free = plan(PlanCode.FREE, 2);
        when(entitlementService.getSnapshot(currentUser)).thenReturn(new EntitlementSnapshot(null, free, free, 1L, 2, true, false));

        carResource.createCar(newCar());

        Mockito.verify(carRepository).save(Mockito.any(Car.class));
    }

    @Test
    void flagEnabledAllowsFirstFreeVehicle() throws Exception {
        setEnforcementFlag(true);
        Plan free = plan(PlanCode.FREE, 2);
        when(entitlementService.getSnapshot(currentUser)).thenReturn(new EntitlementSnapshot(null, free, free, 0L, 2, true, false));

        carResource.createCar(newCar());

        Mockito.verify(carRepository).save(Mockito.any(Car.class));
    }

    @Test
    void flagEnabledBlocksCreationWhenAtLimit() throws Exception {
        setEnforcementFlag(true);
        Plan free = plan(PlanCode.FREE, 2);
        Plan bronze = plan(PlanCode.BRONZE, 5);
        when(entitlementService.getSnapshot(currentUser))
            .thenReturn(new EntitlementSnapshot(null, free, bronze, 2L, 2, false, true));

        assertThatThrownBy(() -> carResource.createCar(newCar())).isInstanceOf(VehicleLimitReachedException.class);

        Mockito.verify(carRepository, Mockito.never()).save(Mockito.any(Car.class));
    }

    @Test
    void flagEnabledBlocksBronzeSixthVehicle() throws Exception {
        setEnforcementFlag(true);
        mockSnapshotAtLimit(PlanCode.BRONZE, 5);

        assertThatThrownBy(() -> carResource.createCar(newCar())).isInstanceOf(VehicleLimitReachedException.class);
        Mockito.verify(carRepository, Mockito.never()).save(Mockito.any(Car.class));
    }

    @Test
    void flagEnabledBlocksSilverSixteenthVehicle() throws Exception {
        setEnforcementFlag(true);
        mockSnapshotAtLimit(PlanCode.SILVER, 15);

        assertThatThrownBy(() -> carResource.createCar(newCar())).isInstanceOf(VehicleLimitReachedException.class);
        Mockito.verify(carRepository, Mockito.never()).save(Mockito.any(Car.class));
    }

    @Test
    void flagEnabledBlocksGoldThirtyFirstVehicle() throws Exception {
        setEnforcementFlag(true);
        mockSnapshotAtLimit(PlanCode.GOLD, 30);

        assertThatThrownBy(() -> carResource.createCar(newCar())).isInstanceOf(VehicleLimitReachedException.class);
        Mockito.verify(carRepository, Mockito.never()).save(Mockito.any(Car.class));
    }

    @Test
    void flagEnabledBlocksPlatinumOneHundredFirstVehicle() throws Exception {
        setEnforcementFlag(true);
        mockSnapshotAtLimit(PlanCode.PLATINUM, 100);

        assertThatThrownBy(() -> carResource.createCar(newCar())).isInstanceOf(VehicleLimitReachedException.class);
        Mockito.verify(carRepository, Mockito.never()).save(Mockito.any(Car.class));
    }

    @Test
    void flagEnabledAllowsUnlimitedFrottaCreation() throws Exception {
        setEnforcementFlag(true);
        Plan frotta = plan(PlanCode.FROTTA, null);
        when(entitlementService.getSnapshot(currentUser))
            .thenReturn(new EntitlementSnapshot(null, frotta, frotta, 10_000L, null, true, false));

        carResource.createCar(newCar());

        Mockito.verify(carRepository).save(Mockito.any(Car.class));
    }

    @Test
    void multipartCreationUsesTheSameEnforcement() throws Exception {
        setEnforcementFlag(true);
        mockSnapshotAtLimit(PlanCode.FREE, 2);

        assertThatThrownBy(() -> carResource.createCarMultipart(new CarFormDTO()))
            .isInstanceOf(VehicleLimitReachedException.class);
        Mockito.verify(carRepository, Mockito.never()).save(Mockito.any(Car.class));
    }

    @Test
    void flagEnabledDoesNotInterfereWithUpdatingAnExistingCar() throws Exception {
        // "não interferir em edição" - partialUpdateCar never calls EntitlementService at all.
        setEnforcementFlag(true);
        Car existing = new Car();
        existing.setId(7L);
        existing.setUser(currentUser);
        when(carRepository.findByCurrentUserAndId(7L)).thenReturn(Optional.of(existing));

        Car patch = new Car();
        patch.setId(7L);
        patch.setName("Novo nome");
        carResource.partialUpdateCar(7L, patch);

        Mockito.verifyNoInteractions(entitlementService);
    }
}
