import {
  IonButton,
  IonCardContent,
  IonCardHeader,
  IonCardSubtitle,
  IonCardTitle,
  IonIcon,
  IonInput,
  IonItem,
} from "@ionic/react";
import { shieldCheckmarkOutline } from "ionicons/icons";
import {
  FormErrors,
  PASSWORD_MIN_LENGTH,
  SecurityForm,
} from "./profilePanelUtils";
import FrottoCard from "../../components/UI/FrottoCard";
import FormInputLabel from "../../components/Form/FormInputLabel";
import FormError from "../../components/Form/FormError";
import { getFormErrorId } from "../../components/Form/FormItemWrapper";

interface SecurityTabProps {
  form: SecurityForm;
  touched: Record<keyof SecurityForm, boolean>;
  errors: FormErrors<keyof SecurityForm>;
  onTouch: (field: keyof SecurityForm) => void;
  onChange: (form: SecurityForm) => void;
  isSaving: boolean;
  saveDisabled: boolean;
  onSave: () => void;
}

const getInputValue = (event: any): string =>
  event?.detail?.value ?? event?.target?.value ?? "";

const SecurityTab: React.FC<SecurityTabProps> = ({
  form,
  touched,
  errors,
  onTouch,
  onChange,
  isSaving,
  saveDisabled,
  onSave,
}) => {
  const fieldError = (field: keyof SecurityForm) => {
    const show = touched[field] && Boolean(errors[field]);
    const id = getFormErrorId(field);
    return {
      wrapClassName: show ? "app-form-field app-form-field--invalid" : "app-form-field",
      ariaInvalid: show ? ("true" as const) : undefined,
      ariaDescribedby: show ? id : undefined,
      node: show ? <FormError id={id} message={errors[field] as string} /> : null,
    };
  };

  const oldPasswordField = fieldError("oldPassword");
  const newPasswordField = fieldError("newPassword");
  const confirmPasswordField = fieldError("confirmPassword");

  return (
    <FrottoCard>
      <IonCardHeader className="app-panel-header">
        <div className="app-soft-icon">
          <IonIcon icon={shieldCheckmarkOutline} />
        </div>
        <div className="app-panel-header__content">
          <IonCardTitle className="app-panel-title">Segurança</IonCardTitle>
          <IonCardSubtitle className="app-panel-subtitle">
            Atualize sua senha com validação imediata.
          </IonCardSubtitle>
        </div>
      </IonCardHeader>
      <IonCardContent>
        <div className="app-form-grid">
          <div className={oldPasswordField.wrapClassName}>
            <IonItem className="app-form-item">
              <FormInputLabel name="Senha antiga" />
              <IonInput
                type="password"
                value={form.oldPassword}
                placeholder="Digite sua senha atual"
                autocomplete="current-password"
                aria-invalid={oldPasswordField.ariaInvalid}
                aria-describedby={oldPasswordField.ariaDescribedby}
                onIonChange={(event: any) => {
                  onTouch("oldPassword");
                  onChange({ ...form, oldPassword: getInputValue(event) });
                }}
                onIonBlur={() => onTouch("oldPassword")}
              />
            </IonItem>
            {oldPasswordField.node}
          </div>

          <div className={newPasswordField.wrapClassName}>
            <IonItem className="app-form-item">
              <FormInputLabel name="Nova senha" />
              <IonInput
                type="password"
                value={form.newPassword}
                placeholder={`Mínimo ${PASSWORD_MIN_LENGTH} caracteres`}
                autocomplete="new-password"
                aria-invalid={newPasswordField.ariaInvalid}
                aria-describedby={newPasswordField.ariaDescribedby}
                onIonChange={(event: any) => {
                  onTouch("newPassword");
                  onChange({ ...form, newPassword: getInputValue(event) });
                }}
                onIonBlur={() => onTouch("newPassword")}
              />
            </IonItem>
            {newPasswordField.node}
          </div>

          <div className={confirmPasswordField.wrapClassName}>
            <IonItem className="app-form-item">
              <FormInputLabel name="Confirmação da nova senha" />
              <IonInput
                type="password"
                value={form.confirmPassword}
                placeholder="Repita a nova senha"
                autocomplete="new-password"
                aria-invalid={confirmPasswordField.ariaInvalid}
                aria-describedby={confirmPasswordField.ariaDescribedby}
                onIonChange={(event: any) => {
                  onTouch("confirmPassword");
                  onChange({ ...form, confirmPassword: getInputValue(event) });
                }}
                onIonBlur={() => onTouch("confirmPassword")}
              />
            </IonItem>
            {confirmPasswordField.node}
          </div>
        </div>

        <div className="cadastro-section-actions cadastro-section-actions--edit">
          <IonButton className="app-primary-btn" disabled={isSaving || saveDisabled} onClick={onSave}>
            {isSaving ? "Salvando..." : "Salvar nova senha"}
          </IonButton>
        </div>
      </IonCardContent>
    </FrottoCard>
  );
};

export default SecurityTab;
