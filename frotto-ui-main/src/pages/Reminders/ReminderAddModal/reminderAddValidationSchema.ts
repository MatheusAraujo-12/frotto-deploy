import { ReminderModel } from "./../../../constants/CarModels";
import * as Yup from "yup";
import { TEXT } from "../../../constants/texts";

export const initialReminderValues = (initialValues: ReminderModel) => {
  return {
    id: initialValues.id || undefined,
    message: initialValues.message || "",
  };
};

export const reminderAddValidationSchema = Yup.object().shape({
  id: Yup.number().nullable(),
  message: Yup.string().required(TEXT.requiredField),
});
