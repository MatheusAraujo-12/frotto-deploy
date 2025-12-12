import * as Yup from "yup";
import { TEXT } from "../../constants/texts";

export const loginValidationSchema = Yup.object().shape({
  email: Yup.string().required(TEXT.requiredField).email(TEXT.invalidEmail),
  password: Yup.string()
    .required(TEXT.requiredField)
    .min(4, TEXT.minFieldSize("4"))
    .max(40, TEXT.maxFieldSize("40")),
});
