import { act, configure, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { IonApp } from "@ionic/react";
import DriverPendencyAdd from "./DriverPendencyAdd";
import api from "../../../services/axios/axios";
import { apiError } from "../../../services/driverAssignmentContract.fixtures";

jest.mock("../../../services/axios/axios");
const mockAlerts = { showErrorAlert: jest.fn(), showSuccessAlert: jest.fn(), showWarningAlert: jest.fn() };
jest.mock("../../../services/hooks/useAlert", () => ({ useAlert: () => mockAlerts }));
const mockedApi = api as jest.Mocked<typeof api>;
configure({ getElementError: (message) => new Error(`${message}`.split("\n")[0]) });

/** Chargeable maintenances of the contract's car, as GET /chargeable-maintenances returns them. */
const OPTIONS = [
  { id: 101, date: "2026-09-10", local: "Oficina Centro", description: "Freio, Mão de obra", cost: 1200, assignedAmount: 800, availableAmount: 400, chargeable: true },
  { id: 102, date: "2026-09-12", local: "Oficina Norte", description: "Óleo", cost: 300, assignedAmount: 300, availableAmount: 0, chargeable: false },
];

const renderForm = (initialValues?: any) => {
  const closeModal = jest.fn();
  render(
    <IonApp>
      <DriverPendencyAdd closeModal={closeModal} driverCarId="3" initialValues={initialValues} />
    </IonApp>
  );
  return { closeModal };
};

const ionChange = async (element: Element | null, value: unknown) => {
  if (!element) throw new Error("field not found");
  await act(async () => {
    fireEvent(element, new CustomEvent("ionChange", { detail: { value }, bubbles: true }));
  });
};
const chooseKind = (kind: string) => ionChange(document.querySelector('[data-testid="pendency-kind"] ion-select'), kind);
const typeAmount = async (digits: string) => {
  const field = document.querySelector('[data-field="amount"]') as Element;
  for (const key of digits) {
    await act(async () => {
      fireEvent.keyDown(field, { key });
    });
  }
};
const saveButton = () => screen.getAllByText("Salvar").map((node) => node.closest("ion-button") as any).find(Boolean);
/** The name suggestions open in an IonModal that renders asynchronously: wait for its items. */
const openNamePicker = async () => {
  await click(document.querySelector("#driver-pendency-add-page ion-item[button]") as Element);
  await waitFor(() => expect(document.querySelectorAll(".app-picker-modal__item").length).toBeGreaterThan(0));
};
const click = async (element: Element) => {
  await act(async () => {
    fireEvent.click(element);
  });
};

describe("Nova Pendência: um ponto de entrada, o tipo decide o formulário", () => {
  beforeAll(() => {
    (global as any).IntersectionObserver = class {
      observe() {}
      unobserve() {}
      disconnect() {}
    };
  });
  beforeEach(() => {
    jest.resetAllMocks();
    mockedApi.get.mockImplementation((url: string) =>
      url === "/api/pendencies/car-driver/3/chargeable-maintenances" ? Promise.resolve({ data: OPTIONS }) : Promise.resolve({ data: [] })
    );
  });

  it("A) Multa: only value + date are required and it saves through the fine endpoint (no invented time)", async () => {
    const { closeModal } = renderForm();
    await chooseKind("FINE");
    expect(screen.getByTestId("fine-fields")).toBeInTheDocument();
    expect(saveButton().disabled).toBe(true);

    await typeAmount("19500");
    expect(saveButton().disabled).toBe(true);
    await ionChange(document.querySelector('[data-field="infractionDate"]'), "2026-08-20");
    expect(saveButton().disabled).toBe(false);

    mockedApi.post.mockResolvedValueOnce({ data: { id: 70, originType: "FINE" } });
    await click(saveButton());

    await waitFor(() => expect(closeModal).toHaveBeenCalledWith({ id: 70, originType: "FINE" }));
    const [url, body] = mockedApi.post.mock.calls[0] as [string, any];
    expect(url).toBe("/api/pendencies/car-driver/3/fines");
    expect(body).toMatchObject({ amount: 195, infractionDate: "2026-08-20" });
    expect(body.infractionTime).toBeUndefined();
    expect(body.ait).toBeUndefined();
    expect(body.idempotencyKey).toMatch(/^op-/);
  });

  it("C/D) Multa: Mais detalhes is collapsed; filled it is sent, empty it never blocks", async () => {
    renderForm();
    await chooseKind("FINE");
    expect(document.querySelector('[data-field="ait"]')).toBeNull();
    await click(screen.getByTestId("fine-more-details"));
    expect(document.querySelector('[data-field="infractionTime"]')).not.toBeNull();

    await typeAmount("10000");
    await ionChange(document.querySelector('[data-field="infractionDate"]'), "2026-08-20");
    expect(saveButton().disabled).toBe(false); // details still empty
    await ionChange(document.querySelector('[data-field="infractionTime"]'), "10:30");
    await ionChange(document.querySelector('[data-field="ait"]'), "AIT-1");
    await ionChange(document.querySelector('[data-field="agency"]'), "DETRAN");
    await ionChange(document.querySelector('[data-field="dueDate"]'), "2026-10-20");

    mockedApi.post.mockResolvedValueOnce({ data: { id: 71 } });
    await click(saveButton());
    await waitFor(() => expect(mockedApi.post).toHaveBeenCalled());
    expect(mockedApi.post.mock.calls[0][1]).toMatchObject({ infractionTime: "10:30", ait: "AIT-1", agency: "DETRAN", dueDate: "2026-10-20" });
  });

  it("E/F) Manutenção compartilhada: pick the maintenance, see cost / assigned / available, save without observation", async () => {
    const { closeModal } = renderForm();
    await chooseKind("SHARED_MAINTENANCE");
    await waitFor(() => expect(document.querySelector('[data-testid="maintenance-fields"] ion-select')).not.toBeNull());
    expect(document.querySelectorAll('[data-testid="maintenance-fields"] ion-select-option')).toHaveLength(2);

    await ionChange(document.querySelector('[data-testid="maintenance-fields"] ion-select'), "101");
    const summary = screen.getByTestId("maintenance-summary").textContent || "";
    expect(summary).toMatch(/Custo da manutenção\s?R\$\s?1\.200,00/);
    expect(summary).toMatch(/Já atribuído\s?R\$\s?800,00/);
    expect(summary).toMatch(/Disponível para atribuição\s?R\$\s?400,00/);
    // Nothing of the maintenance is retyped: no description, workshop or total field.
    expect(document.querySelector('[data-field="description"], [data-field="oficina"], [data-field="valorTotal"]')).toBeNull();

    await typeAmount("30000");
    mockedApi.post.mockResolvedValueOnce({ data: { id: 80, originType: "SHARED_MAINTENANCE" } });
    await click(saveButton());

    await waitFor(() => expect(closeModal).toHaveBeenCalledWith({ id: 80, originType: "SHARED_MAINTENANCE" }));
    expect(mockedApi.post.mock.calls[0]).toEqual([
      "/api/pendencies/car-driver/3/shared-maintenance",
      { idempotencyKey: expect.stringMatching(/^op-/), maintenanceId: 101, amount: 300, note: undefined },
    ]);
  });

  it("G) above what is available: clear message, cannot save", async () => {
    renderForm();
    await chooseKind("SHARED_MAINTENANCE");
    await waitFor(() => expect(document.querySelector('[data-testid="maintenance-fields"] ion-select')).not.toBeNull());
    await ionChange(document.querySelector('[data-testid="maintenance-fields"] ion-select'), "101");
    await typeAmount("50000");

    expect(document.getElementById("form-error-amount")?.textContent).toMatch(/^O valor não pode ultrapassar o saldo disponível de R\$\s?400,00\.$/);
    expect(saveButton().disabled).toBe(true);
  });

  it("H) a maintenance without balance is marked and cannot be charged; none with balance is explained", async () => {
    renderForm();
    await chooseKind("SHARED_MAINTENANCE");
    await waitFor(() => expect(document.querySelector('[data-testid="maintenance-fields"] ion-select')).not.toBeNull());
    expect(document.body.textContent).toContain("sem saldo para cobrança");
    await ionChange(document.querySelector('[data-testid="maintenance-fields"] ion-select'), "102");
    await typeAmount("100");
    expect(document.getElementById("form-error-maintenanceId")?.textContent).toBe("Esta manutenção não possui saldo disponível para cobrança.");
    expect(saveButton().disabled).toBe(true);
  });

  it("H) every maintenance fully assigned (or none registered): explained, no charge possible", async () => {
    mockedApi.get.mockResolvedValue({ data: [OPTIONS[1]] });
    renderForm();
    await chooseKind("SHARED_MAINTENANCE");
    await waitFor(() => expect(document.getElementById("form-error-maintenanceId")?.textContent).toBe("Nenhuma manutenção deste veículo possui saldo disponível para cobrança."));
    expect(saveButton().disabled).toBe(true);
  });

  it("I) a retry after a failure reuses the same operation key (one pendency on the server)", async () => {
    renderForm();
    await chooseKind("FINE");
    await typeAmount("5000");
    await ionChange(document.querySelector('[data-field="infractionDate"]'), "2026-08-20");
    mockedApi.post.mockRejectedValueOnce({ message: "timeout" }).mockResolvedValueOnce({ data: { id: 72 } });

    await click(saveButton());
    await waitFor(() => expect(screen.getByRole("alert")).toBeInTheDocument());
    await click(saveButton());

    await waitFor(() => expect(mockedApi.post).toHaveBeenCalledTimes(2));
    expect((mockedApi.post.mock.calls[1][1] as any).idempotencyKey).toBe((mockedApi.post.mock.calls[0][1] as any).idempotencyKey);
  });

  it("backend refusal of a share is shown and nothing closes", async () => {
    const { closeModal } = renderForm();
    await chooseKind("SHARED_MAINTENANCE");
    await waitFor(() => expect(document.querySelector('[data-testid="maintenance-fields"] ion-select')).not.toBeNull());
    await ionChange(document.querySelector('[data-testid="maintenance-fields"] ion-select'), "101");
    await typeAmount("10000");
    mockedApi.post.mockRejectedValueOnce(apiError(400, { errorKey: "maintenancechargeexceeded", message: "error.maintenancechargeexceeded" }));
    await click(saveButton());
    await waitFor(() => expect(screen.getByRole("alert").textContent).toBe("A soma das cobranças desta manutenção ultrapassaria o custo da manutenção."));
    expect(closeModal).not.toHaveBeenCalled();
  });

  it("L/M) a common pendency keeps the regular form and endpoint; 'Multa contratual' is never a traffic fine", async () => {
    const { closeModal } = renderForm();
    expect(document.querySelector('[data-testid="fine-fields"], [data-testid="maintenance-fields"]')).toBeNull();
    // Suggestions: no "Multa" / "Manutenção compartilhada" as free text anymore; contractual fines are explicit.
    await openNamePicker();
    const suggestions = Array.from(document.querySelectorAll(".app-picker-modal__item")).map((item) => item.textContent?.trim());
    expect(suggestions).toContain("Multa contratual");
    expect(suggestions).not.toContain("Manutenção compartilhada");
    expect(suggestions).not.toContain("Multa");
    await click(screen.getByText("Multa contratual"));
    const cost = document.querySelector('#driver-pendency-add-page ion-input[inputmode="numeric"]') as Element;
    for (const key of "15000") {
      await act(async () => {
        fireEvent.keyDown(cost, { key });
      });
    }
    mockedApi.post.mockResolvedValueOnce({ data: { id: 90, name: "Multa contratual", cost: 150 } });
    await click(saveButton());

    await waitFor(() => expect(closeModal).toHaveBeenCalled());
    expect(mockedApi.post.mock.calls[0][0]).toBe("/api/pendencies/car-driver/3");
    expect(mockedApi.post.mock.calls[0][1]).toMatchObject({ name: "Multa contratual", cost: 150 });
    expect(mockedApi.post.mock.calls.some((call) => `${call[0]}`.includes("/fines"))).toBe(false);
  });

  it("A) a common pendency of R$ 0,00 cannot be saved and says why; with a value it can", async () => {
    renderForm();
    await openNamePicker();
    await click(await screen.findByText("Dano"));

    expect(saveButton().disabled).toBe(true);
    expect(screen.getByTestId("pendency-cost-hint").textContent).toBe("Informe um valor maior que zero.");
    await click(saveButton());
    expect(mockedApi.post).not.toHaveBeenCalled();

    const cost = document.querySelector('#driver-pendency-add-page ion-input[inputmode="numeric"]') as Element;
    for (const key of "15000") {
      await act(async () => {
        fireEvent.keyDown(cost, { key });
      });
    }
    expect(saveButton().disabled).toBe(false);
    expect(screen.queryByTestId("pendency-cost-hint")).toBeNull();
  });

  it("E) a legacy pendency of R$ 0,00 still opens for editing (no hint, save allowed)", async () => {
    renderForm({ id: 9, name: "Dano antigo", cost: 0, date: "2024-02-01", status: "PAID", paidAmount: 0, remainingAmount: 0 });
    expect(screen.queryByTestId("pendency-cost-hint")).toBeNull();
    expect(saveButton().disabled).toBe(false);
  });

  it("editing an existing fine shows its structural kind and keeps the regular edit form", async () => {
    renderForm({ id: 70, name: "Multa de trânsito", cost: 195, date: "2026-08-20", originType: "FINE", status: "OPEN" });
    expect(screen.getByTestId("pendency-origin").textContent).toBe("Tipo: Multa");
    expect(document.querySelector('[data-testid="pendency-kind"]')).toBeNull();
  });
});

/**
 * Staging report: "Manutenção compartilhada não salva, o botão fica desabilitado e não diz o que falta".
 * Reproduced in Chrome: with a maintenance registered without services (cost R$ 0,00) or a value above what is left,
 * the only explanation was a muted sentence at the bottom of the form (and a wrong one for the zero-cost case).
 */
describe("Manutenção compartilhada: o que impede salvar fica visível no campo", () => {
  const STAGING_OPTIONS = [
    { id: 1, date: "2026-09-10", local: "Oficina Centro", description: "Freio, Mão de obra", cost: 1200, assignedAmount: 0, availableAmount: 1200, chargeable: true },
    { id: 2, date: "2026-09-15", local: "Oficina Sem Serviço", description: null, cost: 0, assignedAmount: 0, availableAmount: 0, chargeable: false },
    { id: 3, date: "2026-09-20", local: "Oficina Parcial", description: "Pneu", cost: 500, assignedAmount: 300, availableAmount: 200, chargeable: true },
  ];

  beforeAll(() => {
    (global as any).IntersectionObserver = class {
      observe() {}
      unobserve() {}
      disconnect() {}
    };
  });
  beforeEach(() => {
    jest.resetAllMocks();
    mockedApi.get.mockImplementation((url: string) =>
      url === "/api/pendencies/car-driver/3/chargeable-maintenances" ? Promise.resolve({ data: STAGING_OPTIONS }) : Promise.resolve({ data: [] })
    );
  });

  const openSharedMaintenance = async () => {
    const result = renderForm();
    await chooseKind("SHARED_MAINTENANCE");
    await waitFor(() => expect(mockedApi.get).toHaveBeenCalledWith("/api/pendencies/car-driver/3/chargeable-maintenances"));
    await waitFor(() => expect(document.querySelector('[data-testid="maintenance-fields"] ion-select')).not.toBeNull());
    return result;
  };
  const selectMaintenance = (id: number) => ionChange(document.querySelector('[data-testid="maintenance-fields"] ion-select'), `${id}`);
  /** Android/IME: keydown says "Unidentified", the digit arrives in the input event. */
  const typeMobile = async (digits: string) => {
    const field = document.querySelector('[data-field="amount"]') as HTMLIonInputElement;
    for (const digit of digits) {
      await act(async () => {
        fireEvent.keyDown(field, { key: "Unidentified", keyCode: 229 });
      });
      field.value = `${field.value || ""}${digit}`;
      await act(async () => {
        fireEvent(field, new CustomEvent("ionInput", { detail: { value: field.value }, bubbles: true }));
      });
    }
    return field;
  };
  const fieldError = (name: string) => document.getElementById(`form-error-${name}`)?.textContent || null;

  it("valid maintenance + R$ 400 typed on a phone + empty observation: no error, Salvar enabled, 201, closes", async () => {
    const { closeModal } = await openSharedMaintenance();
    await selectMaintenance(1);
    const field = await typeMobile("40000");

    expect(field.value).toBe(currencyFormatForTest(400));
    expect(fieldError("maintenanceId")).toBeNull();
    expect(fieldError("amount")).toBeNull();
    expect(document.querySelectorAll('[data-testid="maintenance-fields"] .app-form-field--invalid')).toHaveLength(0);
    expect(saveButton().disabled).toBe(false);

    mockedApi.post.mockResolvedValueOnce({ status: 201, data: { id: 80, originType: "SHARED_MAINTENANCE", cost: 400 } });
    await click(saveButton());

    await waitFor(() => expect(closeModal).toHaveBeenCalledWith({ id: 80, originType: "SHARED_MAINTENANCE", cost: 400 }));
    expect(mockedApi.post).toHaveBeenCalledTimes(1);
    expect(mockedApi.post.mock.calls[0]).toEqual([
      "/api/pendencies/car-driver/3/shared-maintenance",
      { idempotencyKey: expect.stringMatching(/^op-/), maintenanceId: 1, amount: 400, note: undefined },
    ]);
  });

  it("the selected id (number) survives a later re-render of the list", async () => {
    await openSharedMaintenance();
    await selectMaintenance(3);
    await typeMobile("10000");
    expect(screen.getByTestId("maintenance-summary").textContent).toMatch(/Disponível para atribuição\s?R\$\s?200,00/);
    expect(saveButton().disabled).toBe(false);
  });

  it("nothing selected: 'Selecione uma manutenção.' under the field", async () => {
    await openSharedMaintenance();
    await typeMobile("40000");
    expect(fieldError("maintenanceId")).toBe("Selecione uma manutenção.");
    expect(saveButton().disabled).toBe(true);
  });

  it("zero value: 'Informe o valor cobrado do motorista.' under the value", async () => {
    await openSharedMaintenance();
    await selectMaintenance(1);
    expect(fieldError("amount")).toBe("Informe o valor cobrado do motorista.");
    expect(saveButton().disabled).toBe(true);
  });

  it("maintenance of R$ 0,00 (registered without services): says it has no balance, not that it was assigned", async () => {
    await openSharedMaintenance();
    await selectMaintenance(2);
    await typeMobile("40000");
    expect(fieldError("maintenanceId")).toBe("Esta manutenção não possui saldo disponível para cobrança.");
    expect(document.body.textContent).not.toContain("já tem todo o custo atribuído");
    expect(saveButton().disabled).toBe(true);
  });

  it("above what is left: 'O valor não pode ultrapassar o saldo disponível de R$ 200,00.' under the value", async () => {
    await openSharedMaintenance();
    await selectMaintenance(3);
    await typeMobile("40000");
    expect(fieldError("amount")).toMatch(/^O valor não pode ultrapassar o saldo disponível de R\$\s?200,00\.$/);
    expect(saveButton().disabled).toBe(true);
  });
});

function currencyFormatForTest(value: number) {
  return new Intl.NumberFormat("pt-BR", { style: "currency", currency: "BRL", minimumFractionDigits: 2, maximumFractionDigits: 3 }).format(value);
}
