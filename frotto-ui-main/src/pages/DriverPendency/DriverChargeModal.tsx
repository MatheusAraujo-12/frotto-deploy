import { IonButton, IonButtons, IonContent, IonHeader, IonInput, IonItem, IonProgressBar, IonTitle, IonToolbar } from "@ionic/react";
import { useEffect, useMemo, useRef, useState } from "react";
import FormCurrency from "../../components/Form/FormCurrency";
import FormInput from "../../components/Form/FormInput";
import FormInputArea from "../../components/Form/FormInputArea";
import FormInputLabel from "../../components/Form/FormInputLabel";
import FormItemWrapper from "../../components/Form/FormItemWrapper";
import FormSelect from "../../components/Form/FormSelect";
import { DriverPendencyModel, MaintenanceChargeSummaryModel, MaintenanceModel } from "../../constants/CarModels";
import { getApiErrorMessage } from "../../services/apiErrorMessage";
import { createSingleFlight } from "../../services/driverAssignmentService";
import {
  DRIVER_CHARGE_ERROR_MESSAGES,
  chargeFine,
  chargeSharedMaintenance,
  getMaintenanceChargeSummary,
  listCarMaintenances,
  newOperationKey,
} from "../../services/driverChargeService";
import { currencyFormat } from "../../services/currencyFormat";
import { formatDateView } from "../../services/dateFormat";
import "./DebtConfessionPreview.css";

export type DriverChargeMode = "fine" | "maintenance";

interface DriverChargeModalProps {
  mode: DriverChargeMode;
  driverCarId: number | string;
  carId?: number | null;
  onCancel: () => void;
  onCreated: (pendency: DriverPendencyModel) => void;
}

/** Native date/time field: empty until the user fills it (nothing is assumed). */
const NativeField: React.FC<{ name: string; label: string; type: "date" | "time"; value?: string; required?: boolean; onChange: (value: string) => void }> = ({
  name,
  label,
  type,
  value,
  required,
  onChange,
}) => (
  <FormItemWrapper>
    <IonItem className="app-form-item">
      <FormInputLabel name={label} required={required} />
      <IonInput
        type={type}
        data-field={name}
        aria-label={label}
        value={value || ""}
        onIonChange={(event) => onChange(`${event.detail.value || ""}`)}
      />
    </IonItem>
  </FormItemWrapper>
);

const maintenanceLabel = (maintenance: MaintenanceModel) =>
  [formatDateView(maintenance.date), maintenance.local, currencyFormat(maintenance.cost)].filter(Boolean).join(" · ");

/**
 * Nova multa / Cobrar manutenção: one debt of the driver of this contract. The maintenance keeps its full cost; only
 * the share typed here becomes the driver's debt. One operation key per opening of this screen, reused on every
 * retry: the backend answers a repeated attempt with the pendency it already created.
 */
const DriverChargeModal: React.FC<DriverChargeModalProps> = ({ mode, driverCarId, carId, onCancel, onCreated }) => {
  const operationKey = useRef(newOperationKey());
  const once = useRef(createSingleFlight());
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [amount, setAmount] = useState(0);
  const [infractionDate, setInfractionDate] = useState("");
  const [infractionTime, setInfractionTime] = useState("");
  const [ait, setAit] = useState("");
  const [agency, setAgency] = useState("");
  const [location, setLocation] = useState("");
  const [classification, setClassification] = useState("");
  const [dueDate, setDueDate] = useState("");
  const [note, setNote] = useState("");
  const [maintenances, setMaintenances] = useState<MaintenanceModel[]>([]);
  const [maintenanceId, setMaintenanceId] = useState<number | null>(null);
  const [summary, setSummary] = useState<MaintenanceChargeSummaryModel | null>(null);

  useEffect(() => {
    if (mode !== "maintenance" || !carId) return;
    listCarMaintenances(carId)
      .then(setMaintenances)
      .catch(() => setError("Não foi possível carregar as manutenções do veículo."));
  }, [mode, carId]);

  useEffect(() => {
    setSummary(null);
    if (!maintenanceId) return;
    getMaintenanceChargeSummary(maintenanceId)
      .then(setSummary)
      .catch(() => setError("Não foi possível carregar a manutenção selecionada."));
  }, [maintenanceId]);

  const problem = useMemo(() => {
    if (!(amount > 0)) return "Informe o valor cobrado do motorista.";
    if (mode === "fine") {
      if (!infractionDate || !infractionTime) return "Informe a data e a hora da infração.";
      return null;
    }
    if (!maintenanceId) return "Selecione a manutenção.";
    if (summary && amount > summary.availableAmount) {
      return `O valor ultrapassa o disponível desta manutenção (${currencyFormat(summary.availableAmount)}).`;
    }
    return null;
  }, [amount, mode, infractionDate, infractionTime, maintenanceId, summary]);

  const submit = () =>
    once.current(async () => {
      if (problem) return;
      setBusy(true);
      setError(null);
      try {
        const pendency =
          mode === "fine"
            ? await chargeFine(
                driverCarId,
                { amount, infractionDate, infractionTime, ait, agency, location, classification, dueDate, note },
                operationKey.current
              )
            : await chargeSharedMaintenance(driverCarId, { maintenanceId: maintenanceId as number, amount, note }, operationKey.current);
        onCreated(pendency);
      } catch (failure) {
        // The key stays: trying again is the same operation, never a second charge.
        setError(getApiErrorMessage(failure, "Não foi possível registrar a cobrança. Tente novamente.", DRIVER_CHARGE_ERROR_MESSAGES));
      } finally {
        setBusy(false);
      }
    });

  const title = mode === "fine" ? "Nova multa" : "Cobrar manutenção";

  return (
    <>
      <IonHeader className="ion-no-border">
        <IonToolbar className="app-toolbar-clean">
          <IonButtons slot="start">
            <IonButton className="app-cancel-btn" fill="clear" onClick={onCancel} disabled={busy}>
              Cancelar
            </IonButton>
          </IonButtons>
          <IonTitle>{title}</IonTitle>
        </IonToolbar>
        {busy && <IonProgressBar type="indeterminate" />}
      </IonHeader>
      <IonContent>
        <div className="app-shell app-shell--compact debt-confession-preview driver-charge-modal">
          <section className="debt-confession-preview__block">
            <span className="debt-confession-preview__eyebrow">{mode === "fine" ? "Infração" : "Manutenção do veículo"}</span>
            <form className="app-form-grid" onSubmit={(event) => event.preventDefault()}>
              {mode === "maintenance" && (
                <>
                  {maintenances.length === 0 ? (
                    <p className="debt-confession-preview__muted">Nenhuma manutenção registrada para este veículo.</p>
                  ) : (
                    <FormSelect
                      label="Manutenção"
                      required
                      options={maintenances.filter((item) => item.id).map((item) => ({ value: `${item.id}`, label: maintenanceLabel(item) }))}
                      initialValue={maintenanceId ? `${maintenanceId}` : ""}
                      changeCallback={(value: string) => setMaintenanceId(value ? Number(value) : null)}
                    />
                  )}
                  {summary && (
                    <p className="debt-confession-preview__muted" data-testid="maintenance-summary">
                      Custo da manutenção {currencyFormat(summary.maintenanceCost)} · já atribuído a motoristas{" "}
                      {currencyFormat(summary.assignedAmount)} · disponível {currencyFormat(summary.availableAmount)}
                    </p>
                  )}
                </>
              )}
              {mode === "fine" && (
                <>
                  <NativeField name="infractionDate" label="Data da infração" type="date" required value={infractionDate} onChange={setInfractionDate} />
                  <NativeField name="infractionTime" label="Hora da infração" type="time" required value={infractionTime} onChange={setInfractionTime} />
                  <FormInput label="AIT" data-field="ait" maxlength={40} initialValue={ait} changeCallback={(value: string) => setAit(value)} />
                  <FormInput label="Órgão autuador" data-field="agency" maxlength={120} initialValue={agency} changeCallback={(value: string) => setAgency(value)} />
                  <FormInput label="Local" data-field="location" maxlength={255} initialValue={location} changeCallback={(value: string) => setLocation(value)} />
                  <FormInput
                    label="Enquadramento"
                    data-field="classification"
                    maxlength={255}
                    initialValue={classification}
                    changeCallback={(value: string) => setClassification(value)}
                  />
                  <NativeField name="dueDate" label="Vencimento" type="date" value={dueDate} onChange={setDueDate} />
                </>
              )}
              <FormCurrency
                label={mode === "fine" ? "Valor da multa" : "Valor cobrado do motorista"}
                required
                data-field="amount"
                initialValue={amount}
                changeCallback={(value: number) => setAmount(Number(value) || 0)}
              />
              <FormInputArea
                label={mode === "fine" ? "Observação" : "Motivo da cobrança"}
                maxlength={255}
                initialValue={note}
                changeCallback={(value: string) => setNote(value)}
              />
            </form>
          </section>

          {mode === "maintenance" && (
            <p className="debt-confession-preview__hint">
              A manutenção continua com o custo integral no veículo; só o valor cobrado vira pendência do motorista.
            </p>
          )}
          {(error || problem) && (
            <p className={error ? "driver-pendency-list-item__blocked" : "debt-confession-preview__hint"} role={error ? "alert" : undefined}>
              {error || problem}
            </p>
          )}
          <div className="debt-confession-preview__actions">
            <IonButton fill="outline" className="app-outline-btn" onClick={onCancel} disabled={busy}>
              Cancelar
            </IonButton>
            <IonButton className="app-primary-btn" onClick={submit} disabled={busy || Boolean(problem)}>
              Registrar pendência
            </IonButton>
          </div>
        </div>
      </IonContent>
    </>
  );
};

export default DriverChargeModal;
