import { AdminBillingUser, GrantPlanPayload, GrantablePlanCode, SubscriptionStatus } from "../../constants/AdminBillingModels";
import type { FrottoBadgeVariant } from "../../components/UI/FrottoBadge";

/**
 * Pure logic extracted out of AdminBillingPage so it can be unit tested without mounting any
 * Ionic component. Full-render interaction tests (typing into IonSearchbar, opening the
 * IonModal, picking an IonSelect option) were attempted but proved too slow/unreliable in this
 * project's current jsdom test setup - see the Billing Etapa 4A report for details and the
 * recommended follow-up. This module is what actually carries the business rules that matter
 * (validation, revoke-eligibility, payload shaping), so it's what gets tested directly.
 */

export const REVOCABLE_STATUSES = ["ACTIVE", "PAST_DUE"];

export interface GrantFormErrors {
  planCode?: string;
  expiresAt?: string;
}

/** An ADMIN_GRANT can only be revoked through this panel while it's still ACTIVE/PAST_DUE. */
export function canRevokeSubscription(user: AdminBillingUser | null): boolean {
  if (!user || !user.currentSubscriptionId) {
    return false;
  }
  if (user.source !== "ADMIN_GRANT") {
    return false;
  }
  return REVOCABLE_STATUSES.includes(user.subscriptionStatus || "");
}

/**
 * Mirrors the backend's own grant validation (plan required, expiresAt must be in the future)
 * so invalid input is caught before the request is even sent - the backend still re-validates
 * independently and remains the source of truth.
 */
export function validateGrantForm(
  planCode: GrantablePlanCode | "",
  hasExpiry: boolean,
  expiresAtDate: string,
  today: Date = new Date()
): GrantFormErrors {
  const errors: GrantFormErrors = {};
  if (!planCode) {
    errors.planCode = "Selecione um plano.";
  }
  const todayIso = today.toISOString().slice(0, 10);
  if (hasExpiry && (!expiresAtDate || expiresAtDate < todayIso)) {
    errors.expiresAt = "Informe uma data futura.";
  }
  return errors;
}

/**
 * Real SubscriptionStatus values only (same enum as MyPlan's statusVariant) - no invented
 * "Trial"/"Inadimplente" bucket. CANCELED/EXPIRED are terminal-but-normal, never "danger".
 */
export function statusVariant(status: SubscriptionStatus | null | undefined): FrottoBadgeVariant {
  const variants: Record<SubscriptionStatus, FrottoBadgeVariant> = {
    ACTIVE: "success", PAST_DUE: "warning", PAUSED: "warning", CANCELED: "neutral", EXPIRED: "neutral",
  };
  return status ? variants[status] : "neutral";
}

export function buildGrantPayload(
  userId: number,
  planCode: GrantablePlanCode,
  hasExpiry: boolean,
  expiresAtDate: string,
  reason: string
): GrantPlanPayload {
  return {
    userId,
    planCode,
    expiresAt: hasExpiry && expiresAtDate ? `${expiresAtDate}T23:59:59Z` : null,
    reason: reason.trim() || null,
  };
}
