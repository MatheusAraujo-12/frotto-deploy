export type PlanCode = "FREE" | "BRONZE" | "SILVER" | "GOLD" | "PLATINUM" | "FROTTA";
export type SubscriptionSource = "PAYMENT_PROVIDER" | "ADMIN_GRANT" | "GRANDFATHERED";
export type SubscriptionStatus = "ACTIVE" | "PAST_DUE" | "PAUSED" | "CANCELED" | "EXPIRED";
export type BillingCycle = "MONTHLY" | "YEARLY";
export type BillingModel = "FLAT" | "PROGRESSIVE";
export type SubscriptionCancellationState = "NONE" | "PENDING_CONFIRMATION" | "CONFIRMED";

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
  cancellationState: SubscriptionCancellationState;
  /** 5G.12: present only while a downgrade is scheduled (Subscription.pendingPlan). planCode/planName above stay the CURRENT (still effective) plan until planChangeEffectiveAt. */
  pendingPlanCode?: PlanCode | null;
  pendingPlanName?: string | null;
  pendingPlanPrice?: number | null;
  planChangeEffectiveAt?: string | null;
}

export type PlanChangeType = "UPGRADE" | "DOWNGRADE";

export interface PlanChangeResultDTO {
  currentPlan: PlanCode;
  targetPlan: PlanCode;
  changeType: PlanChangeType;
  effectiveAt: string | null;
  contractedPrice: number;
  pending: boolean;
}

export interface SubscriptionCancellationResultDTO {
  hasResidualActiveContract?: boolean;
  state: "CONFIRMED" | "PENDING_CONFIRMATION";
  planCode: PlanCode;
  subscriptionStatus: SubscriptionStatus;
  currentPeriodEnd: string | null;
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
  /** Mirrors the backend's 5G financial-coverage verdict (BillingInvoice + PaymentAttempt evidence). status=ACTIVE alone is never proof of payment. */
  financiallyCovered: boolean;
  /**
   * Whether THIS remote contract can be cancelled right now - independent of financiallyCovered
   * (5G.9 section B: a subscription authorized but not yet financially proven, or paused, is
   * still a real remote contract the user must be able to cancel). Never derive cancelability
   * from BillingMeDTO.subscriptionSource/subscriptionStatus - those reflect effective entitlement
   * only, and are null/absent whenever this contract isn't currently granting paid access.
   */
  canCancel: boolean;
  cancellationState: SubscriptionCancellationState;
  currentPeriodEnd: string | null;
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
