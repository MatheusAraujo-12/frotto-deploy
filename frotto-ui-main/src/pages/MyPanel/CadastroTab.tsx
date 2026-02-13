import { IonButton, IonCard, IonCardContent, IonCardHeader, IonCardSubtitle, IonCardTitle, IonIcon, IonInput, IonItem, IonLabel, IonText } from "@ionic/react";
import { businessOutline } from "ionicons/icons";
import { CadastroForm, FormErrors } from "./profilePanelUtils";

interface CadastroTabProps {
  form: CadastroForm;
  touched: Record<keyof CadastroForm, boolean>;
  errors: FormErrors<keyof CadastroForm>;
  hasData: boolean;
  onTouch: (field: keyof CadastroForm) => void;
  onChange: (form: CadastroForm) => void;
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

const CadastroTab: React.FC<CadastroTabProps> = ({ form, touched, errors, hasData, onTouch, onChange, onQuickSave }) => (
  <IonCard className="my-panel-card">
    <IonCardHeader>
      <IonCardTitle>
        <IonIcon icon={businessOutline} />
        Cadastro
      </IonCardTitle>
      <IonCardSubtitle>Campos gerais do perfil do usuário no sistema.</IonCardSubtitle>
    </IonCardHeader>
    <IonCardContent>
      {!hasData && (
        <div className="my-panel-empty-state">
          <h3>Perfil ainda sem cadastro complementar</h3>
          <p>Complete os campos para personalizar sua conta.</p>
          <IonButton size="small" fill="outline" onClick={onQuickSave}>
            Salvar Agora
          </IonButton>
        </div>
      )}

      <IonItem className="my-panel-item">
        <IonLabel position="stacked">Primeiro nome</IonLabel>
        <IonInput
          value={form.firstName}
          placeholder="Nome usado no perfil"
          maxlength={50}
          onIonInput={(event: any) => {
            onTouch("firstName");
            onChange({ ...form, firstName: getInputValue(event) });
          }}
          onIonBlur={() => onTouch("firstName")}
        />
      </IonItem>
      {renderFieldError(touched.firstName, errors.firstName)}

      <IonItem className="my-panel-item">
        <IonLabel position="stacked">Sobrenome</IonLabel>
        <IonInput
          value={form.lastName}
          placeholder="Sobrenome"
          maxlength={50}
          onIonInput={(event: any) => {
            onTouch("lastName");
            onChange({ ...form, lastName: getInputValue(event) });
          }}
          onIonBlur={() => onTouch("lastName")}
        />
      </IonItem>
      {renderFieldError(touched.lastName, errors.lastName)}

      <IonItem className="my-panel-item">
        <IonLabel position="stacked">URL da imagem</IonLabel>
        <IonInput
          value={form.imageUrl}
          placeholder="https://..."
          maxlength={256}
          onIonInput={(event: any) => {
            onTouch("imageUrl");
            onChange({ ...form, imageUrl: getInputValue(event) });
          }}
          onIonBlur={() => onTouch("imageUrl")}
        />
      </IonItem>
      {renderFieldError(touched.imageUrl, errors.imageUrl)}

      <IonItem className="my-panel-item">
        <IonLabel position="stacked">Idioma</IonLabel>
        <IonInput
          value={form.langKey}
          placeholder="pt-br"
          maxlength={10}
          onIonInput={(event: any) => {
            onTouch("langKey");
            onChange({ ...form, langKey: getInputValue(event) });
          }}
          onIonBlur={() => onTouch("langKey")}
        />
      </IonItem>
      {renderFieldError(touched.langKey, errors.langKey)}
    </IonCardContent>
  </IonCard>
);

export default CadastroTab;
