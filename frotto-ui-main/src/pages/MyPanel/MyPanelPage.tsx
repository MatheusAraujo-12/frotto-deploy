import {
  IonButton,
  IonButtons,
  IonCard,
  IonCardContent,
  IonContent,
  IonFooter,
  IonHeader,
  IonMenuButton,
  IonPage,
  IonSegment,
  IonSegmentButton,
  IonSkeletonText,
  IonLabel,
  IonTitle,
  IonToolbar,
  useIonAlert,
  useIonToast,
} from "@ionic/react";
import { useCallback, useEffect, useMemo, useState } from "react";
import profileService, { MeResponseDTO } from "../../services/profileService";
import CadastroTab from "./CadastroTab";
import FiscalTab from "./FiscalTab";
import PersonalTab from "./PersonalTab";
import SecurityTab from "./SecurityTab";
import {
  CadastroForm,
  EMPTY_CADASTRO_FORM,
  EMPTY_CADASTRO_TOUCHED,
  EMPTY_FISCAL_FORM,
  EMPTY_FISCAL_TOUCHED,
  EMPTY_PERSONAL_FORM,
  EMPTY_PERSONAL_TOUCHED,
  EMPTY_SECURITY_FORM,
  EMPTY_SECURITY_TOUCHED,
  FiscalForm,
  hasCadastroData,
  hasFiscalData,
  hasPersonalData,
  mapCadastroForm,
  mapFiscalForm,
  mapPersonalForm,
  PanelTab,
  PersonalForm,
  serializeCadastroForm,
  serializeFiscalForm,
  serializePersonalForm,
  toNullable,
  toNullableDigits,
  validateCadastro,
  validateFiscal,
  validatePersonal,
  validateSecurity,
  SecurityForm,
} from "./profilePanelUtils";
import "./MyPanelPage.css";

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

const touchAllCadastro: Record<keyof CadastroForm, boolean> = {
  firstName: true,
  lastName: true,
  imageUrl: true,
  langKey: true,
};

const MyPanelPage: React.FC = () => {
  const [presentAlert] = useIonAlert();
  const [presentToast] = useIonToast();

  const [activeTab, setActiveTab] = useState<PanelTab>("pessoal");
  const [isLoading, setIsLoading] = useState(true);
  const [isSaving, setIsSaving] = useState(false);
  const [hasLoadError, setHasLoadError] = useState(false);

  const [personalForm, setPersonalForm] = useState<PersonalForm>(EMPTY_PERSONAL_FORM);
  const [fiscalForm, setFiscalForm] = useState<FiscalForm>(EMPTY_FISCAL_FORM);
  const [securityForm, setSecurityForm] = useState<SecurityForm>(EMPTY_SECURITY_FORM);
  const [cadastroForm, setCadastroForm] = useState<CadastroForm>(EMPTY_CADASTRO_FORM);

  const [initialPersonalForm, setInitialPersonalForm] = useState<PersonalForm>(EMPTY_PERSONAL_FORM);
  const [initialFiscalForm, setInitialFiscalForm] = useState<FiscalForm>(EMPTY_FISCAL_FORM);
  const [initialSecurityForm, setInitialSecurityForm] = useState<SecurityForm>(EMPTY_SECURITY_FORM);
  const [initialCadastroForm, setInitialCadastroForm] = useState<CadastroForm>(EMPTY_CADASTRO_FORM);

  const [personalTouched, setPersonalTouched] = useState(EMPTY_PERSONAL_TOUCHED);
  const [fiscalTouched, setFiscalTouched] = useState(EMPTY_FISCAL_TOUCHED);
  const [securityTouched, setSecurityTouched] = useState(EMPTY_SECURITY_TOUCHED);
  const [cadastroTouched, setCadastroTouched] = useState(EMPTY_CADASTRO_TOUCHED);

  const personalErrors = useMemo(() => validatePersonal(personalForm), [personalForm]);
  const fiscalErrors = useMemo(() => validateFiscal(fiscalForm), [fiscalForm]);
  const securityErrors = useMemo(() => validateSecurity(securityForm), [securityForm]);
  const cadastroErrors = useMemo(() => validateCadastro(cadastroForm), [cadastroForm]);

  const personalDirty = useMemo(() => serializePersonalForm(personalForm) !== serializePersonalForm(initialPersonalForm), [personalForm, initialPersonalForm]);
  const fiscalDirty = useMemo(() => serializeFiscalForm(fiscalForm) !== serializeFiscalForm(initialFiscalForm), [fiscalForm, initialFiscalForm]);
  const securityDirty = useMemo(() => JSON.stringify(securityForm) !== JSON.stringify(initialSecurityForm) && Object.values(securityForm).some(Boolean), [securityForm, initialSecurityForm]);
  const cadastroDirty = useMemo(() => serializeCadastroForm(cadastroForm) !== serializeCadastroForm(initialCadastroForm), [cadastroForm, initialCadastroForm]);

  const hydrateForms = useCallback((data: MeResponseDTO) => {
    const personal = mapPersonalForm(data);
    const fiscal = mapFiscalForm(data);
    const cadastro = mapCadastroForm(data);

    setPersonalForm(personal);
    setFiscalForm(fiscal);
    setCadastroForm(cadastro);
    setSecurityForm(EMPTY_SECURITY_FORM);

    setInitialPersonalForm(personal);
    setInitialFiscalForm(fiscal);
    setInitialCadastroForm(cadastro);
    setInitialSecurityForm(EMPTY_SECURITY_FORM);

    setPersonalTouched({ ...EMPTY_PERSONAL_TOUCHED });
    setFiscalTouched({ ...EMPTY_FISCAL_TOUCHED });
    setSecurityTouched({ ...EMPTY_SECURITY_TOUCHED });
    setCadastroTouched({ ...EMPTY_CADASTRO_TOUCHED });
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

  const savePersonalAndCadastro = async () => {
    const payload = {
      firstName: toNullable(cadastroForm.firstName),
      lastName: toNullable(cadastroForm.lastName),
      imageUrl: toNullable(cadastroForm.imageUrl),
      langKey: toNullable(cadastroForm.langKey),
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

  const onSave = async () => {
    if (activeTab === "pessoal") setPersonalTouched({ ...touchAllPersonal });
    if (activeTab === "fiscal") setFiscalTouched({ ...touchAllFiscal });
    if (activeTab === "seguranca") setSecurityTouched({ ...touchAllSecurity });
    if (activeTab === "cadastro") setCadastroTouched({ ...touchAllCadastro });

    const invalid =
      (activeTab === "pessoal" && Object.keys(personalErrors).length > 0) ||
      (activeTab === "fiscal" && Object.keys(fiscalErrors).length > 0) ||
      (activeTab === "seguranca" && Object.keys(securityErrors).length > 0) ||
      (activeTab === "cadastro" && Object.keys(cadastroErrors).length > 0);

    if (invalid) return;

    setIsSaving(true);
    try {
      if (activeTab === "pessoal" || activeTab === "cadastro") await savePersonalAndCadastro();
      if (activeTab === "fiscal") await saveFiscal();
      if (activeTab === "seguranca") await saveSecurity();
    } catch (error: any) {
      showError(getErrorMessage(error, "Falha ao salvar os dados."));
    } finally {
      setIsSaving(false);
    }
  };

  const isDirty = activeTab === "pessoal" ? personalDirty : activeTab === "fiscal" ? fiscalDirty : activeTab === "seguranca" ? securityDirty : cadastroDirty;
  const isInvalid = activeTab === "pessoal" ? Object.keys(personalErrors).length > 0 : activeTab === "fiscal" ? Object.keys(fiscalErrors).length > 0 : activeTab === "seguranca" ? Object.keys(securityErrors).length > 0 : Object.keys(cadastroErrors).length > 0;
  const saveLabel = activeTab === "pessoal" ? "Salvar Dados Pessoais" : activeTab === "fiscal" ? "Salvar Dados Fiscais" : activeTab === "seguranca" ? "Salvar Nova Senha" : "Salvar Cadastro";

  return (
    <IonPage id="my-panel-page">
      <IonHeader>
        <IonToolbar>
          <IonButtons slot="start">
            <IonMenuButton menu="main-menu" />
          </IonButtons>
          <IonTitle>Meu Painel</IonTitle>
        </IonToolbar>
        <IonToolbar className="my-panel-segment-toolbar">
          <IonSegment value={activeTab} className="my-panel-segment" onIonChange={(event) => setActiveTab((event.detail.value as PanelTab) || "pessoal")}>
            <IonSegmentButton value="pessoal"><IonLabel>Pessoal</IonLabel></IonSegmentButton>
            <IonSegmentButton value="fiscal"><IonLabel>Fiscal</IonLabel></IonSegmentButton>
            <IonSegmentButton value="seguranca"><IonLabel>Segurança</IonLabel></IonSegmentButton>
            <IonSegmentButton value="cadastro"><IonLabel>Cadastro</IonLabel></IonSegmentButton>
          </IonSegment>
        </IonToolbar>
      </IonHeader>

      <IonContent fullscreen className="my-panel-content">
        <div className="section-shell my-panel-shell">
          {hasLoadError && (
            <IonCard className="my-panel-card my-panel-error-card">
              <IonCardContent>
                <h3>Não foi possível carregar todos os dados</h3>
                <p>Você pode tentar novamente agora ou continuar preenchendo o formulário.</p>
                <IonButton size="small" fill="outline" onClick={loadProfile}>Tentar Novamente</IonButton>
              </IonCardContent>
            </IonCard>
          )}

          {isLoading && renderSkeleton()}
          {!isLoading && activeTab === "pessoal" && <PersonalTab form={personalForm} touched={personalTouched} errors={personalErrors} hasData={hasPersonalData(personalForm)} onTouch={(field) => setPersonalTouched((prev) => ({ ...prev, [field]: true }))} onChange={setPersonalForm} onQuickSave={onSave} />}
          {!isLoading && activeTab === "fiscal" && <FiscalTab form={fiscalForm} touched={fiscalTouched} errors={fiscalErrors} hasData={hasFiscalData(fiscalForm)} onTouch={(field) => setFiscalTouched((prev) => ({ ...prev, [field]: true }))} onChange={setFiscalForm} onQuickSave={onSave} />}
          {!isLoading && activeTab === "seguranca" && <SecurityTab form={securityForm} touched={securityTouched} errors={securityErrors} onTouch={(field) => setSecurityTouched((prev) => ({ ...prev, [field]: true }))} onChange={setSecurityForm} />}
          {!isLoading && activeTab === "cadastro" && <CadastroTab form={cadastroForm} touched={cadastroTouched} errors={cadastroErrors} hasData={hasCadastroData(cadastroForm)} onTouch={(field) => setCadastroTouched((prev) => ({ ...prev, [field]: true }))} onChange={setCadastroForm} onQuickSave={onSave} />}
        </div>
      </IonContent>

      <IonFooter translucent className="my-panel-footer">
        <IonToolbar>
          <div className="my-panel-footer-inner">
            <IonButton expand="block" disabled={isLoading || isSaving || isInvalid || !isDirty} onClick={onSave}>
              {isSaving ? "Salvando..." : saveLabel}
            </IonButton>
          </div>
        </IonToolbar>
      </IonFooter>
    </IonPage>
  );
};

export default MyPanelPage;
