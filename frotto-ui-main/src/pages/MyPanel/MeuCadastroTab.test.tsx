import { render, screen, within } from "@testing-library/react";
import MeuCadastroTab, { CadastroSection } from "./MeuCadastroTab";
import {
  EMPTY_FISCAL_FORM,
  EMPTY_FISCAL_TOUCHED,
  EMPTY_PERSONAL_FORM,
  EMPTY_PERSONAL_TOUCHED,
  FiscalForm,
  PersonalForm,
} from "./profilePanelUtils";

const PF: FiscalForm = {
  ...EMPTY_FISCAL_FORM,
  taxPersonType: "CPF",
  taxLandlordName: "Maria Souza",
  taxCpf: "529.982.247-25",
  taxEmail: "maria@example.com",
  taxPhone: "(11) 98765-4321",
};

const PJ: FiscalForm = {
  ...EMPTY_FISCAL_FORM,
  taxPersonType: "CNPJ",
  taxCompanyName: "Frotto Locações LTDA",
  taxCnpj: "11.222.333/0001-81",
  taxIe: "123.456.789.110",
  taxContactPhone: "(11) 3333-4444",
  taxAddress: "Rua das Flores, 100",
};

const PERSONAL: PersonalForm = { ...EMPTY_PERSONAL_FORM, personalBirthDate: "1990-05-20" };

const setup = (overrides: Partial<React.ComponentProps<typeof MeuCadastroTab>> = {}) => {
  const props: React.ComponentProps<typeof MeuCadastroTab> = {
    editingSection: null as CadastroSection | null,
    isSaving: false,
    onStartEdit: jest.fn(),
    onCancelEdit: jest.fn(),
    onSaveSection: jest.fn(),
    fiscalForm: PF,
    savedFiscalForm: PF,
    fiscalTouched: EMPTY_FISCAL_TOUCHED,
    fiscalErrors: {},
    fiscalDirty: false,
    onFiscalTouch: jest.fn(),
    onFiscalChange: jest.fn(),
    personalForm: PERSONAL,
    savedPersonalForm: PERSONAL,
    personalTouched: EMPTY_PERSONAL_TOUCHED,
    personalErrors: {},
    personalDirty: false,
    onPersonalTouch: jest.fn(),
    onPersonalChange: jest.fn(),
    avatarPreviewUrl: "",
    avatarDirty: false,
    avatarRemoved: false,
    canRemoveAvatar: false,
    onChangeAvatar: jest.fn(),
    onRemoveAvatar: jest.fn(),
    logoPreviewUrl: "",
    logoDirty: false,
    logoRemoved: false,
    canRemoveLogo: false,
    onChangeLogo: jest.fn(),
    onRemoveLogo: jest.fn(),
    ...overrides,
  };
  const view = render(<MeuCadastroTab {...props} />);
  return { ...view, props };
};

const card = (id: string) => screen.getByTestId(`cadastro-${id}`);
const inputLabels = (element: HTMLElement) =>
  Array.from(element.querySelectorAll("ion-item ion-label")).map((node) => node.textContent?.replace(/\s*\*$/, "").trim());

describe("MeuCadastroTab - visualizar → editar → salvar", () => {
  it("view mode shows a read-only summary (no inputs) with an Editar action", () => {
    setup();
    const identity = card("identity");
    expect(within(identity).getByText("Maria Souza")).toBeInTheDocument();
    expect(within(identity).getByText("Pessoa Física")).toBeInTheDocument();
    expect(identity.querySelectorAll("ion-input").length).toBe(0);
    expect(within(identity).getByText("Editar dados")).toBeInTheDocument();
  });

  it("empty identity shows the empty state and enters edit mode from it", () => {
    const { props } = setup({ fiscalForm: EMPTY_FISCAL_FORM, savedFiscalForm: EMPTY_FISCAL_FORM });
    within(card("identity")).getByText("Preencher dados").click();
    expect(props.onStartEdit).toHaveBeenCalledWith("identity");
  });

  it("edit mode places Salvar after the fields, with Cancelar as the red secondary action", () => {
    setup({ editingSection: "identity", fiscalDirty: true });
    const identity = card("identity");
    const inputs = identity.querySelectorAll("ion-input");
    const cancel = identity.querySelector("ion-button.app-cancel-btn") as HTMLElement;
    const save = identity.querySelector("ion-button.app-save-btn") as HTMLElement;

    expect(inputs.length).toBe(4);
    expect(cancel).toHaveTextContent("Cancelar");
    expect(save).toHaveTextContent("Salvar");
    // Salvar vem depois do último campo e depois de Cancelar.
    expect(inputs[inputs.length - 1].compareDocumentPosition(save) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
    expect(cancel.compareDocumentPosition(save) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
  });

  it("locks the other sections while one is being edited", () => {
    setup({ editingSection: "identity" });
    const editPersonal = within(card("personal")).getByText("Editar").closest("ion-button");
    expect(editPersonal).toHaveAttribute("disabled");
    expect(editPersonal?.getAttribute("disabled")).not.toBe("false");
  });
});

describe("MeuCadastroTab - Pessoa Física sem duplicidade", () => {
  it("does not render personal name/CPF/e-mail/phone inputs for PF (only birth date)", () => {
    setup({ editingSection: "personal" });
    expect(inputLabels(card("personal"))).toEqual(["Data de nascimento"]);
  });

  it("PF view reuses the Dados cadastrais values instead of repeating them", () => {
    setup();
    const personal = card("personal");
    expect(within(personal).getByTestId("cadastro-pf-source-note")).toBeInTheDocument();
    expect(within(personal).queryByText("Maria Souza")).not.toBeInTheDocument();
    expect(within(personal).getByText("20/05/1990")).toBeInTheDocument();
    expect(within(card("identity")).getByText("Maria Souza")).toBeInTheDocument();
  });

  it("keeps a divergent legacy personal value visible, with an explicit label", () => {
    const legacy = { ...PERSONAL, personalName: "Maria S. Legado" };
    setup({ personalForm: legacy, savedPersonalForm: legacy });
    expect(within(card("personal")).getByText("Nome (cadastro pessoal)")).toBeInTheDocument();
    expect(within(card("personal")).getByText("Maria S. Legado")).toBeInTheDocument();
  });
});

describe("MeuCadastroTab - Pessoa Jurídica", () => {
  it("groups company data and keeps the account holder separate", () => {
    setup({ fiscalForm: PJ, savedFiscalForm: PJ });
    const identity = card("identity");
    ["Empresa", "Contato da empresa", "Endereço", "Dados fiscais"].forEach((group) =>
      expect(within(identity).getByText(group)).toBeInTheDocument()
    );
    expect(within(identity).getByText("Frotto Locações LTDA")).toBeInTheDocument();
    expect(within(identity).getByText("123.456.789.110")).toBeInTheDocument();
    expect(within(card("personal")).getByText("Responsável pela conta")).toBeInTheDocument();
  });

  it("PJ account holder edits every personal field", () => {
    setup({ fiscalForm: PJ, savedFiscalForm: PJ, editingSection: "personal" });
    expect(inputLabels(card("personal"))).toEqual(["Nome", "CPF", "Data de nascimento", "E-mail", "Telefone"]);
  });

  it("warns (without changing the rule) that switching type erases the other branch on save", () => {
    setup({ savedFiscalForm: PF, fiscalForm: { ...EMPTY_FISCAL_FORM, taxPersonType: "CNPJ" }, editingSection: "identity" });
    expect(
      within(card("identity")).getByText("Ao salvar como Pessoa Jurídica, os dados de Pessoa Física serão apagados.")
    ).toBeInTheDocument();
  });
});
