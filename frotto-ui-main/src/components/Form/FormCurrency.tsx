import { IonInput, IonItem } from "@ionic/react";
import { TEXT } from "../../constants/texts";
import {
  currencyFormat,
  updateNumberByKeyandPrevious,
} from "../../services/currencyFormat";
import FormInputLabel from "./FormInputLabel";
import FormItemWrapper from "./FormItemWrapper";
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
  return (
    <FormItemWrapper errorsObj={errorsObj} errorName={errorName}>
      <IonItem>
        <FormInputLabel name={label} required={required} />
        <CurrencyInput
          value={currencyFormat(initialValue)}
          color="primary"
          class="ion-text-end"
          inputmode="numeric"
          placeholder={TEXT.zeroMoney}
          onKeyDown={(e) => {
            e.preventDefault();
            changeCallback(
              updateNumberByKeyandPrevious(
                e.key.toString(),
                initialValue.toString()
              )
            );
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
