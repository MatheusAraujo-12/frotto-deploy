import React from "react";
import { act, fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import DocumentsPage from "./DocumentsPage";
import documentService from "../../services/documentService";
import debtItemTypeService from "../../services/debtItemTypeService";
import api from "../../services/axios/axios";
import { generateDocumentPdf } from "./documentPdf";

const mockToast = jest.fn();

/* DOM stand-ins for the Ionic components (same pattern as DocumentsAutocomplete.test), keeping data-field. */
jest.mock("@ionic/react", () => {
  const React = require("react");
  const passthrough = (tag: string) =>
    React.forwardRef(({ children, className, onClick }: any, ref: any) =>
      React.createElement(tag, { ref, className, onClick }, children)
    );
  // ionInput only when the user types; ionChange whenever the value changes (as @ionic/core 6.7.5).
  const IonInput = ({ value, onIonChange, onIonInput, type, "data-field": field }: any) => {
    const [shown, setShown] = React.useState(value ?? "");
    const internal = React.useRef(value ?? "");
    React.useEffect(() => {
      const next = value ?? "";
      if (next !== internal.current) {
        internal.current = next;
        setShown(next);
        onIonChange?.({ detail: { value: next } });
      }
      // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [value]);
    return React.createElement("input", {
      type: type === "date" || type === "time" ? "text" : type,
      "data-field": field,
      value: shown,
      onChange: (event: any) => {
        const next = event.target.value;
        internal.current = next;
        setShown(next);
        onIonInput?.({ target: event.target, detail: event.nativeEvent });
        onIonChange?.({ detail: { value: next } });
      },
    });
  };
  const IonTextarea = ({ value, onIonChange }: any) =>
    React.createElement("textarea", {
      value: value ?? "",
      onChange: (event: any) => onIonChange?.({ detail: { value: event.target.value } }),
    });
  const IonSelect = ({ value, onIonChange, children, className }: any) =>
    React.createElement(
      "select",
      { value: value ?? "", className, onChange: (event: any) => onIonChange?.({ detail: { value: event.target.value } }) },
      React.createElement("option", { value: "" }, ""),
      children
    );
  return {
    IonButton: ({ children, onClick, disabled, className }: any) =>
      React.createElement("button", { onClick, disabled, className, type: "button" }, children),
    IonModal: ({ children, isOpen, className }: any) =>
      isOpen ? React.createElement("section", { role: "dialog", className }, children) : null,
    IonInput,
    IonTextarea,
    IonSelect,
    IonSelectOption: ({ value, children }: any) => React.createElement("option", { value }, children),
    IonCheckbox: ({ checked, onIonChange }: any) =>
      React.createElement("input", {
        type: "checkbox",
        checked: Boolean(checked),
        onChange: (event: any) => onIonChange?.({ detail: { checked: event.target.checked } }),
      }),
    IonIcon: () => null,
    IonProgressBar: () => null,
    IonMenuButton: () => null,
    useIonToast: () => [mockToast, jest.fn()],
    IonPage: passthrough("div"),
    IonHeader: passthrough("header"),
    IonFooter: passthrough("footer"),
    IonToolbar: passthrough("div"),
    IonTitle: passthrough("h1"),
    IonButtons: passthrough("div"),
    IonContent: passthrough("main"),
    IonCard: passthrough("div"),
    IonCardContent: passthrough("div"),
    IonCardHeader: passthrough("div"),
    IonCardTitle: passthrough("div"),
    IonCardSubtitle: passthrough("div"),
    IonItem: passthrough("div"),
    IonLabel: passthrough("label"),
    IonList: passthrough("div"),
    IonText: passthrough("span"),
    IonSearchbar: () => null,
    IonNote: passthrough("span"),
  };
});

jest.mock("../../services/documentService");
jest.mock("../../services/debtItemTypeService");
jest.mock("./documentPdf", () => ({ generateDocumentPdf: jest.fn() }));
jest.mock("../../services/axios/axios", () => ({ __esModule: true, default: { get: jest.fn() } }));

const mockedDocuments = documentService as jest.Mocked<typeof documentService>;
const mockedDebtTypes = debtItemTypeService as jest.Mocked<typeof debtItemTypeService>;
const mockedApi = api as jest.Mocked<typeof api>;

const DRIVER = { id: 1, name: "João Motorista", cpf: "39053344705", active: true };
const CAR = { id: 7, plate: "ABC1D23", model: "Onix", active: true };
const OPEN_CONTRACT = { id: 5, driver: { id: 1, name: "João Motorista" }, status: "ACTIVE", startDate: "2026-09-01", carId: 7 };

/** The wizard dialog (the PERMANENT/RESERVE choice opens a second one). */
const wizard = () => screen.getAllByRole("dialog")[0];
const labelled = (label: string) => {
  const labels = within(wizard()).getAllByText((text, node) => node?.tagName === "LABEL" && text.trim().startsWith(label));
  return labels[0].parentElement!;
};
const input = (label: string) => labelled(label).querySelector("input, textarea") as HTMLInputElement;
const select = (label: string) => labelled(label).querySelector("select") as HTMLSelectElement;
const field = (name: string) => wizard().querySelector(`[data-field="${name}"]`) as HTMLInputElement;
const change = (element: Element, value: string) => fireEvent.change(element, { target: { value } });
const click = (name: string | RegExp) => fireEvent.click(within(wizard()).getByRole("button", { name }));
const toastMessages = () => mockToast.mock.calls.map((call) => call[0]?.message);

let contracts: any[] = [];
let stored: any;

function apiError(status: number, errorKey: string, extra: Record<string, any> = {}) {
  return Object.assign(new Error(errorKey), { response: { status, data: { errorKey, message: `error.${errorKey}`, ...extra } } });
}

async function openChecklist() {
  render(<DocumentsPage />);
  fireEvent.click(await screen.findByRole("button", { name: /Novo Documento/ }));
  change(input("Motorista"), "Jo");
  fireEvent.click(await within(wizard()).findByText("João Motorista (39053344705)"));
  change(input("Carro"), "ABC");
  fireEvent.click(await within(wizard()).findByText("ABC1D23 - Onix"));
  click("Próximo");
  change(wizard().querySelector("select") as HTMLSelectElement, "ENTREGA_DEVOLUCAO_CHECKLIST");
  click("Próximo");
  await within(wizard()).findByText("Passo 3 de 3");
}

/** Part 2 of the structured form (Continuar validates part 1), with the two emergency contacts filled there. */
async function conference() {
  const next = within(wizard()).queryByRole("button", { name: "Continuar para conferência" });
  if (next) {
    fireEvent.click(next);
  }
  await act(async () => undefined);
  if (within(wizard()).queryAllByText((text, node) => node?.tagName === "LABEL" && text.trim().startsWith("Contato 1 - Nome")).length && !input("Contato 1 - Nome").value) {
    change(input("Contato 1 - Nome"), "Ana Souza");
    change(input("Contato 1 - Telefone"), "11999990000");
    change(input("Contato 2 - Nome"), "Carlos Lima");
    change(input("Contato 2 - Telefone"), "11988880000");
  }
}

function fill(type: "ENTREGA" | "DEVOLUCAO", { km = "15000", fuel = "HALF", date = "2026-10-07" } = {}) {
  change(select("Tipo"), type);
  change(field("dataVistoria"), date);
  change(field("horaVistoria"), "08:30");
  change(field("km"), km);
  change(select("Combustível"), fuel);
  change(select("Limpeza interna"), "Boa");
  change(select("Limpeza externa"), "Aceitavel");
}

beforeEach(() => {
  jest.clearAllMocks();
  contracts = [];
  stored = null;
  jest.spyOn(window, "confirm").mockReturnValue(true);
  mockedApi.get.mockImplementation(async (url: string) => ({ data: `${url}`.startsWith("/api/driver-cars/car/") ? contracts : [] }) as any);
  mockedDocuments.listDocuments.mockResolvedValue([] as any);
  mockedDocuments.searchDrivers.mockResolvedValue([DRIVER] as any);
  mockedDocuments.searchCars.mockResolvedValue([CAR] as any);
  mockedDocuments.listOpenPendenciesByDriver.mockResolvedValue([] as any);
  mockedDebtTypes.listDebtItemTypes.mockResolvedValue([] as any);
  mockedDocuments.uploadDocumentAttachments.mockResolvedValue(undefined as any);
  mockedDocuments.createDocument.mockImplementation(async (request: any) => {
    stored = { id: 99, status: "DRAFT", attachments: [], ...request };
    return stored;
  });
  mockedDocuments.updateDocument.mockImplementation(async (_id: number, request: any) => {
    stored = { ...stored, ...request };
    return stored;
  });
  mockedDocuments.getDocument.mockImplementation(async () => ({ ...stored }));
  mockedDocuments.finalizeDocument.mockImplementation(async () => {
    stored = { ...stored, status: "FINAL", driverCarId: 5, inspectionId: 70 };
    return stored;
  });
  mockedDocuments.generateDocumentPdf.mockImplementation(async () => stored);
});

afterEach(() => {
  (window.confirm as jest.Mock).mockRestore?.();
});

describe("Checklist de Entrega/Devolução - campos estruturados", () => {
  it("the type is required: nothing is saved without Entrega / Devolução", async () => {
    await openChecklist();
    click("Salvar rascunho");

    await waitFor(() => expect(toastMessages()).toContain("Informe se o checklist é de Entrega ou de Devolução."));
    expect(mockedDocuments.createDocument).not.toHaveBeenCalled();
  });

  it("saves the structured date, numeric km and fuel code with the type (Entrega sends no contract)", async () => {
    await openChecklist();
    fill("ENTREGA");
    click("Salvar rascunho");

    await waitFor(() => expect(mockedDocuments.createDocument).toHaveBeenCalledTimes(1));
    const request = mockedDocuments.createDocument.mock.calls[0][0] as any;
    expect(request).toMatchObject({ type: "ENTREGA_DEVOLUCAO_CHECKLIST", checklistType: "ENTREGA" });
    expect(request).not.toHaveProperty("driverCarId");
    expect(request.payload).toMatchObject({ dataVistoria: "2026-10-07", horaVistoria: "08:30", km: 15000, combustivel: "HALF", tipo: "ENTREGA" });
  });

  it("the km keeps only digits and separators, and an ambiguous km blocks the finalization", async () => {
    await openChecklist();
    fill("ENTREGA", { km: "15.000 km" });
    expect(field("km").value).toBe("15.000");

    click("Continuar para conferência"); // part 1 is validated before the conference

    await waitFor(() => expect(toastMessages().join(" ")).toMatch(/Informe o KM do veículo/));
    expect(mockedDocuments.finalizeDocument).not.toHaveBeenCalled();
  });

  it("date and fuel are required to finalize", async () => {
    await openChecklist();
    change(select("Tipo"), "ENTREGA");
    change(field("km"), "100");
    click("Continuar para conferência"); // part 1 is validated before the conference

    await waitFor(() => expect(toastMessages().join(" ")).toMatch(/Informe a data da vistoria\. Selecione o nível de combustível\./));
    expect(mockedDocuments.finalizeDocument).not.toHaveBeenCalled();
  });
});

describe("Checklist - Finalizar e Gerar PDF", () => {
  it("in draft there is Finalizar and no Gerar PDF; finalizing explains the effects, then only the PDF remains", async () => {
    await openChecklist();
    fill("ENTREGA");
    expect(within(wizard()).queryByRole("button", { name: "Gerar PDF" })).not.toBeInTheDocument();
    expect(within(wizard()).getByTestId("checklist-contract-info").textContent).toMatch(/o motorista será vinculado a este veículo/);

    await conference();
    click("Finalizar entrega");

    await within(wizard()).findByTestId("checklist-final");
    expect(window.confirm).toHaveBeenCalledWith(expect.stringMatching(/vincula o motorista a este veículo.*registra a vistoria como inspeção/));
    expect(mockedDocuments.finalizeDocument).toHaveBeenCalledWith(99, undefined);
    expect(within(wizard()).getByText("Checklist de Entrega finalizado.")).toBeInTheDocument();
    expect(within(wizard()).getByText(/Vistoria: 07\/10\/2026 às 08:30 · KM: 15000 · Combustível: 1\/2/)).toBeInTheDocument();
    expect(within(wizard()).queryByRole("button", { name: /Finalizar/ })).not.toBeInTheDocument();
    expect(within(wizard()).queryByRole("button", { name: "Salvar rascunho" })).not.toBeInTheDocument();
    expect(within(wizard()).queryByRole("button", { name: /Excluir/ })).not.toBeInTheDocument();

    // The PDF of the FINAL document is generated right after the confirmed finalization...
    await waitFor(() => expect(mockedDocuments.generateDocumentPdf).toHaveBeenCalledWith(99));
    expect(generateDocumentPdf).toHaveBeenCalledTimes(1);
    expect(mockedDocuments.generateDocumentPdf.mock.invocationCallOrder[0]).toBeGreaterThan(
      mockedDocuments.finalizeDocument.mock.invocationCallOrder[0]
    );
    // ...and Gerar PDF stays available for another copy.
    click("Gerar PDF");
    await waitFor(() => expect(generateDocumentPdf).toHaveBeenCalledTimes(2));
    expect(mockedDocuments.finalizeDocument).toHaveBeenCalledTimes(1); // the PDF never finalizes again
  });

  it("a PDF failure after the finalization is explained and retried with Gerar PDF, never finalizing again", async () => {
    (generateDocumentPdf as jest.Mock).mockRejectedValueOnce(new Error("popup blocked"));
    await openChecklist();
    fill("ENTREGA");
    await conference();
    click("Finalizar entrega");

    expect(await within(wizard()).findByTestId("checklist-pdf-failed")).toBeInTheDocument();
    expect(toastMessages()).toContain("Checklist finalizado, mas o PDF não pôde ser gerado agora. Use Gerar PDF para tentar novamente.");
    expect(within(wizard()).getByTestId("checklist-final")).toBeInTheDocument();

    click("Gerar PDF");

    await waitFor(() => expect(within(wizard()).queryByTestId("checklist-pdf-failed")).not.toBeInTheDocument());
    expect(generateDocumentPdf).toHaveBeenCalledTimes(2);
    expect(mockedDocuments.generateDocumentPdf).toHaveBeenCalledTimes(1);
    expect(mockedDocuments.finalizeDocument).toHaveBeenCalledTimes(1);
    expect(mockedDocuments.createDocument).toHaveBeenCalledTimes(1);
  });

  it("cancelling the confirmation sends nothing", async () => {
    (window.confirm as jest.Mock).mockReturnValue(false);
    await openChecklist();
    fill("ENTREGA");
    await conference();
    click("Finalizar entrega");

    await act(async () => undefined);
    expect(mockedDocuments.createDocument).not.toHaveBeenCalled();
    expect(mockedDocuments.finalizeDocument).not.toHaveBeenCalled();
  });

  it("another open contract of the driver: the PERMANENT / RESERVE choice of the contract screen, then finalize with it", async () => {
    mockedDocuments.finalizeDocument.mockRejectedValueOnce(
      apiError(409, "driverassignmentrequired", { conflictingDriverCarId: 3, conflictingCarPlate: "XYZ9Z99" })
    );
    await openChecklist();
    fill("ENTREGA");
    await conference();
    click("Finalizar entrega");

    expect(await screen.findByText("Motorista já vinculado a outro veículo")).toBeInTheDocument();
    expect(screen.getByText(/possui um vínculo com o veículo XYZ9Z99/)).toBeInTheDocument();
    fireEvent.click(screen.getByText("Carro reserva").closest("button")!);

    await within(wizard()).findByTestId("checklist-final");
    expect(mockedDocuments.finalizeDocument).toHaveBeenNthCalledWith(2, 99, "RESERVE");
    expect(mockedDocuments.createDocument).toHaveBeenCalledTimes(1);
  });

  it("cancelling the choice finalizes nothing", async () => {
    mockedDocuments.finalizeDocument.mockRejectedValueOnce(apiError(409, "driverassignmentrequired"));
    await openChecklist();
    fill("ENTREGA");
    await conference();
    click("Finalizar entrega");
    await screen.findByText("Motorista já vinculado a outro veículo");
    fireEvent.click(screen.getAllByRole("button", { name: "Cancelar" })[0]);

    await waitFor(() => expect(screen.queryByText("Motorista já vinculado a outro veículo")).not.toBeInTheDocument());
    expect(mockedDocuments.finalizeDocument).toHaveBeenCalledTimes(1);
    expect(within(wizard()).queryByTestId("checklist-final")).not.toBeInTheDocument();
  });

  it("a backend refusal is explained (e.g. a final checklist of the same type already exists for the contract)", async () => {
    mockedDocuments.finalizeDocument.mockRejectedValueOnce(apiError(400, "checklistalreadyfinalized"));
    await openChecklist();
    fill("ENTREGA");
    await conference();
    click("Finalizar entrega");

    await waitFor(() => expect(toastMessages()).toContain("Este vínculo já possui um checklist deste tipo finalizado."));
    expect(within(wizard()).queryByTestId("checklist-final")).not.toBeInTheDocument();
  });

  it("retry after a lost response: an already final document is shown, nothing is sent again", async () => {
    mockedDocuments.finalizeDocument.mockImplementationOnce(async () => {
      stored = { ...stored, status: "FINAL", driverCarId: 5 }; // committed on the server...
      throw new Error("Network Error"); // ...but the response was lost
    });
    await openChecklist();
    fill("ENTREGA");
    await conference();
    click("Finalizar entrega");
    await waitFor(() => expect(mockedDocuments.finalizeDocument).toHaveBeenCalledTimes(1));
    await waitFor(() => expect(within(wizard()).getByRole("button", { name: "Finalizar entrega" })).not.toBeDisabled());

    await conference();
    click("Finalizar entrega");

    await within(wizard()).findByTestId("checklist-final");
    expect(within(wizard()).getByTestId("checklist-final-feedback").textContent).toBe("Este checklist já estava finalizado.");
    expect(mockedDocuments.finalizeDocument).toHaveBeenCalledTimes(1);
    expect(mockedDocuments.updateDocument).not.toHaveBeenCalled();
  });
});

describe("Checklist - vínculo (DriverCar)", () => {
  it("Entrega with the right contract already open says it will be used", async () => {
    contracts = [OPEN_CONTRACT];
    await openChecklist();
    fill("ENTREGA");

    expect((await within(wizard()).findByTestId("checklist-contract-info")).textContent).toBe(
      "Vínculo desde 01/09/2026: a entrega será registrada neste vínculo."
    );
  });

  it("Entrega on a car operated by another driver is explained and blocked before sending", async () => {
    contracts = [{ id: 8, driver: { id: 2, name: "Maria Outra" }, status: "ACTIVE", startDate: "2026-08-01" }];
    await openChecklist();
    fill("ENTREGA");

    expect((await within(wizard()).findByTestId("checklist-contract-blocked")).textContent).toMatch(
      /O veículo ABC1D23 está vinculado a Maria Outra\. Registre a devolução ou encerre esse vínculo antes da entrega\./
    );
    click("Continuar para conferência"); // part 1 is validated before the conference
    await waitFor(() => expect(toastMessages().join(" ")).toMatch(/vinculado a Maria Outra/));
    expect(mockedDocuments.finalizeDocument).not.toHaveBeenCalled();
  });

  it("Devolução is born on the open contract of the driver and car, and says what finalizing does", async () => {
    contracts = [OPEN_CONTRACT, { id: 4, driver: { id: 1 }, status: "CONCLUDED", startDate: "2025-01-01" }];
    await openChecklist();
    fill("DEVOLUCAO", { date: "2026-11-01" });

    expect((await within(wizard()).findByTestId("checklist-contract-info")).textContent).toMatch(
      /Vínculo desde 01\/09\/2026: ao finalizar, este vínculo será encerrado/
    );
    await conference();
    click("Finalizar devolução");

    await within(wizard()).findByTestId("checklist-final");
    expect(mockedDocuments.createDocument.mock.calls[0][0]).toMatchObject({ checklistType: "DEVOLUCAO", driverCarId: 5 });
    expect(window.confirm).toHaveBeenCalledWith(expect.stringMatching(/encerra o vínculo do motorista/));
  });

  it("Devolução of a reserve explains the return and shows the outcome of the return", async () => {
    contracts = [{ ...OPEN_CONTRACT, reserve: true, primaryDriverCarId: 3 }];
    mockedDocuments.finalizeDocument.mockImplementationOnce(async () => {
      stored = { ...stored, status: "FINAL" };
      return { ...stored, reserveReturn: { outcome: "PRIMARY_CAR_OCCUPIED", primaryCarPlate: "PRI1M11", occupyingDriverName: "Rui" } };
    });
    await openChecklist();
    fill("DEVOLUCAO");
    expect((await within(wizard()).findByTestId("checklist-contract-info")).textContent).toMatch(/o carro reserva é devolvido/);

    await conference();
    click("Finalizar devolução");

    expect((await within(wizard()).findByTestId("checklist-final-feedback")).textContent).toMatch(
      /O veículo principal PRI1M11 está atualmente vinculado a Rui\. O vínculo principal de João Motorista permanece suspenso\./
    );
  });

  it("Devolução without an open contract of this driver on this car is explained and nothing is saved", async () => {
    contracts = [{ id: 4, driver: { id: 1 }, status: "CONCLUDED", startDate: "2025-01-01" }];
    await openChecklist();
    fill("DEVOLUCAO");

    expect((await within(wizard()).findByTestId("checklist-contract-blocked")).textContent).toMatch(
      /não possui vínculo ativo com o veículo ABC1D23\. A devolução só pode ser registrada sobre um vínculo existente\./
    );
    click("Salvar rascunho");
    await waitFor(() => expect(toastMessages().join(" ")).toMatch(/não possui vínculo ativo/));
    expect(mockedDocuments.createDocument).not.toHaveBeenCalled();
  });

  it("a suspended contract (driver on a reserve car) is explained, never delivered or returned", async () => {
    contracts = [{ ...OPEN_CONTRACT, status: "SUSPENDED", suspended: true }];
    await openChecklist();
    fill("ENTREGA");

    expect((await within(wizard()).findByTestId("checklist-contract-blocked")).textContent).toMatch(/está suspenso/);
  });
});

describe("Checklist - histórico", () => {
  it("a finalized structured checklist cannot be deleted from the list; an old one keeps its actions", async () => {
    mockedDocuments.listDocuments.mockResolvedValue([
      { id: 1, type: "ENTREGA_DEVOLUCAO_CHECKLIST", status: "FINAL", driverId: 1, driverName: "Novo", checklistType: "ENTREGA" },
      { id: 2, type: "ENTREGA_DEVOLUCAO_CHECKLIST", status: "FINAL", driverId: 1, driverName: "Antigo", checklistType: null },
    ] as any);
    render(<DocumentsPage />);

    await screen.findByText("Novo");
    expect(screen.getAllByRole("button", { name: "Abrir PDF" })).toHaveLength(2);
    expect(screen.getAllByRole("button", { name: "Excluir" })).toHaveLength(1);
  });

  const openLegacyDraft = async () => {
    mockedDocuments.listDocuments.mockResolvedValue([
      { id: 3, type: "ENTREGA_DEVOLUCAO_CHECKLIST", status: "DRAFT", driverId: 1, driverName: "Antigo", carId: 7, checklistType: null },
    ] as any);
    stored = {
      id: 3,
      type: "ENTREGA_DEVOLUCAO_CHECKLIST",
      status: "DRAFT",
      driverId: 1,
      carId: 7,
      carPlate: "ABC1D23",
      checklistType: null,
      payload: { tipo: "ENTREGA", dataHora: "ontem 10h", km: "12.000", combustivel: "meio tanque" },
      attachments: [],
    };
    render(<DocumentsPage />);
    fireEvent.click(await screen.findByRole("button", { name: "Editar" }));
    await screen.findByTestId("checklist-legacy-draft");
  };

  it("an old checklist draft is kept as it was: free-text fields, saved without becoming structured", async () => {
    await openLegacyDraft();

    expect(input("Data/Hora").value).toBe("ontem 10h");
    expect(input("KM").value).toBe("12.000");
    expect(input("Combustível").value).toBe("meio tanque");
    expect(within(wizard()).queryByRole("button", { name: /Finalizar/ })).not.toBeInTheDocument();
    change(input("Contato 1 - Nome"), "Ana Souza");
    change(input("Contato 1 - Telefone"), "11999990000");
    change(input("Contato 2 - Nome"), "Carlos Lima");
    change(input("Contato 2 - Telefone"), "11988880000");
    click("Salvar rascunho");

    await waitFor(() => expect(mockedDocuments.updateDocument).toHaveBeenCalled());
    const request = mockedDocuments.updateDocument.mock.calls[0][1] as any;
    expect(request).not.toHaveProperty("checklistType");
    expect(request.payload).toMatchObject({ dataHora: "ontem 10h", km: "12.000", combustivel: "meio tanque" });
  });

  it("an old checklist draft keeps its old Gerar PDF (finalized without contract or inspection)", async () => {
    await openLegacyDraft();
    change(input("Contato 1 - Nome"), "Ana Souza");
    change(input("Contato 1 - Telefone"), "11999990000");
    change(input("Contato 2 - Nome"), "Carlos Lima");
    change(input("Contato 2 - Telefone"), "11988880000");
    click("Gerar PDF");

    await waitFor(() => expect(mockedDocuments.finalizeDocument).toHaveBeenCalledWith(3));
    await waitFor(() => expect(generateDocumentPdf).toHaveBeenCalled());
    expect(mockedDocuments.updateDocument.mock.calls[0][1]).not.toHaveProperty("checklistType");
  });

  it("converting an old draft is explicit and only then the structured fields and the type are sent", async () => {
    await openLegacyDraft();
    click("Converter em checklist estruturado");

    expect(within(wizard()).queryByTestId("checklist-legacy-draft")).not.toBeInTheDocument();
    expect((await screen.findByTestId("checklist-legacy-datetime")).textContent).toMatch(/ontem 10h/);
    expect(select("Tipo").value).toBe("ENTREGA");
    change(field("dataVistoria"), "2026-10-07");
    change(field("km"), "12000");
    change(select("Combustível"), "HALF");
    change(select("Limpeza interna"), "Ótima");
    change(select("Limpeza externa"), "Boa");
    fireEvent.click(within(wizard()).getByRole("tab", { name: "2. Conferência e finalização" }));
    change(input("Contato 1 - Nome"), "Ana Souza");
    change(input("Contato 1 - Telefone"), "11999990000");
    change(input("Contato 2 - Nome"), "Carlos Lima");
    change(input("Contato 2 - Telefone"), "11988880000");
    click("Salvar rascunho");

    await waitFor(() => expect(mockedDocuments.updateDocument).toHaveBeenCalled());
    expect(mockedDocuments.updateDocument.mock.calls[0][1]).toMatchObject({
      checklistType: "ENTREGA",
      payload: expect.objectContaining({ dataVistoria: "2026-10-07", km: 12000, combustivel: "HALF" }),
    });
  });
});

describe("Checklist aberto a partir de Inspeções", () => {
  const CAR_ROW = { id: 7, plate: "ABC1D23", model: "Onix", active: true };
  /** The page under a router-like harness: replace really changes the location (and re-renders), as in the app. */
  const Harness: React.FC<{ search: string; history: { push: jest.Mock; replace: jest.Mock } }> = ({ search, history }) => {
    const [location, setLocation] = React.useState({ pathname: "/documents", search, hash: "", state: undefined });
    history.replace.mockImplementation((to: any) => setLocation({ pathname: to.pathname, search: to.search, hash: "", state: undefined }));
    return <DocumentsPage {...({ location, history } as any)} />;
  };
  const launch = async (search: string, contractsOfCar: any[] = []) => {
    mockedApi.get.mockImplementation(async (url: string) => {
      if (url === "/api/cars/7") return { data: { car: CAR_ROW } } as any; // the real shape of GET /cars/{id}
      if (url === "/api/driver-cars/car/7") return { data: contractsOfCar } as any;
      return { data: [] } as any;
    });
    const props = { history: { push: jest.fn(), replace: jest.fn() } };
    render(<Harness search={search} history={props.history} />);
    return props;
  };

  it("Devolução opens the wizard on the form of that car, with the driver of its active contract, and returns on close", async () => {
    const props = await launch("?checklist=DEVOLUCAO&carId=7&from=%2Fmenu%2Fcarros%2F7", [OPEN_CONTRACT]);

    await within(await screen.findByRole("dialog")).findByText("Passo 3 de 3");
    expect(props.history.replace).toHaveBeenCalledWith({ pathname: "/documents", search: "" });
    expect(select("Tipo").value).toBe("DEVOLUCAO");
    expect((await within(wizard()).findByTestId("checklist-contract-info")).textContent).toMatch(/Vínculo desde 01\/09\/2026/);

    fireEvent.click(wizard().querySelector("button.app-cancel-btn") as HTMLElement); // Fechar (icon button)
    expect(props.history.push).toHaveBeenCalledWith("/menu/carros/7");
  });

  it("Entrega opens on the first step with the car already chosen (the driver is picked there)", async () => {
    await launch("?checklist=ENTREGA&carId=7&from=%2Fmenu%2Fcarros%2F7");

    await within(await screen.findByRole("dialog")).findByText("Passo 1 de 3");
    expect(input("Carro").value).toBe("ABC1D23");
    expect(input("Motorista").value).toBe(""); // an Entrega never assumes the driver
  });

  it("an invalid or external launch request is ignored", async () => {
    await launch("?checklist=OUTRO&carId=7");
    await act(async () => undefined);
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
    expect(mockedApi.get).not.toHaveBeenCalledWith("/api/cars/7");
  });
});

describe("Checklist - condições do veículo e conferência", () => {
  const LAST_INSPECTION = { leftFront: { model: "MICHELIN", integrity: "90-100%" }, spare: { model: "PIRELLI", integrity: "50-70%" } };
  const DAMAGE = { id: 11, part: "Porta dianteira", date: "2026-09-01", responsible: "Oficina" };
  const routeApi = (extra: Record<string, any> = {}) =>
    mockedApi.get.mockImplementation(async (url: string) => {
      if (url in extra) return { data: extra[url] } as any;
      if (url.startsWith("/api/driver-cars/car/")) return { data: contracts } as any;
      return { data: [] } as any;
    });
  const tireSelect = (position: string, field: "Marca" | "Integridade") => select(`${position} - ${field}`);
  /** The brand picker of the inspection form (opens a list: its brands and the custom ones saved there). */
  const brandItem = (position: string) => labelled(`${position} - Marca`);
  const brandShown = (position: string) => `${brandItem(position).querySelector("button")?.textContent || ""}`.trim();
  const chooseBrand = async (position: string, brand: string) => {
    fireEvent.click(brandItem(position));
    const options = await within(wizard()).findAllByText(brand);
    fireEvent.click(options[options.length - 1]);
  };
  const savedPayload = () => (mockedDocuments.createDocument.mock.calls[0][0] as any).payload;

  it("part 1 has the cleaning (required) and the five tire positions; Finalizar only in part 2", async () => {
    routeApi();
    await openChecklist();
    change(select("Tipo"), "ENTREGA");
    change(field("dataVistoria"), "2026-10-07");
    change(field("km"), "100");
    change(select("Combustível"), "FULL");

    expect(within(wizard()).getAllByTestId("checklist-tire-position")).toHaveLength(5);
    expect(within(wizard()).queryByRole("button", { name: /^Finalizar/ })).not.toBeInTheDocument();
    click("Continuar para conferência");

    await waitFor(() => expect(toastMessages().join(" ")).toMatch(/Informe a limpeza interna do veículo\. Informe a limpeza externa do veículo\./));
    expect(within(wizard()).getByRole("tab", { name: "1. Condições do veículo" })).toHaveAttribute("aria-selected", "true");

    change(select("Limpeza interna"), "Boa");
    change(select("Limpeza externa"), "Aceitavel");
    click("Continuar para conferência");
    expect(within(wizard()).getByRole("tab", { name: "2. Conferência e finalização" })).toHaveAttribute("aria-selected", "true");
    expect(within(wizard()).getByRole("button", { name: "Finalizar entrega" })).toBeInTheDocument();
  });

  it("tires are optional: a draft saved in part 1 keeps the cleaning and only the positions informed", async () => {
    routeApi();
    await openChecklist();
    fill("ENTREGA");
    await chooseBrand("Dianteiro esquerdo", "PIRELLI");
    change(tireSelect("Dianteiro esquerdo", "Integridade"), "70-90%");
    change(tireSelect("Estepe", "Integridade"), "30-50%");
    click("Salvar rascunho"); // part 1, contacts not filled yet: a draft is still saved

    await waitFor(() => expect(mockedDocuments.createDocument).toHaveBeenCalledTimes(1));
    expect(savedPayload()).toMatchObject({ limpezaInterna: "Boa", limpezaExterna: "Aceitavel" });
    expect(savedPayload().tires.source).toBe("CHECKLIST");
    const informed = savedPayload().tires.positions.filter((position: any) => position.marca || position.estado);
    expect(informed).toEqual([
      { posicao: "Dianteiro esquerdo", marca: "PIRELLI", estado: "70-90%" },
      { posicao: "Estepe", estado: "30-50%" },
    ]);
  });

  it("the tires of the last inspection are never copied by themselves, only when the user applies them", async () => {
    routeApi({ "/api/cars/7": { car: { id: 7 }, lastInspection: LAST_INSPECTION } });
    await openChecklist();
    fill("ENTREGA");
    expect(await within(wizard()).findByText(/Usar pneus da última inspeção/)).toBeInTheDocument();
    expect(brandShown("Dianteiro esquerdo")).not.toContain("MICHELIN");

    click("Salvar rascunho");
    await waitFor(() => expect(mockedDocuments.createDocument).toHaveBeenCalledTimes(1));
    expect(savedPayload().tires).toBeUndefined();

    click("Usar pneus da última inspeção (conferir antes de finalizar)");
    expect(brandShown("Dianteiro esquerdo")).toBe("MICHELIN");
    expect(tireSelect("Dianteiro esquerdo", "Integridade").value).toBe("90-100%");
    expect(tireSelect("Estepe", "Integridade").value).toBe("50-70%");
  });

  it("a saved draft reopens with the vehicle conditions recovered", async () => {
    routeApi();
    stored = {
      id: 5,
      type: "ENTREGA_DEVOLUCAO_CHECKLIST",
      status: "DRAFT",
      driverId: 1,
      carId: 7,
      carPlate: "ABC1D23",
      checklistType: "ENTREGA",
      payload: {
        tipo: "ENTREGA",
        dataVistoria: "2026-10-07",
        km: 100,
        combustivel: "HALF",
        limpezaInterna: "Ótima",
        limpezaExterna: "Ruim",
        tires: { source: "CHECKLIST", positions: [{ posicao: "Traseiro direito", marca: "GOODYEAR", estado: "10-20%" }] },
      },
      attachments: [],
    };
    mockedDocuments.listDocuments.mockResolvedValue([{ ...stored, payload: undefined }] as any);
    render(<DocumentsPage />);
    fireEvent.click(await screen.findByRole("button", { name: "Editar" }));

    await within(await screen.findByRole("dialog")).findByText("Passo 3 de 3");
    expect(select("Limpeza interna").value).toBe("Ótima");
    expect(select("Limpeza externa").value).toBe("Ruim");
    expect(brandShown("Traseiro direito")).toBe("GOODYEAR");
    expect(tireSelect("Traseiro direito", "Integridade").value).toBe("10-20%");
    expect(within(wizard()).getByRole("tab", { name: "1. Condições do veículo" })).toHaveAttribute("aria-selected", "true");
  });

  it("damages already registered on the car are confirmed in part 2 (a snapshot for the PDF; the backend links them)", async () => {
    routeApi({ "/api/car-body-damages/car/7": [DAMAGE] });
    await openChecklist();
    fill("ENTREGA");
    await conference();

    const damage = await within(wizard()).findByText(/Porta dianteira/);
    fireEvent.click(damage.parentElement!.querySelector("input[type=checkbox]") as HTMLInputElement);
    expect(within(wizard()).getByTestId("checklist-review")).toHaveTextContent("Danos confirmados: 1");
    click("Salvar rascunho");

    await waitFor(() => expect(mockedDocuments.createDocument).toHaveBeenCalledTimes(1));
    expect(savedPayload().existingDamages).toEqual([{ id: 11, part: "Porta dianteira", date: "2026-09-01" }]);
  });

  it("damages of the car are listed but the explicitly resolved ones; an unknown resolution (older records) is shown", async () => {
    routeApi({
      "/api/car-body-damages/car/7": [
        DAMAGE,
        { id: 12, part: "Para-choque", date: "2025-01-01", resolved: null },
        { id: 13, part: "Retrovisor", date: "2025-02-01", resolved: true },
      ],
    });
    await openChecklist();
    fill("ENTREGA");
    await conference();

    expect(await within(wizard()).findByText(/Porta dianteira/)).toBeInTheDocument();
    expect(within(wizard()).getByText(/Para-choque/)).toBeInTheDocument();
    expect(within(wizard()).queryByText(/Retrovisor/)).not.toBeInTheDocument();
    fireEvent.click(within(wizard()).getByText(/Para-choque/).parentElement!.querySelector("input[type=checkbox]") as HTMLInputElement);
    click("Salvar rascunho");
    await waitFor(() => expect(mockedDocuments.createDocument).toHaveBeenCalledTimes(1));
    expect(savedPayload().existingDamages).toEqual([{ id: 12, part: "Para-choque", date: "2025-01-01" }]);
  });

  it("the brands are the inspection form's, custom ones saved there included", async () => {
    window.localStorage.setItem("TIRE_BRANDS_KEY", JSON.stringify(["MARCA DA CASA"]));
    routeApi();
    await openChecklist();
    fill("ENTREGA");

    fireEvent.click(brandItem("Estepe"));
    expect(await within(wizard()).findByText("PIRELLI")).toBeInTheDocument(); // a standard brand
    fireEvent.click(within(wizard()).getByText("MARCA DA CASA")); // a custom brand of the inspection form
    expect(brandShown("Estepe")).toBe("MARCA DA CASA");
    click("Salvar rascunho");

    await waitFor(() => expect(mockedDocuments.createDocument).toHaveBeenCalledTimes(1));
    expect(savedPayload().tires.positions.find((position: any) => position.posicao === "Estepe")).toMatchObject({ marca: "MARCA DA CASA" });
    window.localStorage.removeItem("TIRE_BRANDS_KEY");
  });

  it("an Entrega on a contract without a final checklist is not warned (asked for that contract)", async () => {
    contracts = [OPEN_CONTRACT];
    routeApi({ "/api/driver-cars/5/final-checklists": [] });
    await openChecklist();
    fill("ENTREGA");

    expect((await within(wizard()).findByTestId("checklist-contract-info")).textContent).toMatch(/a entrega será registrada neste vínculo/);
    expect(mockedApi.get).toHaveBeenCalledWith("/api/driver-cars/5/final-checklists");
  });

  it("a second Entrega on a contract that already has one is warned before filling; nothing is finalized", async () => {
    contracts = [OPEN_CONTRACT];
    // Asked to the backend for this contract: found whatever the number of documents of the car.
    routeApi({ "/api/driver-cars/5/final-checklists": ["ENTREGA"] });
    await openChecklist();
    fill("ENTREGA");

    expect((await within(wizard()).findByTestId("checklist-contract-blocked")).textContent).toMatch(
      /Este vínculo já possui um Checklist de Entrega finalizado\. Para outra via, use Emitir 2ª via \(PDF\) em Inspeções\./
    );
    click("Continuar para conferência");
    await waitFor(() => expect(toastMessages().join(" ")).toMatch(/já possui um Checklist de Entrega finalizado/));
    expect(mockedDocuments.finalizeDocument).not.toHaveBeenCalled();
  });
});
