import { BillingMeDTO, PlanDTO, PricePreviewDTO } from "../constants/BillingModels";
import endpoints from "../constants/endpoints";
import api from "./axios/axios";

const billingService = {
  async getMyBilling(): Promise<BillingMeDTO> {
    const { data } = await api.get<BillingMeDTO>(endpoints.BILLING_ME());
    return data;
  },
  async getPlans(): Promise<PlanDTO[]> {
    const { data } = await api.get<PlanDTO[]>(endpoints.BILLING_PLANS());
    return data;
  },
  async getPricePreview(vehicleCount: number): Promise<PricePreviewDTO> {
    const { data } = await api.get<PricePreviewDTO>(
      endpoints.BILLING_PRICE_PREVIEW({ query: { vehicleCount } })
    );
    return data;
  },
};

export default billingService;
