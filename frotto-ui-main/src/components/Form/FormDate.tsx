import {
  IonDatetime,
  IonDatetimeButton,
  IonItem,
  IonModal,
} from "@ionic/react";
import FormInputLabel from "./FormInputLabel";
import { format, parseISO } from "date-fns";
import styled from "styled-components";

const MyIonModal = styled(IonModal)`
  height: 100%;
  background-color: #aaaaaa99;
`;

export interface DateProps {
  id: string;
  label: string;
  presentation: "year" | "date" | "month-year" | undefined;
  formCallBack: Function;
  required?: boolean;
  initialValue?: string | string[] | null | undefined;
  min?: string;
}

const FormDate: React.FC<DateProps> = ({
  id,
  label,
  presentation,
  formCallBack,
  required,
  initialValue,
  min
}) => {

  const confirmDate = (dateValue: string | string[] | null | undefined) => {
    if (typeof dateValue === "string") {
      if (presentation === "year") {
        const formattedString = format(parseISO(dateValue), "yyyy");
        formCallBack(formattedString);
      }
      if (presentation === "date") {
        const formattedString = format(parseISO(dateValue), "yyyy-MM-dd");
        formCallBack(formattedString);
      }
      if (presentation === "month-year") {
        const formattedString = format(parseISO(dateValue), "yyyy-MM-dd");
        formCallBack(formattedString);
      }
    }
  };

  return (
    <IonItem>
      <FormInputLabel name={label} required={required} />
      <IonDatetimeButton datetime={id} slot="end"></IonDatetimeButton>
      <MyIonModal keepContentsMounted={true}>
        <IonDatetime
          id={id}
          presentation={presentation}
          value={initialValue}
          min={min}
          onIonChange={(e) => {
            confirmDate(e.detail.value);
          }}
        ></IonDatetime>
      </MyIonModal>
    </IonItem>
  );
};

export default FormDate;
