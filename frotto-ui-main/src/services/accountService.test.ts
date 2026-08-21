import endpoints from "../constants/endpoints";
import accountService from "./accountService";
import api from "./axios/axios";

jest.mock("./axios/axios");

const mockedApi = api as jest.Mocked<typeof api>;

describe("accountService", () => {
  it("returns the AccountDTO response data instead of the AxiosResponse wrapper", async () => {
    const account = { authorities: ["ROLE_USER", "ROLE_ADMIN"] };
    mockedApi.get.mockResolvedValue({ data: account } as any);

    await expect(accountService.getAccount()).resolves.toBe(account);
    expect(mockedApi.get).toHaveBeenCalledWith(endpoints.ACCOUNT());
  });
});
