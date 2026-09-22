import { BillingCheckoutStatus, BillingMeDTO, BillingPaymentStateDTO, PLAN_LABELS, PlanChangeType, PlanDTO, SubscriptionCancellationState, SubscriptionSource, SubscriptionStatus } from "../../constants/BillingModels";

/**
 * 5G.9 section B: cancelability of the remote PAYMENT_PROVIDER contract is a DIFFERENT question
 * from BillingMeDTO's effective entitlement (planCode/subscriptionSource/subscriptionStatus),
 * which reports FREE/null whenever the contract isn't currently granting paid access (e.g.
 * authorized but not yet financially proven, or PAST_DUE past its grace period). Always read
 * canCancel from /api/billing/payment-state's paymentProviderSubscription, which is populated
 * from the raw contract row regardless of financial coverage - never re-derive this from
 * BillingMeDTO, or the original bug (no cancel action for an uncovered but real remote contract)
 * comes back.
 */
export const isSubscriptionCancelable = (paymentState: BillingPaymentStateDTO | null): boolean =>
  Boolean(paymentState?.paymentProviderSubscription?.canCancel);

/** Prefers the payment-state's own cancellation state (always present when a contract exists) over BillingMeDTO's (only present when the contract is also the effective entitlement). */
export const remoteCancellationState = (billing: BillingMeDTO, paymentState: BillingPaymentStateDTO | null): SubscriptionCancellationState =>
  paymentState?.paymentProviderSubscription?.cancellationState ?? billing.cancellationState;

export const remoteCurrentPeriodEnd = (billing: BillingMeDTO, paymentState: BillingPaymentStateDTO | null): string | null =>
  paymentState?.paymentProviderSubscription?.currentPeriodEnd ?? billing.currentPeriodEnd;

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

/**
 * 5G.12: determined structurally from minVehicles ordering, never from price (a progressive plan's
 * base price is not a reliable ranking - see PricingService#calculatePriceForPlan on the backend,
 * which is the actual source of truth for what a plan costs). Mirrors SubscriptionPlanChangeService
 * #directionOf on the backend.
 */
export const planDirection = (currentPlan: PlanDTO, targetPlan: PlanDTO): PlanChangeType =>
  (targetPlan.minVehicles || 0) > (currentPlan.minVehicles || 0) ? "UPGRADE" : "DOWNGRADE";

/** True once BillingMeDTO reports a scheduled downgrade (Subscription.pendingPlan) - see billingResource#getMyBilling, which effectuates any already-due change before responding. */
export const hasPendingPlanChange = (billing: BillingMeDTO): boolean => Boolean(billing.pendingPlanCode);

/** "Mudança para X agendada para DD/MM/AAAA" - null when there is nothing pending. */
export const pendingPlanChangeMessage = (billing: BillingMeDTO): string | null => {
  if (!billing.pendingPlanCode || !billing.pendingPlanName) return null;
  const date = billing.planChangeEffectiveAt ? formatDate(billing.planChangeEffectiveAt) : null;
  return date && date !== "—"
    ? `Mudança para ${billing.pendingPlanName} agendada para ${date}.`
    : `Mudança para ${billing.pendingPlanName} agendada para o fim do período atual.`;
};

/**
 * 5G.12 section 19: an already-scheduled cancellation (CONFIRMED or PENDING_CONFIRMATION) blocks
 * every functional upgrade/downgrade action - reactivating a cancelled preapproval is out of scope
 * for this stage, so the UI must not offer a path that implies it is possible.
 */
export const isPlanChangeBlockedByCancellation = (cancellationState: SubscriptionCancellationState): boolean =>
  cancellationState === "CONFIRMED" || cancellationState === "PENDING_CONFIRMATION";

const PLAN_CHANGE_ERROR_MESSAGES: Record<string, string> = {
  BILLING_PLAN_CHANGE_AMBIGUOUS_SUBSCRIPTION: "Existem múltiplas assinaturas ativas vinculadas à sua conta. Resolva ou cancele as duplicidades antes de mudar de plano.",
  BILLING_PLAN_CHANGE_ALREADY_PENDING: "Já existe uma mudança de plano agendada para esta assinatura. Aguarde a efetivação antes de solicitar outra.",
  BILLING_PLAN_CHANGE_PROVIDER_REJECTED: "O Mercado Pago não confirmou a alteração do valor da assinatura. Nenhuma mudança de plano foi aplicada.",
  BILLING_PLAN_CHANGE_NOOP: "Você já está no plano selecionado.",
};

/** Friendly message for every backend change-plan error key - see getApiErrorMessage's overrides parameter. */
export const planChangeErrorMessage = (error: unknown): string =>
  getApiErrorMessageForOverrides(error, "Não foi possível concluir a mudança de plano. Tente novamente.", PLAN_CHANGE_ERROR_MESSAGES);

function getApiErrorMessageForOverrides(error: unknown, fallback: string, overrides: Record<string, string>): string {
  const response = (error as { response?: { status?: number; data?: { message?: string; errorKey?: string } } })?.response;
  const code = response?.data?.message || response?.data?.errorKey || "";
  const key = Object.keys(overrides).find((candidate) => code.includes(candidate));
  return key ? overrides[key] : fallback;
}

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
  if (subscription?.status === "ACTIVE" && subscription.financiallyCovered) return { title: "Assinatura ativa", detail: `Plano ${friendlyPlan(subscription.planCode)} confirmado.`, tone: "info" };
  // Preapproval/checkout authorized and Subscription.status=ACTIVE, but the 5G financial evidence
  // (BillingInvoice + PaymentAttempt) is not in yet - never say "confirmado" from status alone.
  // Since 5G.10, this ACTIVE contract may already be granting the plan (see
  // isNoticeRedundantWithGrantedPlan, which suppresses this exact banner once BillingMeDTO
  // confirms it) - this notice on its own still exists for the case where it is NOT (yet) the
  // effective plan (e.g. an ADMIN_GRANT currently wins precedence).
  if (subscription?.status === "ACTIVE") return { title: "Pagamento em processamento", detail: `Recebemos a autorização do Mercado Pago para o plano ${friendlyPlan(subscription.planCode)} e estamos confirmando o pagamento. Isso pode levar alguns minutos.`, tone: "info" };
  const checkout = state?.latestCheckout?.status;
  if (checkout === "CREATED") return { title: "Pagamento em preparação", detail: "Seu pagamento está sendo preparado. Aguarde antes de iniciar uma nova tentativa.", tone: "info" };
  if (checkout === "PROVIDER_PENDING") return { title: "Aguardando confirmação do Mercado Pago", detail: "Seu pagamento foi iniciado e estamos aguardando a confirmação.", tone: "info" };
  if (checkout === "PROVIDER_UNKNOWN") return { title: "Estamos confirmando seu pagamento", detail: "Recebemos uma resposta inconclusiva do serviço de pagamento. Estamos verificando o status antes de permitir uma nova tentativa.", tone: "warning" };
  if (checkout === "FAILED") return { title: "Não foi possível iniciar o pagamento.", detail: "Você pode tentar novamente quando quiser.", tone: "danger" };
  if (checkout === "AUTHORIZED") return { title: "Confirmação em andamento", detail: "O pagamento foi autorizado e estamos confirmando sua assinatura.", tone: "info" };
  return null;
};

const NOTICES_REDUNDANT_WITH_A_GRANTED_PLAN = new Set(["Assinatura ativa", "Pagamento em processamento"]);

/**
 * 5G.11 item 5: once BillingMeDTO itself confirms this PAYMENT_PROVIDER contract is ACTIVE and
 * already the effective plan (5G.10 - it does not need a BillingInvoice to have been granted),
 * the "aguardando confirmação"/"pagamento em processamento" banners built from payment-state alone
 * become redundant with (or flatly contradict) the main plan card. This must never suppress the
 * SAME banner for a genuinely pending checkout/preapproval - only for the exact case where
 * BillingMeDTO's own effective source/status already say PAYMENT_PROVIDER/ACTIVE.
 */
export const isNoticeRedundantWithGrantedPlan = (notice: PaymentNotice | null, billing: BillingMeDTO): boolean =>
  Boolean(notice) && billing.subscriptionSource === "PAYMENT_PROVIDER" && billing.subscriptionStatus === "ACTIVE"
  && NOTICES_REDUNDANT_WITH_A_GRANTED_PLAN.has((notice as PaymentNotice).title);

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

/** 5G.9 section A: the backend refuses a second remote recurrence with this stable 409 error key. */
export const isRecurringSubscriptionExistsError = (error: unknown): boolean => {
  const response = (error as { response?: { status?: number; data?: { message?: string; errorKey?: string } } })?.response;
  const key = response?.data?.message || response?.data?.errorKey || "";
  return response?.status === 409 && key.includes("BILLING_RECURRING_SUBSCRIPTION_EXISTS");
};

/**
 * Message for isRecurringSubscriptionExistsError, adapted to what the backend's own payment-state
 * already told us (never anything the error response itself carries - it never has provider
 * identifiers). No upgrade/replace flow exists yet, so the only actionable next step is always
 * "cancel or wait for the existing contract to resolve", never a plan-swap suggestion.
 */
export const recurringSubscriptionExistsMessage = (paymentState: BillingPaymentStateDTO | null): string => {
  const base = "Você já possui uma assinatura recorrente vinculada à sua conta.";
  const subscription = paymentState?.paymentProviderSubscription;
  if (subscription?.cancellationState === "PENDING_CONFIRMATION") {
    return `${base} Estamos confirmando o cancelamento solicitado com o Mercado Pago; aguarde a confirmação antes de contratar outro plano.`;
  }
  if (subscription?.canCancel) {
    return `${base} Cancele a assinatura atual antes de contratar outro plano.`;
  }
  return `${base} Conclua ou cancele a assinatura atual antes de contratar outro plano.`;
};
