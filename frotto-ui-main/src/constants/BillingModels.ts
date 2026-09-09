export type PlanCode = "FREE" | "BRONZE" | "SILVER" | "GOLD" | "PLATINUM" | "FROTTA";
export type SubscriptionSource = "PAYMENT_PROVIDER" | "ADMIN_GRANT" | "GRANDFATHERED";
export type SubscriptionStatus = "ACTIVE" | "PAST_DUE" | "PAUSED" | "CANCELED" | "EXPIRED";
export type BillingCycle = "MONTHLY" | "YEARLY";
export type BillingModel = "FLAT" | "PROGRESSIVE";

export interface BillingMeDTO {
  planCode: PlanCode;
  planName: string;
  subscriptionStatus: SubscriptionStatus | null;
  billingCycle: BillingCycle | null;
  subscriptionSource: SubscriptionSource | null;
  activeVehicleCount: number;
  vehicleLimit: number | null;
  canAddVehicle: boolean;
  needsUpgrade: boolean;
  requiredPlanCode: PlanCode;
  requiredPlanName: string;
  currentMonthlyPrice: number;
  currentPeriodStart: string | null;
  currentPeriodEnd: string | null;
  grantExpiresAt: string | null;
  cancelAtPeriodEnd: boolean;
}

export interface PlanPricingTierDTO {
  fromVehicleCount: number;
  toVehicleCount: number | null;
  pricePerVehicle: number;
}

export interface PlanDTO {
  code: PlanCode;
  name: string;
  minVehicles: number | null;
  maxVehicles: number | null;
  monthlyBasePrice: number;
  billingModel: BillingModel;
  tiers: PlanPricingTierDTO[];
}

export interface PricingComponent {
  fromVehicleCount: number;
  toVehicleCount: number | null;
  unitPrice: number;
  quantity: number;
  subtotal: number;
}

export interface PricePreviewDTO {
  vehicleCount: number;
  planCode: PlanCode;
  planName: string;
  billingCycle: BillingCycle;
  monthlyPrice: number;
  averagePricePerVehicle: number;
  components: PricingComponent[];
}

export interface BillingCheckoutDTO {
  checkoutId: number;
  planCode: PlanCode;
  quotedPrice: number;
  billingCycle: BillingCycle;
  status: "PROVIDER_PENDING";
  checkoutUrl: string;
}

export type BillingCheckoutStatus = "CREATED" | "PROVIDER_PENDING" | "PROVIDER_UNKNOWN" | "AUTHORIZED" | "FAILED" | "CANCELED";

export interface PaymentProviderSubscriptionState {
  status: SubscriptionStatus;
  planCode: PlanCode;
  billingCycle: BillingCycle;
}

export interface LatestCheckoutState {
  status: BillingCheckoutStatus;
  planCode: PlanCode;
  createdAt: string;
  canResume: boolean;
  checkoutUrl: string | null;
}

export interface BillingPaymentStateDTO {
  paymentProviderSubscription: PaymentProviderSubscriptionState | null;
  latestCheckout: LatestCheckoutState | null;
}

export const PLAN_LABELS: Record<PlanCode, string> = {
  FREE: "Gratuito", BRONZE: "Bronze", SILVER: "Prata", GOLD: "Ouro",
  PLATINUM: "Platinum", FROTTA: "Frotta",
};
