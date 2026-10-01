import { IonInput, IonItem } from "@ionic/react";
import { maskCPF, maskPhone } from "../../services/profileFormat";
import { FormErrors, PersonalForm } from "./profilePanelUtils";
import FormInputLabel from "../../components/Form/FormInputLabel";
import FormError from "../../components/Form/FormError";
import { getFormErrorId } from "../../components/Form/FormItemWrapper";

export const PERSONAL_FIELD_LABELS: Record<keyof PersonalForm, string> = {
  personalName: "Nome",
  personalCpf: "CPF",
  personalBirthDate: "Data de nascimento",
  personalEmail: "E-mail",
  personalPhone: "Telefone",
};

const PLACEHOLDERS: Record<keyof PersonalForm, string | undefined> = {
  personalName: "Ex.: Matheus Silva",
  personalCpf: "000.000.000-00",
  personalBirthDate: undefined,
  personalEmail: "voce@email.com",
  personalPhone: "(11) 99999-9999",
};

const MASKS: Partial<Record<keyof PersonalForm, (value: string) => string>> = {
  personalCpf: maskCPF,
  personalPhone: maskPhone,
};

const INPUT_TYPES: Partial<Record<keyof PersonalForm, "date" | "email">> = {
  personalBirthDate: "date",
  personalEmail: "email",
};

interface PersonalTabProps {
  form: PersonalForm;
  touched: Record<keyof PersonalForm, boolean>;
  errors: FormErrors<keyof PersonalForm>;
  /** Campos a exibir, na ordem do formulário (ver getVisiblePersonalFields). */
  visibleFields: Array<keyof PersonalForm>;
  labels?: Partial<Record<keyof PersonalForm, string>>;
  onTouch: (field: keyof PersonalForm) => void;
  onChange: (form: PersonalForm) => void;
}

const getInputValue = (event: any): string =>
  event?.detail?.value ?? event?.target?.value ?? event?.currentTarget?.value ?? "";

/** Campos editáveis de dados pessoais (PATCH /api/me). Payload inalterado. */
const PersonalTab: React.FC<PersonalTabProps> = ({
  form,
  touched,
  errors,
  visibleFields,
  labels,
  onTouch,
  onChange,
}) => (
  <div className="app-form-grid cadastro-grid">
    {visibleFields.map((field) => {
      const show = touched[field] && Boolean(errors[field]);
      const errorId = getFormErrorId(field);
      const mask = MASKS[field];

      return (
        <div key={field} className={show ? "app-form-field app-form-field--invalid" : "app-form-field"}>
          <IonItem className="app-form-item">
            <FormInputLabel name={labels?.[field] ?? PERSONAL_FIELD_LABELS[field]} />
            <IonInput
              type={INPUT_TYPES[field]}
              value={form[field]}
              placeholder={PLACEHOLDERS[field]}
              aria-invalid={show ? "true" : undefined}
              aria-describedby={show ? errorId : undefined}
              onIonInput={(event: any) => {
                const raw = getInputValue(event);
                onTouch(field);
                onChange({ ...form, [field]: mask ? mask(raw) : raw });
              }}
              onIonBlur={() => onTouch(field)}
            />
          </IonItem>
          {show ? <FormError id={errorId} message={errors[field] as string} /> : null}
        </div>
      );
    })}
  </div>
);

export default PersonalTab;
