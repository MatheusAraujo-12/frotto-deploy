import { SubscriptionCancellationResultDTO, BillingCheckoutDTO, BillingMeDTO, BillingPaymentStateDTO, PlanChangePreviewDTO, PlanChangeResultDTO, PlanCode, PlanDTO, PlanUpgradeStatusDTO, PricePreviewDTO } from "../constants/BillingModels";
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
  /**
   * 5G.12 / 5G.12.1: changes the SAME Mercado Pago recurrence - never a second subscription. An
   * upgrade returns a prorated payment link (status UPGRADE_PAYMENT_REQUIRED) and is only applied
   * after the backend confirms the payment; a downgrade is scheduled for the end of the cycle.
   */
  async changePlan(targetPlanCode: PlanCode): Promise<PlanChangeResultDTO> {
    const { data } = await api.post<PlanChangeResultDTO>(endpoints.BILLING_CHANGE_PLAN(), { targetPlanCode });
    return data;
  },
  /** 5G.12.1: prorated amount / next recurring price computed by the backend - the frontend never calculates prices. */
  async previewChangePlan(targetPlanCode: PlanCode): Promise<PlanChangePreviewDTO> {
    const { data } = await api.get<PlanChangePreviewDTO>(endpoints.BILLING_CHANGE_PLAN_PREVIEW({ query: { targetPlanCode } }));
    return data;
  },
  /** 5G.12.1: removes the scheduled downgrade once Mercado Pago confirms the original recurring amount. No charge. */
  async undoDowngrade(): Promise<PlanChangeResultDTO> {
    const { data } = await api.post<PlanChangeResultDTO>(endpoints.BILLING_UNDO_DOWNGRADE());
    return data;
  },
  /** 5G.12.1: latest prorated upgrade, reconciled server-side (never trusts the checkout return URL). */
  async getPlanUpgradeStatus(): Promise<PlanUpgradeStatusDTO> {
    const { data } = await api.get<PlanUpgradeStatusDTO>(endpoints.BILLING_PLAN_UPGRADE());
    return data;
  },
};

export default billingService;
