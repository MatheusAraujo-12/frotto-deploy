import { DATE_TODAY, YEAR_NOW } from "./../../constants/form";

import * as Yup from "yup";
import { TEXT } from "../../constants/texts";
import { REPORTS, REPORT_PERIODS } from "../../constants/selectOptions";

export interface ReportModel {
  report: string;
  group: string;
  date: string;
  year: number;
  period: string;
  customStartDate?: string;
  customEndDate?: string;
}

export const initialReportsValues = () => {
  return {
    report: REPORTS.month,
    group: "",
    date: DATE_TODAY,
    year: YEAR_NOW,
    period: REPORT_PERIODS.annual,
    customStartDate: "",
    customEndDate: DATE_TODAY,
  };
};

export const reportsValidationSchema = Yup.object().shape({
  report: Yup.string().required(TEXT.requiredField),
  group: Yup.string().required(TEXT.requiredField),
  date: Yup.string().required(TEXT.requiredField),
  year: Yup.number().required(TEXT.requiredField),
  period: Yup.string().when("report", {
    is: REPORTS.history,
    then: (schema) => schema.required(TEXT.requiredField),
    otherwise: (schema) => schema.notRequired(),
  }),
  customStartDate: Yup.string().when(["report", "period"], {
    is: (report: string, period: string) =>
      report === REPORTS.history && period === REPORT_PERIODS.custom,
    then: (schema) => schema.required(TEXT.requiredField),
    otherwise: (schema) => schema.notRequired(),
  }),
  customEndDate: Yup.string().when(["report", "period"], {
    is: (report: string, period: string) =>
      report === REPORTS.history && period === REPORT_PERIODS.custom,
    then: (schema) =>
      schema
        .required(TEXT.requiredField)
        .test(
          "end-after-start",
          TEXT.endDateBeforeStartDate,
          (value, context) =>
            !value ||
            !context.parent.customStartDate ||
            value >= context.parent.customStartDate
        ),
    otherwise: (schema) => schema.notRequired(),
  }),
});
