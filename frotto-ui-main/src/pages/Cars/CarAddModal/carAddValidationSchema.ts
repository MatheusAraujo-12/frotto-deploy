import { CarModel } from "../../../constants/CarModels";
import * as Yup from "yup";
import { TEXT } from "../../../constants/texts";

export const initialCarValues = (initialValues: CarModel) => {
  return {
    id: initialValues.id || undefined,
    name: initialValues.name || "",
    odometer: initialValues.odometer || 0,
    initialValue: initialValues.initialValue || 0,
    administrationFee: initialValues.administrationFee || 0,
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
  administrationFee: Yup.number()
    .typeError(TEXT.requiredField)
    .required(TEXT.requiredField)
    .max(100, TEXT.maxFieldNumber("100%"))
    .min(0, TEXT.minFieldNumber("0%")),
  plate: Yup.string().required(TEXT.requiredField),
  model: Yup.string(),
  color: Yup.string(),
  year: Yup.number(),
  group: Yup.string().required(TEXT.requiredField),
  active: Yup.boolean(),
});
