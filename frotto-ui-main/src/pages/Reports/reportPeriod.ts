import { endOfMonth, format, startOfMonth, subMonths } from "date-fns";
import { REPORT_PERIODS } from "../../constants/selectOptions";

export const PERIOD_MONTHS: Record<string, number> = {
  [REPORT_PERIODS.monthly]: 1,
  [REPORT_PERIODS.bimonthly]: 2,
  [REPORT_PERIODS.quarterly]: 3,
  [REPORT_PERIODS.semiannual]: 6,
  [REPORT_PERIODS.annual]: 12,
};

export interface PeriodRange {
  startDate: string;
  endDate: string;
}

export const resolvePeriodRange = (
  period: string,
  customStartDate?: string,
  customEndDate?: string
): PeriodRange => {
  if (period === REPORT_PERIODS.custom) {
    return {
      startDate: customStartDate ?? "",
      endDate: customEndDate ?? "",
    };
  }

  const months = PERIOD_MONTHS[period] ?? 12;
  const end = endOfMonth(new Date());
  const start = startOfMonth(subMonths(end, months - 1));

  return {
    startDate: format(start, "yyyy-MM-dd"),
    endDate: format(end, "yyyy-MM-dd"),
  };
};
