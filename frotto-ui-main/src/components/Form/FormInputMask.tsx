import { IonInput, IonItem } from "@ionic/react";
import IMask, { AnyMaskedOptions } from "imask";
import FormInputLabel from "./FormInputLabel";
import FormItemWrapper from "./FormItemWrapper";

interface FormInputMaskProps {
  label: string;
  initialValue: string;
  required?: boolean;
  errorsObj?: Object;
  errorName?: string;
  changeCallback: Function;
  maskOptions?: AnyMaskedOptions;
  [x: string]: any;
}

const FormInputMask: React.FC<FormInputMaskProps> = ({
  label,
  initialValue,
  required = false,
  errorsObj,
  errorName,
  changeCallback,
  maskOptions = {
    mask: "000.000.000-00",
  },
  ...rest
}) => {
  const masked = IMask.createMask(maskOptions);
  masked.resolve(initialValue);

  return (
    <FormItemWrapper errorsObj={errorsObj} errorName={errorName}>
      <IonItem>
        <FormInputLabel name={label} required={required} />
        <IonInput
          value={masked.value}
          color="primary"
          class="ion-text-end"
          onIonChange={(e) => {
            const changedValue = e.target.value;
            if (changedValue && changedValue !== "") {
              masked.resolve(changedValue.toString());
              changeCallback(masked.unmaskedValue);
            } else {
              changeCallback("");
            }
          }}
          {...rest}
        />
      </IonItem>
    </FormItemWrapper>
  );
};

export default FormInputMask;
