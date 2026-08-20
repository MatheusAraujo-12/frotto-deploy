import { render, screen, waitFor } from "@testing-library/react";
import { IonApp } from "@ionic/react";
import { IonReactRouter } from "@ionic/react-router";
import Menu from "./Menu";
import accountService from "../../services/accountService";
import profileService from "../../services/profileService";

jest.mock("../../services/accountService");
jest.mock("../../services/profileService");

const mockedAccountService = accountService as jest.Mocked<typeof accountService>;
const mockedProfileService = profileService as jest.Mocked<typeof profileService>;

const renderMenu = () =>
  render(
    <IonApp>
      <IonReactRouter>
        <Menu />
      </IonReactRouter>
    </IonApp>
  );

describe("Menu - Billing Etapa 4A admin visibility", () => {
  beforeEach(() => {
    mockedProfileService.getMe.mockResolvedValue({} as any);
  });

  it("does not show the Administração/Billing item for a ROLE_USER account", async () => {
    mockedAccountService.getAccount.mockResolvedValue({ authorities: ["ROLE_USER"] });

    renderMenu();

    await waitFor(() => expect(mockedAccountService.getAccount).toHaveBeenCalled());
    expect(screen.queryByText("Billing")).not.toBeInTheDocument();
    expect(screen.queryByText("Administração")).not.toBeInTheDocument();
  });

  it("shows the Administração/Billing item for a ROLE_ADMIN account", async () => {
    mockedAccountService.getAccount.mockResolvedValue({ authorities: ["ROLE_USER", "ROLE_ADMIN"] });

    renderMenu();

    expect(await screen.findByText("Billing", {}, { timeout: 5000 })).toBeInTheDocument();
    expect(screen.getByText("Administração")).toBeInTheDocument();
  });

  it("treats a failed account lookup as non-admin (fails closed)", async () => {
    mockedAccountService.getAccount.mockRejectedValue(new Error("network error"));

    renderMenu();

    await waitFor(() => expect(mockedAccountService.getAccount).toHaveBeenCalled());
    expect(screen.queryByText("Billing")).not.toBeInTheDocument();
  });
});
