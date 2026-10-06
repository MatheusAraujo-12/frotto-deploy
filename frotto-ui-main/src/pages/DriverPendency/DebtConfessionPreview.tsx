import { IonButton, IonButtons, IonContent, IonHeader, IonInput, IonItem, IonProgressBar, IonTitle, IonToolbar } from "@ionic/react";
import { useMemo, useState } from "react";
import FormInput from "../../components/Form/FormInput";
import FormInputArea from "../../components/Form/FormInputArea";
import FormInputLabel from "../../components/Form/FormInputLabel";
import FormItemWrapper from "../../components/Form/FormItemWrapper";
import FormSelect from "../../components/Form/FormSelect";
import { currencyFormat } from "../../services/currencyFormat";
import { formatDateView } from "../../services/dateFormat";
import { DebtConfessionPreview, DebtConfessionPreviewItem } from "../../services/debtConfessionService";
import {
  ConfessionTermsErrors,
  DebtConfessionTerms,
  MAX_NOTE_LENGTH,
  PAYMENT_FORM_OPTIONS,
  validateConfessionTerms,
} from "../../services/debtConfessionTerms";
import { formatCPF } from "../../services/iMaskFormat";
import "./DebtConfessionPreview.css";

interface DebtConfessionPreviewProps {
  preview: DebtConfessionPreview;
  isGenerating: boolean;
  onCancel: () => void;
  onGenerate: (terms: DebtConfessionTerms) => void;
}

type TermsField = keyof DebtConfessionTerms;

/** Errors in the shape the form components read (react-hook-form), only for the fields the user already touched. */
function visibleErrors(errors: ConfessionTermsErrors, touched: Set<TermsField>) {
  const visible: Record<string, { type: string; message: string }> = {};
  (Object.keys(errors) as TermsField[]).forEach((field) => {
    if (touched.has(field)) visible[field] = { type: "validate", message: errors[field] as string };
  });
  return visible;
}

/** Native date field: stays empty until the user picks a date (the deadline is never assumed). */
const TermsDateField: React.FC<{ name: TermsField; label: string; value?: string; errors: Object; onChange: (value: string) => void }> = ({
  name,
  label,
  value,
  errors,
  onChange,
}) => (
  <FormItemWrapper errorsObj={errors} errorName={name}>
    <IonItem className="app-form-item">
      <FormInputLabel name={label} required />
      <IonInput
        type="date"
        data-field={name}
        aria-label={label}
        aria-required
        value={value || ""}
        onIonChange={(event) => onChange(`${event.detail.value || ""}`)}
      />
    </IonItem>
  </FormItemWrapper>
);

/** Vehicle and contract the debt was born in, exactly as the backend reports them (nothing is guessed). */
function originLabel(item: DebtConfessionPreviewItem): string | null {
  const vehicle = [item.originCarModel, item.originCarPlate].filter((part) => `${part || ""}`.trim()).join(" - ");
  const contract = `${item.originContractNumber || ""}`.trim();
  if (!vehicle && !contract) return null;
  return [vehicle && `Veículo de origem: ${vehicle}`, contract && `Contrato ${contract}`].filter(Boolean).join(" · ");
}

/**
 * Preview of a Confissão de Dívida built by the backend from the selected pendencies: debtor, every debt with its
 * own origin and balance, and the total - all as returned, never recomputed here. Cancelar writes nothing.
 */
const DebtConfessionPreviewView: React.FC<DebtConfessionPreviewProps> = ({ preview, isGenerating, onCancel, onGenerate }) => {
  const [terms, setTerms] = useState<DebtConfessionTerms>({ formaPagamento: "" });
  const [touched, setTouched] = useState<Set<TermsField>>(new Set());
  const update = (patch: Partial<DebtConfessionTerms>) => {
    setTerms((current) => ({ ...current, ...patch }));
    setTouched((current) => new Set([...Array.from(current), ...(Object.keys(patch) as TermsField[])]));
  };
  const errors = useMemo(() => validateConfessionTerms(terms), [terms]);
  const isValid = Object.keys(errors).length === 0;
  const shownErrors = visibleErrors(errors, touched);
  const installments = terms.formaPagamento === "PARCELADO";

  return (
    <>
      <IonHeader className="ion-no-border">
        <IonToolbar className="app-toolbar-clean">
          <IonButtons slot="start">
            <IonButton className="app-cancel-btn" fill="clear" onClick={onCancel} disabled={isGenerating}>
              Cancelar
            </IonButton>
          </IonButtons>
          <IonTitle>Confissão de Dívida</IonTitle>
        </IonToolbar>
        {isGenerating && <IonProgressBar type="indeterminate" />}
      </IonHeader>
      <IonContent>
        <div className="app-shell app-shell--compact debt-confession-preview">
          <section className="debt-confession-preview__block">
            <span className="debt-confession-preview__eyebrow">Devedor</span>
            <strong className="debt-confession-preview__driver">{preview.driverName || "Motorista"}</strong>
            {preview.driverCpf && <span className="debt-confession-preview__muted">CPF: {formatCPF(preview.driverCpf) || preview.driverCpf}</span>}
            <span className="debt-confession-preview__muted">Origem: Pendências</span>
          </section>

          <section className="debt-confession-preview__block">
            <span className="debt-confession-preview__eyebrow">Valor total</span>
            <strong className="debt-confession-preview__amount">{currencyFormat(preview.valorTotal)}</strong>
          </section>

          <section className="debt-confession-preview__block">
            <span className="debt-confession-preview__eyebrow">Pendências incluídas</span>
            <ul className="debt-confession-preview__items">
              {preview.items.map((item) => {
                const origin = originLabel(item);
                const partiallyPaid = typeof item.paidAmount === "number" && item.paidAmount > 0;
                return (
                  <li key={item.pendencyId} className="debt-confession-preview__item">
                    <div className="debt-confession-preview__item-head">
                      <span>
                        <strong>{item.name || item.typeNameSnapshot || "Pendência"}</strong>
                        {item.date && <span className="debt-confession-preview__muted"> · {formatDateView(item.date)}</span>}
                      </span>
                      <strong className="app-text-financial-negative">{currencyFormat(item.valorItem)}</strong>
                    </div>
                    {origin && <span className="debt-confession-preview__muted">{origin}</span>}
                    {item.note && <span className="debt-confession-preview__muted">{item.note}</span>}
                    {partiallyPaid && (
                      <span className="debt-confession-preview__muted">
                        Valor original {currencyFormat(item.cost ?? undefined)} · já pago {currencyFormat(item.paidAmount ?? undefined)} · saldo{" "}
                        {currencyFormat(item.remainingAmount ?? item.valorItem)}
                      </span>
                    )}
                  </li>
                );
              })}
            </ul>
            <div className="debt-confession-preview__total">
              <span>Total da Confissão</span>
              <strong>{currencyFormat(preview.valorTotal)}</strong>
            </div>
          </section>

          <section className="debt-confession-preview__block">
            <span className="debt-confession-preview__eyebrow">Condições de pagamento</span>
            <form className="app-form-grid" onSubmit={(event) => event.preventDefault()}>
              <FormSelect
                label="Forma de pagamento"
                required
                options={PAYMENT_FORM_OPTIONS}
                initialValue={terms.formaPagamento || ""}
                errorsObj={shownErrors}
                errorName="formaPagamento"
                changeCallback={(value: string) => update({ formaPagamento: (value || "") as DebtConfessionTerms["formaPagamento"] })}
              />
              <TermsDateField
                name="prazoPagamento"
                label="Prazo para pagamento"
                value={terms.prazoPagamento}
                errors={shownErrors}
                onChange={(value) => update({ prazoPagamento: value })}
              />
              {installments && (
                <>
                  <FormInput
                    label="Quantidade de parcelas"
                    required
                    type="number"
                    inputmode="numeric"
                    min={2}
                    aria-label="Quantidade de parcelas"
                    data-field="parcelasQtd"
                    initialValue={terms.parcelasQtd ?? ""}
                    errorsObj={shownErrors}
                    errorName="parcelasQtd"
                    changeCallback={(value: string) => update({ parcelasQtd: `${value ?? ""}`.trim() === "" ? null : Number(value) })}
                  />
                  <TermsDateField
                    name="primeiroVencimento"
                    label="Primeiro vencimento"
                    value={terms.primeiroVencimento}
                    errors={shownErrors}
                    onChange={(value) => update({ primeiroVencimento: value })}
                  />
                </>
              )}
              <FormInputArea
                label="Observação"
                maxlength={MAX_NOTE_LENGTH}
                aria-label="Observação"
                initialValue={terms.observacao ?? ""}
                errorsObj={shownErrors}
                errorName="observacao"
                changeCallback={(value: string) => update({ observacao: value })}
              />
            </form>
          </section>

          {!isValid && (
            <p className="debt-confession-preview__hint">
              {installments
                ? "Informe a forma de pagamento, o prazo, a quantidade de parcelas (2 ou mais) e o primeiro vencimento para gerar o PDF."
                : "Informe a forma de pagamento e o prazo para pagamento para gerar o PDF."}
            </p>
          )}
          <div className="debt-confession-preview__actions">
            <IonButton fill="outline" className="app-outline-btn" onClick={onCancel} disabled={isGenerating}>
              Cancelar
            </IonButton>
            <IonButton className="app-primary-btn" onClick={() => isValid && onGenerate(terms)} disabled={isGenerating || !isValid}>
              Gerar PDF
            </IonButton>
          </div>
        </div>
      </IonContent>
    </>
  );
};

export default DebtConfessionPreviewView;
