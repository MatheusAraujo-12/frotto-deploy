import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import MyPlanPage from "./MyPlanPage";
import billingService from "../../services/billingService";
import { removeToken, setToken } from "../../services/localStorage/localstorage";
import { navigateToCheckout } from "./checkoutNavigation";
import { BillingMeDTO, BillingPaymentStateDTO, PlanDTO } from "../../constants/BillingModels";

jest.mock("../../services/billingService");
jest.mock("./checkoutNavigation");
jest.mock("@ionic/react", () => {
  const React = jest.requireActual("react");
  const component = (tag: string) => ({ children, ...props }: any) =>
    React.createElement(tag, props, children);
  const modal = ({ children, isOpen }: any) => isOpen ? React.createElement("section", { role: "dialog" }, children) : null;

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

describe("MyPlanPage - 5G.12 plan change", () => {
  beforeEach(() => {
    jest.clearAllMocks();
    mockedBillingService.cancelSubscription.mockReset();
    localStorage.clear();
    setToken("Bearer user-token");
    mockedBillingService.getMyBilling.mockResolvedValue(billing);
    mockedBillingService.getBillingPaymentState.mockResolvedValue(cancellablePaymentState);
    mockedBillingService.getPlans.mockResolvedValue(plans);
    mockedBillingService.getPricePreview.mockImplementation(() => new Promise(() => {}));
  });

  afterEach(() => localStorage.clear());

  it("desabilita downgrade para plano pago incompatível e habilita quando a frota cabe", async () => {
    // Current SILVER (max 20) with 15 vehicles: BRONZE (max 10) does not fit.
    mockedBillingService.getMyBilling.mockResolvedValue({ ...billing, planCode: "SILVER", planName: "Prata", activeVehicleCount: 15, requiredPlanCode: "SILVER", requiredPlanName: "Prata" });
    await renderLoadedPage();

    expect(planCardButton("Bronze", "Fazer downgrade")).toBeDisabled();
    expect(screen.getByText("Bronze").closest("section")).toHaveTextContent("Sua frota atual excede o limite deste plano.");
  });

  it("habilita Fazer downgrade quando a frota atual cabe no plano inferior", async () => {
    mockedBillingService.getMyBilling.mockResolvedValue({ ...billing, planCode: "SILVER", planName: "Prata", activeVehicleCount: 6, requiredPlanCode: "SILVER", requiredPlanName: "Prata" });
    await renderLoadedPage();

    expect(planCardButton("Bronze", "Fazer downgrade")).toBeEnabled();
  });

  it("modal de upgrade mostra o novo valor calculado pelo backend e aplica imediatamente", async () => {
    mockedBillingService.getPricePreview.mockResolvedValue({ vehicleCount: 6, planCode: "SILVER", planName: "Prata", billingCycle: "MONTHLY", monthlyPrice: 44.9, averagePricePerVehicle: 7.48, components: [] });
    mockedBillingService.changePlan.mockResolvedValue({ currentPlan: "BRONZE", targetPlan: "SILVER", changeType: "UPGRADE", effectiveAt: "2026-09-15T12:00:00Z", contractedPrice: 44.9, pending: false });
    await renderLoadedPage();

    fireEvent.click(planCardButton("Prata", "Fazer upgrade")!);
    expect(screen.getByRole("dialog")).toHaveTextContent("Alterar de Bronze para Prata");
    expect(await screen.findByText("R$ 44,90/mês")).toBeInTheDocument();
    expect(screen.getByText(/liberado imediatamente/)).toBeInTheDocument();
    expect(screen.getByText(/Não haverá cobrança proporcional/)).toBeInTheDocument();
    expect(mockedBillingService.getPricePreview).toHaveBeenCalledWith(6, "SILVER");

    mockedBillingService.getMyBilling.mockResolvedValue({ ...billing, planCode: "SILVER", planName: "Prata", currentMonthlyPrice: 44.9, requiredPlanCode: "SILVER", requiredPlanName: "Prata" });
    fireEvent.click(screen.getByRole("button", { name: "Confirmar upgrade" }));
    expect(mockedBillingService.changePlan).toHaveBeenCalledWith("SILVER");
    await waitFor(() => expect(screen.queryByRole("dialog")).not.toBeInTheDocument());
    expect(await screen.findByText("Prata", { selector: "h1" })).toBeInTheDocument();
  });

  it("modal de downgrade explica a data de efetivação e não altera o plano atual visualmente", async () => {
    mockedBillingService.getMyBilling.mockResolvedValue({ ...billing, planCode: "SILVER", planName: "Prata", currentPeriodEnd: "2026-10-09T00:00:00Z", requiredPlanCode: "SILVER", requiredPlanName: "Prata" });
    mockedBillingService.getPricePreview.mockResolvedValue({ vehicleCount: 6, planCode: "BRONZE", planName: "Bronze", billingCycle: "MONTHLY", monthlyPrice: 15.9, averagePricePerVehicle: 2.65, components: [] });
    mockedBillingService.changePlan.mockResolvedValue({ currentPlan: "SILVER", targetPlan: "BRONZE", changeType: "DOWNGRADE", effectiveAt: "2026-10-09T00:00:00Z", contractedPrice: 15.9, pending: true });
    await renderLoadedPage();

    fireEvent.click(planCardButton("Bronze", "Fazer downgrade")!);
    expect(screen.getByRole("dialog")).toHaveTextContent("Alterar de Prata para Bronze");
    expect(screen.getByText(/continuará disponível até 09\/10\/2026/)).toBeInTheDocument();
    await screen.findByText(/R\$ 15,90\/mês/);

    mockedBillingService.getMyBilling.mockResolvedValue({
      ...billing, planCode: "SILVER", planName: "Prata", currentPeriodEnd: "2026-10-09T00:00:00Z", requiredPlanCode: "SILVER", requiredPlanName: "Prata",
      pendingPlanCode: "BRONZE", pendingPlanName: "Bronze", pendingPlanPrice: 15.9, planChangeEffectiveAt: "2026-10-09T00:00:00Z",
    });
    fireEvent.click(screen.getByRole("button", { name: "Agendar downgrade" }));
    expect(mockedBillingService.changePlan).toHaveBeenCalledWith("BRONZE");
    // The current plan never flips to Bronze before the scheduled date - only the banner appears.
    expect(await screen.findByText("Mudança para Bronze agendada para 09/10/2026.")).toBeInTheDocument();
    expect(screen.getByText("Prata", { selector: "h1" })).toBeInTheDocument();
    expect(screen.queryByText("Bronze", { selector: "h1" })).not.toBeInTheDocument();
  });

  it("mostra o aviso de mudança agendada e desabilita outras mudanças enquanto ela estiver pendente", async () => {
    mockedBillingService.getMyBilling.mockResolvedValue({
      ...billing, planCode: "SILVER", planName: "Prata", requiredPlanCode: "SILVER", requiredPlanName: "Prata",
      pendingPlanCode: "BRONZE", pendingPlanName: "Bronze", pendingPlanPrice: 15.9, planChangeEffectiveAt: "2026-10-09T00:00:00Z",
    });
    await renderLoadedPage();

    expect(screen.getByText("Mudança para Bronze agendada para 09/10/2026.")).toBeInTheDocument();
    expect(planCardButton("Gratuito", "Mudar para Gratuito")).toBeDisabled();
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

  it("trata erro 409 de mudança de plano com mensagem amigável", async () => {
    mockedBillingService.getPricePreview.mockResolvedValue({ vehicleCount: 6, planCode: "SILVER", planName: "Prata", billingCycle: "MONTHLY", monthlyPrice: 44.9, averagePricePerVehicle: 7.48, components: [] });
    mockedBillingService.changePlan.mockRejectedValue({ response: { status: 409, data: { message: "error.BILLING_PLAN_CHANGE_ALREADY_PENDING" } } });
    await renderLoadedPage();

    fireEvent.click(planCardButton("Prata", "Fazer upgrade")!);
    await screen.findByText("R$ 44,90/mês");
    fireEvent.click(screen.getByRole("button", { name: "Confirmar upgrade" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("Já existe uma mudança de plano agendada para esta assinatura.");
  });
});
