import { IonItem, IonTextarea } from "@ionic/react";
import FormItemWrapper from "./FormItemWrapper";

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
  return (
    <FormItemWrapper errorsObj={errorsObj} errorName={errorName}>
      <IonItem counter={true}>
        <IonTextarea
          value={initialValue}
          placeholder={label}
          color="primary"
          autoGrow={true}
          maxlength={maxlength}
          onIonChange={(e) => {
            changeCallback(e.target.value);
          }}
          {...rest}
        />
      </IonItem>
    </FormItemWrapper>
  );
};

export default FormInputArea;
