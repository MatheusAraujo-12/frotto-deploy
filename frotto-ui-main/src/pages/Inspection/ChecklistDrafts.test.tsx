import { act, fireEvent, render, screen } from "@testing-library/react";
import { IonApp } from "@ionic/react";
import { MemoryRouter, Route } from "react-router";
import ChecklistDrafts from "./ChecklistDrafts";
import documentService from "../../services/documentService";

jest.mock("../../services/documentService");
jest.mock("@ionic/react", () => {
  const actual = jest.requireActual("@ionic/react");
  return { ...actual, useIonViewWillEnter: () => undefined };
});
const mockedDocuments = documentService as jest.Mocked<typeof documentService>;

const DRAFTS = [
  { id: 21, type: "ENTREGA_DEVOLUCAO_CHECKLIST", status: "DRAFT", checklistType: "DEVOLUCAO", driverName: "Maria", updatedAt: "2026-10-07T09:00:00Z" },
  { id: 22, type: "ENTREGA_DEVOLUCAO_CHECKLIST", status: "DRAFT", checklistType: "ENTREGA", driverName: "João", updatedAt: "2026-10-08T09:00:00Z" },
  { id: 23, type: "ENTREGA_DEVOLUCAO_CHECKLIST", status: "DRAFT", checklistType: null, driverName: "Antigo", updatedAt: "2025-01-01T09:00:00Z" },
];

const renderDrafts = async (returnTo = "/menu/carros/7/inspecoes") => {
  render(
    <IonApp>
      <MemoryRouter initialEntries={[returnTo]}>
        <ChecklistDrafts carId="7" returnTo={returnTo} />
        <Route path="*" render={({ location }) => <span data-testid="location">{location.pathname + location.search}</span>} />
      </MemoryRouter>
    </IonApp>
  );
  await act(async () => undefined);
};

describe("Inspeções - checklists em rascunho", () => {
  beforeEach(() => jest.resetAllMocks());

  it("lists the drafts of the car (Entrega, Devolução, older form), most recently updated first, with type, status and id", async () => {
    mockedDocuments.listDocuments.mockResolvedValue(DRAFTS as any);
    await renderDrafts();

    expect(mockedDocuments.listDocuments).toHaveBeenCalledWith({ carId: 7, type: "ENTREGA_DEVOLUCAO_CHECKLIST", status: "DRAFT", limit: 50 });
    const items = screen.getAllByTestId("checklist-draft").map((item) => item.textContent);
    expect(items[0]).toMatch(/Checklist de Entrega.*#22 · João · atualizado em 08\/10\/2026.*Rascunho.*Continuar preenchimento/);
    expect(items[1]).toMatch(/Checklist de Devolução.*#21 · Maria/);
    expect(items[2]).toMatch(/Checklist \(formato anterior\).*#23 · Antigo/);
  });

  it("Continuar preenchimento opens that draft in the wizard and comes back to this screen", async () => {
    mockedDocuments.listDocuments.mockResolvedValue([DRAFTS[0]] as any);
    await renderDrafts();

    await act(async () => {
      fireEvent.click(screen.getByText("Continuar preenchimento"));
    });

    expect(screen.getByTestId("location")).toHaveTextContent("/documents?carId=7&documentId=21&from=%2Fmenu%2Fcarros%2F7%2Finspecoes");
  });

  it("shows nothing when the car has no draft (or the list cannot be read)", async () => {
    mockedDocuments.listDocuments.mockResolvedValue([] as any);
    await renderDrafts();
    expect(screen.queryByTestId("checklist-drafts")).not.toBeInTheDocument();

    mockedDocuments.listDocuments.mockRejectedValue(new Error("offline"));
    await renderDrafts();
    expect(screen.queryByTestId("checklist-drafts")).not.toBeInTheDocument();
  });
});
