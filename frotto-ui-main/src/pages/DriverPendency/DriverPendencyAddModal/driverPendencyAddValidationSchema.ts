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
  };
};

export const driverPendencyAddValidationSchema = Yup.object().shape({
  id: Yup.number().nullable(),
  name: Yup.string().required(TEXT.requiredField),
  cost: Yup.number().typeError(TEXT.requiredField).required(TEXT.requiredField),
  date: Yup.string().required(TEXT.requiredField),
});
