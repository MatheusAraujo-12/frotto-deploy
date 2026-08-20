package com.localuz.web.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.localuz.domain.Plan;
import com.localuz.domain.Subscription;
import com.localuz.domain.User;
import com.localuz.domain.enumeration.PlanCode;
import com.localuz.repository.SubscriptionRepository;
import com.localuz.repository.UserRepository;
import com.localuz.service.EntitlementService;
import com.localuz.service.GrandfatheringService;
import com.localuz.service.SubscriptionAdminService;
import com.localuz.service.UserService;
import com.localuz.service.dto.AdminBillingUserDTO;
import com.localuz.service.dto.AdminUserSearchDTO;
import com.localuz.service.dto.EntitlementSnapshot;
import com.localuz.service.dto.GrandfatherPreviewDTO;
import com.localuz.service.dto.GrandfatherResultDTO;
import com.localuz.service.dto.GrantPlanRequestDTO;
import com.localuz.service.dto.SubscriptionAdminDTO;
import com.localuz.web.rest.errors.BadRequestAlertException;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;

/**
 * Plain unit tests for the business logic AdminBillingResource delegates to. ROLE_ADMIN
 * enforcement itself (the @PreAuthorize on the class) is not exercised here - calling the
 * resource's Java methods directly bypasses the Spring Security AOP proxy entirely, so it
 * cannot prove a ROLE_USER caller is blocked. See AdminBillingResourceSecurityTest for what is
 * verified instead, and the Billing Etapa 3 report for why a full MockMvc/@SpringBootTest
 * security test was not added in this stage.
 */
class AdminBillingResourceTest {

    private UserService userService;
    private UserRepository userRepository;
    private SubscriptionRepository subscriptionRepository;
    private SubscriptionAdminService subscriptionAdminService;
    private GrandfatheringService grandfatheringService;
    private EntitlementService entitlementService;
    private AdminBillingResource adminBillingResource;

    private User admin;
    private User targetUser;

    @BeforeEach
    void setUp() {
        userService = Mockito.mock(UserService.class);
        userRepository = Mockito.mock(UserRepository.class);
        subscriptionRepository = Mockito.mock(SubscriptionRepository.class);
        subscriptionAdminService = Mockito.mock(SubscriptionAdminService.class);
        grandfatheringService = Mockito.mock(GrandfatheringService.class);
        entitlementService = Mockito.mock(EntitlementService.class);
        adminBillingResource = new AdminBillingResource(
            userService,
            userRepository,
            subscriptionRepository,
            subscriptionAdminService,
            grandfatheringService,
            entitlementService
        );

        admin = new User();
        admin.setId(1L);
        admin.setLogin("admin");
        targetUser = new User();
        targetUser.setId(123L);
        targetUser.setLogin("cliente");
        targetUser.setEmail("cliente@empresa.com");
    }

    private static Plan plan(PlanCode code) {
        Plan plan = new Plan();
        plan.setCode(code);
        plan.setName(code.name());
        return plan;
    }

    private static Subscription grantOf(User user, Plan plan) {
        Subscription subscription = new Subscription();
        subscription.setId(5L);
        subscription.setUser(user);
        subscription.setPlan(plan);
        subscription.setContractedPrice(java.math.BigDecimal.ZERO);
        subscription.setContractedVehicleCount(0);
        return subscription;
    }

    @Test
    void grantPlanResolvesTheActingAdminFromTheSecurityContextOnly() {
        when(userService.getUserWithAuthorities()).thenReturn(Optional.of(admin));
        GrantPlanRequestDTO request = new GrantPlanRequestDTO();
        request.setUserId(123L);
        request.setPlanCode(PlanCode.GOLD);
        Subscription grant = grantOf(targetUser, plan(PlanCode.GOLD));
        when(subscriptionAdminService.grantPlan(request, admin)).thenReturn(grant);

        SubscriptionAdminDTO dto = adminBillingResource.grantPlan(request);

        assertThat(dto.getPlanCode()).isEqualTo(PlanCode.GOLD);
        Mockito.verify(subscriptionAdminService).grantPlan(request, admin);
    }

    @Test
    void grantPlanRejectsWhenNoAdminIsAuthenticated() {
        when(userService.getUserWithAuthorities()).thenReturn(Optional.empty());
        GrantPlanRequestDTO request = new GrantPlanRequestDTO();
        request.setUserId(123L);
        request.setPlanCode(PlanCode.GOLD);

        assertThatThrownBy(() -> adminBillingResource.grantPlan(request)).isInstanceOf(BadRequestAlertException.class);
    }

    @Test
    void revokeGrantDelegatesToTheAdminService() {
        Subscription revoked = grantOf(targetUser, plan(PlanCode.GOLD));
        when(subscriptionAdminService.revokeGrant(5L)).thenReturn(revoked);

        SubscriptionAdminDTO dto = adminBillingResource.revokeGrant(5L);

        assertThat(dto.getId()).isEqualTo(5L);
        Mockito.verify(subscriptionAdminService).revokeGrant(5L);
    }

    @Test
    void getUserBillingComposesEntitlementSnapshotAndHistory() {
        when(userRepository.findById(123L)).thenReturn(Optional.of(targetUser));
        Plan free = plan(PlanCode.FREE);
        EntitlementSnapshot snapshot = new EntitlementSnapshot(null, free, free, 1L, 2, true, false);
        when(entitlementService.getSnapshot(targetUser)).thenReturn(snapshot);
        when(subscriptionRepository.findByUserIdOrderByStartDateDesc(123L)).thenReturn(List.of());

        AdminBillingUserDTO dto = adminBillingResource.getUserBilling(123L);

        assertThat(dto.getUserId()).isEqualTo(123L);
        assertThat(dto.getUserEmail()).isEqualTo("cliente@empresa.com");
        assertThat(dto.getPlanCode()).isEqualTo(PlanCode.FREE);
    }

    @Test
    void getUserBillingRejectsAnUnknownUserId() {
        when(userRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> adminBillingResource.getUserBilling(404L)).isInstanceOf(BadRequestAlertException.class);
    }

    @Test
    void previewGrandfatheringDelegatesToTheGrandfatheringService() {
        when(userRepository.findById(123L)).thenReturn(Optional.of(targetUser));
        GrandfatherPreviewDTO preview = new GrandfatherPreviewDTO(123L, 10L, PlanCode.SILVER, "Prata", false, true);
        when(grandfatheringService.preview(targetUser)).thenReturn(preview);

        GrandfatherPreviewDTO result = adminBillingResource.previewGrandfathering(123L);

        assertThat(result.getRequiredPlanCode()).isEqualTo(PlanCode.SILVER);
    }

    @Test
    void applyGrandfatheringDelegatesToTheGrandfatheringService() {
        when(userRepository.findById(123L)).thenReturn(Optional.of(targetUser));
        GrandfatherPreviewDTO preview = new GrandfatherPreviewDTO(123L, 10L, PlanCode.SILVER, "Prata", false, true);
        GrandfatherResultDTO applyResult = new GrandfatherResultDTO(preview, true, 999L);
        when(grandfatheringService.apply(targetUser)).thenReturn(applyResult);

        GrandfatherResultDTO result = adminBillingResource.applyGrandfathering(123L);

        assertThat(result.getSubscriptionId()).isEqualTo(999L);
        Mockito.verify(grandfatheringService).apply(targetUser);
        Mockito.verify(grandfatheringService, Mockito.never()).preview(Mockito.any());
    }

    @Test
    void applyGrandfatheringRejectsAnUnknownUserId() {
        when(userRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> adminBillingResource.applyGrandfathering(404L)).isInstanceOf(BadRequestAlertException.class);
    }

    @Test
    void searchUsersReturnsMatchesFromLoginOrEmail() {
        Page<User> page = new PageImpl<>(List.of(targetUser));
        when(
            userRepository.findByLoginContainingIgnoreCaseOrEmailContainingIgnoreCase(
                Mockito.eq("cliente"),
                Mockito.eq("cliente"),
                Mockito.any()
            )
        )
            .thenReturn(page);

        List<AdminUserSearchDTO> results = adminBillingResource.searchUsers("cliente");

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getLogin()).isEqualTo("cliente");
    }

    @Test
    void searchUsersReturnsEmptyForABlankQueryWithoutHittingTheRepository() {
        List<AdminUserSearchDTO> results = adminBillingResource.searchUsers("   ");

        assertThat(results).isEmpty();
        Mockito.verifyNoInteractions(userRepository);
    }
}
