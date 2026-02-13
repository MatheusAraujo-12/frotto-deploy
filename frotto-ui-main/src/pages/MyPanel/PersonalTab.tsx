import { IonButton, IonCard, IonCardContent, IonCardHeader, IonCardSubtitle, IonCardTitle, IonIcon, IonInput, IonItem, IonLabel, IonText } from "@ionic/react";
import { personCircleOutline } from "ionicons/icons";
import { maskCPF, maskPhone } from "../../services/profileFormat";
import { FormErrors, PersonalForm } from "./profilePanelUtils";

interface PersonalTabProps {
  form: PersonalForm;
  touched: Record<keyof PersonalForm, boolean>;
  errors: FormErrors<keyof PersonalForm>;
  hasData: boolean;
  onTouch: (field: keyof PersonalForm) => void;
  onChange: (form: PersonalForm) => void;
  onQuickSave: () => void;
}

const renderFieldError = (show: boolean, message?: string) =>
  show && message ? (
    <IonText color="danger" className="my-panel-field-error">
      {message}
    </IonText>
  ) : null;

const getInputValue = (event: any): string =>
  event?.detail?.value ?? event?.target?.value ?? event?.currentTarget?.value ?? "";

const PersonalTab: React.FC<PersonalTabProps> = ({ form, touched, errors, hasData, onTouch, onChange, onQuickSave }) => (
  <IonCard className="my-panel-card">
    <IonCardHeader>
      <IonCardTitle>
        <IonIcon icon={personCircleOutline} />
        Dados Pessoais
      </IonCardTitle>
      <IonCardSubtitle>Mantenha suas informações básicas sempre atualizadas.</IonCardSubtitle>
    </IonCardHeader>
    <IonCardContent>
      {!hasData && (
        <div className="my-panel-empty-state">
          <h3>Nenhum dado pessoal salvo</h3>
          <p>Preencha os campos abaixo para começar.</p>
          <IonButton size="small" fill="outline" onClick={onQuickSave}>
            Salvar Agora
          </IonButton>
        </div>
      )}

      <IonItem className="my-panel-item">
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

      <IonItem className="my-panel-item">
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

      <IonItem className="my-panel-item">
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

      <IonItem className="my-panel-item">
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

      <IonItem className="my-panel-item">
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
    </IonCardContent>
  </IonCard>
);

export default PersonalTab;
