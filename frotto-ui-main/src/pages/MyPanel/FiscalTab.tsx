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

type FiscalTextField = Exclude<keyof FiscalForm, "taxPersonType">;

interface FiscalInputProps extends FiscalFieldsProps {
  field: FiscalTextField;
  label: string;
  placeholder: string;
  type?: "email";
  mask?: (value: string) => string;
  multiline?: boolean;
}

const FiscalInput: React.FC<FiscalInputProps> = ({
  form,
  touched,
  errors,
  onTouch,
  onChange,
  field,
  label,
  placeholder,
  type,
  mask,
  multiline,
}) => {
  const state = fieldError(field, touched, errors);
  const handleInput = (event: any) => {
    const raw = getInputValue(event);
    onTouch(field);
    onChange({ ...form, [field]: mask ? mask(raw) : raw });
  };

  return (
    <div className={state.wrapClassName}>
      <IonItem className={multiline ? "app-form-item app-form-item--textarea" : "app-form-item"}>
        <FormInputLabel name={label} />
        {multiline ? (
          <IonTextarea
            value={form[field]}
            autoGrow
            rows={3}
            placeholder={placeholder}
            aria-invalid={state.ariaInvalid}
            aria-describedby={state.ariaDescribedby}
            onIonInput={handleInput}
            onIonBlur={() => onTouch(field)}
          />
        ) : (
          <IonInput
            type={type}
            value={form[field]}
            placeholder={placeholder}
            aria-invalid={state.ariaInvalid}
            aria-describedby={state.ariaDescribedby}
            onIonInput={handleInput}
            onIonBlur={() => onTouch(field)}
          />
        )}
      </IonItem>
      {state.node}
    </div>
  );
};

interface FiscalCadastralFieldsProps extends FiscalFieldsProps {
  /** Aviso curto quando o tipo selecionado difere do tipo salvo (troca apaga o outro ramo ao salvar). */
  typeChangeWarning?: string;
}

/**
 * Formulário de edição de "Dados cadastrais" (identidade fiscal usada nos
 * documentos). PJ é agrupado em Empresa / Contato / Endereço / Dados fiscais;
 * os campos, máscaras e o payload (PATCH /api/me/tax-data) são os mesmos.
 */
export const FiscalCadastralFields: React.FC<FiscalCadastralFieldsProps> = ({ typeChangeWarning, ...props }) => {
  const { form, onTouch, onChange } = props;

  return (
    <div className="cadastro-edit">
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

      {typeChangeWarning && (
        <IonText color="warning" className="cadastro-type-warning" role="status">
          {typeChangeWarning}
        </IonText>
      )}

      {form.taxPersonType === "CPF" ? (
        <div className="app-form-grid cadastro-grid">
          <FiscalInput {...props} field="taxLandlordName" label="Nome" placeholder="Ex.: João da Silva" />
          <FiscalInput {...props} field="taxCpf" label="CPF" placeholder="000.000.000-00" mask={maskCPF} />
          <FiscalInput {...props} field="taxEmail" label="E-mail" placeholder="voce@email.com" type="email" />
          <FiscalInput {...props} field="taxPhone" label="Telefone" placeholder="(11) 99999-9999" mask={maskPhone} />
        </div>
      ) : (
        <>
          <section className="cadastro-group" aria-label="Empresa">
            <h3 className="cadastro-group__title">Empresa</h3>
            <div className="app-form-grid cadastro-grid">
              <FiscalInput
                {...props}
                field="taxCompanyName"
                label="Razão social"
                placeholder="Ex.: Frotto Locações LTDA"
              />
              <FiscalInput {...props} field="taxCnpj" label="CNPJ" placeholder="00.000.000/0000-00" mask={maskCNPJ} />
            </div>
          </section>

          <section className="cadastro-group" aria-label="Contato da empresa">
            <h3 className="cadastro-group__title">Contato da empresa</h3>
            <div className="app-form-grid">
              <FiscalInput
                {...props}
                field="taxContactPhone"
                label="Telefone para contato"
                placeholder="(11) 99999-9999"
                mask={maskPhone}
              />
            </div>
          </section>

          <section className="cadastro-group" aria-label="Endereço">
            <h3 className="cadastro-group__title">Endereço</h3>
            <FiscalInput
              {...props}
              field="taxAddress"
              label="Endereço completo"
              placeholder="Rua, número, bairro, cidade, estado e CEP"
              multiline
            />
          </section>

          <section className="cadastro-group" aria-label="Dados fiscais">
            <h3 className="cadastro-group__title">Dados fiscais</h3>
            <div className="app-form-grid">
              <FiscalInput {...props} field="taxIe" label="Inscrição Estadual" placeholder="Inscrição estadual" />
            </div>
          </section>
        </>
      )}
    </div>
  );
};
