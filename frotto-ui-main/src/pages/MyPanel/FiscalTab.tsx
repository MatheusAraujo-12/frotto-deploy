import { IonButton, IonCard, IonCardContent, IonCardHeader, IonCardSubtitle, IonCardTitle, IonIcon, IonInput, IonItem, IonLabel, IonSegment, IonSegmentButton, IonText, IonTextarea } from "@ionic/react";
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
    <IonText color="danger" className="my-panel-field-error">
      {message}
    </IonText>
  ) : null;

const FiscalTab: React.FC<FiscalTabProps> = ({ form, touched, errors, hasData, onTouch, onChange, onQuickSave }) => (
  <IonCard className="my-panel-card">
    <IonCardHeader>
      <IonCardTitle>
        <IonIcon icon={cardOutline} />
        Dados Fiscais
      </IonCardTitle>
      <IonCardSubtitle>Defina o tipo de pessoa fiscal e os campos obrigatórios.</IonCardSubtitle>
    </IonCardHeader>
    <IonCardContent>
      {!hasData && (
        <div className="my-panel-empty-state">
          <h3>Nenhum dado fiscal salvo</h3>
          <p>Escolha CPF ou CNPJ e preencha as informações principais.</p>
          <IonButton size="small" fill="outline" onClick={onQuickSave}>
            Salvar Agora
          </IonButton>
        </div>
      )}

      <IonSegment
        value={form.taxPersonType}
        className="my-panel-tax-switch"
        onIonChange={(event) => {
          onTouch("taxPersonType");
          onChange({
            ...form,
            taxPersonType: (event.detail.value as TaxPersonType) || "CPF",
          });
        }}
      >
        <IonSegmentButton value="CPF">
          <IonLabel>CPF</IonLabel>
        </IonSegmentButton>
        <IonSegmentButton value="CNPJ">
          <IonLabel>CNPJ</IonLabel>
        </IonSegmentButton>
      </IonSegment>

      {form.taxPersonType === "CPF" ? (
        <>
          <IonItem className="my-panel-item">
            <IonLabel position="stacked">Nome do locador</IonLabel>
            <IonInput
              value={form.taxLandlordName}
              placeholder="Ex.: João Locador"
              onIonInput={(event: any) => {
                onTouch("taxLandlordName");
                onChange({ ...form, taxLandlordName: event.detail.value || "" });
              }}
              onIonBlur={() => onTouch("taxLandlordName")}
            />
          </IonItem>
          {renderFieldError(touched.taxLandlordName, errors.taxLandlordName)}

          <IonItem className="my-panel-item">
            <IonLabel position="stacked">CPF</IonLabel>
            <IonInput
              value={form.taxCpf}
              placeholder="000.000.000-00"
              onIonInput={(event: any) => {
                onTouch("taxCpf");
                onChange({ ...form, taxCpf: maskCPF(event.detail.value || "") });
              }}
              onIonBlur={() => onTouch("taxCpf")}
            />
          </IonItem>
          {renderFieldError(touched.taxCpf, errors.taxCpf)}

          <IonItem className="my-panel-item">
            <IonLabel position="stacked">E-mail</IonLabel>
            <IonInput
              type="email"
              value={form.taxEmail}
              placeholder="locador@email.com"
              onIonInput={(event: any) => {
                onTouch("taxEmail");
                onChange({ ...form, taxEmail: event.detail.value || "" });
              }}
              onIonBlur={() => onTouch("taxEmail")}
            />
          </IonItem>
          {renderFieldError(touched.taxEmail, errors.taxEmail)}

          <IonItem className="my-panel-item">
            <IonLabel position="stacked">Telefone</IonLabel>
            <IonInput
              value={form.taxPhone}
              placeholder="(11) 99999-9999"
              onIonInput={(event: any) => {
                onTouch("taxPhone");
                onChange({ ...form, taxPhone: maskPhone(event.detail.value || "") });
              }}
              onIonBlur={() => onTouch("taxPhone")}
            />
          </IonItem>
          {renderFieldError(touched.taxPhone, errors.taxPhone)}
        </>
      ) : (
        <>
          <IonItem className="my-panel-item">
            <IonLabel position="stacked">Empresa / Razão social</IonLabel>
            <IonInput
              value={form.taxCompanyName}
              placeholder="Ex.: Frotto Locações LTDA"
              onIonInput={(event: any) => {
                onTouch("taxCompanyName");
                onChange({ ...form, taxCompanyName: event.detail.value || "" });
              }}
              onIonBlur={() => onTouch("taxCompanyName")}
            />
          </IonItem>
          {renderFieldError(touched.taxCompanyName, errors.taxCompanyName)}

          <IonItem className="my-panel-item">
            <IonLabel position="stacked">CNPJ</IonLabel>
            <IonInput
              value={form.taxCnpj}
              placeholder="00.000.000/0000-00"
              onIonInput={(event: any) => {
                onTouch("taxCnpj");
                onChange({ ...form, taxCnpj: maskCNPJ(event.detail.value || "") });
              }}
              onIonBlur={() => onTouch("taxCnpj")}
            />
          </IonItem>
          {renderFieldError(touched.taxCnpj, errors.taxCnpj)}

          <IonItem className="my-panel-item">
            <IonLabel position="stacked">IE</IonLabel>
            <IonInput
              value={form.taxIe}
              placeholder="Inscrição estadual"
              onIonInput={(event: any) => {
                onTouch("taxIe");
                onChange({ ...form, taxIe: event.detail.value || "" });
              }}
              onIonBlur={() => onTouch("taxIe")}
            />
          </IonItem>
          {renderFieldError(touched.taxIe, errors.taxIe)}

          <IonItem className="my-panel-item">
            <IonLabel position="stacked">Telefone para contato</IonLabel>
            <IonInput
              value={form.taxContactPhone}
              placeholder="(11) 99999-9999"
              onIonInput={(event: any) => {
                onTouch("taxContactPhone");
                onChange({
                  ...form,
                  taxContactPhone: maskPhone(event.detail.value || ""),
                });
              }}
              onIonBlur={() => onTouch("taxContactPhone")}
            />
          </IonItem>
          {renderFieldError(touched.taxContactPhone, errors.taxContactPhone)}

          <IonItem className="my-panel-item">
            <IonLabel position="stacked">Endereço completo</IonLabel>
            <IonTextarea
              value={form.taxAddress}
              autoGrow
              rows={3}
              placeholder="Rua, número, bairro, cidade, estado e CEP"
              onIonInput={(event: any) => {
                onTouch("taxAddress");
                onChange({ ...form, taxAddress: event.detail.value || "" });
              }}
              onIonBlur={() => onTouch("taxAddress")}
            />
          </IonItem>
          {renderFieldError(touched.taxAddress, errors.taxAddress)}
        </>
      )}
    </IonCardContent>
  </IonCard>
);

export default FiscalTab;
