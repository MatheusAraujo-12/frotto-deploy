import {
  IonDatetime,
  IonDatetimeButton,
  IonItem,
  IonModal,
  IonText,
} from "@ionic/react";
import FormInputLabel from "./FormInputLabel";
import { format, parseISO } from "date-fns";
import styled from "styled-components";

const MyIonModal = styled(IonModal)`
  height: 100%;
  background-color: var(--app-modal-backdrop);
`;

export interface DateProps {
  id: string;
  label: string;
  presentation: "year" | "date" | "month-year" | undefined;
  formCallBack: (value: string) => void;
  required?: boolean;
  initialValue?: string | string[] | null | undefined;
  min?: string;

  // ✅ novo: permite passar erro do react-hook-form
  error?: string;
}

const FormDate: React.FC<DateProps> = ({
  id,
  label,
  presentation,
  formCallBack,
  required,
  initialValue,
  min,
  error,
}) => {
  const confirmDate = (dateValue: string | string[] | null | undefined) => {
    if (typeof dateValue !== "string") return;

    if (presentation === "year") {
      formCallBack(format(parseISO(dateValue), "yyyy"));
      return;
    }

    if (presentation === "date") {
      formCallBack(format(parseISO(dateValue), "yyyy-MM-dd"));
      return;
    }

    if (presentation === "month-year") {
      // se você quiser só mês/ano, dá pra mudar para "yyyy-MM"
      formCallBack(format(parseISO(dateValue), "yyyy-MM-dd"));
      return;
    }

    // fallback seguro
    formCallBack(dateValue);
  };

  return (
    <div className="app-form-date-field">
      <IonItem className="app-form-item app-form-item--date">
        <FormInputLabel name={label} required={required} />
        <IonDatetimeButton datetime={id} slot="end" />
        <MyIonModal keepContentsMounted={true}>
          <IonDatetime
            id={id}
            presentation={presentation}
            value={initialValue}
            min={min}
            onIonChange={(e) => confirmDate(e.detail.value)}
          />
        </MyIonModal>
      </IonItem>

      {!!error && (
        <div className="app-form-date-field__error">
          <IonText color="danger" className="app-form-error">
            {error}
          </IonText>
        </div>
      )}
    </div>
  );
};

export default FormDate;
