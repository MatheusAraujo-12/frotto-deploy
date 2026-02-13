import {
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
  IonThumbnail,
} from "@ionic/react";
import { businessOutline, imageOutline, syncOutline } from "ionicons/icons";
import { ChangeEvent, useRef } from "react";
import { CadastroForm, FormErrors } from "./profilePanelUtils";

interface CadastroTabProps {
  form: CadastroForm;
  touched: Record<keyof CadastroForm, boolean>;
  errors: FormErrors<keyof CadastroForm>;
  hasData: boolean;
  avatarPreviewUrl: string;
  onTouch: (field: keyof CadastroForm) => void;
  onChange: (form: CadastroForm) => void;
  onQuickSave: () => void;
  onAvatarSelect: (file: File, previewUrl: string) => void;
  onRemoveAvatar: () => void;
  canRemoveAvatar: boolean;
  onUseFiscalData: () => void;
}

const renderFieldError = (show: boolean, message?: string) =>
  show && message ? (
    <IonText color="danger" className="my-panel-field-error">
      {message}
    </IonText>
  ) : null;

const getInputValue = (event: any): string =>
  event?.detail?.value ?? event?.target?.value ?? event?.currentTarget?.value ?? "";

const CadastroTab: React.FC<CadastroTabProps> = ({
  form,
  touched,
  errors,
  hasData,
  avatarPreviewUrl,
  onTouch,
  onChange,
  onQuickSave,
  onAvatarSelect,
  onRemoveAvatar,
  canRemoveAvatar,
  onUseFiscalData,
}) => {
  const fileInputRef = useRef<HTMLInputElement | null>(null);

  const openFilePicker = () => {
    fileInputRef.current?.click();
  };

  const handleFileChange = (event: ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0];
    if (!file) {
      return;
    }

    const previewUrl = URL.createObjectURL(file);
    onTouch("imageUrl");
    onAvatarSelect(file, previewUrl);

    // allow selecting the same file again
    event.target.value = "";
  };

  return (
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

        <div className="my-panel-inline-actions">
          <IonButton size="small" fill="outline" onClick={onUseFiscalData}>
            <IonIcon icon={syncOutline} slot="start" />
            Usar dados fiscais
          </IonButton>
        </div>

        <input
          ref={fileInputRef}
          type="file"
          accept="image/*"
          style={{ display: "none" }}
          onChange={handleFileChange}
        />

        <IonItem className="my-panel-item">
          {avatarPreviewUrl ? (
            <IonThumbnail slot="start">
              <img src={avatarPreviewUrl} alt="Avatar do usuário" />
            </IonThumbnail>
          ) : (
            <IonIcon icon={imageOutline} slot="start" />
          )}
          <IonLabel>Foto de perfil</IonLabel>
          <IonButton slot="end" fill="outline" onClick={openFilePicker}>
            Upload
          </IonButton>
          <IonButton
            slot="end"
            fill="clear"
            color="danger"
            disabled={!canRemoveAvatar}
            onClick={onRemoveAvatar}
          >
            Remover
          </IonButton>
        </IonItem>

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
};

export default CadastroTab;
