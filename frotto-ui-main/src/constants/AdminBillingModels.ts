export type PlanCode = "FREE" | "BRONZE" | "SILVER" | "GOLD" | "PLATINUM" | "FROTTA";
export type GrantablePlanCode = Exclude<PlanCode, "FREE">;
export type SubscriptionSource = "PAYMENT_PROVIDER" | "ADMIN_GRANT" | "GRANDFATHERED";
export type SubscriptionStatus = "ACTIVE" | "PAST_DUE" | "CANCELED" | "EXPIRED";
export type BillingCycle = "MONTHLY" | "YEARLY";

export interface AdminUserSearchResult {
  id: number;
  login: string;
  email?: string;
  firstName?: string;
  lastName?: string;
}

export interface SubscriptionHistoryEntry {
  id: number;
  planCode: PlanCode;
  planName: string;
  source: SubscriptionSource;
  status: SubscriptionStatus;
  startDate?: string;
  canceledAt?: string | null;
  grantedByLogin?: string | null;
  grantReason?: string | null;
  grantExpiresAt?: string | null;
  contractedPrice: number;
  contractedVehicleCount: number;
}

export interface AdminBillingUser {
  userId: number;
  userLogin: string;
  userEmail?: string;
  currentSubscriptionId?: number | null;
  planCode: PlanCode;
  planName: string;
  source?: SubscriptionSource | null;
  subscriptionStatus?: SubscriptionStatus | null;
  billingCycle?: BillingCycle | null;
  grantExpiresAt?: string | null;
  currentPeriodStart?: string | null;
  currentPeriodEnd?: string | null;
  activeVehicleCount: number;
  vehicleLimit?: number | null;
  canAddVehicle: boolean;
  needsUpgrade: boolean;
  requiredPlanCode: PlanCode;
  requiredPlanName: string;
  history: SubscriptionHistoryEntry[];
}

export interface GrantPlanPayload {
  userId: number;
  planCode: GrantablePlanCode;
  expiresAt?: string | null;
  reason?: string | null;
}

export interface SubscriptionAdminResult {
  id: number;
  userId: number;
  userLogin: string;
  planCode: PlanCode;
  planName: string;
  source: SubscriptionSource;
  status: SubscriptionStatus;
  billingCycle: BillingCycle;
  startDate?: string;
  grantedByUserId?: number | null;
  grantedByLogin?: string | null;
  grantedAt?: string | null;
  grantReason?: string | null;
  grantExpiresAt?: string | null;
  canceledAt?: string | null;
  contractedPrice: number;
  contractedVehicleCount: number;
}

export interface GrandfatherPreview {
  userId: number;
  activeVehicleCount: number;
  requiredPlanCode: PlanCode;
  requiredPlanName: string;
  alreadyHasCurrentSubscription: boolean;
  wouldCreateSubscription: boolean;
}

export interface GrandfatherResult {
  preview: GrandfatherPreview;
  subscriptionCreated: boolean;
  subscriptionId?: number | null;
}

export const GRANTABLE_PLAN_CODES: GrantablePlanCode[] = ["BRONZE", "SILVER", "GOLD", "PLATINUM", "FROTTA"];

export const PLAN_LABELS: Record<PlanCode, string> = {
  FREE: "Gratuito",
  BRONZE: "Bronze",
  SILVER: "Prata",
  GOLD: "Ouro",
  PLATINUM: "Platinum",
  FROTTA: "Frotta",
};

export const SOURCE_LABELS: Record<SubscriptionSource, string> = {
  PAYMENT_PROVIDER: "Pagamento",
  ADMIN_GRANT: "Cortesia (admin)",
  GRANDFATHERED: "Legado (grandfathered)",
};

export const STATUS_LABELS: Record<SubscriptionStatus, string> = {
  ACTIVE: "Ativa",
  PAST_DUE: "Em atraso",
  CANCELED: "Cancelada",
  EXPIRED: "Expirada",
};
