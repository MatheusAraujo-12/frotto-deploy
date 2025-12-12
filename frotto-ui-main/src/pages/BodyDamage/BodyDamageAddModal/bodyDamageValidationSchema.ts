import { CarBodyDamageModel } from "../../../constants/CarModels";
import * as Yup from "yup";
import { TEXT } from "../../../constants/texts";
import { DATE_TODAY } from "../../../constants/form";

export const initialBodyValues = (initialValues: CarBodyDamageModel) => {
  return {
    id: initialValues.id || undefined,
    date: initialValues.date || DATE_TODAY,
    responsible: initialValues.responsible || "",
    part: initialValues.part || "",
    cost: initialValues.cost || 0,
    resolved: initialValues.resolved || false,
    imagePath: initialValues.imagePath || undefined,
    imagePath2: initialValues.imagePath2 || undefined,
  };
};

export const bodyDamageAddValidationSchema = Yup.object().shape({
  id: Yup.number().nullable(),
  date: Yup.string().required(TEXT.requiredField),
  part: Yup.string().required(TEXT.requiredField),
  cost: Yup.number().typeError(TEXT.requiredField).required(TEXT.requiredField),
  responsible: Yup.string(),
  resolved: Yup.boolean(),
});
