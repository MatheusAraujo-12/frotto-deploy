import { CommissionConfig, CommissionType } from "../constants/CarModels";

const toNumber = (value: unknown): number => {
  const parsed = Number(value);
  return Number.isFinite(parsed) ? parsed : 0;
};

export const calcCommission = (profitValue: number, config?: CommissionConfig) => {
  if (!config) {
    return 0;
  }

  const profit = toNumber(profitValue);
  const commissionPercent =
    toNumber(config.commissionPercent ?? config.administrationFee ?? 0);
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
    const commissionFixed = toNumber(config.commissionFixed);
    if (commissionFixed <= 0) {
      return 0;
    }
    return commissionFixed;
  }

  if (profit <= 0) {
    return 0;
  }

  if (commissionPercent <= 0) {
    return 0;
  }

  return (profit * commissionPercent) / 100;
};
