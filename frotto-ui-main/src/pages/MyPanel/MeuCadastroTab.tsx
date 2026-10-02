import {
  IonAvatar,
  IonButton,
  IonCardContent,
  IonCardHeader,
  IonCardSubtitle,
  IonCardTitle,
  IonIcon,
  IonText,
} from "@ionic/react";
import { idCardOutline, imageOutline, imagesOutline, personOutline } from "ionicons/icons";
import FrottoBadge from "../../components/UI/FrottoBadge";
import FrottoCard from "../../components/UI/FrottoCard";
import {
  FiscalForm,
  FormErrors,
  getVisiblePersonalFields,
  hasFiscalData,
  PersonalForm,
} from "./profilePanelUtils";
import PersonalTab, { PERSONAL_FIELD_LABELS } from "./PersonalTab";
import { FiscalCadastralFields } from "./FiscalTab";
import {
  formatBirthDate,
  SectionEditActions,
  SectionViewActions,
  SummaryItem,
  SummaryList,
} from "./CadastroSection";

/** Unidades editáveis de Meu Cadastro. Só uma fica em edição por vez. */
export type CadastroSection = "identity" | "personal" | "avatar" | "logo";

const PERSON_TYPE_LABEL = { CPF: "Pessoa Física", CNPJ: "Pessoa Jurídica" } as const;

/** PF: rótulo explícito para um campo pessoal que diverge do dado fiscal. */
const PF_DIVERGENT_LABELS: Partial<Record<keyof PersonalForm, string>> = {
  personalName: "Nome (cadastro pessoal)",
  personalCpf: "CPF (cadastro pessoal)",
  personalEmail: "E-mail (cadastro pessoal)",
  personalPhone: "Telefone (cadastro pessoal)",
};

interface MeuCadastroTabProps {
  editingSection: CadastroSection | null;
  isSaving: boolean;
  onStartEdit: (section: CadastroSection) => void;
  onCancelEdit: () => void;
  onSaveSection: (section: CadastroSection) => void;

  fiscalForm: FiscalForm;
  savedFiscalForm: FiscalForm;
  fiscalTouched: Record<keyof FiscalForm, boolean>;
  fiscalErrors: FormErrors<keyof FiscalForm>;
  fiscalDirty: boolean;
  onFiscalTouch: (field: keyof FiscalForm) => void;
  onFiscalChange: (form: FiscalForm) => void;

  personalForm: PersonalForm;
  savedPersonalForm: PersonalForm;
  personalTouched: Record<keyof PersonalForm, boolean>;
  personalErrors: FormErrors<keyof PersonalForm>;
  personalDirty: boolean;
  onPersonalTouch: (field: keyof PersonalForm) => void;
  onPersonalChange: (form: PersonalForm) => void;

  avatarPreviewUrl: string;
  avatarDirty: boolean;
  avatarRemoved: boolean;
  canRemoveAvatar: boolean;
  onChangeAvatar: () => void;
  onRemoveAvatar: () => void;

  logoPreviewUrl: string;
  logoDirty: boolean;
  logoRemoved: boolean;
  canRemoveLogo: boolean;
  onChangeLogo: () => void;
  onRemoveLogo: () => void;
}

const CardHeader: React.FC<{ icon: string; title: string; subtitle: string; badge?: React.ReactNode }> = ({
  icon,
  title,
  subtitle,
  badge,
}) => (
  <IonCardHeader className="app-panel-header">
    <div className="app-soft-icon">
      <IonIcon icon={icon} />
    </div>
    <div className="app-panel-header__content">
      <div className="cadastro-card-title">
        <IonCardTitle className="app-panel-title">{title}</IonCardTitle>
        {badge}
      </div>
      <IonCardSubtitle className="app-panel-subtitle">{subtitle}</IonCardSubtitle>
    </div>
  </IonCardHeader>
);

const identitySummary = (form: FiscalForm): Array<{ title?: string; items: SummaryItem[] }> =>
  form.taxPersonType === "CPF"
    ? [
        {
          items: [
            { label: "Nome", value: form.taxLandlordName },
            { label: "CPF", value: form.taxCpf },
            { label: "E-mail", value: form.taxEmail },
            { label: "Telefone", value: form.taxPhone },
          ],
        },
      ]
    : [
        {
          title: "Empresa",
          items: [
            { label: "Razão social", value: form.taxCompanyName },
            { label: "CNPJ", value: form.taxCnpj },
          ],
        },
        { title: "Contato da empresa", items: [{ label: "Telefone para contato", value: form.taxContactPhone }] },
        { title: "Endereço", items: [{ label: "Endereço completo", value: form.taxAddress }] },
        { title: "Dados fiscais", items: [{ label: "Inscrição Estadual", value: form.taxIe }] },
      ];

const MeuCadastroTab: React.FC<MeuCadastroTabProps> = (props) => {
  const {
    editingSection,
    isSaving,
    onStartEdit,
    onCancelEdit,
    onSaveSection,
    fiscalForm,
    savedFiscalForm,
    personalForm,
    savedPersonalForm,
  } = props;

  const isEditing = (section: CadastroSection) => editingSection === section;
  const isLocked = (section: CadastroSection) => editingSection !== null && editingSection !== section;

  const hasSavedIdentity = hasFiscalData(savedFiscalForm);
  const isPf = savedFiscalForm.taxPersonType === "CPF";
  const visiblePersonalFields = getVisiblePersonalFields(savedPersonalForm, savedFiscalForm);
  const personalLabels = isPf ? PF_DIVERGENT_LABELS : undefined;

  const typeChangeWarning =
    hasSavedIdentity && fiscalForm.taxPersonType !== savedFiscalForm.taxPersonType
      ? `Ao salvar como ${PERSON_TYPE_LABEL[fiscalForm.taxPersonType]}, os dados de ${
          PERSON_TYPE_LABEL[savedFiscalForm.taxPersonType]
        } serão apagados.`
      : undefined;

  const personalSummary: SummaryItem[] = visiblePersonalFields.map((field) => ({
    label: personalLabels?.[field] ?? PERSONAL_FIELD_LABELS[field],
    value: field === "personalBirthDate" ? formatBirthDate(savedPersonalForm[field]) : savedPersonalForm[field],
  }));

  return (
    <>
      <div className="app-section">
        <h2 className="app-section-title">Meu Cadastro</h2>
        <p className="app-section-subtitle">Gerencie seus dados e a identidade usada nos documentos.</p>
      </div>
      <FrottoCard className="cadastro-card" data-testid="cadastro-identity">
        <CardHeader
          icon={idCardOutline}
          title="Dados cadastrais"
          subtitle="Identificação usada nos documentos e relatórios."
          badge={
            hasSavedIdentity && !isEditing("identity") ? (
              <FrottoBadge variant="info">{PERSON_TYPE_LABEL[savedFiscalForm.taxPersonType]}</FrottoBadge>
            ) : undefined
          }
        />
        <IonCardContent className="cadastro-card__body">
          {isEditing("identity") ? (
            <>
              <FiscalCadastralFields
                form={fiscalForm}
                touched={props.fiscalTouched}
                errors={props.fiscalErrors}
                onTouch={props.onFiscalTouch}
                onChange={props.onFiscalChange}
                typeChangeWarning={typeChangeWarning}
              />
              <SectionEditActions
                isSaving={isSaving}
                saveDisabled={!props.fiscalDirty}
                onCancel={onCancelEdit}
                onSave={() => onSaveSection("identity")}
              />
            </>
          ) : (
            <>
              {identitySummary(savedFiscalForm).map((group, index) => (
                <SummaryList key={group.title ?? index} title={group.title} items={group.items} />
              ))}
              <SectionViewActions
                label={hasSavedIdentity ? "Editar dados" : "Preencher dados"}
                disabled={isLocked("identity")}
                onEdit={() => onStartEdit("identity")}
              />
            </>
          )}
        </IonCardContent>
      </FrottoCard>

      <FrottoCard className="cadastro-card" data-testid="cadastro-personal">
        <CardHeader
          icon={personOutline}
          title={isPf ? "Dados complementares" : "Responsável pela conta"}
          subtitle={
            isPf
              ? "Informações adicionais da sua conta. Não aparecem nos documentos."
              : "Quem usa o Frotto em nome da empresa. Não aparece nos documentos."
          }
        />
        <IonCardContent className="cadastro-card__body">
          <div className="cadastro-group">
            <h3 className="cadastro-group__title">Foto de perfil</h3>
            <div className="app-avatar-block">
              <IonAvatar className="app-avatar-block__avatar">
                {props.avatarPreviewUrl ? (
                  <img src={props.avatarPreviewUrl} alt="Avatar do usuário" />
                ) : (
                  <div className="app-avatar-block__placeholder">
                    <IonIcon icon={imageOutline} />
                  </div>
                )}
              </IonAvatar>
              <div className="app-avatar-block__content">
                <IonText color="medium" className="cadastro-hint">
                  {isEditing("avatar")
                    ? props.avatarRemoved
                      ? "A foto será removida ao salvar."
                      : "Pré-visualização. Salve para aplicar."
                    : "Aparece no menu. Não é usada nos documentos."}
                </IonText>
              </div>
            </div>
            {isEditing("avatar") ? (
              <SectionEditActions
                saveLabel="Salvar foto"
                isSaving={isSaving}
                saveDisabled={!props.avatarDirty}
                onCancel={onCancelEdit}
                onSave={() => onSaveSection("avatar")}
              />
            ) : (
              <div className="app-actions-row">
                <IonButton
                  fill="outline"
                  className="app-outline-btn"
                  disabled={isLocked("avatar")}
                  onClick={props.onChangeAvatar}
                >
                  Alterar foto
                </IonButton>
                <IonButton
                  fill="clear"
                  className="app-danger-btn"
                  disabled={isLocked("avatar") || !props.canRemoveAvatar}
                  onClick={props.onRemoveAvatar}
                >
                  Remover
                </IonButton>
              </div>
            )}
          </div>

          {isEditing("personal") ? (
            <>
              <PersonalTab
                form={personalForm}
                touched={props.personalTouched}
                errors={props.personalErrors}
                visibleFields={visiblePersonalFields}
                labels={personalLabels}
                onTouch={props.onPersonalTouch}
                onChange={props.onPersonalChange}
              />
              <SectionEditActions
                isSaving={isSaving}
                saveDisabled={!props.personalDirty}
                onCancel={onCancelEdit}
                onSave={() => onSaveSection("personal")}
              />
            </>
          ) : (
            <>
              {isPf && (
                <IonText color="medium" className="cadastro-hint" data-testid="cadastro-pf-source-note">
                  Nome, CPF, e-mail e telefone são os informados em Dados cadastrais.
                </IonText>
              )}
              <SummaryList items={personalSummary} />
              <SectionViewActions
                label="Editar"
                disabled={isLocked("personal")}
                onEdit={() => onStartEdit("personal")}
              />
            </>
          )}
        </IonCardContent>
      </FrottoCard>

      <FrottoCard className="cadastro-card" data-testid="cadastro-logo">
        <CardHeader
          icon={imagesOutline}
          title="Identidade nos documentos"
          subtitle="Logomarca usada nos documentos e relatórios gerados pelo Frotto."
        />
        <IonCardContent className="cadastro-card__body">
          <div className="app-avatar-block">
            <div className="app-avatar-block__avatar app-avatar-block__avatar--square">
              {props.logoPreviewUrl ? (
                <img src={props.logoPreviewUrl} alt="Logomarca da empresa" />
              ) : (
                <div className="app-avatar-block__placeholder">
                  <IonIcon icon={imagesOutline} />
                </div>
              )}
            </div>
            <div className="app-avatar-block__content">
              <h2>Logomarca</h2>
              <IonText color="medium" className="cadastro-hint">
                {isEditing("logo")
                  ? props.logoRemoved
                    ? "A logomarca será removida ao salvar."
                    : "Pré-visualização. Salve para aplicar."
                  : props.logoPreviewUrl
                  ? "Aparece no cabeçalho dos documentos."
                  : "Nenhuma logomarca. Os documentos saem só com os dados cadastrais."}
              </IonText>
            </div>
          </div>

          {isEditing("logo") ? (
            <SectionEditActions
              saveLabel="Salvar logomarca"
              isSaving={isSaving}
              saveDisabled={!props.logoDirty}
              onCancel={onCancelEdit}
              onSave={() => onSaveSection("logo")}
            />
          ) : (
            <div className="app-actions-row">
              <IonButton
                fill="outline"
                className="app-outline-btn"
                disabled={isLocked("logo")}
                onClick={props.onChangeLogo}
              >
                Alterar logomarca
              </IonButton>
              <IonButton
                fill="clear"
                className="app-danger-btn"
                disabled={isLocked("logo") || !props.canRemoveLogo}
                onClick={props.onRemoveLogo}
              >
                Remover
              </IonButton>
            </div>
          )}
        </IonCardContent>
      </FrottoCard>
    </>
  );
};

export default MeuCadastroTab;
