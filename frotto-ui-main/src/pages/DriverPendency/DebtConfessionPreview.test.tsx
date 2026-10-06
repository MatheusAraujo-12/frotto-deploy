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

describe("Prévia da Confissão de Dívida (dados reais do backend)", () => {
  beforeAll(() => {
    (global as any).IntersectionObserver = class {
      observe() {}
      unobserve() {}
      disconnect() {}
    };
  });

  it("shows the debtor, every debt with its own origin and the backend total", () => {
    renderPreview();
    const text = textOf();

    expect(screen.getByText("João Silva")).toBeInTheDocument();
    expect(text).toContain("CPF: 111.111.111-11");
    expect(text).toContain("Origem: Pendências");
    expect(text).toContain("Veículo de origem: Onix - ONX1A11");
    expect(text).toContain("Veículo de origem: HB20 - HBV2B22");
    expect(text).toContain("Veículo de origem: Argo - ARG3C33");
    expect(text).toContain("AIT 123");
    expect(text).toContain("20/08/2026");
    // Partially paid: the backend balance, with the original and paid amounts.
    expect(text).toMatch(/Valor original R\$\s?1\.000,00 · já pago R\$\s?400,00 · saldo R\$\s?600,00/);
    expect(text).toMatch(/Total da Confissão\s?R\$\s?1\.150,00/);
  });

  it("Cancelar only closes; Gerar PDF sends the terms (à vista by default)", async () => {
    const { onCancel, onGenerate } = renderPreview();

    await act(async () => {
      fireEvent.click(screen.getAllByText("Cancelar")[0]);
    });
    expect(onCancel).toHaveBeenCalledTimes(1);
    expect(onGenerate).not.toHaveBeenCalled();

    await act(async () => {
      fireEvent.click(screen.getByText("Gerar PDF"));
    });
    expect(onGenerate).toHaveBeenCalledWith({ formaPagamento: "A_VISTA" });
  });

  it("while generating, the actions are disabled", () => {
    renderPreview(true);
    // ion-button receives "disabled" as a web-component property.
    expect((screen.getByText("Gerar PDF").closest("ion-button") as any).disabled).toBe(true);
    // The preview's own Cancelar buttons (header and footer); the date picker has its own unrelated one.
    const previewCancels = Array.from(document.querySelectorAll("ion-button")).filter(
      (button) => button.textContent?.trim() === "Cancelar" && !button.closest("ion-modal")
    );
    expect(previewCancels).toHaveLength(2);
    previewCancels.forEach((button) => expect((button as any).disabled).toBe(true));
  });
});
