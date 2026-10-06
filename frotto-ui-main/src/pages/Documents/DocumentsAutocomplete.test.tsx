import { act, fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import DocumentsPage from "./DocumentsPage";
import documentService from "../../services/documentService";
import debtItemTypeService from "../../services/debtItemTypeService";

const mockToast = jest.fn();

/*
 * Stand-ins DOM para os componentes Ionic (mesmo padrão de MyPlanPage.test).
 * O IonInput reproduz o comportamento do @ionic/core 6.7.5 que causou o bug:
 *  - ionInput: só quando o USUÁRIO digita;
 *  - ionChange: sempre que o valor muda — inclusive quando o CÓDIGO altera `value`.
 */
jest.mock("@ionic/react", () => {
  const React = require("react");
  const passthrough = (tag: string) =>
    React.forwardRef(({ children, className, onClick }: any, ref: any) =>
      React.createElement(tag, { ref, className, onClick }, children)
    );

  const IonInput = ({ value, onIonChange, onIonInput, type }: any) => {
    const [shown, setShown] = React.useState(value ?? "");
    const internal = React.useRef(value ?? "");
    React.useEffect(() => {
      const next = value ?? "";
      if (next !== internal.current) {
        internal.current = next;
        setShown(next);
        onIonChange?.({ detail: { value: next } }); // mudança programática também emite ionChange
      }
      // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [value]);
    return React.createElement("input", {
      type: type === "date" ? "text" : type,
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

  const IonTextarea = ({ value, onIonChange, onIonInput }: any) =>
    React.createElement("textarea", {
      value: value ?? "",
      onChange: (event: any) => {
        onIonInput?.({ target: event.target, detail: event.nativeEvent });
        onIonChange?.({ detail: { value: event.target.value } });
      },
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
    IonNote: passthrough("span"),
  };
});

jest.mock("../../services/documentService");
jest.mock("../../services/debtItemTypeService");
jest.mock("./documentPdf", () => ({ generateDocumentPdf: jest.fn() }));
jest.mock("../../services/axios/axios", () => ({ __esModule: true, default: { get: jest.fn().mockResolvedValue({ data: {} }) } }));

const mockedDocuments = documentService as jest.Mocked<typeof documentService>;
const mockedDebtTypes = debtItemTypeService as jest.Mocked<typeof debtItemTypeService>;

const DRIVER = { id: 1, name: "João Motorista", cpf: "39053344705", active: true };
const CAR = { id: 7, plate: "ABC1D23", model: "Onix", name: "Onix Teste", brand: "Chevrolet", active: true, adminStatus: "ATIVO" };

const dialog = () => screen.getByRole("dialog");
const fieldInput = (label: string, index = 0) => {
  const labels = within(dialog()).getAllByText((text, node) => node?.tagName === "LABEL" && text.trim().startsWith(label));
  return labels[index].parentElement!.querySelector("input, textarea") as HTMLInputElement;
};
const typeInto = (input: HTMLInputElement, value: string) => fireEvent.change(input, { target: { value } });
const clickButton = (name: string) => fireEvent.click(within(dialog()).getByRole("button", { name }));

async function openWizardAndSelect({ withCar = true } = {}) {
  render(<DocumentsPage />);
  fireEvent.click(await screen.findByRole("button", { name: /Novo Documento/ }));

  // DIGITAR → RESULTADOS → SELECIONAR (busca parcial, sem redigitar o nome completo)
  typeInto(fieldInput("Motorista"), "Jo");
  fireEvent.click(await within(dialog()).findByText("João Motorista (39053344705)"));
  if (withCar) {
    typeInto(fieldInput("Carro"), "ABC");
    fireEvent.click(await within(dialog()).findByText("ABC1D23 - Onix"));
  }
}

async function goToStep3(type: string) {
  clickButton("Próximo");
  const select = dialog().querySelector("select") as HTMLSelectElement;
  fireEvent.change(select, { target: { value: type } });
  clickButton("Próximo");
  await within(dialog()).findByText("Passo 3 de 3");
}

beforeEach(() => {
  jest.clearAllMocks();
  mockedDocuments.listDocuments.mockResolvedValue([] as any);
  mockedDocuments.searchDrivers.mockResolvedValue([DRIVER] as any);
  mockedDocuments.searchCars.mockResolvedValue([CAR] as any);
  mockedDocuments.listOpenPendenciesByDriver.mockResolvedValue([] as any);
  mockedDocuments.createDocument.mockImplementation(async (request: any) => ({ id: 99, ...request }) as any);
  mockedDocuments.uploadDocumentAttachments.mockResolvedValue(undefined as any);
  mockedDocuments.getDocument.mockImplementation(async () => ({ id: 99, attachments: [] }) as any);
  mockedDebtTypes.listDebtItemTypes.mockResolvedValue([] as any);
});

describe("Documentos - autocomplete de motorista/carro", () => {
  it("DIGITAR → RESULTADOS → SELECIONAR → SALVAR mantém motorista e carro selecionados (por id)", async () => {
    await openWizardAndSelect();

    // A atualização programática do texto após a seleção não invalida a entidade
    // e não reabre a lista de resultados.
    expect(fieldInput("Motorista").value).toBe("João Motorista");
    expect(fieldInput("Carro").value).toBe("ABC1D23");
    await act(async () => new Promise((resolve) => setTimeout(resolve, 400)));
    expect(within(dialog()).queryByText("João Motorista (39053344705)")).not.toBeInTheDocument();

    await goToStep3("RECIBO_ALUGUEL");
    clickButton("Salvar rascunho");

    await waitFor(() => expect(mockedDocuments.createDocument).toHaveBeenCalledTimes(1));
    expect(mockedDocuments.createDocument.mock.calls[0][0]).toMatchObject({
      type: "RECIBO_ALUGUEL",
      status: "DRAFT",
      driverId: 1,
      carId: 7,
      payload: expect.objectContaining({ driverName: "João Motorista", driverCpf: "39053344705", carPlate: "ABC1D23" }),
    });
    expect(mockToast).not.toHaveBeenCalledWith(expect.objectContaining({ message: "Selecione um motorista." }));
  });

  it("SELECIONAR → USUÁRIO ALTERA O TEXTO → seleção invalidada (mesmo se o texto voltar a ser igual)", async () => {
    await openWizardAndSelect({ withCar: false });

    typeInto(fieldInput("Motorista"), "João Motoris"); // usuário edita a pesquisa
    typeInto(fieldInput("Motorista"), "João Motorista"); // texto idêntico, mas sem escolher a entidade

    await goToStep3("RECIBO_ALUGUEL");
    clickButton("Salvar rascunho");

    await waitFor(() =>
      expect(mockToast).toHaveBeenCalledWith(expect.objectContaining({ message: "Selecione um motorista." }))
    );
    expect(mockedDocuments.createDocument).not.toHaveBeenCalled();
  });
});

describe("Documentos - Novo Documento sem Multa / Manutenção Compartilhada", () => {
  it("H/I) the type selector of a new document no longer offers them (they are born in Pendências)", async () => {
    await openWizardAndSelect();
    clickButton("Próximo");
    const options = Array.from((dialog().querySelector("select") as HTMLSelectElement).options).map((option) => option.value);

    expect(options).not.toContain("MULTA");
    expect(options).not.toContain("MANUTENCAO_COMPARTILHADA");
    expect(options).toEqual(expect.arrayContaining(["RECIBO_ALUGUEL", "CONFISSAO_DIVIDA", "ENTREGA_DEVOLUCAO_CHECKLIST"]));
    expect(within(dialog()).getByTestId("documents-moved-to-pendencies").textContent).toMatch(/registradas em Pendências/);
  });
});

describe("Documentos - Checklist / contatos de emergência", () => {
  it("Adicionar contato cria uma nova linha; linhas vazias não vão para o payload e nomes mantêm espaços", async () => {
    await openWizardAndSelect();
    await goToStep3("ENTREGA_DEVOLUCAO_CHECKLIST");

    expect(within(dialog()).queryByText(/Contato 3 - Nome/)).not.toBeInTheDocument();
    clickButton("Adicionar contato");
    expect(within(dialog()).getByText(/Contato 3 - Nome/)).toBeInTheDocument();
    expect(within(dialog()).getAllByRole("button", { name: "Remover contato" })).toHaveLength(3);

    typeInto(fieldInput("Contato 1 - Nome"), "Carlos ");
    typeInto(fieldInput("Contato 1 - Nome"), "Carlos Souza");
    typeInto(fieldInput("Contato 1 - Telefone"), "11933334444");
    typeInto(fieldInput("Contato 2 - Nome"), "Beatriz Lima");
    typeInto(fieldInput("Contato 2 - Telefone"), "11955556666");
    expect(fieldInput("Contato 1 - Nome").value).toBe("Carlos Souza");

    clickButton("Salvar rascunho");
    await waitFor(() => expect(mockedDocuments.createDocument).toHaveBeenCalledTimes(1));
    expect(mockedDocuments.createDocument.mock.calls[0][0].payload?.emergencyContacts).toEqual([
      { nome: "Carlos Souza", telefone: "11933334444" },
      { nome: "Beatriz Lima", telefone: "11955556666" },
    ]);
  });
});
