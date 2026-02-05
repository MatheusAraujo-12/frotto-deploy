import { CommissionConfig, CommissionType } from "../constants/CarModels";

export const calcCommission = (profit: number, config?: CommissionConfig) => {
  if (!config || !Number.isFinite(profit) || profit <= 0) {
    return 0;
  }

  const commissionPercent =
    config.commissionPercent ?? config.administrationFee ?? 0;
  let commissionType: CommissionType = config.commissionType ?? "PERCENT_PROFIT";

  if (
    !config.commissionType &&
    config.commissionFixed != null &&
    config.commissionPercent == null &&
    config.administrationFee == null
  ) {
    commissionType = "FIXED";
  }

  if (commissionType === "FIXED") {
    if (config.commissionFixed == null || config.commissionFixed < 0) {
      return 0;
    }
    return config.commissionFixed;
  }

  if (!Number.isFinite(commissionPercent) || commissionPercent <= 0) {
    return 0;
  }

  return (profit * commissionPercent) / 100;
};
