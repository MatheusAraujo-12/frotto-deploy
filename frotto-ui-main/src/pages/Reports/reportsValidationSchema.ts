import { DATE_TODAY, YEAR_NOW } from "./../../constants/form";

import * as Yup from "yup";
import { TEXT } from "../../constants/texts";
import { REPORTS } from "../../constants/selectOptions";

export interface ReportModel {
  report: string;
  group: string;
  date: string;
  year: number;
}

export const initialReportsValues = () => {
  return {
    report: REPORTS.month,
    group: "",
    date: DATE_TODAY,
    year: YEAR_NOW
  };
};

export const reportsValidationSchema = Yup.object().shape({
  report: Yup.string().required(TEXT.requiredField),
  group: Yup.string().required(TEXT.requiredField),
  date: Yup.string().required(TEXT.requiredField),
  year: Yup.number().required(TEXT.requiredField),
});
