import { IonButton, IonButtons, IonContent, IonHeader, IonProgressBar, IonTitle, IonToolbar } from "@ionic/react";
import { useState } from "react";
import FormCurrency from "../../components/Form/FormCurrency";
import FormDate from "../../components/Form/FormDate";
import FormInput from "../../components/Form/FormInput";
import FormSelect from "../../components/Form/FormSelect";
import { currencyFormat } from "../../services/currencyFormat";
import { formatDateView } from "../../services/dateFormat";
import { DebtConfessionPreview, DebtConfessionPreviewItem, DebtConfessionTerms } from "../../services/debtConfessionService";
import { formatCPF } from "../../services/iMaskFormat";
import "./DebtConfessionPreview.css";

interface DebtConfessionPreviewProps {
  preview: DebtConfessionPreview;
  isGenerating: boolean;
  onCancel: () => void;
  onGenerate: (terms: DebtConfessionTerms) => void;
}

const FORMA_PAGAMENTO_OPTIONS = [
  { value: "A_VISTA", label: "À vista" },
  { value: "PARCELADO", label: "Parcelado" },
];

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
  const [terms, setTerms] = useState<DebtConfessionTerms>({ formaPagamento: "A_VISTA" });
  const update = (patch: Partial<DebtConfessionTerms>) => setTerms((current) => ({ ...current, ...patch }));

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
            <span className="debt-confession-preview__eyebrow">Motorista devedor</span>
            <strong className="debt-confession-preview__driver">{preview.driverName || "Motorista"}</strong>
            {preview.driverCpf && <span className="debt-confession-preview__muted">CPF: {formatCPF(preview.driverCpf) || preview.driverCpf}</span>}
            <span className="debt-confession-preview__muted">Origem: Pendências</span>
          </section>

          <section className="debt-confession-preview__block">
            <span className="debt-confession-preview__eyebrow">Dívidas</span>
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
            <span className="debt-confession-preview__eyebrow">Condições (opcional)</span>
            <form className="app-form-grid" onSubmit={(event) => event.preventDefault()}>
              <FormSelect
                label="Forma de pagamento"
                options={FORMA_PAGAMENTO_OPTIONS}
                initialValue={terms.formaPagamento || "A_VISTA"}
                changeCallback={(value: string) => update({ formaPagamento: value === "PARCELADO" ? "PARCELADO" : "A_VISTA" })}
              />
              {terms.formaPagamento === "PARCELADO" && (
                <>
                  <FormInput
                    label="Parcelas (qtd)"
                    type="number"
                    inputmode="numeric"
                    initialValue={terms.parcelasQtd ?? ""}
                    changeCallback={(value: string) => update({ parcelasQtd: Number(value) > 0 ? Math.floor(Number(value)) : undefined })}
                  />
                  <FormCurrency
                    label="Valor da parcela"
                    initialValue={terms.valorParcela ?? 0}
                    changeCallback={(value: number) => update({ valorParcela: value > 0 ? value : undefined })}
                  />
                </>
              )}
              <FormDate
                id="debt-confession-due-date"
                label="Vencimento inicial"
                presentation="date"
                initialValue={terms.vencimentoInicial ?? ""}
                formCallBack={(value: string) => update({ vencimentoInicial: value })}
              />
              <FormInput label="Testemunha 1 - nome" initialValue={terms.testemunha1Nome ?? ""} changeCallback={(value: string) => update({ testemunha1Nome: value })} />
              <FormInput label="Testemunha 1 - CPF" initialValue={terms.testemunha1Cpf ?? ""} changeCallback={(value: string) => update({ testemunha1Cpf: value })} />
              <FormInput label="Testemunha 2 - nome" initialValue={terms.testemunha2Nome ?? ""} changeCallback={(value: string) => update({ testemunha2Nome: value })} />
              <FormInput label="Testemunha 2 - CPF" initialValue={terms.testemunha2Cpf ?? ""} changeCallback={(value: string) => update({ testemunha2Cpf: value })} />
              <FormInput label="Observações" initialValue={terms.observacoes ?? ""} changeCallback={(value: string) => update({ observacoes: value })} />
            </form>
          </section>

          <div className="debt-confession-preview__actions">
            <IonButton fill="outline" className="app-outline-btn" onClick={onCancel} disabled={isGenerating}>
              Cancelar
            </IonButton>
            <IonButton className="app-primary-btn" onClick={() => onGenerate(terms)} disabled={isGenerating}>
              Gerar PDF
            </IonButton>
          </div>
        </div>
      </IonContent>
    </>
  );
};

export default DebtConfessionPreviewView;
