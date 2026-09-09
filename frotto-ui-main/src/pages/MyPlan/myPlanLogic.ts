import { BillingCheckoutStatus, BillingMeDTO, BillingPaymentStateDTO, PLAN_LABELS, PlanDTO, SubscriptionSource, SubscriptionStatus } from "../../constants/BillingModels";

export const money = (value: number): string =>
  new Intl.NumberFormat("pt-BR", { style: "currency", currency: "BRL" }).format(value);

export const friendlyPlan = (code: keyof typeof PLAN_LABELS): string => PLAN_LABELS[code];

export const sourceLabel = (source: SubscriptionSource | null): string => ({
  PAYMENT_PROVIDER: "Assinatura",
  ADMIN_GRANT: "Cortesia",
  GRANDFATHERED: "Condição de cliente anterior",
} as Record<SubscriptionSource, string>)[source as SubscriptionSource] || "Plano gratuito";

export const statusLabel = (status: SubscriptionStatus | null): string => ({
  ACTIVE: "Ativa", PAST_DUE: "Com pendência", PAUSED: "Pausada", CANCELED: "Cancelada", EXPIRED: "Expirada",
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

export type PaymentNotice = { title: string; detail: string; tone: "info" | "warning" | "danger" };

const OPEN_CHECKOUTS: BillingCheckoutStatus[] = ["CREATED", "PROVIDER_PENDING", "PROVIDER_UNKNOWN"];

export const checkoutBlocksPurchase = (state: BillingPaymentStateDTO | null): boolean =>
  Boolean(state?.latestCheckout && OPEN_CHECKOUTS.includes(state.latestCheckout.status));

export const checkoutNeedsRefresh = (state: BillingPaymentStateDTO | null): boolean => {
  const status = state?.latestCheckout?.status;
  return Boolean(status && (OPEN_CHECKOUTS.includes(status) || (status === "AUTHORIZED" && !state?.paymentProviderSubscription)));
};

export const paymentNotice = (state: BillingPaymentStateDTO | null): PaymentNotice | null => {
  const subscription = state?.paymentProviderSubscription;
  if (subscription?.status === "PAST_DUE") return { title: "Pagamento pendente", detail: "Identificamos uma pendência no pagamento da sua assinatura. Seu acesso continua ativo enquanto a situação é regularizada.", tone: "warning" };
  if (subscription?.status === "PAUSED") return { title: "Assinatura pausada", detail: "Sua assinatura paga está temporariamente pausada.", tone: "warning" };
  if (subscription?.status === "CANCELED") return { title: "Assinatura encerrada", detail: "Esta assinatura paga foi encerrada e não está vigente.", tone: "info" };
  if (subscription?.status === "ACTIVE") return { title: "Assinatura ativa", detail: `Plano ${friendlyPlan(subscription.planCode)} confirmado.`, tone: "info" };
  const checkout = state?.latestCheckout?.status;
  if (checkout === "CREATED") return { title: "Pagamento em preparação", detail: "Seu pagamento está sendo preparado. Aguarde antes de iniciar uma nova tentativa.", tone: "info" };
  if (checkout === "PROVIDER_PENDING") return { title: "Aguardando confirmação do Mercado Pago", detail: "Seu pagamento foi iniciado e estamos aguardando a confirmação.", tone: "info" };
  if (checkout === "PROVIDER_UNKNOWN") return { title: "Estamos confirmando seu pagamento", detail: "Recebemos uma resposta inconclusiva do serviço de pagamento. Estamos verificando o status antes de permitir uma nova tentativa.", tone: "warning" };
  if (checkout === "FAILED") return { title: "Não foi possível iniciar o pagamento.", detail: "Você pode tentar novamente quando quiser.", tone: "danger" };
  if (checkout === "AUTHORIZED") return { title: "Confirmação em andamento", detail: "O pagamento foi autorizado e estamos confirmando sua assinatura.", tone: "info" };
  return null;
};

/**
 * Trusts the backend's `canResume` flag rather than re-deriving eligibility here, so the
 * resumable-status rule (BillingCheckoutStatus#isResumable + init_point presence) lives in one
 * place only.
 */
export const resumableCheckoutUrl = (state: BillingPaymentStateDTO | null): string | null => {
  const checkout = state?.latestCheckout;
  return checkout?.canResume ? checkout.checkoutUrl : null;
};

export const isCheckoutInProgressError = (error: unknown): boolean => {
  const response = (error as { response?: { status?: number; data?: { message?: string; errorKey?: string } } })?.response;
  const key = response?.data?.message || response?.data?.errorKey || "";
  return response?.status === 409 && key.includes("BILLING_CHECKOUT_IN_PROGRESS");
};
