import { IonInput, IonItem } from "@ionic/react";
import { TEXT } from "../../constants/texts";
import {
  centsFromTypedText,
  currencyFormat,
  isUnidentifiedKey,
  updateNumberByKeyandPrevious,
} from "../../services/currencyFormat";
import FormInputLabel from "./FormInputLabel";
import FormItemWrapper, {
  getFormErrorId,
  getFormErrorMessage,
} from "./FormItemWrapper";
import styled from "styled-components";

export const CurrencyInput = styled(IonInput)`
  caret-color: transparent;
`;

interface FormCurrencyProps {
  label: string;
  initialValue: string | number;
  required?: boolean;
  errorsObj?: Object;
  errorName?: string;
  changeCallback: Function;
  [x: string]: any;
}

const FormCurrency: React.FC<FormCurrencyProps> = ({
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
        <CurrencyInput
          value={currencyFormat(initialValue)}
          color="primary"
          class="ion-text-end"
          inputmode="numeric"
          placeholder={TEXT.zeroMoney}
          aria-invalid={hasError ? "true" : undefined}
          aria-describedby={hasError ? errorId : undefined}
          aria-required={required || undefined}
          onKeyDown={(e) => {
            // Mobile keyboards: the character comes in the input event below.
            if (isUnidentifiedKey(e.key, e.keyCode)) {
              return;
            }
            e.preventDefault();
            changeCallback(
              updateNumberByKeyandPrevious(
                e.key.toString(),
                String(initialValue ?? 0)
              )
            );
          }}
          onIonInput={(e) => {
            // Reached only when the keydown did not handle the key (mobile keyboards, paste): read the typed text.
            const next = centsFromTypedText(`${(e.target as HTMLIonInputElement)?.value ?? ""}`);
            (e.target as HTMLIonInputElement).value = currencyFormat(next);
            changeCallback(next);
          }}
          onIonChange={(e) => {
            e.preventDefault();
          }}
          {...rest}
        />
      </IonItem>
    </FormItemWrapper>
  );
};

export default FormCurrency;
