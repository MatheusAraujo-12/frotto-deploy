import { act, configure, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { DefaultIonLifeCycleContext, IonApp, IonLifeCycleContext } from "@ionic/react";
import { MemoryRouter } from "react-router";
import Inspections from "./Inspections";
import api from "../../services/axios/axios";
import documentService from "../../services/documentService";
import { generateDocumentPdf } from "../Documents/documentPdf";

jest.mock("../../services/axios/axios");
jest.mock("../../services/documentService");
jest.mock("../Documents/documentPdf", () => ({ generateDocumentPdf: jest.fn() }));
const mockAlerts = { showErrorAlert: jest.fn(), showSuccessAlert: jest.fn(), showWarningAlert: jest.fn() };
jest.mock("../../services/hooks/useAlert", () => ({ useAlert: () => mockAlerts }));
jest.mock("@ionic/react", () => {
  const actual = jest.requireActual("@ionic/react");
  return { ...actual, useIonRouter: () => ({ push: jest.fn() }), useIonViewWillLeave: () => undefined };
});

// The Ionic DOM is too large to print in a query error: keep only the message.
configure({ getElementError: (message) => new Error(`${message}`.split("\n")[0]) });

const mockedApi = api as jest.Mocked<typeof api>;
const mockedDocuments = documentService as jest.Mocked<typeof documentService>;

let nextDocument = 900;
const scenario = () => {
  nextDocument += 2;
  const entrega = nextDocument;
  const devolucao = nextDocument + 1;
  const list = [
    { id: 1, date: "2026-10-01", driverName: "Manual", odometer: 100, cost: 50 },
    { id: 2, date: "2026-10-07", driverName: "João", odometer: 15000, originDocumentId: entrega, driverCarId: 3, fuelLevel: "HALF" },
    { id: 3, date: "2026-10-15", driverName: "João", odometer: 15500, originDocumentId: devolucao, driverCarId: 3, fuelLevel: "FULL" },
  ];
  const documents: Record<number, any> = {
    [entrega]: { id: entrega, type: "ENTREGA_DEVOLUCAO_CHECKLIST", status: "FINAL", checklistType: "ENTREGA" },
    [devolucao]: { id: devolucao, type: "ENTREGA_DEVOLUCAO_CHECKLIST", status: "FINAL", checklistType: "DEVOLUCAO" },
  };
  mockedApi.get.mockResolvedValue({ data: list });
  mockedDocuments.getDocument.mockImplementation(async (id: number) => documents[id]);
  mockedDocuments.generateDocumentPdf.mockImplementation(async (id: number) => documents[id]);
  return { entrega, devolucao, documents };
};

const renderList = async () => {
  const view = render(
    <IonApp>
      <MemoryRouter>
        <Inspections match={{ params: { id: "20" }, isExact: true, path: "", url: "" }} history={{} as any} location={{} as any} />
      </MemoryRouter>
    </IonApp>
  );
  await screen.findByText("Checklist de Devolução");
  return view;
};
const secondCopyButtons = () => screen.queryAllByText("Emitir 2ª via (PDF)");

describe("Inspeções - checklists e 2ª via", () => {
  beforeAll(() => {
    (global as any).IntersectionObserver = class {
      observe() {}
      unobserve() {}
      disconnect() {}
    };
  });
  beforeEach(() => jest.resetAllMocks());

  it("marks the inspections created by a checklist; the single inspection keeps its look and actions", async () => {
    scenario();
    await renderList();

    expect(screen.getByText("Checklist de Entrega")).toBeInTheDocument();
    expect(screen.getByText("Checklist de Devolução")).toBeInTheDocument();
    expect(secondCopyButtons()).toHaveLength(2);
    expect(screen.getByText("Manual")).toBeInTheDocument();
  });

  it("2ª via: the PDF of the original document, without any write (no document, inspection or contract)", async () => {
    const { devolucao, documents } = scenario();
    await renderList();

    await act(async () => {
      fireEvent.click(secondCopyButtons()[1]);
    });

    await waitFor(() => expect(generateDocumentPdf).toHaveBeenCalledWith(documents[devolucao]));
    expect(mockedDocuments.generateDocumentPdf).toHaveBeenCalledWith(devolucao);
    expect(mockedDocuments.finalizeDocument).not.toHaveBeenCalled();
    expect(mockedDocuments.createDocument).not.toHaveBeenCalled();
    expect(mockedApi.post).not.toHaveBeenCalled();
    expect(mockedApi.put).not.toHaveBeenCalled();
    expect(mockAlerts.showSuccessAlert).toHaveBeenCalledWith("2ª via do checklist gerada.");
  });

  it("the checklist type is looked up once per document, also across visits", async () => {
    const { entrega, devolucao } = scenario();
    const first = await renderList();
    first.unmount();
    await renderList();

    const lookups = mockedDocuments.getDocument.mock.calls.map((call) => call[0]);
    expect(lookups.filter((id) => id === entrega)).toHaveLength(1);
    expect(lookups.filter((id) => id === devolucao)).toHaveLength(1);
  });

  it("a failed 2ª via is explained and can be tried again", async () => {
    scenario();
    (generateDocumentPdf as jest.Mock).mockRejectedValueOnce(new Error("popup blocked"));
    await renderList();

    await act(async () => {
      fireEvent.click(secondCopyButtons()[0]);
    });
    await waitFor(() =>
      expect(mockAlerts.showErrorAlert).toHaveBeenCalledWith("Não foi possível emitir a 2ª via do checklist. Tente novamente.")
    );
    await act(async () => {
      fireEvent.click(secondCopyButtons()[0]);
    });

    await waitFor(() => expect(generateDocumentPdf).toHaveBeenCalledTimes(2));
    expect(mockedDocuments.finalizeDocument).not.toHaveBeenCalled();
  });
});

describe("Inspeções - volta de um checklist", () => {
  beforeAll(() => {
    (global as any).IntersectionObserver = class {
      observe() {}
      unobserve() {}
      disconnect() {}
    };
  });
  beforeEach(() => jest.resetAllMocks());

  it("coming back to this screen (a checklist finalized or a draft saved) reads the inspections and the drafts again", async () => {
    scenario();
    mockedDocuments.listDocuments.mockResolvedValue([]);
    const lifecycle = new DefaultIonLifeCycleContext();
    render(
      <IonApp>
        <MemoryRouter>
          <IonLifeCycleContext.Provider value={lifecycle}>
            <Inspections match={{ params: { id: "20" }, isExact: true, path: "", url: "" }} history={{} as any} location={{} as any} />
          </IonLifeCycleContext.Provider>
        </MemoryRouter>
      </IonApp>
    );
    await screen.findByText("Checklist de Devolução");
    expect(mockedApi.get).toHaveBeenCalledTimes(1);

    await act(async () => lifecycle.ionViewWillEnter()); // the first entry: already loaded on mount
    expect(mockedApi.get).toHaveBeenCalledTimes(1);

    const drafts = mockedDocuments.listDocuments.mock.calls.length;
    await act(async () => lifecycle.ionViewWillEnter()); // back from the checklist
    expect(mockedApi.get).toHaveBeenCalledTimes(2);
    expect(mockedDocuments.listDocuments.mock.calls.length).toBeGreaterThan(drafts);
  });
});
