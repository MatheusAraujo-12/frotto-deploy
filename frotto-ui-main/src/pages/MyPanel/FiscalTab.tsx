import {
  IonInput,
  IonItem,
  IonSegment,
  IonSegmentButton,
  IonLabel,
  IonText,
  IonTextarea,
} from "@ionic/react";
import { maskCNPJ, maskCPF, maskPhone } from "../../services/profileFormat";
import { TaxPersonType } from "../../services/profileService";
import { FiscalForm, FormErrors } from "./profilePanelUtils";
import FormInputLabel from "../../components/Form/FormInputLabel";
import FormError from "../../components/Form/FormError";
import { getFormErrorId } from "../../components/Form/FormItemWrapper";
import ItemNotFound from "../../components/List/ItemNotFound";

export interface FiscalFieldsProps {
  form: FiscalForm;
  touched: Record<keyof FiscalForm, boolean>;
  errors: FormErrors<keyof FiscalForm>;
  onTouch: (field: keyof FiscalForm) => void;
  onChange: (form: FiscalForm) => void;
}

const getInputValue = (event: any): string =>
  event?.detail?.value ?? event?.target?.value ?? event?.currentTarget?.value ?? "";

export const clearFieldsForTaxType = (form: FiscalForm, taxPersonType: TaxPersonType): FiscalForm => {
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

const fieldError = (
  field: keyof FiscalForm,
  touched: Record<keyof FiscalForm, boolean>,
  errors: FormErrors<keyof FiscalForm>
) => {
  const show = touched[field] && Boolean(errors[field]);
  const id = getFormErrorId(field);
  return {
    wrapClassName: show ? "app-form-field app-form-field--invalid" : "app-form-field",
    ariaInvalid: show ? ("true" as const) : undefined,
    ariaDescribedby: show ? id : undefined,
    node: show ? <FormError id={id} message={errors[field] as string} /> : null,
  };
};

interface FiscalCadastralFieldsProps extends FiscalFieldsProps {
  hasData: boolean;
  onQuickSave: () => void;
}

export const FiscalCadastralFields: React.FC<FiscalCadastralFieldsProps> = ({
  form,
  touched,
  errors,
  hasData,
  onTouch,
  onChange,
  onQuickSave,
}) => {
  const landlordField = fieldError("taxLandlordName", touched, errors);
  const cpfField = fieldError("taxCpf", touched, errors);
  const emailField = fieldError("taxEmail", touched, errors);
  const phoneField = fieldError("taxPhone", touched, errors);
  const companyField = fieldError("taxCompanyName", touched, errors);
  const cnpjField = fieldError("taxCnpj", touched, errors);

  return (
    <>
      {!hasData && (
        <ItemNotFound
          title="Nenhum dado cadastral salvo"
          description="Escolha Pessoa Física ou Jurídica e preencha as informações principais."
          actionLabel="Salvar agora"
          onAction={onQuickSave}
        />
      )}

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
          <IonLabel>Pessoa Física</IonLabel>
        </IonSegmentButton>
        <IonSegmentButton value="CNPJ">
          <IonLabel>Pessoa Jurídica</IonLabel>
        </IonSegmentButton>
      </IonSegment>

      <div className="app-form-grid">
        {form.taxPersonType === "CPF" ? (
          <>
            <div className={landlordField.wrapClassName}>
              <IonItem className="app-form-item">
                <FormInputLabel name="Nome" />
                <IonInput
                  value={form.taxLandlordName}
                  placeholder="Ex.: João da Silva"
                  aria-invalid={landlordField.ariaInvalid}
                  aria-describedby={landlordField.ariaDescribedby}
                  onIonInput={(event: any) => {
                    onTouch("taxLandlordName");
                    onChange({ ...form, taxLandlordName: getInputValue(event) });
                  }}
                  onIonBlur={() => onTouch("taxLandlordName")}
                />
              </IonItem>
              {landlordField.node}
            </div>

            <div className={cpfField.wrapClassName}>
              <IonItem className="app-form-item">
                <FormInputLabel name="CPF" />
                <IonInput
                  value={form.taxCpf}
                  placeholder="000.000.000-00"
                  aria-invalid={cpfField.ariaInvalid}
                  aria-describedby={cpfField.ariaDescribedby}
                  onIonInput={(event: any) => {
                    onTouch("taxCpf");
                    onChange({ ...form, taxCpf: maskCPF(getInputValue(event)) });
                  }}
                  onIonBlur={() => onTouch("taxCpf")}
                />
              </IonItem>
              {cpfField.node}
            </div>

            <div className={emailField.wrapClassName}>
              <IonItem className="app-form-item">
                <FormInputLabel name="E-mail" />
                <IonInput
                  type="email"
                  value={form.taxEmail}
                  placeholder="voce@email.com"
                  aria-invalid={emailField.ariaInvalid}
                  aria-describedby={emailField.ariaDescribedby}
                  onIonInput={(event: any) => {
                    onTouch("taxEmail");
                    onChange({ ...form, taxEmail: getInputValue(event) });
                  }}
                  onIonBlur={() => onTouch("taxEmail")}
                />
              </IonItem>
              {emailField.node}
            </div>

            <div className={phoneField.wrapClassName}>
              <IonItem className="app-form-item">
                <FormInputLabel name="Telefone" />
                <IonInput
                  value={form.taxPhone}
                  placeholder="(11) 99999-9999"
                  aria-invalid={phoneField.ariaInvalid}
                  aria-describedby={phoneField.ariaDescribedby}
                  onIonInput={(event: any) => {
                    onTouch("taxPhone");
                    onChange({ ...form, taxPhone: maskPhone(getInputValue(event)) });
                  }}
                  onIonBlur={() => onTouch("taxPhone")}
                />
              </IonItem>
              {phoneField.node}
            </div>
          </>
        ) : (
          <>
            <div className={companyField.wrapClassName}>
              <IonItem className="app-form-item">
                <FormInputLabel name="Razão social" />
                <IonInput
                  value={form.taxCompanyName}
                  placeholder="Ex.: Frotto Locações LTDA"
                  aria-invalid={companyField.ariaInvalid}
                  aria-describedby={companyField.ariaDescribedby}
                  onIonInput={(event: any) => {
                    onTouch("taxCompanyName");
                    onChange({ ...form, taxCompanyName: getInputValue(event) });
                  }}
                  onIonBlur={() => onTouch("taxCompanyName")}
                />
              </IonItem>
              {companyField.node}
            </div>

            <div className={cnpjField.wrapClassName}>
              <IonItem className="app-form-item">
                <FormInputLabel name="CNPJ" />
                <IonInput
                  value={form.taxCnpj}
                  placeholder="00.000.000/0000-00"
                  aria-invalid={cnpjField.ariaInvalid}
                  aria-describedby={cnpjField.ariaDescribedby}
                  onIonInput={(event: any) => {
                    onTouch("taxCnpj");
                    onChange({ ...form, taxCnpj: maskCNPJ(getInputValue(event)) });
                  }}
                  onIonBlur={() => onTouch("taxCnpj")}
                />
              </IonItem>
              {cnpjField.node}
            </div>
          </>
        )}
      </div>
    </>
  );
};

export const FiscalComplementaryFields: React.FC<FiscalFieldsProps> = ({
  form,
  touched,
  errors,
  onTouch,
  onChange,
}) => {
  const ieField = fieldError("taxIe", touched, errors);
  const contactPhoneField = fieldError("taxContactPhone", touched, errors);
  const addressField = fieldError("taxAddress", touched, errors);

  if (form.taxPersonType !== "CNPJ") {
    return (
      <IonText color="medium" className="my-panel-fiscal-note">
        Pessoa Física não possui dados fiscais complementares hoje.
      </IonText>
    );
  }

  return (
    <div className="app-form-grid">
      <div className={ieField.wrapClassName}>
        <IonItem className="app-form-item">
          <FormInputLabel name="Inscrição Estadual" />
          <IonInput
            value={form.taxIe}
            placeholder="Inscrição estadual"
            aria-invalid={ieField.ariaInvalid}
            aria-describedby={ieField.ariaDescribedby}
            onIonInput={(event: any) => {
              onTouch("taxIe");
              onChange({ ...form, taxIe: getInputValue(event) });
            }}
            onIonBlur={() => onTouch("taxIe")}
          />
        </IonItem>
        {ieField.node}
      </div>

      <div className={contactPhoneField.wrapClassName}>
        <IonItem className="app-form-item">
          <FormInputLabel name="Telefone para contato" />
          <IonInput
            value={form.taxContactPhone}
            placeholder="(11) 99999-9999"
            aria-invalid={contactPhoneField.ariaInvalid}
            aria-describedby={contactPhoneField.ariaDescribedby}
            onIonInput={(event: any) => {
              onTouch("taxContactPhone");
              onChange({ ...form, taxContactPhone: maskPhone(getInputValue(event)) });
            }}
            onIonBlur={() => onTouch("taxContactPhone")}
          />
        </IonItem>
        {contactPhoneField.node}
      </div>

      <div className={addressField.wrapClassName}>
        <IonItem className="app-form-item app-form-item--textarea">
          <FormInputLabel name="Endereço completo" />
          <IonTextarea
            value={form.taxAddress}
            autoGrow
            rows={3}
            placeholder="Rua, número, bairro, cidade, estado e CEP"
            aria-invalid={addressField.ariaInvalid}
            aria-describedby={addressField.ariaDescribedby}
            onIonInput={(event: any) => {
              onTouch("taxAddress");
              onChange({ ...form, taxAddress: getInputValue(event) });
            }}
            onIonBlur={() => onTouch("taxAddress")}
          />
        </IonItem>
        {addressField.node}
      </div>
    </div>
  );
};
