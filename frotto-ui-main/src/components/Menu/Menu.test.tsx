import { act, render, screen, waitFor } from "@testing-library/react";
import { IonApp } from "@ionic/react";
import { IonReactRouter } from "@ionic/react-router";
import Menu from "./Menu";
import accountService from "../../services/accountService";
import profileService from "../../services/profileService";
import { removeToken, setToken } from "../../services/localStorage/localstorage";

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
    jest.clearAllMocks();
    localStorage.clear();
    setToken("Bearer initial-token");
    mockedProfileService.getMe.mockResolvedValue({} as any);
  });

  afterEach(() => localStorage.clear());

  it("does not show the Administração/Billing item for a ROLE_USER account", async () => {
    mockedAccountService.getAccount.mockResolvedValue({ authorities: ["ROLE_USER"] });

    renderMenu();

    await waitFor(() => expect(mockedAccountService.getAccount).toHaveBeenCalled());
    expect(screen.queryByText("Painel do Administrador")).not.toBeInTheDocument();
    expect(screen.queryByText("Administração")).not.toBeInTheDocument();
  });

  it("shows the Administração/Billing item for a ROLE_ADMIN account", async () => {
    mockedAccountService.getAccount.mockResolvedValue({ authorities: ["ROLE_USER", "ROLE_ADMIN"] });

    renderMenu();

    expect(await screen.findByText("Painel do Administrador", {}, { timeout: 5000 })).toBeInTheDocument();
    expect(screen.getByText("Administração")).toBeInTheDocument();
  });

  it("treats a failed account lookup as non-admin (fails closed)", async () => {
    mockedAccountService.getAccount.mockRejectedValue(new Error("network error"));

    renderMenu();

    await waitFor(() => expect(mockedAccountService.getAccount).toHaveBeenCalled());
    expect(screen.queryByText("Painel do Administrador")).not.toBeInTheDocument();
  });

  it("does not show the admin item when authorities are empty", async () => {
    mockedAccountService.getAccount.mockResolvedValue({ authorities: [] });
    renderMenu();
    await waitFor(() => expect(mockedAccountService.getAccount).toHaveBeenCalled());
    expect(screen.queryByText("Painel do Administrador")).not.toBeInTheDocument();
  });

  it("does not show the admin item when authorities are undefined", async () => {
    mockedAccountService.getAccount.mockResolvedValue({ authorities: undefined });
    renderMenu();
    await waitFor(() => expect(mockedAccountService.getAccount).toHaveBeenCalled());
    expect(screen.queryByText("Painel do Administrador")).not.toBeInTheDocument();
  });

  it("clears ADMIN immediately on logout and stays hidden after USER logs in", async () => {
    mockedAccountService.getAccount
      .mockResolvedValueOnce({ authorities: ["ROLE_USER", "ROLE_ADMIN"] })
      .mockResolvedValueOnce({ authorities: ["ROLE_USER"] });
    renderMenu();
    expect(await screen.findByText("Painel do Administrador")).toBeInTheDocument();

    act(() => removeToken());
    expect(screen.queryByText("Painel do Administrador")).not.toBeInTheDocument();

    act(() => setToken("Bearer user-token"));
    await waitFor(() => expect(mockedAccountService.getAccount).toHaveBeenCalledTimes(2));
    expect(screen.queryByText("Painel do Administrador")).not.toBeInTheDocument();
  });

  it("re-evaluates USER to ADMIN in the same mounted menu", async () => {
    mockedAccountService.getAccount
      .mockResolvedValueOnce({ authorities: ["ROLE_USER"] })
      .mockResolvedValueOnce({ authorities: ["ROLE_USER", "ROLE_ADMIN"] });
    renderMenu();
    await waitFor(() => expect(mockedAccountService.getAccount).toHaveBeenCalledTimes(1));
    expect(screen.queryByText("Painel do Administrador")).not.toBeInTheDocument();

    act(() => removeToken());
    act(() => setToken("Bearer admin-token"));

    expect(await screen.findByText("Painel do Administrador")).toBeInTheDocument();
    expect(mockedAccountService.getAccount).toHaveBeenCalledTimes(2);
  });

  it("ignores an ADMIN response that arrives after the token was replaced", async () => {
    let resolveAdmin!: (value: { authorities: string[] }) => void;
    mockedAccountService.getAccount
      .mockImplementationOnce(() => new Promise((resolve) => { resolveAdmin = resolve; }))
      .mockResolvedValueOnce({ authorities: ["ROLE_USER"] });
    renderMenu();
    await waitFor(() => expect(mockedAccountService.getAccount).toHaveBeenCalledTimes(1));

    act(() => setToken("Bearer replacement-user-token"));
    await waitFor(() => expect(mockedAccountService.getAccount).toHaveBeenCalledTimes(2));
    await act(async () => resolveAdmin({ authorities: ["ROLE_ADMIN"] }));

    expect(screen.queryByText("Painel do Administrador")).not.toBeInTheDocument();
  });
});
