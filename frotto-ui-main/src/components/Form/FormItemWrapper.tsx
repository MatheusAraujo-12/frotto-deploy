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
  return (
    <>
      {children}
      {errorsObj && errorName && (
        <ErrorMessage
          errors={errorsObj}
          name={errorName}
          render={({ message }) => <FormError message={message} />}
        />
      )}
    </>
  );
};

export default FormItemWrapper;
