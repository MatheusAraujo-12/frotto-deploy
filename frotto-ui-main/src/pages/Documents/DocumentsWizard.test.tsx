import { render, screen } from "@testing-library/react";
import { WIZARD_STEPS, WizardStepHead, WizardStepper } from "./DocumentsPage";

describe("Documents wizard - indicador de etapas", () => {
  it("marks done, current and upcoming steps (current exposed as aria-current=step)", () => {
    const { container } = render(<WizardStepper current={2} />);
    const items = container.querySelectorAll(".documents-stepper__item");
    expect(items).toHaveLength(3);
    expect(items[0]).toHaveClass("documents-stepper__item--done");
    expect(items[1]).toHaveClass("documents-stepper__item--current");
    expect(items[1]).toHaveAttribute("aria-current", "step");
    expect(items[2]).toHaveClass("documents-stepper__item--upcoming");
    WIZARD_STEPS.forEach(({ title }) => expect(screen.getByText(title)).toBeInTheDocument());
  });

  it("step head shows position, title and short description; step 3 uses the document type", () => {
    const { rerender } = render(<WizardStepHead step={1} />);
    expect(screen.getByText("Passo 1 de 3")).toBeInTheDocument();
    expect(screen.getByText("Motorista e carro")).toBeInTheDocument();

    rerender(<WizardStepHead step={3} typeLabel="Confissão de Dívida" />);
    expect(screen.getByText("Passo 3 de 3")).toBeInTheDocument();
    expect(screen.getByText("Confissão de Dívida")).toBeInTheDocument();
    expect(screen.getByText("Dados que vão no documento.")).toBeInTheDocument();
  });
});
