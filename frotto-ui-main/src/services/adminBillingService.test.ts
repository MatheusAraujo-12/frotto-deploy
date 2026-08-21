import api from "./axios/axios";
import adminBillingService from "./adminBillingService";

describe("adminBillingService authentication", () => {
  afterEach(() => localStorage.clear());

  it("uses the shared client and sends its current Bearer token for search", async () => {
    localStorage.setItem("TOKEN", "Bearer admin-token");
    let authorization: unknown;
    const previousAdapter = api.defaults.adapter;

    api.defaults.adapter = async (config) => {
      authorization = config.headers?.get("Authorization");
      return { data: [], status: 200, statusText: "OK", headers: {}, config };
    };

    try {
      await adminBillingService.searchUsers("cliente");
    } finally {
      api.defaults.adapter = previousAdapter;
    }

    expect(authorization).toBe("Bearer admin-token");
  });
});
