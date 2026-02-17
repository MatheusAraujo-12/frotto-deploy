export type DebtItemTypeFilter = "true" | "false" | "all";

export interface DebtItemTypeModel {
  id: number;
  name: string;
  active: boolean;
  sortOrder: number;
  createdAt?: string;
  updatedAt?: string;
}

export interface DebtItemTypeSavePayload {
  name?: string;
  active?: boolean;
  sortOrder?: number;
}
