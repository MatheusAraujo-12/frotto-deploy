import { act, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { IonApp } from "@ionic/react";
import DriverAdd from "./DriverAdd";
import api from "../../../services/axios/axios";
import {
  ACTIVE_DRIVER_EXISTS_400,
  ASSIGNMENT_REQUIRED_409,
  PRIMARY_ACTIVE,
  PRIMARY_SUSPENDED,
  RESERVE_ACTIVE,
  RESERVE_CONCLUDED,
  apiError,
} from "../../../services/driverAssignmentContract.fixtures";

jest.mock("../../../services/axios/axios");
const mockAlerts = { showErrorAlert: jest.fn(), showSuccessAlert: jest.fn(), showWarningAlert: jest.fn() };
jest.mock("../../../services/hooks/useAlert", () => ({ useAlert: () => mockAlerts }));

const mockedApi = api as jest.Mocked<typeof api>;
const NEW_CONTRACT = { startDate: "2026-09-10", driver: { id: 1, name: "João Silva", cpf: "11111111111" } };

const renderDriverAdd = (initialValues: any) => {
  const closeModal = jest.fn();
  const view = render(
    <IonApp>
      <DriverAdd closeModal={closeModal} initialValues={initialValues} carId="20" />
    </IonApp>
  );
  return { ...view, closeModal };
};

const clickSave = async () => {
  await act(async () => {
    fireEvent.click(screen.getByText("Salvar"));
  });
};

const postedUrls = () => mockedApi.post.mock.calls.map((call) => call[0]);

describe("DriverAdd - transferência definitiva × carro reserva", () => {
  beforeAll(() => {
    (global as any).IntersectionObserver = class {
      observe() {}
      unobserve() {}
      disconnect() {}
    };
  });

  beforeEach(() => {
    jest.resetAllMocks();
    mockedApi.get.mockResolvedValue({ data: {} });
  });

  it("a driver without another contract is added as today, with no question", async () => {
    mockedApi.post.mockResolvedValueOnce({ data: PRIMARY_ACTIVE });
    const { closeModal } = renderDriverAdd(NEW_CONTRACT);

    await clickSave();

    await waitFor(() => expect(closeModal).toHaveBeenCalledWith(PRIMARY_ACTIVE));
    expect(postedUrls()).toEqual(["/api/driver-cars/car/20"]);
    expect(screen.queryByText("Motorista já vinculado a outro veículo")).not.toBeInTheDocument();
    expect(mockAlerts.showSuccessAlert).not.toHaveBeenCalled();
  });

  it("the 409 opens the choice with the real plate; Cancelar changes nothing", async () => {
    mockedApi.post.mockRejectedValueOnce(apiError(409, ASSIGNMENT_REQUIRED_409));
    const { closeModal } = renderDriverAdd(NEW_CONTRACT);

    await clickSave();

    expect(await screen.findByText("Motorista já vinculado a outro veículo")).toBeInTheDocument();
    expect(screen.getByText(/Este motorista possui um vínculo com o veículo ONX1A11/)).toBeInTheDocument();
    expect(screen.getByText("Transferência definitiva")).toBeInTheDocument();
    expect(screen.getByText("Carro reserva")).toBeInTheDocument();
    expect(mockAlerts.showErrorAlert).not.toHaveBeenCalled();

    await act(async () => {
      fireEvent.click(screen.getAllByText("Cancelar").find((node) => node.closest(".driver-assignment-choice"))!);
    });

    await waitFor(() => expect(screen.queryByText("Motorista já vinculado a outro veículo")).not.toBeInTheDocument());
    expect(postedUrls()).toEqual(["/api/driver-cars/car/20"]);
    expect(closeModal).not.toHaveBeenCalled();
    expect(mockAlerts.showSuccessAlert).not.toHaveBeenCalled();
  });

  it.each([
    ["Transferência definitiva", "PERMANENT", "Motorista transferido com sucesso."],
    ["Carro reserva", "RESERVE", "Motorista vinculado temporariamente ao carro reserva."],
  ])("choosing %s resends the request with assignment=%s", async (label, assignment, message) => {
    mockedApi.post.mockRejectedValueOnce(apiError(409, ASSIGNMENT_REQUIRED_409)).mockResolvedValueOnce({ data: RESERVE_ACTIVE });
    const { closeModal } = renderDriverAdd(NEW_CONTRACT);

    await clickSave();
    await act(async () => {
      fireEvent.click(await screen.findByText(label));
    });

    await waitFor(() => expect(closeModal).toHaveBeenCalledWith(RESERVE_ACTIVE));
    expect(postedUrls()).toEqual(["/api/driver-cars/car/20", `/api/driver-cars/car/20?assignment=${assignment}`]);
    expect(mockedApi.post.mock.calls[1][1]).toEqual(mockedApi.post.mock.calls[0][1]);
    expect(mockAlerts.showSuccessAlert).toHaveBeenCalledWith(message);
  });

  it("other errors keep the existing error message and never open the choice", async () => {
    mockedApi.post.mockRejectedValueOnce(apiError(400, ACTIVE_DRIVER_EXISTS_400));
    const { closeModal } = renderDriverAdd(NEW_CONTRACT);

    await clickSave();

    await waitFor(() => expect(mockAlerts.showErrorAlert).toHaveBeenCalled());
    expect(screen.queryByText("Motorista já vinculado a outro veículo")).not.toBeInTheDocument();
    expect(closeModal).not.toHaveBeenCalled();
  });

  it("saving twice while the request runs sends a single request", async () => {
    let resolvePost: (value: unknown) => void = () => undefined;
    mockedApi.post.mockImplementationOnce(() => new Promise((resolve) => (resolvePost = resolve)));
    const { closeModal } = renderDriverAdd(NEW_CONTRACT);

    await clickSave();
    await clickSave();
    await act(async () => resolvePost({ data: PRIMARY_ACTIVE }));

    await waitFor(() => expect(closeModal).toHaveBeenCalledTimes(1));
    expect(mockedApi.post).toHaveBeenCalledTimes(1);
  });

  it("a normal contract keeps the Finalizado toggle (control)", () => {
    renderDriverAdd(PRIMARY_ACTIVE);

    // Badge "Ativo" plus the toggle labelled "Finalizado" (TEXT.resolved).
    expect(screen.getByText("Ativo")).toBeInTheDocument();
    expect(screen.getByText("Finalizado")).toBeInTheDocument();
    expect(screen.queryByText("Devolver carro reserva")).not.toBeInTheDocument();
  });

  it("an active reserve shows its state and is finished by returning it, not by the Finalizado toggle", () => {
    renderDriverAdd(RESERVE_ACTIVE);

    expect(screen.getByText("Ativo")).toBeInTheDocument();
    expect(screen.getByText("Carro reserva")).toBeInTheDocument();
    expect(screen.getByText("Devolver carro reserva")).toBeInTheDocument();
    expect(screen.queryByText("Finalizado")).not.toBeInTheDocument();
  });

  it("a returned reserve cannot be reopened from the form", () => {
    renderDriverAdd(RESERVE_CONCLUDED);

    // Only the status badge says "Finalizado": the toggle that could reopen it is gone.
    expect(screen.getAllByText("Finalizado")).toHaveLength(1);
    expect(screen.getByText("Finalizado").closest(".frotto-badge")).not.toBeNull();
    expect(screen.getByText("Carro reserva devolvido.")).toBeInTheDocument();
    expect(screen.queryByText("Devolver carro reserva")).not.toBeInTheDocument();
  });

  it("a suspended primary offers Reativar vínculo", () => {
    renderDriverAdd(PRIMARY_SUSPENDED);

    expect(screen.getByText("Suspenso")).toBeInTheDocument();
    expect(screen.getByText("Vínculo principal")).toBeInTheDocument();
    expect(screen.getByText("Reativar vínculo")).toBeInTheDocument();
  });
});

describe("DriverAdd - contatos de emergência contra o formulário desatualizado", () => {
  beforeAll(() => {
    (global as any).IntersectionObserver = class {
      observe() {}
      unobserve() {}
      disconnect() {}
    };
  });

  beforeEach(() => {
    jest.resetAllMocks();
    mockedApi.get.mockResolvedValue({ data: {} });
  });

  const WITH_CONTACTS = {
    ...PRIMARY_ACTIVE,
    driver: { ...PRIMARY_ACTIVE.driver, emergencyContact: "11999990000", emergencyContactSecond: "" },
  };

  it("editing a contract sends the contacts it shows and the ones it loaded (the backend applies only real edits)", async () => {
    mockedApi.put.mockResolvedValueOnce({ data: WITH_CONTACTS });
    renderDriverAdd(WITH_CONTACTS);

    await clickSave();

    await waitFor(() => expect(mockedApi.put).toHaveBeenCalledTimes(1));
    const body = mockedApi.put.mock.calls[0][1] as any;
    expect(body.driver).toMatchObject({
      emergencyContact: "11999990000",
      emergencyContactSecond: "",
      loadedEmergencyContacts: ["11999990000", ""],
    });
  });

  it("the refusal of an edit over contacts changed meanwhile is explained", async () => {
    mockedApi.put.mockRejectedValueOnce({ response: { status: 400, data: { errorKey: "driveremergencycontactschanged" } } });
    renderDriverAdd(WITH_CONTACTS);

    await clickSave();

    await waitFor(() =>
      expect(mockAlerts.showErrorAlert).toHaveBeenCalledWith(expect.stringMatching(/alterados enquanto este formulário estava aberto/))
    );
  });
});
