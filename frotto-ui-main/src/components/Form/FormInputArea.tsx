import { IonItem, IonTextarea } from "@ionic/react";
import FormInputLabel from "./FormInputLabel";
import FormItemWrapper, {
  getFormErrorId,
  getFormErrorMessage,
} from "./FormItemWrapper";

interface FormInputAreaProps {
  label: string;
  initialValue: string;
  required?: boolean;
  errorsObj?: Object;
  errorName?: string;
  changeCallback: Function;
  maxlength: number;
  [x: string]: any;
}

const FormInputArea: React.FC<FormInputAreaProps> = ({
  label,
  initialValue,
  required = false,
  errorsObj,
  errorName,
  changeCallback,
  maxlength,
  ...rest
}) => {
  const hasError = Boolean(getFormErrorMessage(errorsObj, errorName));
  const errorId = getFormErrorId(errorName);

  return (
    <FormItemWrapper errorsObj={errorsObj} errorName={errorName}>
      <IonItem counter={true} className="app-form-item app-form-item--textarea">
        <FormInputLabel name={label} required={required} />
        <IonTextarea
          value={initialValue}
          placeholder={label}
          color="primary"
          autoGrow={true}
          maxlength={maxlength}
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

export default FormInputArea;
