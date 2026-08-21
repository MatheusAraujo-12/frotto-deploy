import { render, screen } from "@testing-library/react";
import { IonApp } from "@ionic/react";
import { MemoryRouter } from "react-router-dom";
import AdminBillingPage from "./AdminBillingPage";
import accountService from "../../services/accountService";

jest.mock("../../services/accountService");
jest.mock("react-router-dom", () => ({
  ...jest.requireActual("react-router-dom"),
  Redirect: ({ to }: { to: string }) => <div data-testid="redirect-target">{to}</div>,
}));

const mockedAccountService = accountService as jest.Mocked<typeof accountService>;

const renderPage = () =>
  render(
    <IonApp>
      <MemoryRouter initialEntries={["/menu/admin/billing"]}>
        <AdminBillingPage />
      </MemoryRouter>
    </IonApp>
  );

describe("AdminBillingPage - ROLE_ADMIN gate", () => {
  it("redirects a ROLE_USER account", async () => {
    mockedAccountService.getAccount.mockResolvedValue({ authorities: ["ROLE_USER"] });
    renderPage();
    expect(await screen.findByTestId("redirect-target")).toHaveTextContent("/menu/carros");
  }, 30000);

  it("renders the admin panel for a ROLE_ADMIN account", async () => {
    mockedAccountService.getAccount.mockResolvedValue({ authorities: ["ROLE_ADMIN"] });
    renderPage();
    expect(await screen.findByText("Pesquisar usuário", {}, { timeout: 20000 })).toBeInTheDocument();
  }, 30000);

  it("redirects when the account lookup fails", async () => {
    mockedAccountService.getAccount.mockRejectedValue(new Error("network error"));
    renderPage();
    expect(await screen.findByTestId("redirect-target")).toHaveTextContent("/menu/carros");
  }, 30000);
});
