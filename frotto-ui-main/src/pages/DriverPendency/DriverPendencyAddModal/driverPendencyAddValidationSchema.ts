import { DriverPendencyModel } from "../../../constants/CarModels";
import * as Yup from "yup";
import { TEXT } from "../../../constants/texts";
import { DATE_TODAY } from "../../../constants/form";

export const initialDriverPendencyValues = (
  initialValues: DriverPendencyModel
) => {
  return {
    id: initialValues.id || undefined,
    name: initialValues.name || "",
    cost: initialValues.cost || 0,
    date: initialValues.date || DATE_TODAY,
    note: initialValues.note || "",
    status: initialValues.status || "OPEN",
    paidAt: initialValues.paidAt || undefined,
    paidAmount: initialValues.paidAmount || 0,
    remainingAmount:
      initialValues.remainingAmount ??
      (typeof initialValues.cost === "number" ? initialValues.cost : 0),
    paymentMethod: initialValues.paymentMethod || "",
  };
};

export const driverPendencyAddValidationSchema = Yup.object().shape({
  id: Yup.number().nullable(),
  name: Yup.string().required(TEXT.requiredField),
  cost: Yup.number()
    .typeError(TEXT.requiredField)
    .min(0, TEXT.minFieldNumber("0"))
    .required(TEXT.requiredField),
  date: Yup.string().required(TEXT.requiredField),
  note: Yup.string().max(255, TEXT.maxFieldSize("255")).nullable(),
  paidAmount: Yup.number()
    .transform((value, originalValue) =>
      originalValue === "" || originalValue === null || originalValue === undefined
        ? 0
        : value
    )
    .min(0, TEXT.minFieldNumber("0"))
    .max(Yup.ref("cost"), "Valor pago nao pode ser maior que a divida")
    .nullable(),
  paymentMethod: Yup.string().max(60, TEXT.maxFieldSize("60")).nullable(),
});
