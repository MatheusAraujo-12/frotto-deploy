import { IonInput, IonItem } from "@ionic/react";
import FormInputLabel from "./FormInputLabel";
import FormItemWrapper from "./FormItemWrapper";

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
  return (
    <FormItemWrapper errorsObj={errorsObj} errorName={errorName}>
      <IonItem>
        <FormInputLabel name={label} required={required} />
        <IonInput
          value={initialValue}
          color="primary"
          class="ion-text-end"
          onIonChange={(e) => {
            changeCallback(e.target.value);
          }}
          {...rest}
        />
      </IonItem>
    </FormItemWrapper>
  );
};

export default FormInput;
