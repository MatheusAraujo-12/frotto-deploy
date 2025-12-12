import { IonItem, IonToggle } from "@ionic/react";
import FormInputLabel from "./FormInputLabel";
import FormItemWrapper from "./FormItemWrapper";

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
  return (
    <FormItemWrapper errorsObj={errorsObj} errorName={errorName}>
      <IonItem>
        <FormInputLabel name={label} required={required} />
        <IonToggle
          checked={initialValue}
          slot="end"
          color="primary"
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
