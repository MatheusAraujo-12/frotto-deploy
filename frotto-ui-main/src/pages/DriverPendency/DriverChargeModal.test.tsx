import { act, configure, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { IonApp } from "@ionic/react";
import DriverChargeModal from "./DriverChargeModal";
import api from "../../services/axios/axios";
import { apiError } from "../../services/driverAssignmentContract.fixtures";

jest.mock("../../services/axios/axios");
const mockedApi = api as jest.Mocked<typeof api>;
configure({ getElementError: (message) => new Error(`${message}`.split("\n")[0]) });

const renderModal = (mode: "fine" | "maintenance") => {
  const onCreated = jest.fn();
  const onCancel = jest.fn();
  render(
    <IonApp>
      <DriverChargeModal mode={mode} driverCarId={3} carId={92001} onCancel={onCancel} onCreated={onCreated} />
    </IonApp>
  );
  return { onCreated, onCancel };
};

const field = (name: string) => {
  const element = document.querySelector(`[data-field="${name}"]`);
  if (!element) throw new Error(`field not found: ${name}`);
  return element;
};
const ionChange = async (element: Element, value: unknown) => {
  await act(async () => {
    fireEvent(element, new CustomEvent("ionChange", { detail: { value }, bubbles: true }));
  });
};
/** FormCurrency takes the digits as keys (cents first). */
const typeAmount = async (digits: string) => {
  for (const key of digits) {
    await act(async () => {
      fireEvent.keyDown(field("amount"), { key });
    });
  }
};
const registerButton = () => screen.getByText("Registrar pendência").closest("ion-button") as any;
const click = async (element: Element) => {
  await act(async () => {
    fireEvent.click(element);
  });
};

describe("Nova multa / Cobrar manutenção", () => {
  beforeAll(() => {
    (global as any).IntersectionObserver = class {
      observe() {}
      unobserve() {}
      disconnect() {}
    };
  });
  beforeEach(() => jest.resetAllMocks());

  it("fine: blocked until value, date and time; a retry after a failure reuses the same operation key", async () => {
    const { onCreated } = renderModal("fine");
    expect(registerButton().disabled).toBe(true);

    await typeAmount("19500");
    expect(registerButton().disabled).toBe(true);
    await ionChange(field("infractionDate"), "2026-08-20");
    expect(registerButton().disabled).toBe(true);
    await ionChange(field("infractionTime"), "10:30");
    await ionChange(field("ait"), "AIT-1");
    expect(registerButton().disabled).toBe(false);

    mockedApi.post.mockRejectedValueOnce({ message: "timeout" }).mockResolvedValueOnce({ data: { id: 70, originType: "FINE" } });
    await click(screen.getByText("Registrar pendência"));
    await waitFor(() => expect(screen.getByRole("alert")).toBeInTheDocument());
    expect(onCreated).not.toHaveBeenCalled();
    await click(screen.getByText("Registrar pendência"));

    await waitFor(() => expect(onCreated).toHaveBeenCalledWith({ id: 70, originType: "FINE" }));
    const [first, retry] = mockedApi.post.mock.calls;
    expect(first[0]).toBe("/api/pendencies/car-driver/3/fines");
    expect((first[1] as any).idempotencyKey).toMatch(/^op-/);
    expect((retry[1] as any).idempotencyKey).toBe((first[1] as any).idempotencyKey);
    expect(first[1]).toMatchObject({ amount: 195, infractionDate: "2026-08-20", infractionTime: "10:30", ait: "AIT-1" });
  });

  it("maintenance: the car's maintenances, what is already assigned, and no share above what is left", async () => {
    mockedApi.get.mockImplementation((url: string) => {
      if (url === "/api/maintenances/car/92001") return Promise.resolve({ data: [{ id: 101, date: "2026-09-10", local: "Oficina Centro", cost: 1000 }] });
      if (url === "/api/pendencies/shared-maintenance/101/summary")
        return Promise.resolve({ data: { maintenanceId: 101, maintenanceCost: 1000, assignedAmount: 600, availableAmount: 400, chargesCount: 1 } });
      return Promise.resolve({ data: {} });
    });
    const { onCreated } = renderModal("maintenance");
    await waitFor(() => expect(document.querySelector("ion-select")).not.toBeNull());

    await ionChange(document.querySelector("ion-select") as Element, "101");
    await waitFor(() => expect(screen.getByTestId("maintenance-summary").textContent).toMatch(/disponível R\$\s?400,00/));
    expect(screen.getByTestId("maintenance-summary").textContent).toMatch(/Custo da manutenção R\$\s?1\.000,00/);

    await typeAmount("50000");
    expect(registerButton().disabled).toBe(true);
    expect(document.body.textContent).toMatch(/ultrapassa o disponível desta manutenção \(R\$\s?400,00\)/);

    // Backend refusal (another share was registered meanwhile): shown, nothing closed.
    mockedApi.post.mockRejectedValueOnce(apiError(400, { errorKey: "maintenancechargeexceeded", message: "error.maintenancechargeexceeded" }));
    await act(async () => {
      fireEvent.keyDown(field("amount"), { key: "Backspace" });
    });
    // 500,00 -> backspace -> 50,00 (within what is left)
    expect(registerButton().disabled).toBe(false);
    await click(screen.getByText("Registrar pendência"));
    await waitFor(() =>
      expect(screen.getByRole("alert").textContent).toBe("A soma das cobranças desta manutenção ultrapassaria o custo da manutenção.")
    );
    expect(onCreated).not.toHaveBeenCalled();
    expect(mockedApi.post).toHaveBeenCalledWith("/api/pendencies/car-driver/3/shared-maintenance", expect.objectContaining({ maintenanceId: 101, amount: 50 }));
  });

  it("Cancelar writes nothing", async () => {
    const { onCancel } = renderModal("fine");
    await click(screen.getAllByText("Cancelar")[0]);
    expect(onCancel).toHaveBeenCalled();
    expect(mockedApi.post).not.toHaveBeenCalled();
  });
});
