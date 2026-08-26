import endpoints from "../constants/endpoints";
import api from "./axios/axios";
import billingService from "./billingService";

jest.mock("./axios/axios", () => ({ __esModule: true, default: { get: jest.fn() } }));
const mockedGet = api.get as jest.Mock;

describe("billingService", () => {
  beforeEach(() => mockedGet.mockReset());
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
});
