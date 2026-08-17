import { IonInput, IonItem } from "@ionic/react";
import FormInputLabel from "./FormInputLabel";
import FormItemWrapper, {
  getFormErrorId,
  getFormErrorMessage,
} from "./FormItemWrapper";

interface FormInputProps {
  label: string;
  initialValue: string | number;
  required?: boolean;
  errorsObj?: Object;
  errorName?: string;
  changeCallback: Function;
  [x: string]: any;
}

const FormInput: React.FC<FormInputProps> = ({
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
      <IonItem className="app-form-item">
        <FormInputLabel name={label} required={required} />
        <IonInput
          value={initialValue}
          color="primary"
          class="ion-text-end"
          aria-invalid={hasError ? "true" : undefined}
          aria-describedby={hasError ? errorId : undefined}
          onIonChange={(e) => {
            changeCallback(e.detail.value ?? "");
          }}
          {...rest}
        />
      </IonItem>
    </FormItemWrapper>
  );
};

export default FormInput;
