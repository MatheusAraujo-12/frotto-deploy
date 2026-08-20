import { render, screen, waitFor } from "@testing-library/react";
import { IonApp } from "@ionic/react";
import { IonReactRouter } from "@ionic/react-router";
import AdminBillingPage from "./AdminBillingPage";
import accountService from "../../services/accountService";

// Full interaction-level tests (search -> select -> grant modal -> submit, revoke, preview
// grandfathering) are covered as fast, reliable pure-function tests in
// adminBillingPageLogic.test.ts instead of here - see that file's header comment and the
// Billing Etapa 4A report for why: mounting this page's full card/list UI in jsdom proved slow
// enough (single renders taking well over a minute in this environment, with no existing
// Ionic-component-testing precedent in this project to build on) that driving every
// interaction through it was not a reliable way to verify the actual business rules. What is
// verified here is the one thing that must render via full mount: the ROLE_ADMIN gate itself.
jest.mock("../../services/accountService");

const mockedAccountService = accountService as jest.Mocked<typeof accountService>;

const renderPage = () =>
  render(
    <IonApp>
      <IonReactRouter>
        <AdminBillingPage />
      </IonReactRouter>
    </IonApp>
  );

describe("AdminBillingPage - ROLE_ADMIN gate", () => {
  it("does not render the admin panel content for a non-admin account", async () => {
    mockedAccountService.getAccount.mockResolvedValue({ authorities: ["ROLE_USER"] });

    renderPage();

    await waitFor(() => expect(mockedAccountService.getAccount).toHaveBeenCalled());
    expect(screen.queryByText("Pesquisar usuário")).not.toBeInTheDocument();
  }, 30000);

  it("renders the admin panel content for an admin account", async () => {
    mockedAccountService.getAccount.mockResolvedValue({ authorities: ["ROLE_ADMIN"] });

    renderPage();

    expect(await screen.findByText("Pesquisar usuário", {}, { timeout: 20000 })).toBeInTheDocument();
  }, 30000);
});
