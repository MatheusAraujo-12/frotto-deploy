import { CarModel, CommissionType } from "../../../constants/CarModels";
import * as Yup from "yup";
import { TEXT } from "../../../constants/texts";

export const initialCarValues = (initialValues: CarModel) => {
  const commissionPercent =
    initialValues.commissionPercent ??
    initialValues.administrationFee ??
    0;
  const commissionFixed = initialValues.commissionFixed ?? 0;
  const commissionType: CommissionType =
    initialValues.commissionType ??
    (initialValues.commissionFixed != null &&
    initialValues.commissionPercent == null &&
    initialValues.administrationFee == null
      ? "FIXED"
      : "PERCENT_PROFIT");

  return {
    id: initialValues.id || undefined,
    name: initialValues.name || "",
    odometer: initialValues.odometer || 0,
    initialValue: initialValues.initialValue || 0,
    commissionType,
    commissionPercent,
    commissionFixed,
    plate: initialValues.plate || "",
    model: initialValues.model || "",
    color: initialValues.color || "",
    year: initialValues.year || 2021,
    group: initialValues.group || "",
    active: initialValues.active || true,
  };
};

export const carAddValidationSchema = Yup.object().shape({
  id: Yup.number().nullable(),
  name: Yup.string().required(TEXT.requiredField),
  initialValue: Yup.number().typeError(TEXT.requiredField),
  odometer: Yup.number()
    .typeError(TEXT.requiredField)
    .required(TEXT.requiredField),
  commissionType: Yup.mixed<CommissionType>()
    .oneOf(["PERCENT_PROFIT", "FIXED"])
    .required(TEXT.requiredField),
  commissionPercent: Yup.number().when("commissionType", {
    is: "PERCENT_PROFIT",
    then: (schema) =>
      schema
        .typeError(TEXT.requiredField)
        .required(TEXT.requiredField)
        .max(100, TEXT.maxFieldNumber("100%"))
        .min(0, TEXT.minFieldNumber("0%")),
    otherwise: (schema) => schema.notRequired(),
  }),
  commissionFixed: Yup.number().when("commissionType", {
    is: "FIXED",
    then: (schema) =>
      schema
        .typeError(TEXT.requiredField)
        .required(TEXT.requiredField)
        .min(0, TEXT.minFieldNumber("0")),
    otherwise: (schema) => schema.notRequired(),
  }),
  plate: Yup.string().required(TEXT.requiredField),
  model: Yup.string(),
  color: Yup.string(),
  year: Yup.number(),
  group: Yup.string().required(TEXT.requiredField),
  active: Yup.boolean(),
});
