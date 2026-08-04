import {
  IonAvatar,
  IonButton,
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
import { imageOutline, personCircleOutline } from "ionicons/icons";
import { maskCPF, maskPhone } from "../../services/profileFormat";
import { FormErrors, PersonalForm } from "./profilePanelUtils";

interface PersonalTabProps {
  form: PersonalForm;
  touched: Record<keyof PersonalForm, boolean>;
  errors: FormErrors<keyof PersonalForm>;
  hasData: boolean;
  avatarPreviewUrl: string;
  canRemoveAvatar: boolean;
  onTouch: (field: keyof PersonalForm) => void;
  onChange: (form: PersonalForm) => void;
  onQuickSave: () => void;
  onChangeAvatar: () => void;
  onRemoveAvatar: () => void;
}

const renderFieldError = (show: boolean, message?: string) =>
  show && message ? (
    <IonText color="danger" className="app-form-error">
      {message}
    </IonText>
  ) : null;

const getInputValue = (event: any): string =>
  event?.detail?.value ?? event?.target?.value ?? event?.currentTarget?.value ?? "";

const PersonalTab: React.FC<PersonalTabProps> = ({
  form,
  touched,
  errors,
  hasData,
  avatarPreviewUrl,
  canRemoveAvatar,
  onTouch,
  onChange,
  onQuickSave,
  onChangeAvatar,
  onRemoveAvatar,
}) => (
  <IonCard className="app-panel-card">
    <IonCardHeader className="app-panel-header">
      <div className="app-soft-icon">
        <IonIcon icon={personCircleOutline} />
      </div>
      <div className="app-panel-header__content">
        <IonCardTitle className="app-panel-title">Dados Pessoais</IonCardTitle>
        <IonCardSubtitle className="app-panel-subtitle">
          Mantenha suas informações básicas sempre atualizadas.
        </IonCardSubtitle>
      </div>
    </IonCardHeader>
    <IonCardContent>
      {!hasData && (
        <div className="app-empty-state">
          <strong>Nenhum dado pessoal salvo</strong>
          <span>Preencha os campos abaixo para começar.</span>
          <IonButton size="small" fill="outline" className="app-outline-btn" onClick={onQuickSave}>
            Salvar agora
          </IonButton>
        </div>
      )}

      <div className="app-avatar-block">
        <IonAvatar className="app-avatar-block__avatar">
          {avatarPreviewUrl ? (
            <img src={avatarPreviewUrl} alt="Avatar do usuário" />
          ) : (
            <div className="app-avatar-block__placeholder">
              <IonIcon icon={imageOutline} />
            </div>
          )}
        </IonAvatar>
        <div className="app-avatar-block__content">
          <h2>Foto de perfil</h2>
        </div>
      </div>

      <div className="app-actions-row">
        <IonButton fill="outline" className="app-outline-btn" onClick={onChangeAvatar}>
          Alterar foto
        </IonButton>
        <IonButton
          fill="clear"
          color="danger"
          disabled={!canRemoveAvatar}
          onClick={onRemoveAvatar}
        >
          Remover
        </IonButton>
      </div>

      <div className="app-form-grid">
        <IonItem className="app-form-item">
          <IonLabel position="stacked">Nome</IonLabel>
          <IonInput
            value={form.personalName}
            placeholder="Ex.: Matheus Silva"
            onIonInput={(event: any) => {
              onTouch("personalName");
              onChange({ ...form, personalName: getInputValue(event) });
            }}
            onIonBlur={() => onTouch("personalName")}
          />
        </IonItem>
        {renderFieldError(touched.personalName, errors.personalName)}

        <IonItem className="app-form-item">
          <IonLabel position="stacked">CPF</IonLabel>
          <IonInput
            value={form.personalCpf}
            placeholder="000.000.000-00"
            onIonInput={(event: any) => {
              onTouch("personalCpf");
              onChange({ ...form, personalCpf: maskCPF(getInputValue(event)) });
            }}
            onIonBlur={() => onTouch("personalCpf")}
          />
        </IonItem>
        {renderFieldError(touched.personalCpf, errors.personalCpf)}

        <IonItem className="app-form-item">
          <IonLabel position="stacked">Data de nascimento</IonLabel>
          <IonInput
            type="date"
            value={form.personalBirthDate}
            onIonInput={(event: any) => {
              onTouch("personalBirthDate");
              onChange({ ...form, personalBirthDate: getInputValue(event) });
            }}
            onIonBlur={() => onTouch("personalBirthDate")}
          />
        </IonItem>
        {renderFieldError(touched.personalBirthDate, errors.personalBirthDate)}

        <IonItem className="app-form-item">
          <IonLabel position="stacked">E-mail</IonLabel>
          <IonInput
            type="email"
            value={form.personalEmail}
            placeholder="voce@email.com"
            onIonInput={(event: any) => {
              onTouch("personalEmail");
              onChange({ ...form, personalEmail: getInputValue(event) });
            }}
            onIonBlur={() => onTouch("personalEmail")}
          />
        </IonItem>
        {renderFieldError(touched.personalEmail, errors.personalEmail)}

        <IonItem className="app-form-item">
          <IonLabel position="stacked">Telefone</IonLabel>
          <IonInput
            value={form.personalPhone}
            placeholder="(11) 99999-9999"
            onIonInput={(event: any) => {
              onTouch("personalPhone");
              onChange({ ...form, personalPhone: maskPhone(getInputValue(event)) });
            }}
            onIonBlur={() => onTouch("personalPhone")}
          />
        </IonItem>
        {renderFieldError(touched.personalPhone, errors.personalPhone)}
      </div>
    </IonCardContent>
  </IonCard>
);

export default PersonalTab;
