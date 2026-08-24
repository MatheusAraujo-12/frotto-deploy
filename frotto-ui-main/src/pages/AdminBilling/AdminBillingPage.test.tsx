import { act, render, screen } from "@testing-library/react";
import { IonApp } from "@ionic/react";
import { MemoryRouter } from "react-router-dom";
import AdminBillingPage from "./AdminBillingPage";
import accountService from "../../services/accountService";
import { setToken } from "../../services/localStorage/localstorage";

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
  beforeEach(() => {
    jest.clearAllMocks();
    localStorage.clear();
    setToken("Bearer initial-token");
  });

  afterEach(() => localStorage.clear());

  it("redirects a ROLE_USER account", async () => {
    mockedAccountService.getAccount.mockResolvedValue({ authorities: ["ROLE_USER"] });
    renderPage();
    expect(await screen.findByTestId("redirect-target", {}, { timeout: 20000 })).toHaveTextContent("/menu/carros");
  }, 30000);

  it("renders the admin panel for a ROLE_ADMIN account", async () => {
    mockedAccountService.getAccount.mockResolvedValue({ authorities: ["ROLE_ADMIN"] });
    renderPage();
    expect(await screen.findByText("Pesquisar usuário", {}, { timeout: 20000 })).toBeInTheDocument();
  }, 30000);

  it("redirects when the account lookup fails", async () => {
    mockedAccountService.getAccount.mockRejectedValue(new Error("network error"));
    renderPage();
    expect(await screen.findByTestId("redirect-target", {}, { timeout: 20000 })).toHaveTextContent("/menu/carros");
  }, 30000);

  it("does not retain ADMIN authorization after the token changes to a ROLE_USER", async () => {
    mockedAccountService.getAccount
      .mockResolvedValueOnce({ authorities: ["ROLE_ADMIN"] })
      .mockResolvedValueOnce({ authorities: ["ROLE_USER"] });
    renderPage();
    expect(await screen.findByText("Pesquisar usuário", {}, { timeout: 20000 })).toBeInTheDocument();

    act(() => setToken("Bearer user-token"));

    expect(await screen.findByTestId("redirect-target", {}, { timeout: 20000 })).toHaveTextContent("/menu/carros");
  }, 90000);
});
