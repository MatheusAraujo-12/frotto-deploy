import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import MyPlanPage from "./MyPlanPage";
import billingService from "../../services/billingService";
import { removeToken, setToken } from "../../services/localStorage/localstorage";
import { navigateToCheckout } from "./checkoutNavigation";
import { BillingMeDTO, BillingPaymentStateDTO, PlanChangePreviewDTO, PlanChangeResultDTO, PlanDTO, PlanUpgradeStatusDTO } from "../../constants/BillingModels";

jest.mock("../../services/billingService");
jest.mock("./checkoutNavigation");
/**
 * 5G.12.1: markup the real IonModal would still be SHOWING during its close animation (isOpen just
 * flipped to false, onDidDismiss not fired yet). The old page cleared the modal's subject
 * synchronously, so that frame was empty - the "white modal" seen in staging.
 */
const mockClosingFrames: string[] = [];

jest.mock("@ionic/react", () => {
  const React = jest.requireActual("react");
  const { renderToStaticMarkup } = jest.requireActual("react-dom/server");
  const component = (tag: string) => ({ children, ...props }: any) =>
    React.createElement(tag, props, children);
  const modal = ({ children, isOpen, onDidDismiss }: any) => {
    const wasOpen = React.useRef(isOpen);
    const closing = wasOpen.current && !isOpen;
    if (closing) mockClosingFrames.push(renderToStaticMarkup(React.createElement(React.Fragment, null, children)));
    React.useEffect(() => {
      if (closing && onDidDismiss) onDidDismiss({});
      wasOpen.current = isOpen;
    });
    return isOpen ? React.createElement("section", { role: "dialog" }, children) : null;
  };

  return {
    IonApp: component("div"), IonBadge: component("span"), IonButton: component("button"),
    IonButtons: component("div"), IonCard: component("section"), IonCardContent: component("div"),
    IonContent: component("main"), IonHeader: component("header"), IonIcon: component("span"),
    IonInput: component("input"), IonMenuButton: component("button"), IonModal: modal,
    IonPage: component("div"), IonProgressBar: component("progress"), IonSkeletonText: component("span"),
    IonSpinner: () => React.createElement("span", { role: "progressbar" }),
    IonTitle: component("h1"), IonToolbar: component("div"),
  };
});

const mockedBillingService = billingService as jest.Mocked<typeof billingService>;
const mockedNavigate = navigateToCheckout as jest.MockedFunction<typeof navigateToCheckout>;

const billing: BillingMeDTO = {
  planCode: "BRONZE", planName: "Bronze", subscriptionStatus: "ACTIVE", billingCycle: "MONTHLY",
  subscriptionSource: "PAYMENT_PROVIDER", activeVehicleCount: 6, vehicleLimit: 10, canAddVehicle: true,
  needsUpgrade: false, requiredPlanCode: "BRONZE", requiredPlanName: "Bronze", currentMonthlyPrice: 59.9,
  currentPeriodStart: null, currentPeriodEnd: null, grantExpiresAt: null, cancelAtPeriodEnd: false, cancellationState: "NONE",
};

/**
 * 5G.12: checkout is now reserved for a user with NO effective PAYMENT_PROVIDER/ACTIVE contract
 * (section 2/11 - "FREE -> pago = checkout atual"). The pre-5G.12 checkout tests below all used
 * the PAYMENT_PROVIDER/ACTIVE `billing` fixture above, which after this change routes a compatible
 * plan card to "Fazer upgrade" instead of "Escolher plano" - so every genuine checkout scenario
 * uses this FREE fixture instead.
 */
const freeBilling: BillingMeDTO = { ...billing, planCode: "FREE", planName: "Gratuito", subscriptionStatus: null, subscriptionSource: null, billingCycle: null };

/**
 * 5G.9-B: the cancel action is now driven by payment-state.paymentProviderSubscription.canCancel,
 * not by BillingMeDTO - so tests must keep this in sync with the `billing` fixture's PAYMENT_
 * PROVIDER/ACTIVE shape by default. Tests exercising a scenario where no real remote contract
 * exists at all still override this back to `paymentProviderSubscription: null`.
 */
const cancellablePaymentState: BillingPaymentStateDTO = {
  paymentProviderSubscription: {
    status: "ACTIVE", planCode: "BRONZE", billingCycle: "MONTHLY", financiallyCovered: true,
    canCancel: true, cancellationState: "NONE", currentPeriodEnd: null,
  },
  latestCheckout: null,
};

const plans: PlanDTO[] = [
  { code: "FREE", name: "Free", minVehicles: 0, maxVehicles: 2, monthlyBasePrice: 0, billingModel: "FLAT", tiers: [] },
  { code: "BRONZE", name: "Bronze", minVehicles: 3, maxVehicles: 10, monthlyBasePrice: 59.9, billingModel: "FLAT", tiers: [] },
  { code: "SILVER", name: "Silver", minVehicles: 11, maxVehicles: 20, monthlyBasePrice: 99.9, billingModel: "FLAT", tiers: [] },
];

const deferred = <T,>() => {
  let resolve!: (value: T) => void;
  let reject!: (reason?: unknown) => void;
  const promise = new Promise<T>((res, rej) => { resolve = res; reject = rej; });
  return { promise, resolve, reject };
};

const renderLoadedPage = async () => {
  render(<MyPlanPage />);
  await screen.findByRole("heading", { name: /Planos dispon/ });
};

const chooseSilver = () => {
  const silverCard = screen.getByText("Prata").closest("section")!;
  fireEvent.click(Array.from(silverCard.querySelectorAll("button")).find((button) => button.textContent === "Escolher plano")!);
};

/** 5G.12: the plan-card button for a given plan label, by its visible text - null when no button with that exact label exists on the card. */
const planCardButton = (planLabel: string, buttonLabel: string): HTMLButtonElement | null => {
  const card = screen.getByText(planLabel).closest("section")!;
  return (Array.from(card.querySelectorAll("button")).find((button) => button.textContent === buttonLabel) as HTMLButtonElement) || null;
};

const noUpgrade: PlanUpgradeStatusDTO = {
  status: "NONE", fromPlan: null, targetPlan: null, chargeAmount: null, targetPrice: null, checkoutUrl: null,
  paymentPending: false, paymentRejected: false, appliedAt: null, updatedAt: null,
};

describe("MyPlanPage - checkout modal", () => {
  beforeEach(() => {
    jest.clearAllMocks();
    mockedBillingService.cancelSubscription.mockReset();
    localStorage.clear();
    setToken("Bearer user-token");
    mockedBillingService.getMyBilling.mockResolvedValue(billing);
    mockedBillingService.getBillingPaymentState.mockResolvedValue(cancellablePaymentState);
    mockedBillingService.getPlans.mockResolvedValue(plans);
    mockedBillingService.getPricePreview.mockImplementation(() => new Promise(() => {}));
    mockedBillingService.getPlanUpgradeStatus.mockResolvedValue(noUpgrade);
  });

  afterEach(() => localStorage.clear());

  /**
   * 5G.12 section 22: with an existing PAYMENT_PROVIDER/ACTIVE contract, a higher plan routes to
   * "Fazer upgrade" (never checkout - no second preapproval), and Gratuito always offers "Mudar
   * para Gratuito" (reusing the existing cancellation flow, never gated by fleet compatibility -
   * section 11 has no such gate, unlike a paid-to-paid downgrade).
   */
  it("roteia plano superior para upgrade e Gratuito para 'Mudar para Gratuito' quando já existe assinatura paga", async () => {
    await renderLoadedPage();

    expect(planCardButton("Gratuito", "Mudar para Gratuito")).toBeEnabled();
    expect(planCardButton("Prata", "Fazer upgrade")).toBeEnabled();
    expect(screen.queryByText("Escolher plano")).not.toBeInTheDocument();
  });

  it("abre o modal de checkout para um usuário sem plano pago vigente (FREE -> pago)", async () => {
    mockedBillingService.getMyBilling.mockResolvedValue(freeBilling);
    await renderLoadedPage();

    chooseSilver();
    expect(screen.getByRole("dialog")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Continuar para pagamento" })).toBeEnabled();
  });

  it("inicia um checkout, mostra loading, bloqueia clique duplo e navega uma vez", async () => {
    const checkout = deferred<any>();
    mockedBillingService.createCheckout.mockReturnValue(checkout.promise);
    mockedBillingService.getMyBilling.mockResolvedValue(freeBilling);
    await renderLoadedPage();
    chooseSilver();

    const continueButton = screen.getByRole("button", { name: "Continuar para pagamento" });
    fireEvent.click(continueButton);
    fireEvent.click(continueButton);

    expect(mockedBillingService.createCheckout).toHaveBeenCalledTimes(1);
    expect(mockedBillingService.createCheckout).toHaveBeenCalledWith("SILVER");
    expect(await screen.findByText("Processando...")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /Processando/ })).toBeDisabled();

    checkout.resolve({ checkoutId: 7, planCode: "SILVER", quotedPrice: 99.9, billingCycle: "MONTHLY", status: "PROVIDER_PENDING", checkoutUrl: "https://mp.test/checkout" });
    await waitFor(() => expect(mockedNavigate).toHaveBeenCalledWith("https://mp.test/checkout"));
    expect(mockedNavigate).toHaveBeenCalledTimes(1);
  });

  it("mantÃ©m o modal aberto, exibe erro amigÃ¡vel e permite tentar novamente", async () => {
    mockedBillingService.createCheckout
      .mockRejectedValueOnce(new Error("offline"))
      .mockResolvedValueOnce({ checkoutId: 8, planCode: "SILVER", quotedPrice: 99.9, billingCycle: "MONTHLY", status: "PROVIDER_PENDING", checkoutUrl: "https://mp.test/retry" });
    mockedBillingService.getMyBilling.mockResolvedValue(freeBilling);
    await renderLoadedPage();
    chooseSilver();

    fireEvent.click(screen.getByRole("button", { name: "Continuar para pagamento" }));
    expect(await screen.findByRole("alert")).toHaveTextContent(/conectar ao servidor/);
    expect(screen.getByRole("dialog")).toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: "Continuar para pagamento" }));
    await waitFor(() => expect(mockedNavigate).toHaveBeenCalledWith("https://mp.test/retry"));
    expect(mockedBillingService.createCheckout).toHaveBeenCalledTimes(2);
  });

  it("não ativa assinatura ao carregar ou retornar à página", async () => {
    const first = render(<MyPlanPage />);
    expect((await screen.findAllByText("Bronze")).length).toBeGreaterThan(0);
    expect(mockedBillingService.createCheckout).not.toHaveBeenCalled();
    expect(mockedNavigate).not.toHaveBeenCalled();
    first.unmount();

    render(<MyPlanPage />);
    expect((await screen.findAllByText("Bronze")).length).toBeGreaterThan(0);
    expect(mockedBillingService.getMyBilling).toHaveBeenCalledTimes(2);
    expect(mockedBillingService.createCheckout).not.toHaveBeenCalled();
  });

  it.each(["CREATED", "PROVIDER_PENDING", "PROVIDER_UNKNOWN"] as const)("bloqueia nova contratação durante %s", async (status) => {
    mockedBillingService.getMyBilling.mockResolvedValue(freeBilling);
    mockedBillingService.getBillingPaymentState.mockResolvedValue({ paymentProviderSubscription: null, latestCheckout: { status, planCode: "SILVER", createdAt: "2026-08-28T12:00:00Z", canResume: false, checkoutUrl: null } });
    await renderLoadedPage();
    const silverCard = screen.getByText("Prata").closest("section")!;
    expect(Array.from(silverCard.querySelectorAll("button")).find((button) => button.textContent === "Escolher plano")).toBeDisabled();
    expect(mockedBillingService.createCheckout).not.toHaveBeenCalled();
  });

  it("trata 409 como pagamento em andamento e atualiza payment-state", async () => {
    mockedBillingService.getBillingPaymentState.mockResolvedValueOnce({ paymentProviderSubscription: null, latestCheckout: null }).mockResolvedValueOnce({ paymentProviderSubscription: null, latestCheckout: { status: "PROVIDER_PENDING", planCode: "SILVER", createdAt: "2026-08-28T12:00:00Z", canResume: false, checkoutUrl: null } });
    mockedBillingService.createCheckout.mockRejectedValue({ response: { status: 409, data: { message: "error.BILLING_CHECKOUT_IN_PROGRESS" } } });
    mockedBillingService.getMyBilling.mockResolvedValue(freeBilling);
    await renderLoadedPage(); chooseSilver();
    fireEvent.click(screen.getByRole("button", { name: "Continuar para pagamento" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("Já existe um pagamento em andamento");
    expect(mockedBillingService.getBillingPaymentState).toHaveBeenCalledTimes(2);
    expect(mockedBillingService.createCheckout).toHaveBeenCalledTimes(1);
    expect(mockedNavigate).not.toHaveBeenCalled();
  });

  /**
   * 5G.9 section A/2: the backend refuses a second remote recurrence with 409
   * error.BILLING_RECURRING_SUBSCRIPTION_EXISTS. Before this fix the frontend had no special case
   * for this key, so getApiErrorMessage's generic 409 fallback ("Conflito ao salvar...") leaked
   * through instead of an actionable, safe message.
   */
  it("trata 409 de recorrência existente com mensagem segura e acionável, sem expor IDs do provider", async () => {
    mockedBillingService.createCheckout.mockRejectedValue({ response: { status: 409, data: { message: "error.BILLING_RECURRING_SUBSCRIPTION_EXISTS" } } });
    mockedBillingService.getMyBilling.mockResolvedValue(freeBilling);
    await renderLoadedPage(); chooseSilver();
    fireEvent.click(screen.getByRole("button", { name: "Continuar para pagamento" }));
    const alert = await screen.findByRole("alert");
    expect(alert).toHaveTextContent("Você já possui uma assinatura recorrente vinculada à sua conta.");
    expect(alert).toHaveTextContent("Cancele a assinatura atual antes de contratar outro plano.");
    expect(alert).not.toHaveTextContent("Conflito ao salvar");
    expect(mockedBillingService.getBillingPaymentState).toHaveBeenCalledTimes(2);
    expect(mockedBillingService.createCheckout).toHaveBeenCalledTimes(1);
    expect(mockedNavigate).not.toHaveBeenCalled();
    expect(document.body).not.toHaveTextContent(/pre-\d|providerSubscriptionId|externalReference|idempotencyKey/i);
  });

  it("adapta a mensagem de recorrência existente quando o cancelamento já está pendente de confirmação", async () => {
    mockedBillingService.getBillingPaymentState.mockResolvedValue({
      paymentProviderSubscription: { status: "ACTIVE", planCode: "BRONZE", billingCycle: "MONTHLY", financiallyCovered: true, canCancel: true, cancellationState: "PENDING_CONFIRMATION", currentPeriodEnd: null },
      latestCheckout: null,
    });
    mockedBillingService.createCheckout.mockRejectedValue({ response: { status: 409, data: { message: "error.BILLING_RECURRING_SUBSCRIPTION_EXISTS" } } });
    mockedBillingService.getMyBilling.mockResolvedValue(freeBilling);
    await renderLoadedPage(); chooseSilver();
    fireEvent.click(screen.getByRole("button", { name: "Continuar para pagamento" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("Estamos confirmando o cancelamento solicitado com o Mercado Pago");
  });

  it.each(["status=approved", "status=success", "collection_status=approved"])("ignora query string %s e usa somente o backend", async (query) => {
    window.history.pushState({}, "", `/menu/meu-plano?${query}`);
    mockedBillingService.getMyBilling.mockResolvedValue({ ...billing, planCode: "FREE", planName: "Free", subscriptionStatus: null, subscriptionSource: null });
    const { container } = render(<MyPlanPage />);
    await screen.findByRole("heading", { name: /Planos dispon/ });
    expect(container.querySelector(".my-plan-current h1")).toHaveTextContent("Gratuito");
    expect(mockedBillingService.createCheckout).not.toHaveBeenCalled();
  });

  it("descarta resposta atrasada ao trocar de sessão e limpa no logout", async () => {
    const userA = deferred<BillingMeDTO>();
    mockedBillingService.getMyBilling.mockReturnValueOnce(userA.promise).mockResolvedValueOnce({ ...billing, planCode: "GOLD", planName: "Gold" });
    const { container } = render(<MyPlanPage />);
    setToken("Bearer user-b");
    await waitFor(() => expect(container.querySelector(".my-plan-current h1")).toHaveTextContent("Ouro"));
    userA.resolve(billing); await Promise.resolve();
    expect(container.querySelector(".my-plan-current h1")).toHaveTextContent("Ouro");
    removeToken();
    await waitFor(() => expect(container.querySelector(".my-plan-current")).not.toBeInTheDocument());
  });

  it("mostra Continuar pagamento quando canResume=true e redireciona sem criar novo checkout", async () => {
    mockedBillingService.getBillingPaymentState.mockResolvedValue({
      paymentProviderSubscription: null,
      latestCheckout: { status: "PROVIDER_PENDING", planCode: "BRONZE", createdAt: "2026-08-28T12:00:00Z", canResume: true, checkoutUrl: "https://mp.test/resume" },
    });
    await renderLoadedPage();

    const resumeButton = screen.getByRole("button", { name: "Continuar pagamento" });
    fireEvent.click(resumeButton);

    expect(mockedNavigate).toHaveBeenCalledWith("https://mp.test/resume");
    expect(mockedBillingService.createCheckout).not.toHaveBeenCalled();
  });

  it("esconde Continuar pagamento quando canResume=false", async () => {
    mockedBillingService.getBillingPaymentState.mockResolvedValue({
      paymentProviderSubscription: null,
      latestCheckout: { status: "FAILED", planCode: "BRONZE", createdAt: "2026-08-28T12:00:00Z", canResume: false, checkoutUrl: null },
    });
    await renderLoadedPage();

    expect(screen.queryByRole("button", { name: "Continuar pagamento" })).not.toBeInTheDocument();
  });

  it("preserva Atualizar status junto de Continuar pagamento", async () => {
    mockedBillingService.getBillingPaymentState.mockResolvedValue({
      paymentProviderSubscription: null,
      latestCheckout: { status: "PROVIDER_PENDING", planCode: "BRONZE", createdAt: "2026-08-28T12:00:00Z", canResume: true, checkoutUrl: "https://mp.test/resume" },
    });
    await renderLoadedPage();

    expect(screen.getByRole("button", { name: "Continuar pagamento" })).toBeInTheDocument();
    const refreshButton = screen.getByRole("button", { name: "Atualizar status" });
    fireEvent.click(refreshButton);
    await waitFor(() => expect(mockedBillingService.getBillingPaymentState).toHaveBeenCalledTimes(2));
  });

  it("mostra o estado de erro real com ação de retry e recarrega ao clicar", async () => {
    mockedBillingService.getMyBilling.mockRejectedValueOnce(new Error("offline")).mockResolvedValueOnce(billing);
    render(<MyPlanPage />);
    expect(await screen.findByText("Não foi possível carregar seu plano")).toBeInTheDocument();
    const retryButton = screen.getByRole("button", { name: "Tentar novamente" });
    fireEvent.click(retryButton);
    await screen.findByRole("heading", { name: /Planos dispon/ });
    expect(mockedBillingService.getMyBilling).toHaveBeenCalledTimes(2);
  });

  it("não quebra a página quando não existe checkout", async () => {
    mockedBillingService.getBillingPaymentState.mockResolvedValue({ paymentProviderSubscription: null, latestCheckout: null });
    await renderLoadedPage();

    expect(screen.queryByRole("button", { name: "Continuar pagamento" })).not.toBeInTheDocument();
  });

  it("não afirma assinatura confirmada quando a preapproval está ACTIVE mas sem evidência financeira (5G fail-closed)", async () => {
    // Reproduces the staging observation: Subscription.status=ACTIVE, zero BillingInvoice/PaymentAttempt.
    // /api/billing/me correctly falls back to FREE; the banner must not contradict it.
    mockedBillingService.getMyBilling.mockResolvedValue({ ...billing, planCode: "FREE", planName: "Gratuito", subscriptionStatus: null, subscriptionSource: null, billingCycle: null });
    mockedBillingService.getBillingPaymentState.mockResolvedValue({
      paymentProviderSubscription: { status: "ACTIVE", planCode: "BRONZE", billingCycle: "MONTHLY", financiallyCovered: false, canCancel: true, cancellationState: "NONE", currentPeriodEnd: null },
      latestCheckout: null,
    });
    await renderLoadedPage();

    expect(screen.queryByText("Assinatura ativa")).not.toBeInTheDocument();
    expect(screen.queryByText(/confirmado\./)).not.toBeInTheDocument();
    expect(screen.getByText("Pagamento em processamento")).toBeInTheDocument();
    expect(screen.getByText("Gratuito", { selector: "h1" })).toBeInTheDocument();
    // 5G.9-B fix: even though /api/billing/me fell back to FREE (no financial evidence yet), the
    // remote PAYMENT_PROVIDER contract still exists and can still charge - the user must be able
    // to cancel it. Before the fix, isSubscriptionCancelable read BillingMeDTO (FREE/null here)
    // and this button never appeared.
    expect(screen.getByRole("button", { name: "Cancelar assinatura" })).toBeEnabled();
  });

  it("converge para Bronze em toda a tela quando a evidência financeira é aprovada", async () => {
    mockedBillingService.getBillingPaymentState.mockResolvedValue({
      paymentProviderSubscription: { status: "ACTIVE", planCode: "BRONZE", billingCycle: "MONTHLY", financiallyCovered: true, canCancel: true, cancellationState: "NONE", currentPeriodEnd: null },
      latestCheckout: null,
    });
    await renderLoadedPage();

    // billing.me already reflects BRONZE/ACTIVE (fixture `billing`); the top banner is redundant and stays suppressed.
    expect(screen.queryByText("Assinatura ativa")).not.toBeInTheDocument();
    expect(screen.getByText("Bronze", { selector: "h1" })).toBeInTheDocument();
  });

  it("5G.11: não mostra 'pagamento em processamento' quando a 5G.10 já concedeu o plano sem BillingInvoice", async () => {
    // billing.me (fixture `billing`) already reports BRONZE/ACTIVE/PAYMENT_PROVIDER per 5G.10
    // (authorized -> ACTIVE -> immediate entitlement, no BillingInvoice required), but the
    // payment-state's own subscription still has financiallyCovered=false because no invoice
    // exists yet. Before this fix, this combination showed the misleading "Pagamento em
    // processamento" banner right next to an already-active Bronze plan card.
    mockedBillingService.getBillingPaymentState.mockResolvedValue({
      paymentProviderSubscription: { status: "ACTIVE", planCode: "BRONZE", billingCycle: "MONTHLY", financiallyCovered: false, canCancel: true, cancellationState: "NONE", currentPeriodEnd: null },
      latestCheckout: null,
    });
    await renderLoadedPage();

    expect(screen.queryByText("Pagamento em processamento")).not.toBeInTheDocument();
    expect(screen.queryByText("Assinatura ativa")).not.toBeInTheDocument();
    expect(screen.getByText("Bronze", { selector: "h1" })).toBeInTheDocument();
  });

  it("5G.11: mantém o aviso PROVIDER_PENDING mesmo quando o usuário não tem plano pago vigente", async () => {
    mockedBillingService.getMyBilling.mockResolvedValue({ ...billing, planCode: "FREE", planName: "Gratuito", subscriptionStatus: null, subscriptionSource: null, billingCycle: null });
    mockedBillingService.getBillingPaymentState.mockResolvedValue({
      paymentProviderSubscription: null,
      latestCheckout: { status: "PROVIDER_PENDING", planCode: "BRONZE", createdAt: "2026-08-28T12:00:00Z", canResume: false, checkoutUrl: null },
    });
    await renderLoadedPage();

    expect(screen.getByText("Aguardando confirmação do Mercado Pago")).toBeInTheDocument();
  });

  it("não navega com resposta tardia do checkout da sessão anterior", async () => {
    const oldCheckout = deferred<any>();
    mockedBillingService.createCheckout.mockReturnValue(oldCheckout.promise);
    mockedBillingService.getMyBilling.mockResolvedValue(freeBilling);
    await renderLoadedPage(); chooseSilver();
    fireEvent.click(screen.getByRole("button", { name: "Continuar para pagamento" }));
    setToken("Bearer user-b");
    oldCheckout.resolve({ checkoutId: 91, planCode: "SILVER", quotedPrice: 99.9, billingCycle: "MONTHLY", status: "PROVIDER_PENDING", checkoutUrl: "https://mp.test/user-a" });
    await waitFor(() => expect(mockedBillingService.getMyBilling).toHaveBeenCalledTimes(2));
    expect(mockedNavigate).not.toHaveBeenCalled();
  });
  const openCancellation = async () => {
    await renderLoadedPage();
    fireEvent.click(screen.getByRole("button", { name: "Cancelar assinatura" }));
  };

  it("abre confirmação com data formatada e permite manter sem cancelar", async () => {
    mockedBillingService.getMyBilling.mockResolvedValue({ ...billing, currentPeriodEnd: "2026-10-10T00:00:00Z" });
    await openCancellation();
    expect(screen.getByRole("dialog")).toHaveTextContent("até 10/10/2026");
    fireEvent.click(screen.getByRole("button", { name: "Manter assinatura" }));
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
    expect(mockedBillingService.cancelSubscription).not.toHaveBeenCalled();
  });

  it("envia uma única vez sem argumentos, bloqueia durante loading e exibe confirmação do POST", async () => {
    const request = deferred<any>();
    mockedBillingService.cancelSubscription.mockReturnValue(request.promise);
    await openCancellation();
    const button = screen.getByRole("button", { name: "Confirmar cancelamento" });
    fireEvent.click(button); fireEvent.click(button);
    expect(button).toBeDisabled();
    expect(button).toHaveTextContent("Cancelando...");
    expect(screen.getByRole("button", { name: "Manter assinatura" })).toBeDisabled();
    expect(mockedBillingService.cancelSubscription).toHaveBeenCalledTimes(1);
    expect(mockedBillingService.cancelSubscription).toHaveBeenCalledWith();
    request.resolve({ state: "CONFIRMED", hasResidualActiveContract: false, currentPeriodEnd: "2026-10-10T00:00:00Z", providerSubscriptionId: "private-provider-id" });
    expect(await screen.findByText("Cancelamento agendado")).toBeInTheDocument();
    expect(screen.queryByText(/Uma recorrência foi cancelada/)).not.toBeInTheDocument();
    expect(screen.getByText("Seu plano ficará ativo até 10/10/2026. Não haverá nova renovação.")).toBeInTheDocument();
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Cancelar assinatura" })).not.toBeInTheDocument();
    expect(mockedBillingService.getMyBilling).toHaveBeenCalledTimes(2);
    expect(screen.queryByText(/private-provider-id/)).not.toBeInTheDocument();
  });

  it("avisa sobre recorrência residual e permite atualizar o estado para cancelar novamente", async () => {
    mockedBillingService.cancelSubscription.mockResolvedValue({
      state: "CONFIRMED", planCode: "BRONZE", subscriptionStatus: "ACTIVE",
      currentPeriodEnd: null, hasResidualActiveContract: true,
      providerSubscriptionId: "private-provider-id",
    } as any);
    await openCancellation();
    fireEvent.click(screen.getByRole("button", { name: "Confirmar cancelamento" }));
    expect(await screen.findByText(/Uma recorrência foi cancelada/)).toHaveTextContent(
      "Uma recorrência foi cancelada, mas ainda existe outra assinatura recorrente ativa. Atualize o estado e tente cancelar novamente."
    );
    expect(screen.queryByText("Cancelamento agendado")).not.toBeInTheDocument();
    expect(screen.queryByText(/Não haverá nova renovação/)).not.toBeInTheDocument();
    expect(screen.queryByText(/private-provider-id/)).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Atualizar estado" }));
    expect(await screen.findByRole("button", { name: "Cancelar assinatura" })).toBeEnabled();
    expect(mockedBillingService.getBillingPaymentState).toHaveBeenCalledTimes(2);
  });

  it("mostra pendência sem confirmar, e permite retry", async () => {
    mockedBillingService.cancelSubscription.mockResolvedValueOnce({ state: "PENDING_CONFIRMATION", planCode: "BRONZE", subscriptionStatus: "ACTIVE", currentPeriodEnd: null }).mockResolvedValueOnce({ state: "CONFIRMED", planCode: "BRONZE", subscriptionStatus: "ACTIVE", currentPeriodEnd: null });
    await openCancellation();
    fireEvent.click(screen.getByRole("button", { name: "Confirmar cancelamento" }));
    expect(await screen.findByText("Estamos confirmando o cancelamento com o Mercado Pago.")).toBeInTheDocument();
    expect(screen.queryByText("Cancelamento agendado")).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Tentar novamente" }));
    expect(await screen.findByText("Cancelamento agendado")).toBeInTheDocument();
    expect(mockedBillingService.cancelSubscription).toHaveBeenCalledTimes(2);
  });

  it("mostra feedback HTTP sem expor identificadores e libera nova tentativa", async () => {
    mockedBillingService.cancelSubscription.mockRejectedValue({ response: { status: 400, data: { fieldErrors: [{ field: "providerSubscriptionId", message: "private-provider-id" }] } } });
    await openCancellation();
    fireEvent.click(screen.getByRole("button", { name: "Confirmar cancelamento" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("Não foi possível solicitar o cancelamento. Tente novamente.");
    expect(screen.getByRole("button", { name: "Confirmar cancelamento" })).toBeEnabled();
    expect(document.body).not.toHaveTextContent("private-provider-id");
  });

  /**
   * 5G.9-B: cancelability is decided ENTIRELY by payment-state.paymentProviderSubscription.canCancel
   * now, never by BillingMeDTO's effective plan/source/status - so these cases are expressed at
   * that layer (no real contract, or a contract in a non-cancellable terminal status).
   */
  it.each([
    { paymentProviderSubscription: null },
    { paymentProviderSubscription: { status: "CANCELED" as const, planCode: "BRONZE" as const, billingCycle: "MONTHLY" as const, financiallyCovered: false, canCancel: false, cancellationState: "NONE" as const, currentPeriodEnd: null } },
  ])("não oferece cancelamento quando payment-state reporta %o", async (paymentState) => {
    mockedBillingService.getBillingPaymentState.mockResolvedValue({ ...paymentState, latestCheckout: null });
    await renderLoadedPage();
    expect(screen.queryByRole("button", { name: "Cancelar assinatura" })).not.toBeInTheDocument();
  });

  it("oferece cancelamento mesmo com ADMIN_GRANT como plano efetivo, quando existe contrato remoto pagável", async () => {
    // canCancelRemoteContract != hasPaidEntitlement (5G.9 section B): an ADMIN_GRANT that currently
    // wins the entitlement precedence must not hide the ability to cancel a real, still-chargeable
    // PAYMENT_PROVIDER contract sitting underneath it.
    mockedBillingService.getMyBilling.mockResolvedValue({ ...billing, subscriptionSource: "ADMIN_GRANT", planCode: "GOLD", planName: "Ouro" });
    await renderLoadedPage();
    expect(screen.getByRole("button", { name: "Cancelar assinatura" })).toBeEnabled();
  });

  it.each([null, "2026-10-10T00:00:00Z"])("não infere confirmação no reload com cancelAtPeriodEnd=true e data %s", async (currentPeriodEnd) => {
    mockedBillingService.getMyBilling.mockResolvedValue({ ...billing, cancelAtPeriodEnd: true, currentPeriodEnd });
    await renderLoadedPage();
    expect(screen.queryByText("Cancelamento agendado")).not.toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Cancelar assinatura" })).toBeEnabled();
    expect(mockedBillingService.cancelSubscription).not.toHaveBeenCalled();
  });

  it("ignora confirmação tardia de outra sessão", async () => {
    const request = deferred<any>();
    mockedBillingService.cancelSubscription.mockReturnValue(request.promise);
    await openCancellation();
    fireEvent.click(screen.getByRole("button", { name: "Confirmar cancelamento" }));
    setToken("Bearer user-b");
    request.resolve({ state: "CONFIRMED", currentPeriodEnd: null });
    await waitFor(() => expect(mockedBillingService.getMyBilling).toHaveBeenCalledTimes(2));
    expect(screen.queryByText("Cancelamento agendado")).not.toBeInTheDocument();
  });

  it("carrega CONFIRMED do GET com data, sem cancelar novamente", async () => {
    mockedBillingService.getMyBilling.mockResolvedValue({ ...billing, cancellationState: "CONFIRMED", cancelAtPeriodEnd: true, currentPeriodEnd: "2026-10-10T00:00:00Z" });
    mockedBillingService.getBillingPaymentState.mockResolvedValue({
      paymentProviderSubscription: { status: "ACTIVE", planCode: "BRONZE", billingCycle: "MONTHLY", financiallyCovered: true, canCancel: true, cancellationState: "CONFIRMED", currentPeriodEnd: "2026-10-10T00:00:00Z" },
      latestCheckout: null,
    });
    await renderLoadedPage();
    expect(screen.getByText("Cancelamento agendado")).toBeInTheDocument();
    expect(screen.getByText("Seu plano ficará ativo até 10/10/2026. Não haverá nova renovação.")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Cancelar assinatura" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Tentar novamente" })).not.toBeInTheDocument();
    expect(mockedBillingService.cancelSubscription).not.toHaveBeenCalled();
  });

  it.each(["ACTIVE", "PAST_DUE"] as const)("carrega pendência do GET em %s e retry chama apenas cancelamento", async (subscriptionStatus) => {
    mockedBillingService.getMyBilling.mockResolvedValue({ ...billing, subscriptionStatus, cancellationState: "PENDING_CONFIRMATION", cancelAtPeriodEnd: true });
    mockedBillingService.getBillingPaymentState.mockResolvedValue({
      paymentProviderSubscription: { status: subscriptionStatus, planCode: "BRONZE", billingCycle: "MONTHLY", financiallyCovered: true, canCancel: true, cancellationState: "PENDING_CONFIRMATION", currentPeriodEnd: null },
      latestCheckout: null,
    });
    mockedBillingService.cancelSubscription.mockResolvedValue({ state: "CONFIRMED", planCode: "BRONZE", subscriptionStatus: "ACTIVE", currentPeriodEnd: null });
    await renderLoadedPage();
    expect(screen.getByText("Estamos confirmando o cancelamento com o Mercado Pago.")).toBeInTheDocument();
    expect(screen.queryByText("Cancelamento agendado")).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Tentar novamente" }));
    expect(await screen.findByText("Cancelamento agendado")).toBeInTheDocument();
    expect(mockedBillingService.cancelSubscription).toHaveBeenCalledTimes(1);
    expect(mockedBillingService.cancelSubscription).toHaveBeenCalledWith();
    expect(mockedBillingService.createCheckout).not.toHaveBeenCalled();
    expect(mockedNavigate).not.toHaveBeenCalled();
  });

  it.each(["NONE", "PENDING_CONFIRMATION", "CONFIRMED"] as const)("após remontar usa GET %s em vez da confirmação anterior do POST", async (cancellationState) => {
    mockedBillingService.cancelSubscription.mockResolvedValue({ state: "CONFIRMED", planCode: "BRONZE", subscriptionStatus: "ACTIVE", currentPeriodEnd: null });
    const first = render(<MyPlanPage />);
    fireEvent.click(await screen.findByRole("button", { name: "Cancelar assinatura" }));
    fireEvent.click(screen.getByRole("button", { name: "Confirmar cancelamento" }));
    await screen.findByText("Cancelamento agendado");
    first.unmount();
    mockedBillingService.getMyBilling.mockResolvedValue({ ...billing, cancellationState, cancelAtPeriodEnd: cancellationState !== "NONE" });
    mockedBillingService.getBillingPaymentState.mockResolvedValue({
      paymentProviderSubscription: { status: "ACTIVE", planCode: "BRONZE", billingCycle: "MONTHLY", financiallyCovered: true, canCancel: true, cancellationState, currentPeriodEnd: null },
      latestCheckout: null,
    });
    await renderLoadedPage();
    if (cancellationState === "CONFIRMED") expect(screen.getByText("Cancelamento agendado")).toBeInTheDocument();
    else expect(screen.queryByText("Cancelamento agendado")).not.toBeInTheDocument();
    if (cancellationState === "NONE") expect(screen.getByRole("button", { name: "Cancelar assinatura" })).toBeEnabled();
    if (cancellationState === "PENDING_CONFIRMATION") expect(screen.getByText("Estamos confirmando o cancelamento com o Mercado Pago.")).toBeInTheDocument();
    expect(mockedBillingService.cancelSubscription).toHaveBeenCalledTimes(1);
  });
  it.each([true, false])("rejeição conclusiva limpa pendência e atualiza GET (pendente=%s)", async (pending) => {
    mockedBillingService.getMyBilling.mockResolvedValueOnce({ ...billing, cancellationState: pending ? "PENDING_CONFIRMATION" : "NONE", cancelAtPeriodEnd: pending }).mockResolvedValue({ ...billing, cancellationState: "NONE", cancelAtPeriodEnd: false });
    mockedBillingService.getBillingPaymentState.mockResolvedValueOnce({
      paymentProviderSubscription: { status: "ACTIVE", planCode: "BRONZE", billingCycle: "MONTHLY", financiallyCovered: true, canCancel: true, cancellationState: pending ? "PENDING_CONFIRMATION" : "NONE", currentPeriodEnd: null },
      latestCheckout: null,
    }).mockResolvedValue(cancellablePaymentState);
    mockedBillingService.cancelSubscription.mockRejectedValue({ response: { status: 502, data: {
      message: "error.BILLING_CANCELLATION_PROVIDER_REJECTED", detail: "Invalid preapproval status param: canceled private-provider-id secret-token", requestId: "private-request"
    } } });
    await renderLoadedPage();
    fireEvent.click(screen.getByRole("button", { name: pending ? "Tentar novamente" : "Cancelar assinatura" }));
    if (!pending) fireEvent.click(screen.getByRole("button", { name: "Confirmar cancelamento" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("O Mercado Pago não aceitou o cancelamento neste momento. Sua assinatura permanece ativa e nenhuma alteração de cobrança foi confirmada.");
    await waitFor(() => expect(mockedBillingService.getMyBilling).toHaveBeenCalledTimes(2));
    expect(screen.getByRole("button", { name: "Cancelar assinatura" })).toBeEnabled();
    expect(screen.queryByText("Estamos confirmando o cancelamento com o Mercado Pago.")).not.toBeInTheDocument();
    expect(screen.queryByText("Cancelamento agendado")).not.toBeInTheDocument();
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
    expect(document.body).not.toHaveTextContent(/private-provider-id|secret-token|private-request|Invalid preapproval/);
    expect(mockedBillingService.cancelSubscription).toHaveBeenCalledTimes(1);
    expect(mockedBillingService.createCheckout).not.toHaveBeenCalled();
    expect(mockedNavigate).not.toHaveBeenCalled();
  });

  it("rejeição conclusiva limpa resultado pendente em memória mesmo se refresh falhar", async () => {
    mockedBillingService.cancelSubscription.mockResolvedValueOnce({ state: "PENDING_CONFIRMATION", planCode: "BRONZE", subscriptionStatus: "ACTIVE", currentPeriodEnd: null })
      .mockRejectedValueOnce({ response: { status: 502, data: { message: "error.BILLING_CANCELLATION_PROVIDER_REJECTED" } } });
    await openCancellation();
    fireEvent.click(screen.getByRole("button", { name: "Confirmar cancelamento" }));
    await screen.findByText("Estamos confirmando o cancelamento com o Mercado Pago.");
    mockedBillingService.getMyBilling.mockRejectedValueOnce(new Error("offline"));
    fireEvent.click(screen.getByRole("button", { name: "Tentar novamente" }));
    await screen.findByRole("alert");
    await waitFor(() => expect(screen.getByRole("button", { name: "Cancelar assinatura" })).toBeEnabled());
    expect(screen.queryByText("Estamos confirmando o cancelamento com o Mercado Pago.")).not.toBeInTheDocument();
    expect(screen.queryByText("Cancelamento agendado")).not.toBeInTheDocument();
  });

});

describe("MyPlanPage - 5G.12 / 5G.12.1 plan change", () => {
  const silverBilling: BillingMeDTO = { ...billing, planCode: "SILVER", planName: "Prata", currentMonthlyPrice: 99.9, requiredPlanCode: "SILVER", requiredPlanName: "Prata", currentPeriodEnd: "2026-10-09T00:00:00Z" };
  const silverWithScheduledBronze: BillingMeDTO = { ...silverBilling, pendingPlanCode: "BRONZE", pendingPlanName: "Bronze", pendingPlanPrice: 59.9, planChangeEffectiveAt: "2026-10-09T00:00:00Z" };
  const plansWithGold: PlanDTO[] = [...plans, { code: "GOLD", name: "Gold", minVehicles: 21, maxVehicles: 40, monthlyBasePrice: 149.9, billingModel: "FLAT", tiers: [] }];
  const upgradePreview: PlanChangePreviewDTO = { currentPlan: "BRONZE", targetPlan: "SILVER", changeType: "UPGRADE", currentPrice: 59.9, newMonthlyPrice: 99.9, chargeNow: 13.33, cycleEnd: "2026-10-09T00:00:00Z" };
  const downgradePreview: PlanChangePreviewDTO = { currentPlan: "SILVER", targetPlan: "BRONZE", changeType: "DOWNGRADE", currentPrice: 99.9, newMonthlyPrice: 59.9, chargeNow: null, cycleEnd: "2026-10-09T00:00:00Z" };
  const result = (overrides: Partial<PlanChangeResultDTO>): PlanChangeResultDTO => ({
    currentPlan: "BRONZE", targetPlan: "SILVER", changeType: "UPGRADE", status: "UPGRADE_PAYMENT_REQUIRED", effectiveAt: null, contractedPrice: 99.9,
    chargeAmount: 13.33, nextRenewalPrice: 99.9, checkoutUrl: "https://www.mercadopago.com.br/checkout/v1/redirect?pref_id=up", pending: true, ...overrides,
  });
  const upgradeState = (overrides: Partial<PlanUpgradeStatusDTO>): PlanUpgradeStatusDTO => ({
    status: "AWAITING_PAYMENT", fromPlan: "BRONZE", targetPlan: "SILVER", chargeAmount: 13.33, targetPrice: 99.9,
    checkoutUrl: "https://www.mercadopago.com.br/checkout/v1/redirect?pref_id=up", paymentPending: false, paymentRejected: false,
    appliedAt: null, updatedAt: new Date().toISOString(), ...overrides,
  });

  beforeEach(() => {
    jest.clearAllMocks();
    mockClosingFrames.length = 0;
    mockedBillingService.cancelSubscription.mockReset();
    mockedBillingService.changePlan.mockReset();
    mockedBillingService.undoDowngrade.mockReset();
    mockedBillingService.previewChangePlan.mockReset();
    localStorage.clear();
    setToken("Bearer user-token");
    mockedBillingService.getMyBilling.mockResolvedValue(billing);
    mockedBillingService.getBillingPaymentState.mockResolvedValue(cancellablePaymentState);
    mockedBillingService.getPlans.mockResolvedValue(plans);
    mockedBillingService.getPricePreview.mockImplementation(() => new Promise(() => {}));
    mockedBillingService.getPlanUpgradeStatus.mockResolvedValue(noUpgrade);
  });

  afterEach(() => localStorage.clear());

  it("desabilita downgrade para plano pago incompatível e habilita quando a frota cabe", async () => {
    // Current SILVER (max 20) with 15 vehicles: BRONZE (max 10) does not fit.
    mockedBillingService.getMyBilling.mockResolvedValue({ ...silverBilling, activeVehicleCount: 15 });
    await renderLoadedPage();

    expect(planCardButton("Bronze", "Fazer downgrade")).toBeDisabled();
    expect(screen.getByText("Bronze").closest("section")).toHaveTextContent("Sua frota atual excede o limite deste plano.");
  });

  it("habilita Fazer downgrade quando a frota atual cabe no plano inferior", async () => {
    mockedBillingService.getMyBilling.mockResolvedValue(silverBilling);
    await renderLoadedPage();

    expect(planCardButton("Bronze", "Fazer downgrade")).toBeEnabled();
  });

  it("upgrade mostra o valor proporcional e a próxima renovação calculados pelo backend e só leva ao pagamento", async () => {
    mockedBillingService.previewChangePlan.mockResolvedValue(upgradePreview);
    const pendingResponse = deferred<PlanChangeResultDTO>();
    mockedBillingService.changePlan.mockReturnValue(pendingResponse.promise);
    await renderLoadedPage();

    fireEvent.click(planCardButton("Prata", "Fazer upgrade")!);
    const dialog = screen.getByRole("dialog");
    expect(dialog).toHaveTextContent("Alterar de Bronze para Prata");
    expect(await screen.findByText("R$ 13,33")).toBeInTheDocument();
    expect(screen.getByText("R$ 99,90/mês")).toBeInTheDocument();
    expect(dialog).toHaveTextContent("só será liberado após a confirmação do pagamento");
    expect(dialog).not.toHaveTextContent("Não haverá cobrança proporcional");
    expect(mockedBillingService.previewChangePlan).toHaveBeenCalledWith("SILVER");
    expect(mockedBillingService.getPricePreview).not.toHaveBeenCalledWith(expect.anything(), "SILVER");

    const confirm = screen.getByRole("button", { name: "Confirmar upgrade" });
    fireEvent.click(confirm);
    fireEvent.click(confirm);
    expect(mockedBillingService.changePlan).toHaveBeenCalledTimes(1);
    expect(mockedBillingService.changePlan).toHaveBeenCalledWith("SILVER");
    expect(screen.getByText(/Processando/)).toBeInTheDocument();

    pendingResponse.resolve(result({}));
    await waitFor(() => expect(mockedNavigate).toHaveBeenCalledWith("https://www.mercadopago.com.br/checkout/v1/redirect?pref_id=up"));
    expect(mockedNavigate).toHaveBeenCalledTimes(1);
    expect(screen.getByRole("dialog")).toHaveTextContent("Redirecionando para o Mercado Pago...");
    // Nothing granted before the payment is confirmed.
    expect(screen.getByText("Bronze", { selector: "h1" })).toBeInTheDocument();
  });

  it("upgrade aplicado mostra sucesso claro no modal e o modal nunca fica em branco ao fechar", async () => {
    mockedBillingService.previewChangePlan.mockResolvedValue({ ...upgradePreview, chargeNow: 0 });
    mockedBillingService.changePlan.mockResolvedValue(result({ status: "UPGRADE_APPLIED", chargeAmount: 0, checkoutUrl: null, pending: false, effectiveAt: "2026-09-15T12:00:00Z" }));
    await renderLoadedPage();

    fireEvent.click(planCardButton("Prata", "Fazer upgrade")!);
    await screen.findByText("R$ 99,90/mês");
    mockedBillingService.getMyBilling.mockResolvedValue({ ...billing, planCode: "SILVER", planName: "Prata", currentMonthlyPrice: 99.9, requiredPlanCode: "SILVER", requiredPlanName: "Prata" });
    fireEvent.click(screen.getByRole("button", { name: "Confirmar upgrade" }));

    const dialog = await screen.findByText("Upgrade realizado com sucesso");
    expect(dialog.closest("section")).toHaveTextContent("Seu plano agora é Prata.");
    expect(dialog.closest("section")).toHaveTextContent("Próxima renovação");
    expect(mockedNavigate).not.toHaveBeenCalled();
    expect(await screen.findByText("Prata", { selector: "h1" })).toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: "Fechar" }));
    await waitFor(() => expect(screen.queryByRole("dialog")).not.toBeInTheDocument());
    // The frame displayed during the close animation still carries the success content.
    expect(mockClosingFrames[mockClosingFrames.length - 1]).toContain("Upgrade realizado com sucesso");
  });

  it("fechar o modal de confirmação sem enviar também não deixa o conteúdo em branco", async () => {
    mockedBillingService.previewChangePlan.mockResolvedValue(upgradePreview);
    await renderLoadedPage();

    fireEvent.click(planCardButton("Prata", "Fazer upgrade")!);
    await screen.findByText("R$ 13,33");
    fireEvent.click(screen.getByRole("button", { name: "Cancelar" }));

    await waitFor(() => expect(screen.queryByRole("dialog")).not.toBeInTheDocument());
    expect(mockClosingFrames[mockClosingFrames.length - 1]).toContain("Alterar de Bronze para Prata");
    expect(mockedBillingService.changePlan).not.toHaveBeenCalled();
  });

  it("pagamento pendente informa que aguarda confirmação e não mostra o plano novo como ativo", async () => {
    mockedBillingService.previewChangePlan.mockResolvedValue(upgradePreview);
    mockedBillingService.changePlan.mockResolvedValue(result({ status: "UPGRADE_PAYMENT_PENDING", checkoutUrl: null }));
    mockedBillingService.getPlanUpgradeStatus.mockResolvedValueOnce(noUpgrade).mockResolvedValue(upgradeState({ paymentPending: true, checkoutUrl: null }));
    await renderLoadedPage();

    fireEvent.click(planCardButton("Prata", "Fazer upgrade")!);
    await screen.findByText("R$ 13,33");
    fireEvent.click(screen.getByRole("button", { name: "Confirmar upgrade" }));

    expect(await screen.findByRole("heading", { name: "Estamos aguardando a confirmação do pagamento." })).toBeInTheDocument();
    expect(mockedNavigate).not.toHaveBeenCalled();
    expect(screen.getByText("Bronze", { selector: "h1" })).toBeInTheDocument();
    expect(await screen.findByTestId("upgrade-notice")).toHaveTextContent("Estamos aguardando a confirmação do pagamento.");
  });

  it("upgrade aguardando pagamento oferece retomar o pagamento e bloqueia outras mudanças concorrentes", async () => {
    mockedBillingService.getPlanUpgradeStatus.mockResolvedValue(upgradeState({}));
    await renderLoadedPage();

    const notice = screen.getByTestId("upgrade-notice");
    expect(notice).toHaveTextContent("Upgrade para Prata aguardando pagamento.");
    expect(notice).toHaveTextContent("R$ 13,33");
    expect(screen.getByText("Bronze", { selector: "h1" })).toBeInTheDocument();
    expect(planCardButton("Prata", "Fazer upgrade")).toBeDisabled();
    expect(planCardButton("Gratuito", "Mudar para Gratuito")).toBeDisabled();
    expect(screen.getByText("Prata").closest("section")).toHaveTextContent("Aguarde a confirmação do upgrade em andamento.");

    fireEvent.click(screen.getByRole("button", { name: "Pagar upgrade" }));
    expect(mockedNavigate).toHaveBeenCalledWith("https://www.mercadopago.com.br/checkout/v1/redirect?pref_id=up");
  });

  it("pagamento recusado mantém o plano atual e informa claramente", async () => {
    mockedBillingService.getPlanUpgradeStatus.mockResolvedValue(upgradeState({ paymentRejected: true }));
    await renderLoadedPage();

    expect(screen.getByTestId("upgrade-notice")).toHaveTextContent("O pagamento do upgrade foi recusado.");
    expect(screen.getByTestId("upgrade-notice")).toHaveTextContent("Seu plano atual continua ativo");
    expect(screen.getByText("Bronze", { selector: "h1" })).toBeInTheDocument();
  });

  it("após a confirmação do backend, atualizar o status mostra o sucesso com valor pago e próxima renovação", async () => {
    mockedBillingService.getPlanUpgradeStatus
      .mockResolvedValueOnce(upgradeState({ status: "APPLYING", checkoutUrl: null }))
      .mockResolvedValue(upgradeState({ status: "APPLIED", checkoutUrl: null, appliedAt: new Date().toISOString() }));
    await renderLoadedPage();

    expect(screen.getByTestId("upgrade-notice")).toHaveTextContent("Pagamento confirmado.");
    expect(screen.getByText("Bronze", { selector: "h1" })).toBeInTheDocument();

    mockedBillingService.getMyBilling.mockResolvedValue({ ...billing, planCode: "SILVER", planName: "Prata", currentMonthlyPrice: 99.9, requiredPlanCode: "SILVER", requiredPlanName: "Prata" });
    fireEvent.click(screen.getByRole("button", { name: "Atualizar status do upgrade" }));

    expect(await screen.findByText("Upgrade realizado com sucesso")).toBeInTheDocument();
    expect(screen.getByTestId("upgrade-notice")).toHaveTextContent("Seu plano agora é Prata. Valor pago agora: R$ 13,33. Próxima renovação: R$ 99,90/mês.");
    expect(await screen.findByText("Prata", { selector: "h1" })).toBeInTheDocument();
  });

  it("modal de downgrade explica a data de efetivação e mostra 'Mudança agendada' sem alterar o plano atual", async () => {
    mockedBillingService.getMyBilling.mockResolvedValue(silverBilling);
    mockedBillingService.previewChangePlan.mockResolvedValue(downgradePreview);
    mockedBillingService.changePlan.mockResolvedValue(result({ currentPlan: "SILVER", targetPlan: "BRONZE", changeType: "DOWNGRADE", status: "DOWNGRADE_SCHEDULED", effectiveAt: "2026-10-09T00:00:00Z", chargeAmount: null, nextRenewalPrice: 59.9, checkoutUrl: null }));
    await renderLoadedPage();

    fireEvent.click(planCardButton("Bronze", "Fazer downgrade")!);
    expect(screen.getByRole("dialog")).toHaveTextContent("Alterar de Prata para Bronze");
    expect(screen.getByText(/continuará disponível até 09\/10\/2026/)).toBeInTheDocument();
    await screen.findByText(/R\$ 59,90\/mês/);

    mockedBillingService.getMyBilling.mockResolvedValue(silverWithScheduledBronze);
    fireEvent.click(screen.getByRole("button", { name: "Agendar downgrade" }));
    expect(mockedBillingService.changePlan).toHaveBeenCalledWith("BRONZE");
    expect(await screen.findByRole("heading", { name: "Mudança agendada", level: 2 })).toBeInTheDocument();
    expect(screen.getByRole("dialog")).toHaveTextContent("Seu plano mudará para Bronze em 09/10/2026.");
    expect(await screen.findByTestId("scheduled-downgrade")).toHaveTextContent("Seu plano mudará para Bronze em 09/10/2026.");
    expect(screen.getByText("Prata", { selector: "h1" })).toBeInTheDocument();
    expect(screen.queryByText("Bronze", { selector: "h1" })).not.toBeInTheDocument();
  });

  it("downgrade agendado não bloqueia upgrade e oferece Desfazer downgrade", async () => {
    mockedBillingService.getMyBilling.mockResolvedValue(silverWithScheduledBronze);
    mockedBillingService.getPlans.mockResolvedValue(plansWithGold);
    mockedBillingService.previewChangePlan.mockResolvedValue({ ...upgradePreview, currentPlan: "SILVER", targetPlan: "GOLD", newMonthlyPrice: 149.9, chargeNow: 16.67 });
    await renderLoadedPage();

    const banner = screen.getByTestId("scheduled-downgrade");
    expect(banner).toHaveTextContent("Mudança agendada");
    expect(banner).toHaveTextContent("Seu plano mudará para Bronze em 09/10/2026.");
    expect(screen.getByRole("button", { name: "Desfazer downgrade" })).toBeEnabled();
    expect(planCardButton("Ouro", "Fazer upgrade")).toBeEnabled();
    expect(planCardButton("Bronze", "Downgrade agendado")).toBeDisabled();
    expect(planCardButton("Gratuito", "Mudar para Gratuito")).toBeDisabled();

    fireEvent.click(planCardButton("Ouro", "Fazer upgrade")!);
    await screen.findByText("R$ 16,67");
    expect(screen.getByRole("dialog")).toHaveTextContent("O downgrade agendado para Bronze será cancelado quando o upgrade for concluído. Se o pagamento não for aprovado, ele continua agendado.");
  });

  it("Desfazer downgrade envia uma única vez, confirma e remove o agendamento", async () => {
    mockedBillingService.getMyBilling.mockResolvedValue(silverWithScheduledBronze);
    const undo = deferred<PlanChangeResultDTO>();
    mockedBillingService.undoDowngrade.mockReturnValue(undo.promise);
    await renderLoadedPage();

    const button = screen.getByRole("button", { name: "Desfazer downgrade" });
    fireEvent.click(button);
    fireEvent.click(button);
    expect(mockedBillingService.undoDowngrade).toHaveBeenCalledTimes(1);
    expect(await screen.findByText(/Desfazendo/)).toBeInTheDocument();

    mockedBillingService.getMyBilling.mockResolvedValue(silverBilling);
    undo.resolve(result({ currentPlan: "SILVER", targetPlan: "BRONZE", changeType: "DOWNGRADE", status: "DOWNGRADE_UNDONE", chargeAmount: null, nextRenewalPrice: 99.9, checkoutUrl: null, pending: false }));

    expect(await screen.findByText("Downgrade desfeito. Você continua no plano Prata e a próxima renovação volta para R$ 99,90/mês.")).toBeInTheDocument();
    await waitFor(() => expect(screen.queryByTestId("scheduled-downgrade")).not.toBeInTheDocument());
    expect(planCardButton("Bronze", "Fazer downgrade")).toBeEnabled();
  });

  it("não anuncia sucesso se o backend responde sucesso mas o downgrade permanece", async () => {
    mockedBillingService.getMyBilling.mockResolvedValue(silverWithScheduledBronze);
    mockedBillingService.undoDowngrade.mockResolvedValue(result({ status: "DOWNGRADE_UNDONE" }));
    await renderLoadedPage();
    fireEvent.click(screen.getByRole("button", { name: "Desfazer downgrade" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("Não foi possível confirmar o desfazimento");
    expect(screen.queryByText(/Downgrade desfeito/)).not.toBeInTheDocument();
    expect(screen.getByTestId("scheduled-downgrade")).toBeInTheDocument();
  });

  it("distingue ciclo atual, cobrança fechada e projeção seguinte", async () => {
    mockedBillingService.getMyBilling.mockResolvedValue({ ...silverBilling, planCode: "FROTTA", billableVehicleCount: 102,
      currentMonthlyPrice: 256.9, projectedNextRenewalPrice: 258.9, nextRenewalPrice: 256.9,
      nextRenewalAt: "2026-10-10T00:00:00Z", nextRenewalLockedAt: "2026-10-09T00:00:00Z", nextRenewalSyncedAt: "2026-10-09T00:01:00Z" });
    await renderLoadedPage();
    const renewal = screen.getByTestId("fleet-renewal");
    expect(renewal).toHaveTextContent("Mensalidade do ciclo atual: R$ 256,90");
    expect(renewal).toHaveTextContent("Próxima cobrança confirmada: R$ 256,90");
    expect(renewal).toHaveTextContent("Projeção do ciclo seguinte: R$ 258,90");
    expect(renewal).toHaveTextContent("102 veículos cadastrados");
  });

  it("aguarda o GET antes de anunciar sucesso", async () => {
    mockedBillingService.getMyBilling.mockResolvedValue(silverWithScheduledBronze);
    mockedBillingService.undoDowngrade.mockResolvedValue(result({ status: "DOWNGRADE_UNDONE" }));
    await renderLoadedPage();
    const refresh = deferred<BillingMeDTO>();
    mockedBillingService.getMyBilling.mockReturnValue(refresh.promise);
    fireEvent.click(screen.getByRole("button", { name: "Desfazer downgrade" }));
    await waitFor(() => expect(mockedBillingService.getMyBilling).toHaveBeenCalledTimes(2));
    expect(screen.queryByText(/Downgrade desfeito/)).not.toBeInTheDocument();
    refresh.resolve(silverBilling);
    expect(await screen.findByText(/Downgrade desfeito/)).toBeInTheDocument();
    expect(screen.queryByTestId("scheduled-downgrade")).not.toBeInTheDocument();
  });

  it("falha ao desfazer mantém o downgrade agendado e mostra o erro", async () => {
    mockedBillingService.getMyBilling.mockResolvedValue(silverWithScheduledBronze);
    mockedBillingService.undoDowngrade.mockRejectedValue({ response: { status: 409, data: { message: "error.BILLING_DOWNGRADE_UNDO_REJECTED" } } });
    await renderLoadedPage();

    fireEvent.click(screen.getByRole("button", { name: "Desfazer downgrade" }));

    expect(await screen.findByRole("alert")).toHaveTextContent("O Mercado Pago não confirmou a restauração do valor da assinatura. O downgrade continua agendado.");
    expect(screen.getByTestId("scheduled-downgrade")).toHaveTextContent("Seu plano mudará para Bronze em 09/10/2026.");
    expect(screen.getByRole("button", { name: "Desfazer downgrade" })).toBeEnabled();
  });

  it("bloqueia mudança de plano quando já existe cancelamento agendado", async () => {
    mockedBillingService.getMyBilling.mockResolvedValue({ ...billing, cancellationState: "CONFIRMED", cancelAtPeriodEnd: true, currentPeriodEnd: "2026-10-10T00:00:00Z" });
    mockedBillingService.getBillingPaymentState.mockResolvedValue({
      paymentProviderSubscription: { status: "ACTIVE", planCode: "BRONZE", billingCycle: "MONTHLY", financiallyCovered: true, canCancel: true, cancellationState: "CONFIRMED", currentPeriodEnd: "2026-10-10T00:00:00Z" },
      latestCheckout: null,
    });
    await renderLoadedPage();

    expect(screen.getByText("Sua assinatura já está programada para encerrar em 10/10/2026.")).toBeInTheDocument();
    expect(planCardButton("Prata", "Fazer upgrade")).toBeDisabled();
  });

  it("trata 409 de upgrade em andamento com mensagem amigável e libera nova tentativa", async () => {
    mockedBillingService.previewChangePlan.mockResolvedValue(upgradePreview);
    mockedBillingService.changePlan.mockRejectedValue({ response: { status: 409, data: { message: "error.BILLING_PLAN_UPGRADE_IN_PROGRESS" } } });
    await renderLoadedPage();

    fireEvent.click(planCardButton("Prata", "Fazer upgrade")!);
    await screen.findByText("R$ 13,33");
    fireEvent.click(screen.getByRole("button", { name: "Confirmar upgrade" }));

    expect(await screen.findByRole("alert")).toHaveTextContent("Já existe um upgrade aguardando a confirmação do pagamento.");
    expect(screen.getByRole("button", { name: "Confirmar upgrade" })).toBeEnabled();
    expect(mockedNavigate).not.toHaveBeenCalled();
  });

  it("um 401 real ao mudar de plano é mostrado como sessão expirada, nunca escondido", async () => {
    mockedBillingService.previewChangePlan.mockResolvedValue(upgradePreview);
    mockedBillingService.changePlan.mockRejectedValue({ response: { status: 401, data: { message: "error.http.401" } } });
    await renderLoadedPage();

    fireEvent.click(planCardButton("Prata", "Fazer upgrade")!);
    await screen.findByText("R$ 13,33");
    const refreshesBefore = mockedBillingService.getMyBilling.mock.calls.length;
    fireEvent.click(screen.getByRole("button", { name: "Confirmar upgrade" }));

    expect(await screen.findByRole("alert")).toHaveTextContent("Sua sessão expirou. Faça login novamente.");
    // No follow-up requests with a dead session.
    expect(mockedBillingService.getMyBilling.mock.calls.length).toBe(refreshesBefore);
  });

  it("falha ao atualizar os dados depois da mudança é exibida, não engolida", async () => {
    mockedBillingService.getMyBilling.mockResolvedValue(silverBilling);
    mockedBillingService.previewChangePlan.mockResolvedValue(downgradePreview);
    mockedBillingService.changePlan.mockResolvedValue(result({ currentPlan: "SILVER", targetPlan: "BRONZE", changeType: "DOWNGRADE", status: "DOWNGRADE_SCHEDULED", effectiveAt: "2026-10-09T00:00:00Z", chargeAmount: null, nextRenewalPrice: 59.9, checkoutUrl: null }));
    await renderLoadedPage();

    fireEvent.click(planCardButton("Bronze", "Fazer downgrade")!);
    await screen.findByText(/R\$ 59,90\/mês/);
    mockedBillingService.getMyBilling.mockRejectedValue({ response: { status: 503 } });
    fireEvent.click(screen.getByRole("button", { name: "Agendar downgrade" }));

    expect(await screen.findByText("Não foi possível atualizar os dados do plano. Use Atualizar status ou recarregue a página.")).toBeInTheDocument();
  });

  it("não permite confirmar quando o backend não consegue calcular o valor proporcional", async () => {
    mockedBillingService.previewChangePlan.mockRejectedValue({ response: { status: 409, data: { message: "error.BILLING_PLAN_CHANGE_PERIOD_UNCONFIRMED" } } });
    await renderLoadedPage();

    fireEvent.click(planCardButton("Prata", "Fazer upgrade")!);

    expect(await screen.findByRole("alert")).toHaveTextContent("Nenhuma alteração foi feita");
    expect(screen.getByRole("button", { name: "Confirmar upgrade" })).toBeDisabled();
  });
});
