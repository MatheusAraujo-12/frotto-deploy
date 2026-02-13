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
    <IonText color="danger" className="my-panel-field-error">
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
  <IonCard className="my-panel-card">
    <IonCardHeader>
      <IonCardTitle>
        <IonIcon icon={shieldCheckmarkOutline} />
        Segurança
      </IonCardTitle>
      <IonCardSubtitle>Atualize sua senha com validação imediata.</IonCardSubtitle>
    </IonCardHeader>
    <IonCardContent>
      <IonItem className="my-panel-item">
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

      <IonItem className="my-panel-item">
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

      <IonItem className="my-panel-item">
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
    </IonCardContent>
  </IonCard>
);

export default SecurityTab;
