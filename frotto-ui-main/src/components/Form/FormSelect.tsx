import { IonItem, IonSelect, IonSelectOption } from "@ionic/react";
import { SELECT_TYPE } from "../../constants/form";
import FormInputLabel from "./FormInputLabel";
import FormItemWrapper from "./FormItemWrapper";
import styled from "styled-components";

const MyIonSelect = styled(IonSelect)`
  &::part(text) {
    color: var(--ion-color-primary);
  }
`;

interface FormSelectProps {
  label: string;
  header?: string;
  required: boolean;
  initialValue: string;
  options: string[];
  errorsObj?: Object;
  errorName?: string;
  changeCallback: Function;
}

const FormSelect: React.FC<FormSelectProps> = ({
  label,
  header,
  required,
  initialValue,
  options,
  errorsObj,
  errorName,
  changeCallback,
}) => {
  return (
    <FormItemWrapper errorsObj={errorsObj} errorName={errorName}>
      <IonItem>
        <FormInputLabel name={label} header={header} required={required} />
        <MyIonSelect
          value={initialValue}
          interface={SELECT_TYPE}
          placeholder={label}
          onIonChange={(e) => {
            changeCallback(e.target.value);
          }}
        >
          {options.map((option: string) => (
            <IonSelectOption key={option} value={option}>
              {option}
            </IonSelectOption>
          ))}
        </MyIonSelect>
      </IonItem>
    </FormItemWrapper>
  );
};

export default FormSelect;
