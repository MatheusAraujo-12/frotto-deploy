import { act, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { IonApp } from "@ionic/react";
import { DriverCarBadges, DriverCarLifecycleActions } from "./DriverCarLifecycle";
import { DriverRow } from "./Drivers";
import api from "../../services/axios/axios";
import {
  PRIMARY_ACTIVE,
  PRIMARY_SUSPENDED,
  RESERVE_ACTIVE,
  RESERVE_CONCLUDED,
  RESTORE_CONFLICT_409,
  RETURN_DRIVER_ACTIVE_ELSEWHERE,
  RETURN_PRIMARY_CAR_OCCUPIED,
  RETURN_PRIMARY_CONCLUDED,
  RETURN_RESTORED,
  apiError,
} from "../../services/driverAssignmentContract.fixtures";

jest.mock("../../services/axios/axios");
const mockAlerts = { showErrorAlert: jest.fn(), showSuccessAlert: jest.fn(), showWarningAlert: jest.fn() };
jest.mock("../../services/hooks/useAlert", () => ({ useAlert: () => mockAlerts }));
const mockedApi = api as jest.Mocked<typeof api>;

const renderActions = (driverCar: any) => {
  const onChanged = jest.fn();
  render(
    <IonApp>
      <DriverCarLifecycleActions driverCar={driverCar} onChanged={onChanged} />
    </IonApp>
  );
  return onChanged;
};

const click = async (text: string) => {
  await act(async () => {
    fireEvent.click(screen.getByText(text));
  });
};

describe("Vínculos - estados e ações (contrato real)", () => {
  let confirmSpy: jest.SpyInstance;

  beforeEach(() => {
    jest.resetAllMocks();
    confirmSpy = jest.spyOn(window, "confirm").mockReturnValue(true);
  });

  afterEach(() => confirmSpy.mockRestore());

  describe("badges", () => {
    it.each([
      [PRIMARY_ACTIVE, ["Ativo"], ["Carro reserva", "Vínculo principal"]],
      [PRIMARY_SUSPENDED, ["Suspenso", "Vínculo principal"], ["Carro reserva"]],
      [RESERVE_ACTIVE, ["Ativo", "Carro reserva"], ["Vínculo principal"]],
      [RESERVE_CONCLUDED, ["Finalizado", "Carro reserva"], ["Vínculo principal"]],
    ])("%# shows exactly the state the backend reports", (driverCar, shown, hidden) => {
      render(<DriverCarBadges driverCar={driverCar as any} />);
      shown.forEach((label) => expect(screen.getByText(label)).toBeInTheDocument());
      hidden.forEach((label) => expect(screen.queryByText(label)).not.toBeInTheDocument());
    });
  });

  it("no action for a normal active or a concluded contract", () => {
    renderActions(PRIMARY_ACTIVE);
    renderActions(RESERVE_CONCLUDED);
    expect(screen.queryByText("Devolver carro reserva")).not.toBeInTheDocument();
    expect(screen.queryByText("Reativar vínculo")).not.toBeInTheDocument();
  });

  describe("Devolver carro reserva", () => {
    it.each([
      [RETURN_RESTORED, "showSuccessAlert", "Carro reserva devolvido. O motorista retornou ao veículo principal."],
      [
        RETURN_PRIMARY_CAR_OCCUPIED,
        "showWarningAlert",
        "Carro reserva devolvido. O veículo principal ONX1A11 está atualmente vinculado a Maria Souza. O vínculo principal de João Silva permanece suspenso.",
      ],
      [
        RETURN_DRIVER_ACTIVE_ELSEWHERE,
        "showWarningAlert",
        "Carro reserva devolvido, mas o motorista possui outro vínculo ativo. O vínculo principal permanece suspenso.",
      ],
      [
        RETURN_PRIMARY_CONCLUDED,
        "showWarningAlert",
        "Carro reserva devolvido. O vínculo principal anterior já está finalizado e não foi reativado.",
      ],
    ])("%# calls the return endpoint once and reports the outcome (never as an error)", async (result, alert, message) => {
      mockedApi.post.mockResolvedValueOnce({ data: result });
      const onChanged = renderActions(RESERVE_ACTIVE);

      await click("Devolver carro reserva");

      await waitFor(() => expect(onChanged).toHaveBeenCalledTimes(1));
      expect(mockedApi.post).toHaveBeenCalledTimes(1);
      expect(mockedApi.post.mock.calls[0][0]).toBe("/api/driver-cars/2/return");
      expect((mockAlerts as any)[alert]).toHaveBeenCalledWith(message);
      expect(mockAlerts.showErrorAlert).not.toHaveBeenCalled();
    });

    it("declining the confirmation sends nothing", async () => {
      confirmSpy.mockReturnValue(false);
      const onChanged = renderActions(RESERVE_ACTIVE);

      await click("Devolver carro reserva");

      expect(mockedApi.post).not.toHaveBeenCalled();
      expect(onChanged).not.toHaveBeenCalled();
    });

    it("clicking twice while it runs sends a single request", async () => {
      let resolvePost: (value: unknown) => void = () => undefined;
      mockedApi.post.mockImplementationOnce(() => new Promise((resolve) => (resolvePost = resolve)));
      const onChanged = renderActions(RESERVE_ACTIVE);

      await click("Devolver carro reserva");
      await click("Devolver carro reserva");
      await act(async () => resolvePost({ data: RETURN_RESTORED }));

      await waitFor(() => expect(onChanged).toHaveBeenCalledTimes(1));
      expect(mockedApi.post).toHaveBeenCalledTimes(1);
    });
  });

  describe("Reativar vínculo", () => {
    it("restores the same contract and reports success", async () => {
      const restored = { ...PRIMARY_ACTIVE };
      mockedApi.post.mockResolvedValueOnce({ data: restored });
      const onChanged = renderActions(PRIMARY_SUSPENDED);

      await click("Reativar vínculo");

      await waitFor(() => expect(onChanged).toHaveBeenCalledWith(restored));
      expect(mockedApi.post.mock.calls[0][0]).toBe("/api/driver-cars/1/restore");
      expect(mockAlerts.showSuccessAlert).toHaveBeenCalledWith("Vínculo reativado com sucesso.");
    });

    it("a conflict is explained and changes nothing on screen", async () => {
      mockedApi.post.mockRejectedValueOnce(apiError(409, RESTORE_CONFLICT_409));
      const onChanged = renderActions(PRIMARY_SUSPENDED);

      await click("Reativar vínculo");

      await waitFor(() =>
        expect(mockAlerts.showErrorAlert).toHaveBeenCalledWith(
          "Não é possível reativar este vínculo porque o veículo ou o motorista possui outro vínculo ativo."
        )
      );
      expect(onChanged).not.toHaveBeenCalled();
      expect(mockAlerts.showSuccessAlert).not.toHaveBeenCalled();
    });
  });

  it("a car's driver row shows the badges and the action without opening the editor", async () => {
    mockedApi.post.mockResolvedValueOnce({ data: { ...PRIMARY_ACTIVE } });
    const onOpen = jest.fn();
    const onChanged = jest.fn();
    render(
      <IonApp>
        <DriverRow carDriver={PRIMARY_SUSPENDED as any} outstandingDebt={0} onOpen={onOpen} onOpenPendencies={jest.fn()} onChanged={onChanged} />
      </IonApp>
    );

    expect(screen.getByText("Suspenso")).toBeInTheDocument();
    expect(screen.getByText("Vínculo principal")).toBeInTheDocument();
    expect(screen.getByText(/até —/)).toBeInTheDocument();
    await click("Reativar vínculo");

    await waitFor(() => expect(onChanged).toHaveBeenCalledTimes(1));
    expect(onOpen).not.toHaveBeenCalled();
  });
});
