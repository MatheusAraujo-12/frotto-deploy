import { BillingMeDTO, PLAN_LABELS, PlanDTO, SubscriptionSource, SubscriptionStatus } from "../../constants/BillingModels";

export const money = (value: number): string =>
  new Intl.NumberFormat("pt-BR", { style: "currency", currency: "BRL" }).format(value);

export const friendlyPlan = (code: keyof typeof PLAN_LABELS): string => PLAN_LABELS[code];

export const sourceLabel = (source: SubscriptionSource | null): string => ({
  PAYMENT_PROVIDER: "Assinatura",
  ADMIN_GRANT: "Cortesia",
  GRANDFATHERED: "Condição de cliente anterior",
} as Record<SubscriptionSource, string>)[source as SubscriptionSource] || "Plano gratuito";

export const statusLabel = (status: SubscriptionStatus | null): string => ({
  ACTIVE: "Ativa", PAST_DUE: "Com pendência", CANCELED: "Cancelada", EXPIRED: "Expirada",
} as Record<SubscriptionStatus, string>)[status as SubscriptionStatus] || "Ativo";

export const formatDate = (value: string): string => {
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? "—" : date.toLocaleDateString("pt-BR", { timeZone: "UTC" });
};

export const sourceDetail = (billing: BillingMeDTO): string => {
  if (billing.subscriptionSource === "ADMIN_GRANT") {
    return billing.grantExpiresAt
      ? `Cortesia válida até ${formatDate(billing.grantExpiresAt)}`
      : "Cortesia sem data de expiração";
  }
  if (billing.subscriptionSource === "GRANDFATHERED") return "Condição de cliente anterior";
  if (billing.subscriptionSource === "PAYMENT_PROVIDER") return "Assinatura mensal";
  return "Plano gratuito";
};

export const fleetUsage = (billing: BillingMeDTO): string =>
  billing.vehicleLimit == null
    ? `${billing.activeVehicleCount} veículos · Sem limite fixo`
    : `${billing.activeVehicleCount} de ${billing.vehicleLimit} veículos`;

export const usageState = (billing: BillingMeDTO): "within" | "near" | "reached" => {
  if (billing.vehicleLimit == null) return "within";
  if (!billing.canAddVehicle || billing.activeVehicleCount >= billing.vehicleLimit) return "reached";
  return billing.activeVehicleCount / billing.vehicleLimit >= 0.8 ? "near" : "within";
};

export const isPlanCompatible = (plan: PlanDTO, vehicles: number): boolean =>
  plan.maxVehicles == null || vehicles <= plan.maxVehicles;

export const vehicleRange = (plan: PlanDTO): string => {
  if (plan.maxVehicles == null) return `${plan.minVehicles || 0}+ veículos`;
  if ((plan.minVehicles || 0) === 0) return `Até ${plan.maxVehicles} veículos`;
  return `${plan.minVehicles} a ${plan.maxVehicles} veículos`;
};
