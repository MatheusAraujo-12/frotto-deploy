import {
  IonButton,
  IonCardContent,
  IonCardHeader,
  IonCardSubtitle,
  IonCardTitle,
  IonIcon,
} from "@ionic/react";
import { idCardOutline, imagesOutline, listOutline } from "ionicons/icons";
import FrottoCard from "../../components/UI/FrottoCard";
import { FiscalForm, FormErrors, PersonalForm } from "./profilePanelUtils";
import PersonalTab from "./PersonalTab";
import { FiscalCadastralFields, FiscalComplementaryFields } from "./FiscalTab";

interface MeuCadastroTabProps {
  personalForm: PersonalForm;
  personalTouched: Record<keyof PersonalForm, boolean>;
  personalErrors: FormErrors<keyof PersonalForm>;
  hasPersonalData: boolean;
  avatarPreviewUrl: string;
  canRemoveAvatar: boolean;
  onPersonalTouch: (field: keyof PersonalForm) => void;
  onPersonalChange: (form: PersonalForm) => void;
  onChangeAvatar: () => void;
  onRemoveAvatar: () => void;

  fiscalForm: FiscalForm;
  fiscalTouched: Record<keyof FiscalForm, boolean>;
  fiscalErrors: FormErrors<keyof FiscalForm>;
  hasFiscalData: boolean;
  onFiscalTouch: (field: keyof FiscalForm) => void;
  onFiscalChange: (form: FiscalForm) => void;

  logoPreviewUrl: string;
  canRemoveLogo: boolean;
  onChangeLogo: () => void;
  onRemoveLogo: () => void;

  onQuickSave: () => void;
}

const MeuCadastroTab: React.FC<MeuCadastroTabProps> = ({
  personalForm,
  personalTouched,
  personalErrors,
  hasPersonalData,
  avatarPreviewUrl,
  canRemoveAvatar,
  onPersonalTouch,
  onPersonalChange,
  onChangeAvatar,
  onRemoveAvatar,
  fiscalForm,
  fiscalTouched,
  fiscalErrors,
  hasFiscalData,
  onFiscalTouch,
  onFiscalChange,
  logoPreviewUrl,
  canRemoveLogo,
  onChangeLogo,
  onRemoveLogo,
  onQuickSave,
}) => {
  return (
    <>
      <FrottoCard>
        <IonCardHeader className="app-panel-header">
          <div className="app-soft-icon">
            <IonIcon icon={idCardOutline} />
          </div>
          <div className="app-panel-header__content">
            <IonCardTitle className="app-panel-title">Dados Cadastrais</IonCardTitle>
            <IonCardSubtitle className="app-panel-subtitle">
              Defina se você atua como Pessoa Física ou Jurídica.
            </IonCardSubtitle>
          </div>
        </IonCardHeader>
        <IonCardContent>
          <FiscalCadastralFields
            form={fiscalForm}
            touched={fiscalTouched}
            errors={fiscalErrors}
            hasData={hasFiscalData}
            onTouch={onFiscalTouch}
            onChange={onFiscalChange}
            onQuickSave={onQuickSave}
          />
        </IonCardContent>
      </FrottoCard>

      <FrottoCard>
        <IonCardHeader className="app-panel-header">
          <div className="app-soft-icon">
            <IonIcon icon={listOutline} />
          </div>
          <div className="app-panel-header__content">
            <IonCardTitle className="app-panel-title">Dados complementares</IonCardTitle>
            <IonCardSubtitle className="app-panel-subtitle">
              Informações adicionais do seu cadastro.
            </IonCardSubtitle>
          </div>
        </IonCardHeader>
        <IonCardContent>
          <h3 className="app-section-subtitle">Meus dados</h3>
          <PersonalTab
            form={personalForm}
            touched={personalTouched}
            errors={personalErrors}
            hasData={hasPersonalData}
            avatarPreviewUrl={avatarPreviewUrl}
            canRemoveAvatar={canRemoveAvatar}
            onTouch={onPersonalTouch}
            onChange={onPersonalChange}
            onQuickSave={onQuickSave}
            onChangeAvatar={onChangeAvatar}
            onRemoveAvatar={onRemoveAvatar}
          />

          <h3 className="app-section-subtitle app-section-subtitle--spaced">Endereço e dados fiscais adicionais</h3>
          <FiscalComplementaryFields
            form={fiscalForm}
            touched={fiscalTouched}
            errors={fiscalErrors}
            onTouch={onFiscalTouch}
            onChange={onFiscalChange}
          />
        </IonCardContent>
      </FrottoCard>

      <FrottoCard>
        <IonCardHeader className="app-panel-header">
          <div className="app-soft-icon">
            <IonIcon icon={imagesOutline} />
          </div>
          <div className="app-panel-header__content">
            <IonCardTitle className="app-panel-title">Identidade nos documentos</IonCardTitle>
            <IonCardSubtitle className="app-panel-subtitle">
              Adicione a logomarca que deseja utilizar nos documentos e relatórios gerados pelo Frotto.
            </IonCardSubtitle>
          </div>
        </IonCardHeader>
        <IonCardContent>
          <div className="app-avatar-block">
            <div className="app-avatar-block__avatar app-avatar-block__avatar--square">
              {logoPreviewUrl ? (
                <img src={logoPreviewUrl} alt="Logomarca da empresa" />
              ) : (
                <div className="app-avatar-block__placeholder">
                  <IonIcon icon={imagesOutline} />
                </div>
              )}
            </div>
            <div className="app-avatar-block__content">
              <h2>Logomarca</h2>
            </div>
          </div>

          <div className="app-actions-row">
            <IonButton fill="outline" className="app-outline-btn" onClick={onChangeLogo}>
              Alterar logomarca
            </IonButton>
            <IonButton
              fill="clear"
              color="danger"
              disabled={!canRemoveLogo}
              onClick={onRemoveLogo}
            >
              Remover
            </IonButton>
          </div>
        </IonCardContent>
      </FrottoCard>
    </>
  );
};

export default MeuCadastroTab;
