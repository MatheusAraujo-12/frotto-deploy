import { InspectionExpenseModel } from "../../../constants/CarModels";
import * as Yup from "yup";
import { TEXT } from "../../../constants/texts";

export interface ExpenseModelActive extends InspectionExpenseModel {
  activeIndex?: number;
  delete?: boolean;
}

export const initialExpenseValues = (initialValues: ExpenseModelActive) => {
  return {
    activeIndex: initialValues.activeIndex,
    id: initialValues.id || undefined,
    name: initialValues.name || "",
    cost: initialValues.cost || 0,
    ammount: initialValues.ammount || 1,
  };
};

export const expenseAddValidationSchema = Yup.object().shape({
  activeIndex: Yup.number().nullable(),
  id: Yup.number().nullable(),
  name: Yup.string().required(TEXT.requiredField),
  cost: Yup.number().typeError(TEXT.requiredField).required(TEXT.requiredField),
  ammount: Yup.number()
    .typeError(TEXT.requiredField)
    .required(TEXT.requiredField),
});
