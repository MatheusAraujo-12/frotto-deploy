import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import MyPlanPage from "./MyPlanPage";
import billingService from "../../services/billingService";
import { removeToken, setToken } from "../../services/localStorage/localstorage";
import { navigateToCheckout } from "./checkoutNavigation";
import { BillingMeDTO, PlanDTO } from "../../constants/BillingModels";

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
  currentPeriodStart: null, currentPeriodEnd: null, grantExpiresAt: null, cancelAtPeriodEnd: false,
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

describe("MyPlanPage - checkout modal", () => {
  beforeEach(() => {
    jest.clearAllMocks();
    localStorage.clear();
    setToken("Bearer user-token");
    mockedBillingService.getMyBilling.mockResolvedValue(billing);
    mockedBillingService.getBillingPaymentState.mockResolvedValue({ paymentProviderSubscription: null, latestCheckout: null });
    mockedBillingService.getPlans.mockResolvedValue(plans);
    mockedBillingService.getPricePreview.mockImplementation(() => new Promise(() => {}));
  });

  afterEach(() => localStorage.clear());

  it("permite plano superior, desabilita inferior incompatÃ­vel e abre o modal", async () => {
    await renderLoadedPage();

    const freeCard = screen.getByText("Gratuito").closest("section")!;
    expect(freeCard.querySelector("button")).toBeDisabled();
    expect(freeCard).toHaveTextContent("Sua frota atual excede o limite deste plano.");

    chooseSilver();
    expect(screen.getByRole("dialog")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Continuar para pagamento" })).toBeEnabled();
  });

  it("inicia um checkout, mostra loading, bloqueia clique duplo e navega uma vez", async () => {
    const checkout = deferred<any>();
    mockedBillingService.createCheckout.mockReturnValue(checkout.promise);
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
    await renderLoadedPage();
    chooseSilver();

    fireEvent.click(screen.getByRole("button", { name: "Continuar para pagamento" }));
    expect(await screen.findByRole("alert")).toHaveTextContent(/conectar ao servidor/);
    expect(screen.getByRole("dialog")).toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: "Continuar para pagamento" }));
    await waitFor(() => expect(mockedNavigate).toHaveBeenCalledWith("https://mp.test/retry"));
    expect(mockedBillingService.createCheckout).toHaveBeenCalledTimes(2);
  });

  it("nÃ£o ativa assinatura ao carregar ou retornar Ã  pÃ¡gina", async () => {
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
    mockedBillingService.getBillingPaymentState.mockResolvedValue({ paymentProviderSubscription: null, latestCheckout: { status, planCode: "SILVER", createdAt: "2026-08-28T12:00:00Z" } });
    await renderLoadedPage();
    const silverCard = screen.getByText("Prata").closest("section")!;
    expect(Array.from(silverCard.querySelectorAll("button")).find((button) => button.textContent === "Escolher plano")).toBeDisabled();
    expect(mockedBillingService.createCheckout).not.toHaveBeenCalled();
  });

  it("trata 409 como pagamento em andamento e atualiza payment-state", async () => {
    mockedBillingService.getBillingPaymentState.mockResolvedValueOnce({ paymentProviderSubscription: null, latestCheckout: null }).mockResolvedValueOnce({ paymentProviderSubscription: null, latestCheckout: { status: "PROVIDER_PENDING", planCode: "SILVER", createdAt: "2026-08-28T12:00:00Z" } });
    mockedBillingService.createCheckout.mockRejectedValue({ response: { status: 409, data: { message: "error.BILLING_CHECKOUT_IN_PROGRESS" } } });
    await renderLoadedPage(); chooseSilver();
    fireEvent.click(screen.getByRole("button", { name: "Continuar para pagamento" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("Já existe um pagamento em andamento");
    expect(mockedBillingService.getBillingPaymentState).toHaveBeenCalledTimes(2);
    expect(mockedBillingService.createCheckout).toHaveBeenCalledTimes(1);
    expect(mockedNavigate).not.toHaveBeenCalled();
  });

  it("ignora query string de sucesso e usa somente o backend", async () => {
    window.history.pushState({}, "", "/menu/meu-plano?status=approved");
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
});
