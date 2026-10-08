import { act, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { IonApp } from "@ionic/react";
import DriverVehicleHistory, { matchesVehicle } from "./DriverVehicleHistory";
import { DriverRow } from "./Drivers";
import api from "../../services/axios/axios";

jest.mock("../../services/axios/axios");
const mockedApi = api as jest.Mocked<typeof api>;

const HISTORY = [
  { driverCarId: 5, carId: 3, carPlate: "KWD3C33", carModel: "Kwid", assignmentType: "RESERVE", startDate: "2026-10-08", endDate: "2026-10-15", status: "CONCLUDED" },
  { driverCarId: 4, carId: 2, carPlate: "HBV2B22", carModel: "HB20", assignmentType: "PERMANENT", startDate: "2026-08-01", endDate: null, status: "ACTIVE" },
  { driverCarId: 1, carId: 4, carPlate: "ARG4D44", carModel: "Argo", assignmentType: "PERMANENT", startDate: "2026-01-01", endDate: "2026-06-30", status: "CONCLUDED" },
];

const renderHistory = async (data: any[] = HISTORY) => {
  mockedApi.get.mockResolvedValue({ data });
  const onClose = jest.fn();
  render(
    <IonApp>
      <DriverVehicleHistory driverId={1} driverName="Maria Souza" onClose={onClose} />
    </IonApp>
  );
  await waitFor(() => expect(screen.queryByText("Carregando histórico…")).not.toBeInTheDocument());
  return onClose;
};
const plates = () => screen.queryAllByTestId("driver-vehicle-history-item").map((item) => item.querySelector("strong")?.textContent);
const tab = (name: RegExp) => screen.getByRole("tab", { name });
const search = async (value: string) => {
  await act(async () => {
    fireEvent(document.querySelector(".driver-vehicle-history__search")!, new CustomEvent("ionChange", { detail: { value }, bubbles: true }));
  });
};

describe("Histórico de veículos do motorista", () => {
  beforeAll(() => {
    (global as any).IntersectionObserver = class {
      observe() {}
      unobserve() {}
      disconnect() {}
    };
  });
  beforeEach(() => jest.resetAllMocks());

  it("reads the history of the driver (only a GET) and opens on Carro Principal, most recent first", async () => {
    await renderHistory();

    expect(mockedApi.get).toHaveBeenCalledWith("/api/driver-cars/driver/1");
    expect(mockedApi.post).not.toHaveBeenCalled();
    expect(mockedApi.put).not.toHaveBeenCalled();
    expect(tab(/Carro Principal \(2\)/)).toHaveAttribute("aria-selected", "true");
    expect(plates()).toEqual(["HBV2B22", "ARG4D44"]);
    expect(screen.getByText("Desde 01/08/2026")).toBeInTheDocument();
    expect(screen.getByText("01/01/2026 – 30/06/2026")).toBeInTheDocument();
    expect(screen.getByText("Ativo")).toBeInTheDocument();
    expect(screen.getByText("Finalizado")).toBeInTheDocument();
  });

  it("the Carro Reserva tab lists the reserve contracts", async () => {
    await renderHistory();

    fireEvent.click(tab(/Carro Reserva \(1\)/));

    expect(tab(/Carro Reserva/)).toHaveAttribute("aria-selected", "true");
    expect(plates()).toEqual(["KWD3C33"]);
    expect(screen.getByText("08/10/2026 – 15/10/2026")).toBeInTheDocument();
  });

  it("searches by plate (with or without the dash) or model, in the open tab", async () => {
    await renderHistory();

    await search("hbv-2b");
    expect(plates()).toEqual(["HBV2B22"]);
    await search("argo");
    expect(plates()).toEqual(["ARG4D44"]);
    await search("kwid");
    expect(plates()).toEqual([]);
    expect(screen.getByTestId("driver-vehicle-history-empty")).toHaveTextContent('Nenhum veículo encontrado para "kwid".');
    fireEvent.click(tab(/Carro Reserva/));
    expect(plates()).toEqual(["KWD3C33"]);
  });

  it("each tab has its empty state", async () => {
    await renderHistory([HISTORY[1]]);
    fireEvent.click(tab(/Carro Reserva \(0\)/));
    expect(screen.getByTestId("driver-vehicle-history-empty")).toHaveTextContent("Nenhum vínculo como carro reserva.");

    await renderHistory([]);
    expect(screen.getAllByTestId("driver-vehicle-history-empty").pop()).toHaveTextContent("Nenhum vínculo como carro principal.");
  });

  it("a load failure is explained; Fechar closes", async () => {
    mockedApi.get.mockRejectedValue(new Error("offline"));
    const onClose = jest.fn();
    render(
      <IonApp>
        <DriverVehicleHistory driverId={1} onClose={onClose} />
      </IonApp>
    );
    expect(await screen.findByRole("alert")).toHaveTextContent("Não foi possível carregar o histórico de veículos.");
    fireEvent.click(screen.getByText("Fechar"));
    expect(onClose).toHaveBeenCalled();
  });

  it("matchesVehicle ignores case, accents and punctuation", () => {
    expect(matchesVehicle(HISTORY[1] as any, "hb20")).toBe(true);
    expect(matchesVehicle(HISTORY[1] as any, "HBV 2B22")).toBe(true);
    expect(matchesVehicle(HISTORY[1] as any, "onix")).toBe(false);
    expect(matchesVehicle(HISTORY[1] as any, "  ")).toBe(true);
  });
});

describe("Motoristas do carro - ação Histórico de veículos", () => {
  beforeAll(() => {
    (global as any).IntersectionObserver = class {
      observe() {}
      unobserve() {}
      disconnect() {}
    };
  });

  it("the row action opens the history without opening the contract editor", () => {
    const onOpen = jest.fn();
    const onOpenHistory = jest.fn((event: any) => event.stopPropagation());
    const contract = { id: 4, startDate: "2026-08-01", status: "ACTIVE", driver: { id: 2, name: "Maria Souza" } };
    render(
      <IonApp>
        <DriverRow carDriver={contract as any} outstandingDebt={0} onOpen={onOpen} onOpenPendencies={jest.fn()} onOpenHistory={onOpenHistory} onChanged={jest.fn()} />
      </IonApp>
    );

    fireEvent.click(screen.getByText("Histórico de veículos"));

    expect(onOpenHistory).toHaveBeenCalledTimes(1);
    expect(onOpen).not.toHaveBeenCalled();
  });
});
