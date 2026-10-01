import { render, screen } from "@testing-library/react";
import FrottoModal from "./FrottoModal";

const renderModal = (cancelVariant?: "default" | "danger") =>
  render(
    <FrottoModal
      pageId="test-modal"
      title="Título"
      onCancel={jest.fn()}
      primaryLabel="Salvar"
      onPrimaryAction={jest.fn()}
      cancelVariant={cancelVariant}
    >
      <p>corpo</p>
    </FrottoModal>
  );

describe("FrottoModal - semântica de Cancelar", () => {
  it("keeps the previous Cancelar style by default (consumers not opted in, e.g. Billing)", () => {
    renderModal();
    const cancel = screen.getByText("Cancelar").closest("ion-button");
    expect(cancel).toHaveClass("app-outline-btn");
    expect(cancel).not.toHaveClass("app-cancel-btn");
  });

  it("renders Cancelar as the red cancel action when opted in, primary action stays primary", () => {
    renderModal("danger");
    expect(screen.getByText("Cancelar").closest("ion-button")).toHaveClass("app-cancel-btn");
    expect(screen.getByText("Salvar").closest("ion-button")).toHaveClass("app-primary-btn");
  });
});
