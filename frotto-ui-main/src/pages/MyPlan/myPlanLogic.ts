import { BillingCheckoutStatus, BillingMeDTO, BillingPaymentStateDTO, PLAN_LABELS, PlanChangeType, PlanDTO, PlanUpgradeStatusDTO, SubscriptionCancellationState, SubscriptionSource, SubscriptionStatus } from "../../constants/BillingModels";

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

/** "Seu plano mudará para X em DD/MM/AAAA." - null when there is nothing pending. */
export const pendingPlanChangeMessage = (billing: BillingMeDTO): string | null => {
  if (!billing.pendingPlanCode) return null;
  const target = friendlyPlan(billing.pendingPlanCode);
  const date = billing.planChangeEffectiveAt ? formatDate(billing.planChangeEffectiveAt) : null;
  return date && date !== "—"
    ? `Seu plano mudará para ${target} em ${date}.`
    : `Seu plano mudará para ${target} no fim do período atual.`;
};

/**
 * 5G.12.1: only a prorated upgrade whose payment is awaited or being applied is a real concurrent
 * financial operation - that, not a scheduled downgrade, is what blocks other plan changes.
 */
export const isUpgradeInProgress = (status: PlanUpgradeStatusDTO | null): boolean =>
  status?.status === "AWAITING_PAYMENT" || status?.status === "APPLYING";

export type UpgradeNotice = { title: string; detail: string; tone: "info" | "success" | "warning" };

const RECENT_SUCCESS_MS = 24 * 60 * 60 * 1000;
const RECENT_REVIEW_MS = 30 * 24 * 60 * 60 * 1000;

const isRecent = (value: string | null, windowMs: number, now: number): boolean => {
  if (!value) return false;
  const time = new Date(value).getTime();
  return !Number.isNaN(time) && now - time <= windowMs;
};

/**
 * 5G.12.1: user-facing state of the latest prorated upgrade. The target plan is NEVER described as
 * active before status=APPLIED; every amount shown comes from the backend DTO.
 */
export const upgradeNotice = (status: PlanUpgradeStatusDTO | null, now: number = Date.now()): UpgradeNotice | null => {
  if (!status || !status.targetPlan) return null;
  const target = friendlyPlan(status.targetPlan);
  const charge = status.chargeAmount != null ? money(status.chargeAmount) : null;
  const renewal = status.targetPrice != null ? `${money(status.targetPrice)}/mês` : null;
  switch (status.status) {
    case "AWAITING_PAYMENT":
      if (status.paymentPending) {
        return { title: "Estamos aguardando a confirmação do pagamento.", detail: `O plano ${target} será liberado assim que o Mercado Pago confirmar o pagamento. Seu plano atual continua ativo.`, tone: "info" };
      }
      if (status.paymentRejected) {
        return { title: "O pagamento do upgrade foi recusado.", detail: `Seu plano atual continua ativo e nada foi alterado.${status.checkoutUrl ? " Você pode tentar pagar novamente." : ""}`, tone: "warning" };
      }
      return { title: `Upgrade para ${target} aguardando pagamento.`, detail: `${charge ? `Valor a pagar agora: ${charge}. ` : ""}O plano ${target} só será liberado após a confirmação do pagamento.`, tone: "info" };
    case "APPLYING":
      return { title: "Pagamento confirmado.", detail: `Estamos atualizando sua assinatura para o plano ${target}. Isso pode levar alguns instantes.`, tone: "info" };
    case "APPLIED":
      if (!isRecent(status.appliedAt, RECENT_SUCCESS_MS, now)) return null;
      return { title: "Upgrade realizado com sucesso", detail: [`Seu plano agora é ${target}.`, charge ? `Valor pago agora: ${charge}.` : null, renewal ? `Próxima renovação: ${renewal}.` : null].filter(Boolean).join(" "), tone: "success" };
    case "REQUIRES_REVIEW":
      if (!isRecent(status.updatedAt, RECENT_REVIEW_MS, now)) return null;
      return { title: "Não foi possível concluir o upgrade automaticamente.", detail: `Seu plano atual continua ativo. Nossa equipe vai verificar o pagamento do upgrade para ${target}.`, tone: "warning" };
    default:
      return null;
  }
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
  BILLING_PLAN_CHANGE_ALREADY_PENDING: "Já existe um downgrade agendado. Desfaça-o antes de agendar outro.",
  BILLING_PLAN_CHANGE_PROVIDER_REJECTED: "O Mercado Pago não confirmou a alteração do valor da assinatura. Nenhuma mudança de plano foi aplicada.",
  BILLING_PLAN_CHANGE_NOOP: "Você já está no plano selecionado.",
  BILLING_PLAN_UPGRADE_IN_PROGRESS: "Já existe um upgrade aguardando a confirmação do pagamento. Conclua ou aguarde a confirmação antes de fazer outra alteração.",
  BILLING_PLAN_CHANGE_PERIOD_UNCONFIRMED: "Não foi possível confirmar o ciclo atual da sua assinatura para calcular o valor proporcional. Nenhuma alteração foi feita. Tente novamente mais tarde.",
  BILLING_PLAN_UPGRADE_CHECKOUT_UNAVAILABLE: "Não foi possível gerar o pagamento do upgrade no Mercado Pago. Nada foi cobrado e seu plano atual continua o mesmo.",
  BILLING_DOWNGRADE_UNDO_REJECTED: "O Mercado Pago não confirmou a restauração do valor da assinatura. O downgrade continua agendado.",
  BILLING_DOWNGRADE_UNDO_CONFLICT: "Não foi possível concluir o desfazimento porque o estado da assinatura mudou durante a operação. Atualize os dados e tente novamente.",
  BILLING_RENEWAL_LOCKED: "A próxima renovação já está fechada. Aguarde a confirmação da cobrança para mudar o plano.",
  nopendingdowngrade: "Não há downgrade agendado para desfazer.",
};

/** A real 401 is never disguised as a generic failure (the global interceptor also ends the session). */
const SESSION_EXPIRED_MESSAGE = "Sua sessão expirou. Faça login novamente.";

/** Friendly message for every backend change-plan error key - see getApiErrorMessage's overrides parameter. */
export const planChangeErrorMessage = (error: unknown): string =>
  getApiErrorMessageForOverrides(error, "Não foi possível concluir a mudança de plano. Tente novamente.", PLAN_CHANGE_ERROR_MESSAGES);

export const isUnauthorizedError = (error: unknown): boolean =>
  (error as { response?: { status?: number } })?.response?.status === 401;

function getApiErrorMessageForOverrides(error: unknown, fallback: string, overrides: Record<string, string>): string {
  if (isUnauthorizedError(error)) return SESSION_EXPIRED_MESSAGE;
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
  // FAILED = the preapproval was never created on Mercado Pago (non-ambiguous error), so nothing was charged.
  if (checkout === "FAILED") return { title: "Não foi possível concluir o pagamento.", detail: "Nenhuma cobrança foi realizada. Você pode tentar novamente quando quiser.", tone: "warning" };
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

/**
 * What the Meu Plano page tells the user about their billing right now. Only states the backend
 * already sustains: BillingMeDTO is the entitlement verdict, payment-state the raw contract and
 * the latest checkout attempt.
 */
export type BillingUiState = "FREE" | "ACTIVE" | "CANCEL_SCHEDULED" | "PLAN_ENDED" | "PAYMENT_PENDING" | "PAYMENT_FAILED";

export type BillingPresentation = {
  state: BillingUiState;
  notice: PaymentNotice | null;
  /** "Continuar pagamento" target - only ever set in PAYMENT_PENDING, from the backend's canResume. */
  resumeUrl: string | null;
  /** "Atualizar status" next to the notice. */
  canRefresh: boolean;
  /** Secondary CTA that only leads to the existing plan list (checkout stays the only payment path). */
  planAction: "VIEW_PLANS" | "RETRY_PAYMENT" | null;
};

const TERMINAL_CONTRACT_STATUSES: SubscriptionStatus[] = ["CANCELED", "EXPIRED"];

/**
 * A remote contract is over once it no longer grants anything: either terminal and without
 * residual coverage, or a confirmed cancellation whose paid period the backend no longer covers
 * (the row keeps status=ACTIVE after the period ends - nothing flips it - so status alone is not
 * enough; see SubscriptionService#isProviderSubscriptionEntitled). For the second case the
 * effective entitlement (BillingMeDTO) must also agree the contract is not what grants the plan.
 */
export const isContractEnded = (billing: BillingMeDTO, paymentState: BillingPaymentStateDTO | null): boolean => {
  const contract = paymentState?.paymentProviderSubscription;
  if (!contract || contract.financiallyCovered) return false;
  if (TERMINAL_CONTRACT_STATUSES.includes(contract.status)) return true;
  return contract.cancellationState === "CONFIRMED" && billing.subscriptionSource !== "PAYMENT_PROVIDER";
};

const pastDate = (value: string | null | undefined, now: number): string | null => {
  if (!value) return null;
  const time = new Date(value).getTime();
  return Number.isNaN(time) || time > now ? null : formatDate(value);
};

const planEndedNotice = (billing: BillingMeDTO, endedAt: string | null): PaymentNotice => billing.planCode === "FREE"
  ? { title: "Você está no plano gratuito", detail: `Seu plano pago foi encerrado${endedAt ? ` em ${endedAt}` : ""}. Você pode escolher um novo plano quando quiser.`, tone: "info" }
  : { title: "Assinatura anterior encerrada", detail: `Sua assinatura paga foi encerrada${endedAt ? ` em ${endedAt}` : ""}. Seu plano atual é ${friendlyPlan(billing.planCode)}.`, tone: "info" };

/**
 * Single decision point for the billing banner. "Continuar pagamento" needs BOTH a checkout the
 * backend reports as resumable (canResume: open status + init_point) AND no live contract: while a
 * contract is live the backend refuses any new checkout (RecurringSubscriptionGuardService), so an
 * open checkout next to it can only be that contract's own attempt - never something to pay again.
 * An ended contract, a cancelled/expired/authorized historical checkout or the FREE plan alone
 * never produce a payment CTA.
 */
export const resolveBillingPresentation = (billing: BillingMeDTO, paymentState: BillingPaymentStateDTO | null, now: number = Date.now()): BillingPresentation => {
  const contract = paymentState?.paymentProviderSubscription ?? null;
  const checkout = paymentState?.latestCheckout ?? null;
  const ended = isContractEnded(billing, paymentState);
  const none: BillingPresentation = { state: "FREE", notice: null, resumeUrl: null, canRefresh: false, planAction: null };

  if (contract && !ended) {
    const terminal = TERMINAL_CONTRACT_STATUSES.includes(contract.status);
    const scheduled = terminal || contract.cancellationState !== "NONE";
    // A terminal contract only reaches here with residual paid coverage: "encerrada, não vigente" would be false.
    const rawNotice = terminal ? null : paymentNotice({ paymentProviderSubscription: contract, latestCheckout: null });
    return { ...none, state: scheduled ? "CANCEL_SCHEDULED" : "ACTIVE", notice: isNoticeRedundantWithGrantedPlan(rawNotice, billing) ? null : rawNotice, canRefresh: checkoutNeedsRefresh(paymentState) };
  }

  const checkoutOnly: BillingPaymentStateDTO = { paymentProviderSubscription: null, latestCheckout: checkout };
  if (checkout && OPEN_CHECKOUTS.includes(checkout.status)) {
    return { ...none, state: "PAYMENT_PENDING", notice: paymentNotice(checkoutOnly), resumeUrl: resumableCheckoutUrl(paymentState), canRefresh: true };
  }
  // AUTHORIZED next to an ended contract is that contract's own (historical) checkout.
  if (checkout?.status === "AUTHORIZED" && !contract) {
    return { ...none, state: "PAYMENT_PENDING", notice: paymentNotice(checkoutOnly), canRefresh: true };
  }
  if (checkout?.status === "FAILED") {
    return { ...none, state: "PAYMENT_FAILED", notice: paymentNotice(checkoutOnly), planAction: "RETRY_PAYMENT" };
  }
  if (ended) {
    return { ...none, state: "PLAN_ENDED", notice: planEndedNotice(billing, pastDate(contract?.currentPeriodEnd, now)), planAction: billing.planCode === "FREE" ? "VIEW_PLANS" : null };
  }
  return none;
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
