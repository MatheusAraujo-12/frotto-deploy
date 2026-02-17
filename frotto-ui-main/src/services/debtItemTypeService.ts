import {
  DebtItemTypeFilter,
  DebtItemTypeModel,
  DebtItemTypeSavePayload,
} from "../constants/DebtItemTypeModels";
import endpoints from "../constants/endpoints";
import api from "./axios/axios";

const debtItemTypeService = {
  async listDebtItemTypes(active: DebtItemTypeFilter = "true"): Promise<DebtItemTypeModel[]> {
    const { data } = await api.get<DebtItemTypeModel[]>(
      endpoints.DEBT_ITEM_TYPES({ query: { active } })
    );
    return Array.isArray(data) ? data : [];
  },

  async createDebtItemType(payload: DebtItemTypeSavePayload): Promise<DebtItemTypeModel> {
    const { data } = await api.post<DebtItemTypeModel>(endpoints.DEBT_ITEM_TYPES(), payload);
    return data;
  },

  async updateDebtItemType(id: number, payload: DebtItemTypeSavePayload): Promise<DebtItemTypeModel> {
    const { data } = await api.patch<DebtItemTypeModel>(
      endpoints.DEBT_ITEM_TYPE({ pathVariables: { id } }),
      payload
    );
    return data;
  },

  async deactivateDebtItemType(id: number): Promise<void> {
    await api.delete(endpoints.DEBT_ITEM_TYPE({ pathVariables: { id } }));
  },
};

export default debtItemTypeService;
