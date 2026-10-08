import { act, fireEvent, render, screen } from "@testing-library/react";
import { IonApp } from "@ionic/react";
import { MemoryRouter, Route } from "react-router";
import InspectionNewMenu from "./InspectionNewMenu";

const renderMenu = () => {
  const onSingleInspection = jest.fn();
  render(
    <IonApp>
      <MemoryRouter initialEntries={["/menu/carros/7"]}>
        <InspectionNewMenu carId="7" onSingleInspection={onSingleInspection} />
        <Route path="*" render={({ location }) => <span data-testid="location">{location.pathname + location.search}</span>} />
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

  it("the checklists open the existing wizard for this car and come back here", async () => {
    renderMenu();
    await click("Novo");
    await click("Checklist de Devolução");

    expect(screen.getByTestId("location")).toHaveTextContent("/documents?checklist=DEVOLUCAO&carId=7&from=%2Fmenu%2Fcarros%2F7");
  });
});
