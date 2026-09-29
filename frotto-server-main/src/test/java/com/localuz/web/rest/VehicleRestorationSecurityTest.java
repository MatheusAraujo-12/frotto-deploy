package com.localuz.web.rest;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.localuz.domain.Car;
import com.localuz.repository.*;
import com.localuz.service.*;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;
import org.springframework.security.core.context.SecurityContextHolder;

class VehicleRestorationSecurityTest {
    @Configuration @EnableGlobalMethodSecurity(prePostEnabled = true)
    static class Config {
        @Bean VehicleLifecycleService lifecycle() { return mock(VehicleLifecycleService.class); }
        @Bean CarResource resource(VehicleLifecycleService lifecycle) {
            UserService users = mock(UserService.class);
            when(users.getUserWithAuthorities()).thenReturn(Optional.of(new com.localuz.domain.User()));
            return new CarResource(mock(CarRepository.class), users, mock(DriverCarRepository.class),
                mock(InspectionRepository.class), mock(MaintenanceRepository.class), mock(EntitlementService.class), lifecycle);
        }
    }
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }
    @Test void ordinaryUserCannotInvokeTheRestoreService() {
        try (var context = new AnnotationConfigApplicationContext(Config.class)) {
            SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("user", "test", "ROLE_USER"));
            assertThatThrownBy(() -> context.getBean(CarResource.class).restoreCar(1L, new CarResource.RestoreRequest("Acidente")))
                .isInstanceOf(AccessDeniedException.class);
            verifyNoInteractions(context.getBean(VehicleLifecycleService.class));
        }
    }
    @Test void administratorCanInvokeAuditedRestoration() {
        try (var context = new AnnotationConfigApplicationContext(Config.class)) {
            SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("admin", "test", "ROLE_ADMIN"));
            var service = context.getBean(VehicleLifecycleService.class);
            when(service.restore(eq(1L), any(), eq("Acidente"))).thenReturn(new Car());
            assertThat(context.getBean(CarResource.class).restoreCar(1L, new CarResource.RestoreRequest("Acidente"))).isNotNull();
            verify(service).restore(eq(1L), any(), eq("Acidente"));
        }
    }
}
