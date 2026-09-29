import {
  IonButton,
  IonButtons,
  IonCardContent,
  IonContent,
  IonFooter,
  IonHeader,
  IonIcon,
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
import { idCardOutline, shieldCheckmarkOutline } from "ionicons/icons";
import { useCallback, useEffect, useMemo, useState } from "react";
import FrottoCard from "../../components/UI/FrottoCard";
import ItemNotFound from "../../components/List/ItemNotFound";
import { usePhotoGallery } from "../../services/hooks/usePhotoGallery";
import profileService, { MeResponseDTO } from "../../services/profileService";
import MeuCadastroTab from "./MeuCadastroTab";
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

type SettingsSection = "cadastro" | "seguranca";

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
  <FrottoCard>
    <IonCardContent>
      {Array.from({ length: 5 }).map((_, index) => (
        <div key={`my-panel-skeleton-${index}`} className="my-panel-skeleton-line">
          <IonSkeletonText animated style={{ width: "35%" }} />
          <IonSkeletonText animated style={{ width: "100%" }} />
        </div>
      ))}
    </IonCardContent>
  </FrottoCard>
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

  const [activeSection, setActiveSection] = useState<SettingsSection>("cadastro");
  const [isLoading, setIsLoading] = useState(true);
  const [isSaving, setIsSaving] = useState(false);
  const [hasLoadError, setHasLoadError] = useState(false);

  const [avatarFile, setAvatarFile] = useState<File | null>(null);
  const [avatarPreviewUrl, setAvatarPreviewUrl] = useState("");
  const [avatarRemoved, setAvatarRemoved] = useState(false);

  const [logoFile, setLogoFile] = useState<File | null>(null);
  const [logoPreviewUrl, setLogoPreviewUrl] = useState("");
  const [logoRemoved, setLogoRemoved] = useState(false);

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

    setLogoFile(null);
    setLogoPreviewUrl((previousPreviewUrl) => {
      if (previousPreviewUrl.startsWith("blob:")) {
        URL.revokeObjectURL(previousPreviewUrl);
      }
      return resolveProfileImageUrl(data.logoUrl);
    });
    setLogoRemoved(false);

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
      showError(getErrorMessage(error, "Falha ao carregar as configurações."));
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

  useEffect(() => {
    return () => {
      if (logoPreviewUrl.startsWith("blob:")) {
        URL.revokeObjectURL(logoPreviewUrl);
      }
    };
  }, [logoPreviewUrl]);

  const saveCadastro = async () => {
    let imageUrl = toNullable(accountMeta.imageUrl);
    if (avatarRemoved) {
      const avatarData = await profileService.removeAvatar();
      imageUrl = avatarData.imageUrl ?? null;
    }
    if (avatarFile) {
      const avatarData = await profileService.uploadAvatar(avatarFile);
      imageUrl = avatarData.imageUrl ?? imageUrl;
    }

    if (logoRemoved) {
      await profileService.removeLogo();
    }
    if (logoFile) {
      await profileService.uploadLogo(logoFile);
    }

    let lastResponse: MeResponseDTO | null = null;

    if (personalDirty || avatarFile || avatarRemoved || logoFile || logoRemoved) {
      const derivedAccountMeta = deriveAccountMetaFromFiscal(fiscalForm, accountMeta);
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
      lastResponse = await profileService.updatePersonal(payload);
    }

    if (fiscalDirty) {
      lastResponse = await profileService.updateTaxData({
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
    }

    if (!lastResponse) {
      lastResponse = await profileService.getMe();
    }

    hydrateForms(lastResponse);
    await showSuccess("Cadastro salvo com sucesso.");
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
        { text: "Câmera", handler: () => void pickAvatar(true) },
        { text: "Galeria", handler: () => void pickAvatar(false) },
        { text: "Cancelar", role: "cancel" },
      ],
    });
  };

  const handleLogoSelect = useCallback(
    (file: File, previewUrl: string) => {
      if (logoPreviewUrl.startsWith("blob:")) {
        URL.revokeObjectURL(logoPreviewUrl);
      }

      setLogoFile(file);
      setLogoPreviewUrl(previewUrl);
      setLogoRemoved(false);
    },
    [logoPreviewUrl]
  );

  const handleRemoveLogo = useCallback(() => {
    if (logoPreviewUrl.startsWith("blob:")) {
      URL.revokeObjectURL(logoPreviewUrl);
    }

    setLogoFile(null);
    setLogoPreviewUrl("");
    setLogoRemoved(true);
  }, [logoPreviewUrl]);

  const pickLogo = useCallback(
    async (preferCamera: boolean) => {
      try {
        const picked = await pickImage({ preferCamera, multiple: false });
        if (!picked) {
          return;
        }

        handleLogoSelect(picked.file, picked.previewUrl);
      } catch (error: any) {
        showError(getErrorMessage(error, "Falha ao selecionar a imagem."));
      }
    },
    [pickImage, handleLogoSelect, showError]
  );

  const openLogoPicker = () => {
    presentActionSheet({
      header: "Alterar logomarca",
      buttons: [
        { text: "Câmera", handler: () => void pickLogo(true) },
        { text: "Galeria", handler: () => void pickLogo(false) },
        { text: "Cancelar", role: "cancel" },
      ],
    });
  };

  const onSave = async () => {
    if (activeSection === "cadastro") {
      setPersonalTouched({ ...touchAllPersonal });
      setFiscalTouched({ ...touchAllFiscal });

      const invalid = Object.keys(personalErrors).length > 0 || Object.keys(fiscalErrors).length > 0;
      if (invalid) return;

      setIsSaving(true);
      try {
        await saveCadastro();
      } catch (error: any) {
        showError(getErrorMessage(error, "Falha ao salvar o cadastro."));
      } finally {
        setIsSaving(false);
      }
      return;
    }

    setSecurityTouched({ ...touchAllSecurity });
    if (Object.keys(securityErrors).length > 0) return;

    setIsSaving(true);
    try {
      await saveSecurity();
    } catch (error: any) {
      showError(getErrorMessage(error, "Falha ao salvar os dados."));
    } finally {
      setIsSaving(false);
    }
  };

  const avatarDirty = Boolean(avatarFile) || avatarRemoved;
  const logoDirty = Boolean(logoFile) || logoRemoved;

  const isDirty =
    activeSection === "cadastro" ? personalDirty || fiscalDirty || avatarDirty || logoDirty : securityDirty;

  const isInvalid =
    activeSection === "cadastro"
      ? Object.keys(personalErrors).length > 0 || Object.keys(fiscalErrors).length > 0
      : Object.keys(securityErrors).length > 0;

  const saveLabel = activeSection === "cadastro" ? "Salvar Alterações" : "Salvar Nova Senha";

  const navItems: Array<{ id: SettingsSection; label: string; icon: string }> = [
    { id: "cadastro", label: "Meu Cadastro", icon: idCardOutline },
    { id: "seguranca", label: "Segurança", icon: shieldCheckmarkOutline },
  ];

  return (
    <IonPage id="my-panel-page">
      <IonHeader>
        <IonToolbar className="app-toolbar-clean">
          <IonButtons slot="start">
            <IonMenuButton menu="main-menu" autoHide={false} />
          </IonButtons>
          <IonTitle>Configurações</IonTitle>
        </IonToolbar>

        <IonToolbar className="app-subtoolbar settings-mobile-nav">
          <IonSegment
            value={activeSection}
            className="app-segment-shell"
            onIonChange={(event) =>
              setActiveSection((event.detail.value as SettingsSection) || "cadastro")
            }
          >
            {navItems.map((item) => (
              <IonSegmentButton key={item.id} value={item.id}>
                <IonLabel>{item.label}</IonLabel>
              </IonSegmentButton>
            ))}
          </IonSegment>
        </IonToolbar>
      </IonHeader>

      <IonContent fullscreen className="my-panel-content">
        <div className="app-shell app-shell--compact settings-shell">
          <nav className="settings-rail" aria-label="Seções de Configurações">
            {navItems.map((item) => (
              <button
                key={item.id}
                type="button"
                className={`settings-rail__item${activeSection === item.id ? " settings-rail__item--active" : ""}`}
                onClick={() => setActiveSection(item.id)}
                aria-current={activeSection === item.id ? "page" : undefined}
              >
                <IonIcon icon={item.icon} />
                <span>{item.label}</span>
              </button>
            ))}
          </nav>

          <div className="settings-content">
            {hasLoadError && (
              <FrottoCard>
                <IonCardContent>
                  <ItemNotFound
                    title="Não foi possível carregar todos os dados"
                    description="Você pode tentar novamente agora ou continuar preenchendo o formulário."
                    actionLabel="Tentar novamente"
                    onAction={loadProfile}
                  />
                </IonCardContent>
              </FrottoCard>
            )}

            {isLoading && renderSkeleton()}

            {!isLoading && activeSection === "cadastro" && (
              <MeuCadastroTab
                personalForm={personalForm}
                personalTouched={personalTouched}
                personalErrors={personalErrors}
                hasPersonalData={hasPersonalData(personalForm)}
                avatarPreviewUrl={avatarPreviewUrl}
                canRemoveAvatar={Boolean(avatarPreviewUrl) || avatarRemoved}
                onPersonalTouch={(field) => setPersonalTouched((prev) => ({ ...prev, [field]: true }))}
                onPersonalChange={setPersonalForm}
                onChangeAvatar={openAvatarPicker}
                onRemoveAvatar={handleRemoveAvatar}
                fiscalForm={fiscalForm}
                fiscalTouched={fiscalTouched}
                fiscalErrors={fiscalErrors}
                hasFiscalData={hasFiscalData(fiscalForm)}
                onFiscalTouch={(field) => setFiscalTouched((prev) => ({ ...prev, [field]: true }))}
                onFiscalChange={setFiscalForm}
                logoPreviewUrl={logoPreviewUrl}
                canRemoveLogo={Boolean(logoPreviewUrl) || logoRemoved}
                onChangeLogo={openLogoPicker}
                onRemoveLogo={handleRemoveLogo}
                onQuickSave={onSave}
              />
            )}

            {!isLoading && activeSection === "seguranca" && (
              <SecurityTab
                form={securityForm}
                touched={securityTouched}
                errors={securityErrors}
                onTouch={(field) => setSecurityTouched((prev) => ({ ...prev, [field]: true }))}
                onChange={setSecurityForm}
              />
            )}
          </div>
        </div>
      </IonContent>

      <IonFooter className="app-footer-bar">
        <IonToolbar>
          <div className="app-footer-bar__inner">
            <IonButton
              className="app-semantic-btn app-semantic--success"
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
