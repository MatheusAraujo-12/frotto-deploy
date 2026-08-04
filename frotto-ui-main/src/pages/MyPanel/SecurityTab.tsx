import {
  IonCard,
  IonCardContent,
  IonCardHeader,
  IonCardSubtitle,
  IonCardTitle,
  IonIcon,
  IonInput,
  IonItem,
  IonLabel,
  IonText,
} from "@ionic/react";
import { shieldCheckmarkOutline } from "ionicons/icons";
import {
  FormErrors,
  PASSWORD_MIN_LENGTH,
  SecurityForm,
} from "./profilePanelUtils";

interface SecurityTabProps {
  form: SecurityForm;
  touched: Record<keyof SecurityForm, boolean>;
  errors: FormErrors<keyof SecurityForm>;
  onTouch: (field: keyof SecurityForm) => void;
  onChange: (form: SecurityForm) => void;
}

const renderFieldError = (show: boolean, message?: string) =>
  show && message ? (
    <IonText color="danger" className="app-form-error">
      {message}
    </IonText>
  ) : null;

const getInputValue = (event: any): string =>
  event?.detail?.value ?? event?.target?.value ?? "";

const SecurityTab: React.FC<SecurityTabProps> = ({
  form,
  touched,
  errors,
  onTouch,
  onChange,
}) => (
  <IonCard className="app-panel-card">
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
        <IonItem className="app-form-item">
          <IonLabel position="stacked">Senha antiga</IonLabel>
          <IonInput
            type="password"
            value={form.oldPassword}
            placeholder="Digite sua senha atual"
            autocomplete="current-password"
            onIonChange={(event: any) => {
              onTouch("oldPassword");
              onChange({ ...form, oldPassword: getInputValue(event) });
            }}
            onIonBlur={() => onTouch("oldPassword")}
          />
        </IonItem>
        {renderFieldError(touched.oldPassword, errors.oldPassword)}

        <IonItem className="app-form-item">
          <IonLabel position="stacked">Nova senha</IonLabel>
          <IonInput
            type="password"
            value={form.newPassword}
            placeholder={`Mínimo ${PASSWORD_MIN_LENGTH} caracteres`}
            autocomplete="new-password"
            onIonChange={(event: any) => {
              onTouch("newPassword");
              onChange({ ...form, newPassword: getInputValue(event) });
            }}
            onIonBlur={() => onTouch("newPassword")}
          />
        </IonItem>
        {renderFieldError(touched.newPassword, errors.newPassword)}

        <IonItem className="app-form-item">
          <IonLabel position="stacked">Confirmação da nova senha</IonLabel>
          <IonInput
            type="password"
            value={form.confirmPassword}
            placeholder="Repita a nova senha"
            autocomplete="new-password"
            onIonChange={(event: any) => {
              onTouch("confirmPassword");
              onChange({ ...form, confirmPassword: getInputValue(event) });
            }}
            onIonBlur={() => onTouch("confirmPassword")}
          />
        </IonItem>
        {renderFieldError(touched.confirmPassword, errors.confirmPassword)}
      </div>
    </IonCardContent>
  </IonCard>
);

export default SecurityTab;
