import { ErrorMessage } from "@hookform/error-message";
import FormError from "./FormError";

interface FormItemWrapperProps {
  errorsObj?: Object;
  errorName?: string;
  children: JSX.Element | JSX.Element[];
}

const FormItemWrapper: React.FC<FormItemWrapperProps> = ({
  errorsObj,
  errorName,
  children,
}) => {
  const errorMessage = getFormErrorMessage(errorsObj, errorName);
  const errorId = getFormErrorId(errorName);

  return (
    <div
      className={`app-form-field${
        errorMessage ? " app-form-field--invalid" : ""
      }`}
    >
      {children}
      {errorsObj && errorName && (
        <ErrorMessage
          errors={errorsObj}
          name={errorName}
          render={({ message }) => <FormError id={errorId} message={message} />}
        />
      )}
    </div>
  );
};

export default FormItemWrapper;

export function getFormErrorId(errorName?: string): string | undefined {
  if (!errorName) {
    return undefined;
  }

  return `form-error-${errorName.replace(/[^a-zA-Z0-9_-]/g, "-")}`;
}

export function getFormErrorMessage(
  errorsObj?: Object,
  errorName?: string
): string | undefined {
  if (!errorsObj || !errorName) {
    return undefined;
  }

  const error = errorName
    .split(".")
    .reduce<any>((current, key) => current?.[key], errorsObj);
  const message = error?.message;

  return typeof message === "string" && message.trim() ? message : undefined;
}
