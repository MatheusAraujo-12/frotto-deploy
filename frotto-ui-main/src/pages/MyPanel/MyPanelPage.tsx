import {
  IonButton,
  IonButtons,
  IonCard,
  IonCardContent,
  IonContent,
  IonFooter,
  IonHeader,
  IonLabel,
  IonMenuButton,
  IonPage,
  IonSegment,
  IonSegmentButton,
  IonSkeletonText,
  IonTitle,
  IonToolbar,
  useIonActionSheet,
  useIonAlert,
  useIonToast,
} from "@ionic/react";
import { useCallback, useEffect, useMemo, useState } from "react";
import { usePhotoGallery } from "../../services/hooks/usePhotoGallery";
import profileService, { MeResponseDTO } from "../../services/profileService";
import FiscalTab from "./FiscalTab";
import PersonalTab from "./PersonalTab";
import SecurityTab from "./SecurityTab";
import {
  EMPTY_FISCAL_FORM,
  EMPTY_FISCAL_TOUCHED,
  EMPTY_PERSONAL_FORM,
  EMPTY_PERSONAL_TOUCHED,
  EMPTY_SECURITY_FORM,
  EMPTY_SECURITY_TOUCHED,
  FiscalForm,
  hasFiscalData,
  hasPersonalData,
  mapFiscalForm,
  mapPersonalForm,
  PanelTab,
  PersonalForm,
  SecurityForm,
  serializeFiscalForm,
  serializePersonalForm,
  toNullable,
  toNullableDigits,
  validateFiscal,
  validatePersonal,
  validateSecurity,
} from "./profilePanelUtils";
import "./MyPanelPage.css";

type AccountMetaForm = {
  firstName: string;
  lastName: string;
  langKey: string;
  imageUrl: string;
};

const EMPTY_ACCOUNT_META: AccountMetaForm = {
  firstName: "",
  lastName: "",
  langKey: "",
  imageUrl: "",
};

const renderSkeleton = () => (
  <IonCard className="my-panel-card">
    <IonCardContent>
      {Array.from({ length: 5 }).map((_, index) => (
        <div key={`my-panel-skeleton-${index}`} className="my-panel-skeleton-line">
          <IonSkeletonText animated style={{ width: "35%" }} />
          <IonSkeletonText animated style={{ width: "100%" }} />
        </div>
      ))}
    </IonCardContent>
  </IonCard>
);

const touchAllPersonal: Record<keyof PersonalForm, boolean> = {
  personalName: true,
  personalCpf: true,
  personalBirthDate: true,
  personalEmail: true,
  personalPhone: true,
};

const touchAllFiscal: Record<keyof FiscalForm, boolean> = {
  taxPersonType: true,
  taxLandlordName: true,
  taxCpf: true,
  taxEmail: true,
  taxPhone: true,
  taxCompanyName: true,
  taxCnpj: true,
  taxIe: true,
  taxContactPhone: true,
  taxAddress: true,
};

const touchAllSecurity: Record<keyof SecurityForm, boolean> = {
  oldPassword: true,
  newPassword: true,
  confirmPassword: true,
};

const resolveProfileImageUrl = (imageUrl: string | null | undefined): string => {
  const value = `${imageUrl || ""}`.trim();
  if (!value) {
    return "";
  }

  if (
    value.startsWith("http://") ||
    value.startsWith("https://") ||
    value.startsWith("blob:") ||
    value.startsWith("data:")
  ) {
    return value;
  }

  const s3Base = `${process.env.REACT_APP_S3_URL || ""}`.trim();
  return s3Base ? `${s3Base}${value}` : value;
};

const splitName = (fullName: string): { firstName: string; lastName: string } => {
  const parts = fullName.trim().split(/\s+/).filter(Boolean);
  if (!parts.length) {
    return { firstName: "", lastName: "" };
  }

  return {
    firstName: parts[0],
    lastName: parts.slice(1).join(" "),
  };
};

const mapAccountMeta = (data: MeResponseDTO): AccountMetaForm => ({
  firstName: data.firstName ?? "",
  lastName: data.lastName ?? "",
  langKey: data.langKey ?? "",
  imageUrl: data.imageUrl ?? "",
});

const deriveAccountMetaFromFiscal = (fiscalForm: FiscalForm, currentMeta: AccountMetaForm): AccountMetaForm => {
  const baseLang = currentMeta.langKey.trim() || "pt-br";

  if (fiscalForm.taxPersonType === "CPF") {
    const sourceName = fiscalForm.taxLandlordName.trim();
    if (!sourceName) {
      return { ...currentMeta, langKey: baseLang };
    }

    const names = splitName(sourceName);
    return {
      ...currentMeta,
      firstName: names.firstName,
      lastName: names.lastName,
      langKey: baseLang,
    };
  }

  const companyName = fiscalForm.taxCompanyName.trim();
  if (!companyName) {
    return { ...currentMeta, langKey: baseLang };
  }

  return {
    ...currentMeta,
    firstName: companyName,
    lastName: "",
    langKey: baseLang,
  };
};

const MyPanelPage: React.FC = () => {
  const [presentAlert] = useIonAlert();
  const [presentToast] = useIonToast();
  const [presentActionSheet] = useIonActionSheet();

  const { pickImage } = usePhotoGallery();

  const [activeTab, setActiveTab] = useState<PanelTab>("pessoal");
  const [isLoading, setIsLoading] = useState(true);
  const [isSaving, setIsSaving] = useState(false);
  const [hasLoadError, setHasLoadError] = useState(false);
  const [avatarFile, setAvatarFile] = useState<File | null>(null);
  const [avatarPreviewUrl, setAvatarPreviewUrl] = useState("");
  const [avatarRemoved, setAvatarRemoved] = useState(false);

  const [personalForm, setPersonalForm] = useState<PersonalForm>(EMPTY_PERSONAL_FORM);
  const [fiscalForm, setFiscalForm] = useState<FiscalForm>(EMPTY_FISCAL_FORM);
  const [securityForm, setSecurityForm] = useState<SecurityForm>(EMPTY_SECURITY_FORM);
  const [accountMeta, setAccountMeta] = useState<AccountMetaForm>(EMPTY_ACCOUNT_META);

  const [initialPersonalForm, setInitialPersonalForm] = useState<PersonalForm>(EMPTY_PERSONAL_FORM);
  const [initialFiscalForm, setInitialFiscalForm] = useState<FiscalForm>(EMPTY_FISCAL_FORM);
  const [initialSecurityForm, setInitialSecurityForm] = useState<SecurityForm>(EMPTY_SECURITY_FORM);

  const [personalTouched, setPersonalTouched] = useState(EMPTY_PERSONAL_TOUCHED);
  const [fiscalTouched, setFiscalTouched] = useState(EMPTY_FISCAL_TOUCHED);
  const [securityTouched, setSecurityTouched] = useState(EMPTY_SECURITY_TOUCHED);

  const personalErrors = useMemo(() => validatePersonal(personalForm), [personalForm]);
  const fiscalErrors = useMemo(() => validateFiscal(fiscalForm), [fiscalForm]);
  const securityErrors = useMemo(() => validateSecurity(securityForm), [securityForm]);

  const personalDirty = useMemo(
    () => serializePersonalForm(personalForm) !== serializePersonalForm(initialPersonalForm),
    [personalForm, initialPersonalForm]
  );
  const fiscalDirty = useMemo(
    () => serializeFiscalForm(fiscalForm) !== serializeFiscalForm(initialFiscalForm),
    [fiscalForm, initialFiscalForm]
  );
  const securityDirty = useMemo(
    () =>
      JSON.stringify(securityForm) !== JSON.stringify(initialSecurityForm) &&
      Object.values(securityForm).some(Boolean),
    [securityForm, initialSecurityForm]
  );

  const hydrateForms = useCallback((data: MeResponseDTO) => {
    const personal = mapPersonalForm(data);
    const fiscal = mapFiscalForm(data);

    setPersonalForm(personal);
    setFiscalForm(fiscal);
    setAccountMeta(mapAccountMeta(data));
    setSecurityForm(EMPTY_SECURITY_FORM);
    setAvatarFile(null);
    setAvatarPreviewUrl((previousPreviewUrl) => {
      if (previousPreviewUrl.startsWith("blob:")) {
        URL.revokeObjectURL(previousPreviewUrl);
      }
      return resolveProfileImageUrl(data.imageUrl);
    });
    setAvatarRemoved(false);

    setInitialPersonalForm(personal);
    setInitialFiscalForm(fiscal);
    setInitialSecurityForm(EMPTY_SECURITY_FORM);

    setPersonalTouched({ ...EMPTY_PERSONAL_TOUCHED });
    setFiscalTouched({ ...EMPTY_FISCAL_TOUCHED });
    setSecurityTouched({ ...EMPTY_SECURITY_TOUCHED });
  }, []);

  const getErrorMessage = (error: any, fallback: string): string =>
    error?.response?.data?.detail || error?.response?.data?.title || error?.message || fallback;

  const showError = useCallback(
    (message: string) => presentAlert({ header: "Erro", message, buttons: ["OK"] }),
    [presentAlert]
  );

  const showSuccess = useCallback(
    (message: string) =>
      presentToast({ message, color: "success", duration: 2200, position: "top" }),
    [presentToast]
  );

  const loadProfile = useCallback(async () => {
    setIsLoading(true);
    setHasLoadError(false);
    try {
      const data = await profileService.getMe();
      hydrateForms(data);
    } catch (error: any) {
      setHasLoadError(true);
      showError(getErrorMessage(error, "Falha ao carregar o painel."));
    } finally {
      setIsLoading(false);
    }
  }, [hydrateForms, showError]);

  useEffect(() => {
    loadProfile();
  }, [loadProfile]);

  useEffect(() => {
    return () => {
      if (avatarPreviewUrl.startsWith("blob:")) {
        URL.revokeObjectURL(avatarPreviewUrl);
      }
    };
  }, [avatarPreviewUrl]);

  const savePersonal = async () => {
    let imageUrl = toNullable(accountMeta.imageUrl);
    if (avatarRemoved) {
      const avatarData = await profileService.removeAvatar();
      imageUrl = avatarData.imageUrl ?? null;
    }

    if (avatarFile) {
      const avatarData = await profileService.uploadAvatar(avatarFile);
      imageUrl = avatarData.imageUrl ?? imageUrl;
    }

    const fiscalSource = fiscalDirty ? initialFiscalForm : fiscalForm;
    const derivedAccountMeta = deriveAccountMetaFromFiscal(fiscalSource, accountMeta);

    const payload = {
      firstName: toNullable(derivedAccountMeta.firstName),
      lastName: toNullable(derivedAccountMeta.lastName),
      imageUrl,
      langKey: toNullable(derivedAccountMeta.langKey),
      personalName: toNullable(personalForm.personalName),
      personalCpf: toNullableDigits(personalForm.personalCpf),
      personalBirthDate: personalForm.personalBirthDate || null,
      personalEmail: toNullable(personalForm.personalEmail),
      personalPhone: toNullableDigits(personalForm.personalPhone),
    };

    const data = await profileService.updatePersonal(payload);
    hydrateForms(data);
    await showSuccess("Dados salvos com sucesso.");
  };

  const saveFiscal = async () => {
    const data = await profileService.updateTaxData({
      taxPersonType: fiscalForm.taxPersonType,
      taxLandlordName: toNullable(fiscalForm.taxLandlordName),
      taxCpf: toNullableDigits(fiscalForm.taxCpf),
      taxEmail: toNullable(fiscalForm.taxEmail),
      taxPhone: toNullableDigits(fiscalForm.taxPhone),
      taxCompanyName: toNullable(fiscalForm.taxCompanyName),
      taxCnpj: toNullableDigits(fiscalForm.taxCnpj),
      taxIe: toNullable(fiscalForm.taxIe),
      taxContactPhone: toNullableDigits(fiscalForm.taxContactPhone),
      taxAddress: toNullable(fiscalForm.taxAddress),
    });

    hydrateForms(data);
    await showSuccess("Dados fiscais salvos com sucesso.");
  };

  const saveSecurity = async () => {
    await profileService.changePassword({
      oldPassword: securityForm.oldPassword,
      newPassword: securityForm.newPassword,
    });

    setSecurityForm(EMPTY_SECURITY_FORM);
    setInitialSecurityForm(EMPTY_SECURITY_FORM);
    setSecurityTouched({ ...EMPTY_SECURITY_TOUCHED });
    await showSuccess("Senha alterada com sucesso.");
  };

  const handleAvatarSelect = useCallback(
    (file: File, previewUrl: string) => {
      if (avatarPreviewUrl.startsWith("blob:")) {
        URL.revokeObjectURL(avatarPreviewUrl);
      }

      setAvatarFile(file);
      setAvatarPreviewUrl(previewUrl);
      setAvatarRemoved(false);
    },
    [avatarPreviewUrl]
  );

  const handleRemoveAvatar = useCallback(() => {
    if (avatarPreviewUrl.startsWith("blob:")) {
      URL.revokeObjectURL(avatarPreviewUrl);
    }

    setAvatarFile(null);
    setAvatarPreviewUrl("");
    setAvatarRemoved(true);
  }, [avatarPreviewUrl]);

  const pickAvatar = useCallback(
    async (preferCamera: boolean) => {
      try {
        const picked = await pickImage({ preferCamera, multiple: false });
        if (!picked) {
          return;
        }

        handleAvatarSelect(picked.file, picked.previewUrl);
      } catch (error: any) {
        showError(getErrorMessage(error, "Falha ao selecionar a imagem."));
      }
    },
    [pickImage, handleAvatarSelect, showError]
  );

  const openAvatarPicker = () => {
    presentActionSheet({
      header: "Alterar foto",
      buttons: [
        {
          text: "Camera",
          handler: () => {
            void pickAvatar(true);
          },
        },
        {
          text: "Galeria",
          handler: () => {
            void pickAvatar(false);
          },
        },
        {
          text: "Cancelar",
          role: "cancel",
        },
      ],
    });
  };

  const onSave = async () => {
    if (activeTab === "pessoal") setPersonalTouched({ ...touchAllPersonal });
    if (activeTab === "fiscal") setFiscalTouched({ ...touchAllFiscal });
    if (activeTab === "segurança") setSecurityTouched({ ...touchAllSecurity });

    const invalid =
      (activeTab === "pessoal" && Object.keys(personalErrors).length > 0) ||
      (activeTab === "fiscal" && Object.keys(fiscalErrors).length > 0) ||
      (activeTab === "segurança" && Object.keys(securityErrors).length > 0);

    if (invalid) return;

    setIsSaving(true);
    try {
      if (activeTab === "pessoal") await savePersonal();
      if (activeTab === "fiscal") await saveFiscal();
      if (activeTab === "segurança") await saveSecurity();
    } catch (error: any) {
      showError(getErrorMessage(error, "Falha ao salvar os dados."));
    } finally {
      setIsSaving(false);
    }
  };

  const avatarDirty = Boolean(avatarFile) || avatarRemoved;
  const isDirty =
    activeTab === "pessoal"
      ? personalDirty || avatarDirty
      : activeTab === "fiscal"
      ? fiscalDirty
      : securityDirty;

  const isInvalid =
    activeTab === "pessoal"
      ? Object.keys(personalErrors).length > 0
      : activeTab === "fiscal"
      ? Object.keys(fiscalErrors).length > 0
      : Object.keys(securityErrors).length > 0;

  const saveLabel =
    activeTab === "pessoal"
      ? "Salvar Dados Pessoais"
      : activeTab === "fiscal"
      ? "Salvar Dados Fiscais"
      : "Salvar Nova Senha";

  return (
    <IonPage id="my-panel-page">
      <IonHeader>
        <IonToolbar>
          <IonButtons slot="start">
            <IonMenuButton menu="main-menu" autoHide={false} />
          </IonButtons>
          <IonTitle>Meu Painel</IonTitle>
        </IonToolbar>

        <IonToolbar className="my-panel-segment-toolbar">
          <IonSegment
            value={activeTab}
            className="my-panel-segment"
            onIonChange={(event) =>
              setActiveTab((event.detail.value as PanelTab) || "pessoal")
            }
          >
            <IonSegmentButton value="pessoal">
              <IonLabel>Pessoal</IonLabel>
            </IonSegmentButton>
            <IonSegmentButton value="fiscal">
              <IonLabel>Fiscal</IonLabel>
            </IonSegmentButton>
            <IonSegmentButton value="segurança">
              <IonLabel>Segurança</IonLabel>
            </IonSegmentButton>
          </IonSegment>
        </IonToolbar>
      </IonHeader>

      <IonContent fullscreen className="my-panel-content">
        <div className="section-shell my-panel-shell">
          {hasLoadError && (
            <IonCard className="my-panel-card my-panel-error-card">
              <IonCardContent>
                <h3>Nao foi possivel carregar todos os dados</h3>
                <p>Voce pode tentar novamente agora ou continuar preenchendo o formulario.</p>
                <IonButton size="small" fill="outline" onClick={loadProfile}>
                  Tentar Novamente
                </IonButton>
              </IonCardContent>
            </IonCard>
          )}

          {isLoading && renderSkeleton()}

          {!isLoading && activeTab === "pessoal" && (
            <PersonalTab
              form={personalForm}
              touched={personalTouched}
              errors={personalErrors}
              hasData={hasPersonalData(personalForm)}
              avatarPreviewUrl={avatarPreviewUrl}
              canRemoveAvatar={Boolean(avatarPreviewUrl) || avatarRemoved}
              onTouch={(field) => setPersonalTouched((prev) => ({ ...prev, [field]: true }))}
              onChange={setPersonalForm}
              onQuickSave={onSave}
              onChangeAvatar={openAvatarPicker}
              onRemoveAvatar={handleRemoveAvatar}
            />
          )}

          {!isLoading && activeTab === "fiscal" && (
            <FiscalTab
              form={fiscalForm}
              touched={fiscalTouched}
              errors={fiscalErrors}
              hasData={hasFiscalData(fiscalForm)}
              onTouch={(field) => setFiscalTouched((prev) => ({ ...prev, [field]: true }))}
              onChange={setFiscalForm}
              onQuickSave={onSave}
            />
          )}

          {!isLoading && activeTab === "segurança" && (
            <SecurityTab
              form={securityForm}
              touched={securityTouched}
              errors={securityErrors}
              onTouch={(field) => setSecurityTouched((prev) => ({ ...prev, [field]: true }))}
              onChange={setSecurityForm}
            />
          )}
        </div>
      </IonContent>

      <IonFooter translucent className="my-panel-footer">
        <IonToolbar>
          <div className="my-panel-footer-inner">
            <IonButton
              expand="block"
              disabled={isLoading || isSaving || isInvalid || !isDirty}
              onClick={onSave}
            >
              {isSaving ? "Salvando..." : saveLabel}
            </IonButton>
          </div>
        </IonToolbar>
      </IonFooter>
    </IonPage>
  );
};

export default MyPanelPage;
