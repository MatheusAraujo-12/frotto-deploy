import endpoints from "../constants/endpoints";
import api from "./axios/axios";
import billingService from "./billingService";

jest.mock("./axios/axios", () => ({ __esModule: true, default: { get: jest.fn(), post: jest.fn() } }));
const mockedGet = api.get as jest.Mock;
const mockedPost = api.post as jest.Mock;

describe("billingService", () => {
  beforeEach(() => { mockedGet.mockReset(); mockedPost.mockReset(); });
  it("loads current billing", async () => {
    const data = { planCode: "FREE" };
    mockedGet.mockResolvedValue({ data });
    await expect(billingService.getMyBilling()).resolves.toBe(data);
    expect(mockedGet).toHaveBeenCalledWith(endpoints.BILLING_ME());
  });
  it("loads plans", async () => {
    mockedGet.mockResolvedValue({ data: [] });
    await expect(billingService.getPlans()).resolves.toEqual([]);
    expect(mockedGet).toHaveBeenCalledWith(endpoints.BILLING_PLANS());
  });
  it.each([35, 150])("requests backend preview for %s vehicles", async (vehicleCount) => {
    mockedGet.mockResolvedValue({ data: { vehicleCount } });
    await billingService.getPricePreview(vehicleCount);
    expect(mockedGet).toHaveBeenCalledWith(endpoints.BILLING_PRICE_PREVIEW({ query: { vehicleCount } }));
  });
  it("creates checkout using only the selected plan code", async () => {
    const data = { checkoutId: 1, planCode: "GOLD", checkoutUrl: "https://mp.test/checkout" };
    mockedPost.mockResolvedValue({ data });
    await expect(billingService.createCheckout("GOLD")).resolves.toBe(data);
    expect(mockedPost).toHaveBeenCalledWith(endpoints.BILLING_CHECKOUT(), { planCode: "GOLD" });
    expect(mockedPost.mock.calls[0][1]).not.toHaveProperty("price");
    expect(mockedPost.mock.calls[0][1]).not.toHaveProperty("userId");
    expect(mockedPost.mock.calls[0][1]).not.toHaveProperty("vehicleCount");
  });
});
