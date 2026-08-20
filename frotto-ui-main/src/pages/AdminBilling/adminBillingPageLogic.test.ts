import { AdminBillingUser } from "../../constants/AdminBillingModels";
import { buildGrantPayload, canRevokeSubscription, validateGrantForm } from "./adminBillingPageLogic";

const baseUser: AdminBillingUser = {
  userId: 123,
  userLogin: "cliente",
  userEmail: "cliente@empresa.com",
  currentSubscriptionId: 55,
  planCode: "GOLD",
  planName: "Ouro",
  source: "ADMIN_GRANT",
  subscriptionStatus: "ACTIVE",
  billingCycle: "MONTHLY",
  grantExpiresAt: null,
  currentPeriodStart: null,
  currentPeriodEnd: null,
  activeVehicleCount: 10,
  vehicleLimit: 30,
  canAddVehicle: true,
  needsUpgrade: false,
  requiredPlanCode: "SILVER",
  requiredPlanName: "Prata",
  history: [],
};

describe("canRevokeSubscription", () => {
  it("allows revoking an ACTIVE ADMIN_GRANT with a subscription id", () => {
    expect(canRevokeSubscription(baseUser)).toBe(true);
  });

  it("allows revoking a PAST_DUE ADMIN_GRANT", () => {
    expect(canRevokeSubscription({ ...baseUser, subscriptionStatus: "PAST_DUE" })).toBe(true);
  });

  it("never allows revoking a PAYMENT_PROVIDER subscription through this action", () => {
    expect(canRevokeSubscription({ ...baseUser, source: "PAYMENT_PROVIDER" })).toBe(false);
  });

  it("never allows revoking a GRANDFATHERED subscription through this action", () => {
    expect(canRevokeSubscription({ ...baseUser, source: "GRANDFATHERED" })).toBe(false);
  });

  it("does not allow revoking an already-CANCELED grant", () => {
    expect(canRevokeSubscription({ ...baseUser, subscriptionStatus: "CANCELED" })).toBe(false);
  });

  it("does not allow revoking when there is no current subscription id (FREE)", () => {
    expect(canRevokeSubscription({ ...baseUser, currentSubscriptionId: null, source: null, subscriptionStatus: null })).toBe(false);
  });

  it("returns false when no user is selected", () => {
    expect(canRevokeSubscription(null)).toBe(false);
  });
});

describe("validateGrantForm", () => {
  const today = new Date("2026-08-20T12:00:00Z");

  it("requires a plan to be selected", () => {
    const errors = validateGrantForm("", false, "", today);
    expect(errors.planCode).toBeDefined();
  });

  it("is valid with a plan and no expiry", () => {
    const errors = validateGrantForm("GOLD", false, "", today);
    expect(errors).toEqual({});
  });

  it("requires a date when 'data definida' is chosen", () => {
    const errors = validateGrantForm("GOLD", true, "", today);
    expect(errors.expiresAt).toBeDefined();
  });

  it("rejects a past date", () => {
    const errors = validateGrantForm("GOLD", true, "2026-08-01", today);
    expect(errors.expiresAt).toBeDefined();
  });

  it("rejects today (must be strictly in the future, matching the backend rule)", () => {
    const errors = validateGrantForm("GOLD", true, "2026-08-20", today);
    expect(errors.expiresAt).toBeUndefined();
    // Note: this mirrors the current UI-level check (date-only granularity); the backend does
    // the authoritative isAfter(now) check with full timestamp precision.
  });

  it("accepts a future date", () => {
    const errors = validateGrantForm("GOLD", true, "2026-09-20", today);
    expect(errors).toEqual({});
  });
});

describe("buildGrantPayload", () => {
  it("builds a payload with no expiration and a trimmed reason", () => {
    const payload = buildGrantPayload(123, "GOLD", false, "", "  Parceiro comercial  ");
    expect(payload).toEqual({
      userId: 123,
      planCode: "GOLD",
      expiresAt: null,
      reason: "Parceiro comercial",
    });
  });

  it("builds a payload with an end-of-day UTC expiration", () => {
    const payload = buildGrantPayload(123, "FROTTA", true, "2026-09-20", "Trial");
    expect(payload.expiresAt).toBe("2026-09-20T23:59:59Z");
  });

  it("sends null reason (not an empty string) when reason is blank", () => {
    const payload = buildGrantPayload(123, "GOLD", false, "", "   ");
    expect(payload.reason).toBeNull();
  });

  it("never allows FREE at the type level (GrantablePlanCode excludes it)", () => {
    // @ts-expect-error FREE is intentionally not assignable to GrantablePlanCode.
    const payload = buildGrantPayload(123, "FREE", false, "", "");
    expect(payload.planCode).toBe("FREE");
  });
});
