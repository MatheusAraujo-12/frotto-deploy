package com.localuz.web.rest;

import com.localuz.domain.Subscription;
import com.localuz.domain.User;
import com.localuz.repository.SubscriptionRepository;
import com.localuz.repository.UserRepository;
import com.localuz.security.AuthoritiesConstants;
import com.localuz.service.EntitlementService;
import com.localuz.service.GrandfatheringService;
import com.localuz.service.SubscriptionAdminService;
import com.localuz.service.UserService;
import com.localuz.service.dto.AdminBillingUserDTO;
import com.localuz.service.dto.GrandfatherPreviewDTO;
import com.localuz.service.dto.GrandfatherResultDTO;
import com.localuz.service.dto.GrantPlanRequestDTO;
import com.localuz.service.dto.SubscriptionAdminDTO;
import com.localuz.web.rest.errors.BadRequestAlertException;
import javax.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Administrative Billing API: manual plan grants, revocation, per-user audit lookup, and the
 * (never auto-triggered) grandfathering tool - see the Billing Etapa 3 report.
 *
 * ROLE_ADMIN is enforced twice, deliberately: SecurityConfiguration already maps
 * "/api/admin/**" to hasAuthority(ADMIN) (unchanged - no security config edit was needed), and
 * the class-level @PreAuthorize below is a second, code-local layer that keeps working even if
 * the URL pattern ever changes or a method is moved to a different base path. Applying it at
 * class level (not per-method, unlike this codebase's other admin resources) is intentional
 * here: a future endpoint added to this class can't accidentally ship unprotected.
 */
@RestController
@RequestMapping("/api/admin/billing")
@PreAuthorize("hasAuthority(\"" + AuthoritiesConstants.ADMIN + "\")")
public class AdminBillingResource {

    private static final String ENTITY_NAME = "adminBilling";

    private final UserService userService;
    private final UserRepository userRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final SubscriptionAdminService subscriptionAdminService;
    private final GrandfatheringService grandfatheringService;
    private final EntitlementService entitlementService;

    public AdminBillingResource(
        UserService userService,
        UserRepository userRepository,
        SubscriptionRepository subscriptionRepository,
        SubscriptionAdminService subscriptionAdminService,
        GrandfatheringService grandfatheringService,
        EntitlementService entitlementService
    ) {
        this.userService = userService;
        this.userRepository = userRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.subscriptionAdminService = subscriptionAdminService;
        this.grandfatheringService = grandfatheringService;
        this.entitlementService = entitlementService;
    }

    @PostMapping("/grants")
    public SubscriptionAdminDTO grantPlan(@Valid @RequestBody GrantPlanRequestDTO request) {
        Subscription grant = subscriptionAdminService.grantPlan(request, getCurrentAdmin());
        return SubscriptionAdminDTO.from(grant);
    }

    @PostMapping("/grants/{subscriptionId}/revoke")
    public SubscriptionAdminDTO revokeGrant(@PathVariable Long subscriptionId) {
        Subscription revoked = subscriptionAdminService.revokeGrant(subscriptionId);
        return SubscriptionAdminDTO.from(revoked);
    }

    @GetMapping("/users/{userId}")
    public AdminBillingUserDTO getUserBilling(@PathVariable Long userId) {
        User targetUser = getUserOrThrow(userId);
        return AdminBillingUserDTO.from(
            targetUser,
            entitlementService.getSnapshot(targetUser),
            subscriptionRepository.findByUserIdOrderByStartDateDesc(userId)
        );
    }

    @GetMapping("/grandfather/{userId}/preview")
    public GrandfatherPreviewDTO previewGrandfathering(@PathVariable Long userId) {
        return grandfatheringService.preview(getUserOrThrow(userId));
    }

    @PostMapping("/grandfather/{userId}")
    public GrandfatherResultDTO applyGrandfathering(@PathVariable Long userId) {
        return grandfatheringService.apply(getUserOrThrow(userId));
    }

    private User getUserOrThrow(Long userId) {
        return userRepository
            .findById(userId)
            .orElseThrow(() -> new BadRequestAlertException("User not found", ENTITY_NAME, "usernotfound"));
    }

    private User getCurrentAdmin() {
        return userService
            .getUserWithAuthorities()
            .orElseThrow(() -> new BadRequestAlertException("Usuario administrador autenticado nao encontrado", ENTITY_NAME, "usernotfound"));
    }
}
