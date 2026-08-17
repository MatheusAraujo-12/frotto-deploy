import { IonItem, IonToggle } from "@ionic/react";
import FormInputLabel from "./FormInputLabel";
import FormItemWrapper, {
  getFormErrorId,
  getFormErrorMessage,
} from "./FormItemWrapper";

interface FormToggleProps {
  label: string;
  initialValue: boolean;
  required?: boolean;
  errorsObj?: Object;
  errorName?: string;
  changeCallback: Function;
  [x: string]: any;
}

const FormToggle: React.FC<FormToggleProps> = ({
  label,
  initialValue,
  required = false,
  errorsObj,
  errorName,
  changeCallback,
  ...rest
}) => {
  const hasError = Boolean(getFormErrorMessage(errorsObj, errorName));
  const errorId = getFormErrorId(errorName);

  return (
    <FormItemWrapper errorsObj={errorsObj} errorName={errorName}>
      <IonItem className="app-form-item app-form-item--toggle">
        <FormInputLabel name={label} required={required} />
        <IonToggle
          checked={initialValue}
          slot="end"
          color="primary"
          aria-invalid={hasError ? "true" : undefined}
          aria-describedby={hasError ? errorId : undefined}
          onIonChange={(e) => {
            changeCallback(e.detail.checked);
          }}
          {...rest}
        ></IonToggle>
      </IonItem>
    </FormItemWrapper>
  );
};

export default FormToggle;
