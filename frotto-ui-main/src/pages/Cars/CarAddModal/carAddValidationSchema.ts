import {
  CarAdminStatus,
  CarModel,
  CommissionType,
} from "../../../constants/CarModels";
import * as Yup from "yup";
import { TEXT } from "../../../constants/texts";
import {
  buildCarLegacyName,
  resolveCarIdentity,
} from "../../../components/Car/carIdentity";

const CAR_ADMIN_STATUS_VALUES: CarAdminStatus[] = [
  "ATIVO",
  "RETIRADO",
  "A_VENDA",
  "MANUTENCAO",
  "BLOQUEADO",
];

export const initialCarValues = (initialValues: CarModel) => {
  const identity = resolveCarIdentity(initialValues);
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
    name: buildCarLegacyName(initialValues) || initialValues.name || "",
    brand: identity.brand || "",
    model: identity.model || "",
    odometer: initialValues.odometer || 0,
    initialValue: initialValues.initialValue || 0,
    commissionType,
    commissionPercent,
    commissionFixed,
    plate: initialValues.plate || "",
    color: initialValues.color || "",
    year: initialValues.year || 2021,
    group: initialValues.group || "",
    active: initialValues.active ?? true,
    adminStatus: initialValues.adminStatus || "ATIVO",
  };
};

const requireBrandOrModel = (fieldName: "brand" | "model") =>
  Yup.string().test({
    name: `require-brand-or-model-${fieldName}`,
    message: TEXT.requiredField,
    test(value) {
      const siblingField = fieldName === "brand" ? "model" : "brand";
      const siblingValue = this.parent?.[siblingField];
      return Boolean(
        `${value || ""}`.trim() || `${siblingValue || ""}`.trim()
      );
    },
  });

export const carAddValidationSchema = Yup.object().shape({
  id: Yup.number().nullable(),
  name: Yup.string(),
  brand: requireBrandOrModel("brand"),
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
  model: requireBrandOrModel("model"),
  color: Yup.string(),
  year: Yup.number(),
  group: Yup.string().required(TEXT.requiredField),
  active: Yup.boolean(),
  adminStatus: Yup.mixed<CarAdminStatus>()
    .oneOf(CAR_ADMIN_STATUS_VALUES)
    .required(TEXT.requiredField),
});
