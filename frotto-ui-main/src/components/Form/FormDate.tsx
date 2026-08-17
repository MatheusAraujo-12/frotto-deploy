import {
  IonButton,
  IonButtons,
  IonDatetime,
  IonDatetimeButton,
  IonItem,
  IonModal,
  IonText,
} from "@ionic/react";
import { useRef } from "react";
import FormInputLabel from "./FormInputLabel";
import { isValid, parseISO } from "date-fns";
import styled from "styled-components";
import { TEXT } from "../../constants/texts";
import FormItemWrapper, {
  getFormErrorId,
  getFormErrorMessage,
} from "./FormItemWrapper";

const MyIonModal = styled(IonModal)`
  --backdrop-opacity: 1;

  &::part(backdrop) {
    background: var(--app-modal-backdrop);
  }
`;

export interface DateProps {
  id: string;
  label: string;
  presentation: "year" | "date" | "month-year" | undefined;
  formCallBack: (value: string) => void;
  required?: boolean;
  initialValue?: string | string[] | null | undefined;
  min?: string;
  error?: string;
  errorsObj?: Object;
  errorName?: string;
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
  errorsObj,
  errorName,
}) => {
  // Strips timezone suffix and normalizes to YYYY-MM-DD.
  // Handles IonDatetime emitting full ISO strings like "2022-04-05T00:00:00.000Z"
  // which would cause date-fns parseISO to shift the date by the UTC offset.
  const normalizeDateValue = (value: string): string => {
    const stripped = value.length > 10 ? value.substring(0, 10) : value;
    if (/^\d{4}$/.test(stripped)) return `${stripped}-01-01`;
    if (/^\d{4}-\d{2}$/.test(stripped)) return `${stripped}-01`;
    if (/^\d{4}-\d{2}-\d{2}$/.test(stripped)) return stripped;
    return value;
  };

  const modalRef = useRef<HTMLIonModalElement>(null);
  // Ref to the IonDatetime element — used to call confirm() and reset()
  const datetimeRef = useRef<HTMLIonDatetimeElement>(null);
  // Tracks the value for the current modal session; updated by onIonChange
  const pendingValueRef = useRef<string | string[] | null | undefined>(undefined);

  const confirmDate = (dateValue: string | string[] | null | undefined) => {
    if (typeof dateValue !== "string") return;
    const normalized = normalizeDateValue(dateValue);

    if (presentation === "year") {
      formCallBack(normalized.substring(0, 4));
      return;
    }

    if (presentation === "date" || presentation === "month-year") {
      formCallBack(normalized);
      return;
    }

    formCallBack(normalized);
  };

  // confirm() asks Ionic to commit the user's current selection using activeDateParts
  // (the highlighted state in the calendar UI), which is reliable even when
  // keepContentsMounted causes Stencil to reconcile the .value property from the prop.
  // confirm() fires ionChange synchronously, which updates pendingValueRef before the
  // Promise resolves, so we can safely read it right after await.
  const handleConfirm = async () => {
    await datetimeRef.current?.confirm(false);
    confirmDate(pendingValueRef.current);
    modalRef.current?.dismiss();
  };

  const handleCancel = () => {
    modalRef.current?.dismiss();
    // Reset element state so next open doesn't carry over a canceled selection
    void datetimeRef.current?.reset(
      typeof safeInitialValue === "string" ? safeInitialValue : undefined
    );
  };

  const resolvedInitialValue =
    typeof initialValue === "string"
      ? normalizeDateValue(initialValue)
      : initialValue;

  const safeInitialValue =
    typeof resolvedInitialValue === "string"
      ? (isValid(parseISO(resolvedInitialValue)) ? resolvedInitialValue : undefined)
      : resolvedInitialValue;
  const errorMessage = error || getFormErrorMessage(errorsObj, errorName);
  const hasError = Boolean(errorMessage);
  const errorId = getFormErrorId(errorName || id);

  return (
    <FormItemWrapper errorsObj={errorsObj} errorName={errorName}>
      <div style={{ padding: "8px 0" }}>
        <IonItem className="app-form-item">
          <FormInputLabel name={label} required={required} />
          <IonDatetimeButton
            datetime={id}
            slot="end"
            aria-invalid={hasError ? "true" : undefined}
            aria-describedby={hasError ? errorId : undefined}
          />
          <MyIonModal
            ref={modalRef}
            keepContentsMounted={true}
            onWillPresent={() => {
              pendingValueRef.current = safeInitialValue;
            }}
          >
            <IonDatetime
              id={id}
              ref={datetimeRef}
              presentation={presentation}
              value={safeInitialValue}
              min={min}
              onIonChange={(e) => {
                pendingValueRef.current = e.detail.value;
              }}
            >
              <IonButtons slot="buttons">
                <IonButton color="medium" onClick={handleCancel}>
                  {TEXT.cancel}
                </IonButton>
                <IonButton color="primary" onClick={handleConfirm}>
                  {TEXT.confirm}
                </IonButton>
              </IonButtons>
            </IonDatetime>
          </MyIonModal>
        </IonItem>

        {!!error && (
          <div style={{ padding: "6px 16px 0 16px" }}>
            <IonText color="danger" id={errorId} style={{ fontSize: 12 }}>
              {error}
            </IonText>
          </div>
        )}
      </div>
    </FormItemWrapper>
  );
};

export default FormDate;
