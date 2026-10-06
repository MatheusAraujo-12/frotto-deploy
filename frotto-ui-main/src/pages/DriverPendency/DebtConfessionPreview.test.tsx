import { act, fireEvent, render, screen } from "@testing-library/react";
import { IonApp } from "@ionic/react";
import DebtConfessionPreviewView from "./DebtConfessionPreview";
import { PREVIEW_MULTI_CAR } from "../../services/debtConfessionContract.fixtures";

jest.mock("../../services/axios/axios");

const renderPreview = (isGenerating = false) => {
  const onCancel = jest.fn();
  const onGenerate = jest.fn();
  render(
    <IonApp>
      <DebtConfessionPreviewView preview={PREVIEW_MULTI_CAR as any} isGenerating={isGenerating} onCancel={onCancel} onGenerate={onGenerate} />
    </IonApp>
  );
  return { onCancel, onGenerate };
};

const textOf = () => document.body.textContent?.replace(/\s+/g, " ") || "";
const generateButton = () => screen.getByText("Gerar PDF").closest("ion-button") as any;
const ionChange = async (element: Element | null, value: unknown, name = "") => {
  if (!element) throw new Error(`field not found: ${name}`);
  await act(async () => {
    fireEvent(element, new CustomEvent("ionChange", { detail: { value }, bubbles: true }));
  });
};
const fillTerms = {
  form: (value: string) => ionChange(document.querySelector("ion-select"), value, "form"),
  deadline: (value: string) => ionChange(document.querySelector('ion-input[data-field="prazoPagamento"]'), value, "deadline"),
  installments: (value: string) => ionChange(document.querySelector('ion-input[data-field="parcelasQtd"]'), value, "installments"),
  firstDue: (value: string) => ionChange(document.querySelector('ion-input[data-field="primeiroVencimento"]'), value, "firstDue"),
  note: (value: string) => ionChange(document.querySelector("ion-textarea"), value),
};

describe("Prévia da Confissão de Dívida (dados reais do backend)", () => {
  beforeAll(() => {
    (global as any).IntersectionObserver = class {
      observe() {}
      unobserve() {}
      disconnect() {}
    };
  });

  it("shows the debtor, the backend total, every debt with its own origin, and no witness fields", () => {
    renderPreview();
    const text = textOf();

    expect(screen.getByText("João Silva")).toBeInTheDocument();
    expect(text).toContain("CPF: 111.111.111-11");
    expect(text).toMatch(/Valor total\s?R\$\s?1\.150,00/);
    expect(text).toContain("Pendências incluídas");
    expect(text).toContain("Veículo de origem: Onix - ONX1A11");
    expect(text).toContain("Veículo de origem: HB20 - HBV2B22");
    expect(text).toContain("Veículo de origem: Argo - ARG3C33");
    expect(text).toContain("AIT 123");
    expect(text).toContain("20/08/2026");
    // Q) Partially paid: only the backend balance enters, with the original and paid amounts shown.
    expect(text).toMatch(/Valor original R\$\s?1\.000,00 · já pago R\$\s?400,00 · saldo R\$\s?600,00/);
    expect(text).toMatch(/Total da Confissão\s?R\$\s?1\.150,00/);
    expect(text).toContain("Condições de pagamento");
    expect(text).not.toMatch(/Testemunha/i);
    expect(text).not.toContain("Valor da parcela");
  });

  it("G/H) nothing filled: Gerar PDF is blocked, no deadline is assumed", async () => {
    const { onGenerate } = renderPreview();

    expect(generateButton().disabled).toBe(true);
    expect(document.querySelector('ion-input[data-field="prazoPagamento"]')?.getAttribute("value") || "").toBe("");
    await fillTerms.form("PIX");
    expect(generateButton().disabled).toBe(true); // H) deadline still missing
    await act(async () => {
      fireEvent.click(screen.getByText("Gerar PDF"));
    });
    expect(onGenerate).not.toHaveBeenCalled();
  });

  it("G) deadline without payment form stays blocked", async () => {
    renderPreview();
    await fillTerms.deadline("2026-10-20");
    expect(generateButton().disabled).toBe(true);
  });

  it("A) PIX + prazo + observação: Gerar PDF sends exactly those terms", async () => {
    const { onGenerate } = renderPreview();
    await fillTerms.form("PIX");
    await fillTerms.deadline("2026-10-20");
    await fillTerms.note("Pagamento mediante PIX para a conta informada pela empresa.");

    expect(generateButton().disabled).toBe(false);
    await act(async () => {
      fireEvent.click(screen.getByText("Gerar PDF"));
    });
    expect(onGenerate).toHaveBeenCalledWith({
      formaPagamento: "PIX",
      prazoPagamento: "2026-10-20",
      observacao: "Pagamento mediante PIX para a conta informada pela empresa.",
    });
  });

  it("B) PIX + prazo without observação is enough", async () => {
    const { onGenerate } = renderPreview();
    await fillTerms.form("TRANSFERENCIA");
    await fillTerms.deadline("2026-10-20");
    await act(async () => {
      fireEvent.click(screen.getByText("Gerar PDF"));
    });
    expect(onGenerate).toHaveBeenCalledWith({ formaPagamento: "TRANSFERENCIA", prazoPagamento: "2026-10-20" });
  });

  it("C/D/E/F) Parcelado shows its fields and stays blocked until 2+ installments and the first due date", async () => {
    const { onGenerate } = renderPreview();
    expect(document.querySelector('ion-input[data-field="parcelasQtd"]')).toBeNull();

    await fillTerms.form("PARCELADO");
    await fillTerms.deadline("2027-02-20");
    expect(document.querySelector('ion-input[data-field="parcelasQtd"]')).not.toBeNull();
    expect(document.querySelector('ion-input[data-field="primeiroVencimento"]')).not.toBeNull();
    expect(generateButton().disabled).toBe(true); // D) no quantity

    await fillTerms.firstDue("2026-10-20");
    expect(generateButton().disabled).toBe(true); // D) still no quantity

    await fillTerms.installments("1");
    expect(generateButton().disabled).toBe(true); // E) 1 installment
    expect(textOf()).toContain("Informe a quantidade de parcelas (de 2 a 120).");

    await fillTerms.installments("5");
    expect(generateButton().disabled).toBe(false);

    await fillTerms.firstDue("");
    expect(generateButton().disabled).toBe(true); // F) no first due date

    await fillTerms.firstDue("2026-10-20");
    await act(async () => {
      fireEvent.click(screen.getByText("Gerar PDF"));
    });
    expect(onGenerate).toHaveBeenCalledWith({
      formaPagamento: "PARCELADO",
      prazoPagamento: "2027-02-20",
      parcelasQtd: 5,
      primeiroVencimento: "2026-10-20",
    });
  });

  it("I) Cancelar only closes", async () => {
    const { onCancel, onGenerate } = renderPreview();
    await act(async () => {
      fireEvent.click(screen.getAllByText("Cancelar")[0]);
    });
    expect(onCancel).toHaveBeenCalledTimes(1);
    expect(onGenerate).not.toHaveBeenCalled();
  });

  it("while generating, the actions are disabled", () => {
    renderPreview(true);
    expect(generateButton().disabled).toBe(true);
    const previewCancels = Array.from(document.querySelectorAll("ion-button")).filter((button) => button.textContent?.trim() === "Cancelar");
    expect(previewCancels).toHaveLength(2);
    previewCancels.forEach((button) => expect((button as any).disabled).toBe(true));
  });
});
