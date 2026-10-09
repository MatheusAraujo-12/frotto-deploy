import { act, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { IonApp } from "@ionic/react";
import { MemoryRouter, Route } from "react-router";
import InspectionNewMenu from "./InspectionNewMenu";
import documentService from "../../services/documentService";

jest.mock("../../services/documentService");
const mockedDocuments = documentService as jest.Mocked<typeof documentService>;

const renderMenu = () => {
  const onSingleInspection = jest.fn();
  render(
    <IonApp>
      <MemoryRouter initialEntries={["/menu/carros/7"]}>
        <InspectionNewMenu carId="7" onSingleInspection={onSingleInspection} />
        <Route
          path="*"
          render={({ location }) => (
            <>
              <span data-testid="location">{location.pathname + location.search}</span>
              <span data-testid="state">{JSON.stringify(location.state ?? null)}</span>
            </>
          )}
        />
      </MemoryRouter>
    </IonApp>
  );
  return onSingleInspection;
};
const click = async (text: string) => {
  await act(async () => {
    fireEvent.click(screen.getByText(text));
  });
};

describe("Inspeções - + Novo", () => {
  beforeEach(() => {
    jest.resetAllMocks();
    mockedDocuments.listDocuments.mockResolvedValue([] as any);
  });

  it("offers Inspeção Avulsa, Checklist de Entrega and Checklist de Devolução", async () => {
    renderMenu();
    expect(screen.queryByText("Inspeção Avulsa")).not.toBeInTheDocument();

    await click("Novo");

    expect(screen.getByText("Inspeção Avulsa")).toBeInTheDocument();
    expect(screen.getByText("Checklist de Entrega")).toBeInTheDocument();
    expect(screen.getByText("Checklist de Devolução")).toBeInTheDocument();
    expect(screen.getByText("Novo").closest("ion-button")).toHaveAttribute("aria-expanded", "true");
  });

  it("Inspeção Avulsa keeps the current form", async () => {
    const onSingleInspection = renderMenu();
    await click("Novo");
    await click("Inspeção Avulsa");

    expect(onSingleInspection).toHaveBeenCalledTimes(1);
    expect(screen.getByTestId("location")).toHaveTextContent("/menu/carros/7");
  });

  it("without a draft, a checklist opens the existing wizard for this car (an app entry: it comes back here)", async () => {
    renderMenu();
    await click("Novo");
    await click("Checklist de Devolução");

    await waitFor(() =>
      expect(screen.getByTestId("location")).toHaveTextContent("/documents?checklist=DEVOLUCAO&carId=7&from=%2Fmenu%2Fcarros%2F7")
    );
    expect(screen.getByTestId("state")).toHaveTextContent('{"checklistLaunch":true}');
    expect(mockedDocuments.listDocuments).toHaveBeenCalledWith({ carId: 7, type: "ENTREGA_DEVOLUCAO_CHECKLIST", status: "DRAFT", limit: 50 });
  });

  it("a draft of the same operation is offered first: continue it or create a new one on purpose", async () => {
    mockedDocuments.listDocuments.mockResolvedValue([
      { id: 12, type: "ENTREGA_DEVOLUCAO_CHECKLIST", status: "DRAFT", checklistType: "ENTREGA", driverName: "João", updatedAt: "2026-10-08T10:00:00Z" },
      { id: 13, type: "ENTREGA_DEVOLUCAO_CHECKLIST", status: "DRAFT", checklistType: "DEVOLUCAO", driverName: "Maria" },
    ] as any);
    renderMenu();
    await click("Novo");
    await click("Checklist de Entrega");

    expect(await screen.findByTestId("checklist-draft-choice")).toHaveTextContent("Já existe rascunho de Checklist de Entrega para este veículo:");
    expect(screen.getByText("#12 · João · atualizado em 08/10/2026")).toBeInTheDocument();
    expect(screen.queryByText(/#13/)).not.toBeInTheDocument(); // the Devolução draft is not this operation
    expect(screen.getByTestId("location")).toHaveTextContent("/menu/carros/7"); // nothing opened yet

    await click("Continuar rascunho #12");
    expect(screen.getByTestId("location")).toHaveTextContent("/documents?carId=7&documentId=12&from=%2Fmenu%2Fcarros%2F7");
  });

  it("Criar novo checklist starts a new one even with a draft (never overwritten)", async () => {
    mockedDocuments.listDocuments.mockResolvedValue([
      { id: 12, type: "ENTREGA_DEVOLUCAO_CHECKLIST", status: "DRAFT", checklistType: "ENTREGA" },
    ] as any);
    renderMenu();
    await click("Novo");
    await click("Checklist de Entrega");
    await screen.findByTestId("checklist-draft-choice");

    await click("Criar novo checklist");
    expect(screen.getByTestId("location")).toHaveTextContent("/documents?checklist=ENTREGA&carId=7&from=%2Fmenu%2Fcarros%2F7");
  });
});
