import { act, configure, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { IonApp } from "@ionic/react";
import { MemoryRouter } from "react-router";
import DriverPendencies from "./DriverPendencies";
import api from "../../services/axios/axios";
import { generateDocumentPdf } from "../Documents/documentPdf";
import {
  ERROR_OUTDATED_409,
  JOAO_PENDENCIES,
  LEGACY_WITHOUT_DEBTOR,
  PREVIEW_MULTI_CAR,
  STORED_CONFESSION,
} from "../../services/debtConfessionContract.fixtures";
import { apiError } from "../../services/driverAssignmentContract.fixtures";

jest.mock("../../services/axios/axios");
jest.mock("../Documents/documentPdf", () => ({ generateDocumentPdf: jest.fn() }));
const mockAlerts = { showErrorAlert: jest.fn(), showSuccessAlert: jest.fn(), showWarningAlert: jest.fn() };
jest.mock("../../services/hooks/useAlert", () => ({ useAlert: () => mockAlerts }));
jest.mock("@ionic/react", () => {
  const actual = jest.requireActual("@ionic/react");
  const react = jest.requireActual("react");
  return {
    ...actual,
    // Outside an IonRouterOutlet the page lifecycle never fires: run it on mount like a page entering.
    useIonViewWillEnter: (callback: () => void) => react.useEffect(() => callback(), []),
    useIonRouter: () => ({ push: jest.fn() }),
  };
});

// The Ionic DOM is too large to print in a query error: keep only the message.
configure({ getElementError: (message) => new Error(`${message}`.split("\n")[0]) });

const mockedApi = api as jest.Mocked<typeof api>;
const MARIA_DEBT = { ...JOAO_PENDENCIES.find((pendency) => pendency.status === "OPEN")!, id: 50, name: "Multa de Maria", debtorDriverId: 2 };

const renderPage = (list: any[]) => {
  mockedApi.get.mockImplementation((url: string) => {
    if (url === "/api/driver-cars/3") return Promise.resolve({ data: { id: 3, startDate: "2026-09-20", driver: { id: 1, name: "João Silva", cpf: "11111111111" } } });
    if (url === "/api/drivers/1/debts") return Promise.resolve({ data: list });
    if (url === "/api/documents/1") return Promise.resolve({ data: STORED_CONFESSION });
    return Promise.resolve({ data: {} });
  });
  render(
    <IonApp>
      <MemoryRouter>
        <DriverPendencies match={{ params: { id: "3" }, isExact: true, path: "", url: "" }} history={{} as any} location={{} as any} />
      </MemoryRouter>
    </IonApp>
  );
};

const checkboxOf = (name: string) =>
  screen.getByLabelText(`Selecionar ${name} para a Confissão de Dívida`) as HTMLElement & { disabled?: boolean };
const generateButton = () => screen.getByText(/^Gerar Confissão de Dívida/).closest("ion-button") as any;
const click = async (element: Element) => {
  await act(async () => {
    fireEvent.click(element);
  });
};
const pendencyWrites = () =>
  [...mockedApi.post.mock.calls, ...mockedApi.put.mock.calls, ...mockedApi.delete.mock.calls].filter((call) => `${call[0]}`.startsWith("/api/pendencies/") && !`${call[0]}`.includes("confissao-divida/preview"));

describe("Pendências → Confissão de Dívida", () => {
  beforeAll(() => {
    (global as any).IntersectionObserver = class {
      observe() {}
      unobserve() {}
      disconnect() {}
    };
  });

  beforeEach(() => jest.resetAllMocks());

  it("selecting open debts of the debtor enables the action with the count; paid debts cannot be selected", async () => {
    renderPage(JOAO_PENDENCIES);
    await screen.findByText("Combustível");

    expect(generateButton().disabled).toBe(true);
    expect(screen.queryByLabelText("Selecionar Combustível para a Confissão de Dívida")).not.toBeInTheDocument();

    await click(screen.getAllByLabelText("Selecionar Multa para a Confissão de Dívida")[0]);
    await click(checkboxOf("Danos/Avarias"));

    expect(screen.getByText("Gerar Confissão de Dívida (2)")).toBeInTheDocument();
    expect(generateButton().disabled).toBe(false);
    expect(mockedApi.post).not.toHaveBeenCalled();
  });

  it("another debtor's debt cannot join the selection", async () => {
    renderPage([...JOAO_PENDENCIES, MARIA_DEBT]);
    await screen.findByText("Multa de Maria");

    await click(checkboxOf("Danos/Avarias"));
    await click(checkboxOf("Multa de Maria"));

    expect(mockAlerts.showErrorAlert).toHaveBeenCalledWith("Selecione apenas pendências do mesmo motorista para gerar a Confissão de Dívida.");
    expect(screen.getByText("Gerar Confissão de Dívida (1)")).toBeInTheDocument();
  });

  it("a legacy debt without debtor is never selectable and says why", async () => {
    renderPage([LEGACY_WITHOUT_DEBTOR, ...JOAO_PENDENCIES]);
    await screen.findByText("Legado");

    expect(checkboxOf("Legado").disabled).toBe(true);
    expect(screen.getByText("Sem motorista devedor identificado: não pode entrar na Confissão de Dívida.")).toBeInTheDocument();
    await click(checkboxOf("Legado"));
    expect(generateButton().disabled).toBe(true);
  });

  it("preview from the backend, Cancelar writes nothing; Gerar PDF creates the document and leaves the debts untouched", async () => {
    renderPage(JOAO_PENDENCIES);
    await screen.findByText("Combustível");
    for (const box of screen.getAllByLabelText("Selecionar Multa para a Confissão de Dívida")) await click(box);
    await click(checkboxOf("Danos/Avarias"));

    mockedApi.post.mockResolvedValueOnce({ data: PREVIEW_MULTI_CAR });
    await click(generateButton());
    expect(mockedApi.post).toHaveBeenLastCalledWith("/api/pendencies/confissao-divida/preview", { pendencyIds: expect.arrayContaining([1, 2, 3]) });
    expect(await screen.findByText("Total da Confissão")).toBeInTheDocument();

    // Cancelar: closes, nothing else.
    await click(screen.getAllByText("Cancelar").find((node) => node.closest(".debt-confession-preview__actions"))!);
    await waitFor(() => expect(screen.queryByText("Total da Confissão")).not.toBeInTheDocument());
    expect(mockedApi.post).toHaveBeenCalledTimes(1);

    // Again, then Gerar PDF.
    mockedApi.post
      .mockResolvedValueOnce({ data: PREVIEW_MULTI_CAR })
      .mockResolvedValueOnce({ data: { id: 1, status: "DRAFT" } })
      .mockResolvedValueOnce({ data: { id: 1, status: "FINAL" } })
      .mockResolvedValueOnce({ data: { id: 1, status: "FINAL" } });
    await click(generateButton());
    await screen.findByText("Total da Confissão");
    await click(screen.getByText("Gerar PDF"));

    await waitFor(() =>
      expect(mockAlerts.showSuccessAlert).toHaveBeenCalledWith("Confissão de Dívida gerada. O PDF foi baixado e o documento está em Documentos.")
    );
    expect(mockedApi.post.mock.calls.slice(1).map((call) => call[0])).toEqual([
      "/api/pendencies/confissao-divida/preview",
      "/api/documents",
      "/api/documents/1/finalize",
      "/api/documents/1/generate-pdf",
    ]);
    expect(generateDocumentPdf).toHaveBeenCalledWith(STORED_CONFESSION);
    expect(pendencyWrites()).toEqual([]);
    await waitFor(() => expect(screen.queryByText("Total da Confissão")).not.toBeInTheDocument());
  });

  it("debts changed since the preview: generation refused with the update message", async () => {
    renderPage(JOAO_PENDENCIES);
    await screen.findByText("Combustível");
    await click(checkboxOf("Danos/Avarias"));
    mockedApi.post.mockResolvedValueOnce({ data: PREVIEW_MULTI_CAR }).mockRejectedValueOnce(apiError(409, ERROR_OUTDATED_409));

    await click(generateButton());
    await screen.findByText("Total da Confissão");
    await click(screen.getByText("Gerar PDF"));

    await waitFor(() =>
      expect(mockAlerts.showErrorAlert).toHaveBeenCalledWith("As pendências foram alteradas desde a prévia. Atualize os dados antes de gerar o documento.")
    );
    expect(generateDocumentPdf).not.toHaveBeenCalled();
    expect(mockAlerts.showSuccessAlert).not.toHaveBeenCalled();
  });
});
