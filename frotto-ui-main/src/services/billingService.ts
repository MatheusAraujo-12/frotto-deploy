import { SubscriptionCancellationResultDTO, BillingCheckoutDTO, BillingMeDTO, BillingPaymentStateDTO, PlanChangeResultDTO, PlanCode, PlanDTO, PricePreviewDTO } from "../constants/BillingModels";
import endpoints from "../constants/endpoints";
import api from "./axios/axios";

const billingService = {
  async cancelSubscription(): Promise<SubscriptionCancellationResultDTO> {
    const { data } = await api.post<SubscriptionCancellationResultDTO>(endpoints.BILLING_CANCEL());
    return data;
  },
  async getMyBilling(): Promise<BillingMeDTO> {
    const { data } = await api.get<BillingMeDTO>(endpoints.BILLING_ME());
    return data;
  },
  async getPlans(): Promise<PlanDTO[]> {
    const { data } = await api.get<PlanDTO[]>(endpoints.BILLING_PLANS());
    return data;
  },
  async getBillingPaymentState(): Promise<BillingPaymentStateDTO> {
    const { data } = await api.get<BillingPaymentStateDTO>(endpoints.BILLING_PAYMENT_STATE());
    return data;
  },
  /** planCode prices exactly that plan (may be above what vehicleCount alone would recommend) - never guessed on the frontend, see PricingService#calculatePriceForPlan. */
  async getPricePreview(vehicleCount: number, planCode?: PlanCode): Promise<PricePreviewDTO> {
    const { data } = await api.get<PricePreviewDTO>(
      endpoints.BILLING_PRICE_PREVIEW({ query: planCode ? { vehicleCount, planCode } : { vehicleCount } })
    );
    return data;
  },
  async createCheckout(planCode: PlanCode): Promise<BillingCheckoutDTO> {
    const { data } = await api.post<BillingCheckoutDTO>(endpoints.BILLING_CHECKOUT(), { planCode });
    return data;
  },
  /** 5G.12: changes the SAME Mercado Pago recurrence's plan (upgrade immediate, downgrade scheduled) - never creates a new checkout. */
  async changePlan(targetPlanCode: PlanCode): Promise<PlanChangeResultDTO> {
    const { data } = await api.post<PlanChangeResultDTO>(endpoints.BILLING_CHANGE_PLAN(), { targetPlanCode });
    return data;
  },
};

export default billingService;
