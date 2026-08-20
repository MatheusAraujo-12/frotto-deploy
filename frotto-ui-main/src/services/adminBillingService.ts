import {
  AdminBillingUser,
  AdminUserSearchResult,
  GrandfatherPreview,
  GrandfatherResult,
  GrantPlanPayload,
  SubscriptionAdminResult,
} from "../constants/AdminBillingModels";
import endpoints from "../constants/endpoints";
import api from "./axios/axios";

const adminBillingService = {
  async searchUsers(query: string): Promise<AdminUserSearchResult[]> {
    const { data } = await api.get<AdminUserSearchResult[]>(
      endpoints.ADMIN_BILLING_USERS_SEARCH({ query: { query } })
    );
    return Array.isArray(data) ? data : [];
  },

  async getUserBilling(userId: number): Promise<AdminBillingUser> {
    const { data } = await api.get<AdminBillingUser>(
      endpoints.ADMIN_BILLING_USER({ pathVariables: { userId } })
    );
    return data;
  },

  async grantPlan(payload: GrantPlanPayload): Promise<SubscriptionAdminResult> {
    const { data } = await api.post<SubscriptionAdminResult>(endpoints.ADMIN_BILLING_GRANTS(), payload);
    return data;
  },

  async revokeGrant(subscriptionId: number): Promise<SubscriptionAdminResult> {
    const { data } = await api.post<SubscriptionAdminResult>(
      endpoints.ADMIN_BILLING_GRANT_REVOKE({ pathVariables: { subscriptionId } })
    );
    return data;
  },

  async previewGrandfathering(userId: number): Promise<GrandfatherPreview> {
    const { data } = await api.get<GrandfatherPreview>(
      endpoints.ADMIN_BILLING_GRANDFATHER_PREVIEW({ pathVariables: { userId } })
    );
    return data;
  },

  async applyGrandfathering(userId: number): Promise<GrandfatherResult> {
    const { data } = await api.post<GrandfatherResult>(
      endpoints.ADMIN_BILLING_GRANDFATHER_APPLY({ pathVariables: { userId } })
    );
    return data;
  },
};

export default adminBillingService;
