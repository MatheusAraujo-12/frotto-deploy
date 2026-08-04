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
  IonSegment,
  IonSegmentButton,
  IonText,
  IonTextarea,
} from "@ionic/react";
import { cardOutline } from "ionicons/icons";
import { maskCNPJ, maskCPF, maskPhone } from "../../services/profileFormat";
import { TaxPersonType } from "../../services/profileService";
import { FiscalForm, FormErrors } from "./profilePanelUtils";

interface FiscalTabProps {
  form: FiscalForm;
  touched: Record<keyof FiscalForm, boolean>;
  errors: FormErrors<keyof FiscalForm>;
  hasData: boolean;
  onTouch: (field: keyof FiscalForm) => void;
  onChange: (form: FiscalForm) => void;
  onQuickSave: () => void;
}

const renderFieldError = (show: boolean, message?: string) =>
  show && message ? (
    <IonText color="danger" className="app-form-error">
      {message}
    </IonText>
  ) : null;

const getInputValue = (event: any): string =>
  event?.detail?.value ?? event?.target?.value ?? event?.currentTarget?.value ?? "";

const clearFieldsForTaxType = (form: FiscalForm, taxPersonType: TaxPersonType): FiscalForm => {
  if (taxPersonType === "CPF") {
    return {
      ...form,
      taxPersonType,
      taxCompanyName: "",
      taxCnpj: "",
      taxIe: "",
      taxContactPhone: "",
      taxAddress: "",
    };
  }

  return {
    ...form,
    taxPersonType,
    taxLandlordName: "",
    taxCpf: "",
    taxEmail: "",
    taxPhone: "",
  };
};

const FiscalTab: React.FC<FiscalTabProps> = ({
  form,
  touched,
  errors,
  hasData,
  onTouch,
  onChange,
  onQuickSave,
}) => (
  <IonCard className="app-panel-card">
    <IonCardHeader className="app-panel-header">
      <div className="app-soft-icon">
        <IonIcon icon={cardOutline} />
      </div>
      <div className="app-panel-header__content">
        <IonCardTitle className="app-panel-title">Dados Fiscais</IonCardTitle>
        <IonCardSubtitle className="app-panel-subtitle">
          Defina o tipo de pessoa fiscal e os campos obrigatórios.
        </IonCardSubtitle>
      </div>
    </IonCardHeader>
    <IonCardContent>
      {!hasData && (
        <div className="app-empty-state">
          <strong>Nenhum dado fiscal salvo</strong>
          <span>Escolha CPF ou CNPJ e preencha as informações principais.</span>
          <IonButton size="small" fill="outline" className="app-outline-btn" onClick={onQuickSave}>
            Salvar agora
          </IonButton>
        </div>
      )}

      <IonText color="medium" className="my-panel-fiscal-note">
        Os dados de cadastro são derivados automaticamente destes dados fiscais.
      </IonText>

      <IonSegment
        value={form.taxPersonType}
        className="app-segment-shell"
        onIonChange={(event) => {
          const nextType = (event.detail.value as TaxPersonType) || "CPF";
          onTouch("taxPersonType");
          onChange(clearFieldsForTaxType(form, nextType));
        }}
      >
        <IonSegmentButton value="CPF">
          <IonLabel>CPF</IonLabel>
        </IonSegmentButton>
        <IonSegmentButton value="CNPJ">
          <IonLabel>CNPJ</IonLabel>
        </IonSegmentButton>
      </IonSegment>

      <div className="app-form-grid">
        {form.taxPersonType === "CPF" ? (
          <>
            <IonItem className="app-form-item">
              <IonLabel position="stacked">Nome do locador</IonLabel>
              <IonInput
                value={form.taxLandlordName}
                placeholder="Ex.: João Locador"
                onIonInput={(event: any) => {
                  onTouch("taxLandlordName");
                  onChange({ ...form, taxLandlordName: getInputValue(event) });
                }}
                onIonBlur={() => onTouch("taxLandlordName")}
              />
            </IonItem>
            {renderFieldError(touched.taxLandlordName, errors.taxLandlordName)}

            <IonItem className="app-form-item">
              <IonLabel position="stacked">CPF</IonLabel>
              <IonInput
                value={form.taxCpf}
                placeholder="000.000.000-00"
                onIonInput={(event: any) => {
                  onTouch("taxCpf");
                  onChange({ ...form, taxCpf: maskCPF(getInputValue(event)) });
                }}
                onIonBlur={() => onTouch("taxCpf")}
              />
            </IonItem>
            {renderFieldError(touched.taxCpf, errors.taxCpf)}

            <IonItem className="app-form-item">
              <IonLabel position="stacked">E-mail</IonLabel>
              <IonInput
                type="email"
                value={form.taxEmail}
                placeholder="locador@email.com"
                onIonInput={(event: any) => {
                  onTouch("taxEmail");
                  onChange({ ...form, taxEmail: getInputValue(event) });
                }}
                onIonBlur={() => onTouch("taxEmail")}
              />
            </IonItem>
            {renderFieldError(touched.taxEmail, errors.taxEmail)}

            <IonItem className="app-form-item">
              <IonLabel position="stacked">Telefone</IonLabel>
              <IonInput
                value={form.taxPhone}
                placeholder="(11) 99999-9999"
                onIonInput={(event: any) => {
                  onTouch("taxPhone");
                  onChange({ ...form, taxPhone: maskPhone(getInputValue(event)) });
                }}
                onIonBlur={() => onTouch("taxPhone")}
              />
            </IonItem>
            {renderFieldError(touched.taxPhone, errors.taxPhone)}
          </>
        ) : (
          <>
            <IonItem className="app-form-item">
              <IonLabel position="stacked">Empresa / Razão social</IonLabel>
              <IonInput
                value={form.taxCompanyName}
                placeholder="Ex.: Frotto Locações LTDA"
                onIonInput={(event: any) => {
                  onTouch("taxCompanyName");
                  onChange({ ...form, taxCompanyName: getInputValue(event) });
                }}
                onIonBlur={() => onTouch("taxCompanyName")}
              />
            </IonItem>
            {renderFieldError(touched.taxCompanyName, errors.taxCompanyName)}

            <IonItem className="app-form-item">
              <IonLabel position="stacked">CNPJ</IonLabel>
              <IonInput
                value={form.taxCnpj}
                placeholder="00.000.000/0000-00"
                onIonInput={(event: any) => {
                  onTouch("taxCnpj");
                  onChange({ ...form, taxCnpj: maskCNPJ(getInputValue(event)) });
                }}
                onIonBlur={() => onTouch("taxCnpj")}
              />
            </IonItem>
            {renderFieldError(touched.taxCnpj, errors.taxCnpj)}

            <IonItem className="app-form-item">
              <IonLabel position="stacked">IE</IonLabel>
              <IonInput
                value={form.taxIe}
                placeholder="Inscrição estadual"
                onIonInput={(event: any) => {
                  onTouch("taxIe");
                  onChange({ ...form, taxIe: getInputValue(event) });
                }}
                onIonBlur={() => onTouch("taxIe")}
              />
            </IonItem>
            {renderFieldError(touched.taxIe, errors.taxIe)}

            <IonItem className="app-form-item">
              <IonLabel position="stacked">Telefone para contato</IonLabel>
              <IonInput
                value={form.taxContactPhone}
                placeholder="(11) 99999-9999"
                onIonInput={(event: any) => {
                  onTouch("taxContactPhone");
                  onChange({
                    ...form,
                    taxContactPhone: maskPhone(getInputValue(event)),
                  });
                }}
                onIonBlur={() => onTouch("taxContactPhone")}
              />
            </IonItem>
            {renderFieldError(touched.taxContactPhone, errors.taxContactPhone)}

            <IonItem className="app-form-item app-form-item--textarea">
              <IonLabel position="stacked">Endereço completo</IonLabel>
              <IonTextarea
                value={form.taxAddress}
                autoGrow
                rows={3}
                placeholder="Rua, número, bairro, cidade, estado e CEP"
                onIonInput={(event: any) => {
                  onTouch("taxAddress");
                  onChange({ ...form, taxAddress: getInputValue(event) });
                }}
                onIonBlur={() => onTouch("taxAddress")}
              />
            </IonItem>
            {renderFieldError(touched.taxAddress, errors.taxAddress)}
          </>
        )}
      </div>
    </IonCardContent>
  </IonCard>
);

export default FiscalTab;
