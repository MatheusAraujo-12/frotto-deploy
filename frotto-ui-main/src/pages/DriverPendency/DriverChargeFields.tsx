import { IonInput, IonItem } from "@ionic/react";
import { ReactNode, useEffect, useMemo, useRef, useState } from "react";
import FormCurrency from "../../components/Form/FormCurrency";
import FormError from "../../components/Form/FormError";
import FormInput from "../../components/Form/FormInput";
import FormInputArea from "../../components/Form/FormInputArea";
import FormInputLabel from "../../components/Form/FormInputLabel";
import FormItemWrapper from "../../components/Form/FormItemWrapper";
import FormSelect from "../../components/Form/FormSelect";
import { DriverPendencyModel } from "../../constants/CarModels";
import { getApiErrorMessage } from "../../services/apiErrorMessage";
import { currencyFormat } from "../../services/currencyFormat";
import { formatDateView } from "../../services/dateFormat";
import { createSingleFlight } from "../../services/driverAssignmentService";
import {
  DRIVER_CHARGE_ERROR_MESSAGES,
  MaintenanceChargeOption,
  chargeFine,
  chargeSharedMaintenance,
  listChargeableMaintenances,
  newOperationKey,
} from "../../services/driverChargeService";

/**
 * Kind chosen in Nova Pendência. Structural (a code picked by the user), never derived from the typed name:
 * FINE and SHARED_MAINTENANCE go to their specialized endpoints, OTHER keeps the regular pendency form.
 */
export type PendencyKind = "OTHER" | "FINE" | "SHARED_MAINTENANCE";

export const PENDENCY_KIND_OPTIONS: Array<{ value: PendencyKind; label: string }> = [
  { value: "OTHER", label: "Pendência comum (danos, diárias, combustível...)" },
  { value: "FINE", label: "Multa de trânsito" },
  { value: "SHARED_MAINTENANCE", label: "Manutenção compartilhada" },
];

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
      <IonInput type={type} data-field={name} aria-label={label} value={value || ""} onIonChange={(event) => onChange(`${event.detail.value || ""}`)} />
    </IonItem>
  </FormItemWrapper>
);

const optionLabel = (option: MaintenanceChargeOption) =>
  [
    formatDateView(option.date || undefined),
    option.description || option.local || "Manutenção",
    currencyFormat(option.cost),
    option.chargeable ? null : "sem saldo para cobrança",
  ]
    .filter(Boolean)
    .join(" · ");

export interface DriverChargeForm {
  /** The fields to render for the kind (null for OTHER). */
  fields: ReactNode;
  /** Why it cannot be saved yet (null when it can). */
  problem: string | null;
  busy: boolean;
  submit: () => Promise<DriverPendencyModel | undefined>;
}

/**
 * Fields and submission of a fine / shared-maintenance charge inside Nova Pendência. One operation key per opening
 * of the form, reused on every retry: the backend answers a repeated attempt with the pendency it already created.
 */
export function useDriverChargeForm(kind: PendencyKind, driverCarId: number | string): DriverChargeForm {
  const operationKey = useRef(newOperationKey());
  const once = useRef(createSingleFlight());
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [amount, setAmount] = useState(0);
  const [note, setNote] = useState("");
  // Fine
  const [infractionDate, setInfractionDate] = useState("");
  const [dueDate, setDueDate] = useState("");
  const [showDetails, setShowDetails] = useState(false);
  const [infractionTime, setInfractionTime] = useState("");
  const [ait, setAit] = useState("");
  const [agency, setAgency] = useState("");
  const [location, setLocation] = useState("");
  const [classification, setClassification] = useState("");
  // Shared maintenance
  const [options, setOptions] = useState<MaintenanceChargeOption[] | null>(null);
  const [maintenanceId, setMaintenanceId] = useState<number | null>(null);

  useEffect(() => {
    if (kind !== "SHARED_MAINTENANCE" || options !== null) return;
    listChargeableMaintenances(driverCarId)
      .then(setOptions)
      .catch(() => {
        setOptions([]);
        setError("Não foi possível carregar as manutenções do veículo.");
      });
  }, [kind, driverCarId, options]);

  const selected = options?.find((option) => option.id === maintenanceId) || null;

  /*
   * Shared maintenance: every condition that blocks saving is shown under its own field (never only a muted note):
   * the maintenance (none / without balance - e.g. registered without services, cost R$ 0,00) and the value.
   */
  const maintenanceError = useMemo(() => {
    if (kind !== "SHARED_MAINTENANCE" || options === null) return null;
    if (options.length === 0) return "Nenhuma manutenção registrada para este veículo.";
    if (!selected && !options.some((option) => option.chargeable)) {
      return "Nenhuma manutenção deste veículo possui saldo disponível para cobrança.";
    }
    if (!selected) return "Selecione uma manutenção.";
    if (!selected.chargeable) return "Esta manutenção não possui saldo disponível para cobrança.";
    return null;
  }, [kind, options, selected]);
  const amountError = useMemo(() => {
    if (kind !== "SHARED_MAINTENANCE") return null;
    if (!(amount > 0)) return "Informe o valor cobrado do motorista.";
    if (selected && selected.chargeable && amount > selected.availableAmount) {
      return `O valor não pode ultrapassar o saldo disponível de ${currencyFormat(selected.availableAmount)}.`;
    }
    return null;
  }, [kind, selected, amount]);

  const problem = useMemo(() => {
    if (kind === "OTHER") return null;
    if (kind === "SHARED_MAINTENANCE") {
      if (options === null) return "Carregando manutenções do veículo...";
      return maintenanceError || amountError;
    }
    if (!(amount > 0)) return mode(kind) === "fine" ? "Informe o valor da multa." : "Informe o valor de responsabilidade do motorista.";
    if (kind === "FINE" && !infractionDate) return "Informe a data da infração.";
    return null;
  }, [kind, options, maintenanceError, amountError, amount, infractionDate]);

  const submit = async () =>
    once.current(async () => {
      if (problem) return undefined;
      setBusy(true);
      setError(null);
      try {
        return kind === "FINE"
          ? await chargeFine(
              driverCarId,
              { amount, infractionDate, infractionTime, ait, agency, location, classification, dueDate, note },
              operationKey.current
            )
          : await chargeSharedMaintenance(driverCarId, { maintenanceId: maintenanceId as number, amount, note }, operationKey.current);
      } catch (failure) {
        // The key stays: trying again is the same operation, never a second charge.
        setError(getApiErrorMessage(failure, "Não foi possível registrar a pendência. Tente novamente.", DRIVER_CHARGE_ERROR_MESSAGES));
        return undefined;
      } finally {
        setBusy(false);
      }
    });

  const feedback = (error || problem) && (
    <p className={error ? "driver-pendency-list-item__blocked" : "driver-charge-form__hint"} role={error ? "alert" : undefined} data-testid="charge-feedback">
      {error || problem}
    </p>
  );

  let fields: ReactNode = null;
  if (kind === "FINE") {
    fields = (
      <div className="driver-charge-form" data-testid="fine-fields">
        <FormCurrency
          label="Valor da multa"
          required
          data-field="amount"
          initialValue={amount}
          changeCallback={(value: number) => setAmount(Number(value) || 0)}
        />
        <NativeField name="infractionDate" label="Data da infração" type="date" required value={infractionDate} onChange={setInfractionDate} />
        <NativeField name="dueDate" label="Vencimento" type="date" value={dueDate} onChange={setDueDate} />
        <FormInputArea label="Observação" maxlength={255} initialValue={note} changeCallback={(value: string) => setNote(value)} />
        <button
          type="button"
          className="driver-charge-form__toggle"
          aria-expanded={showDetails}
          data-testid="fine-more-details"
          onClick={() => setShowDetails((open) => !open)}
        >
          {showDetails ? "Menos detalhes" : "Mais detalhes"}
        </button>
        {showDetails && (
          <div className="driver-charge-form__details">
            <NativeField name="infractionTime" label="Hora" type="time" value={infractionTime} onChange={setInfractionTime} />
            <FormInput label="AIT" data-field="ait" maxlength={40} initialValue={ait} changeCallback={(value: string) => setAit(value)} />
            <FormInput label="Órgão" data-field="agency" maxlength={120} initialValue={agency} changeCallback={(value: string) => setAgency(value)} />
            <FormInput label="Local" data-field="location" maxlength={255} initialValue={location} changeCallback={(value: string) => setLocation(value)} />
            <FormInput
              label="Enquadramento"
              data-field="classification"
              maxlength={255}
              initialValue={classification}
              changeCallback={(value: string) => setClassification(value)}
            />
          </div>
        )}
        {feedback}
      </div>
    );
  } else if (kind === "SHARED_MAINTENANCE") {
    fields = (
      <div className="driver-charge-form" data-testid="maintenance-fields">
        {error && (
          <p className="driver-pendency-list-item__blocked" role="alert" data-testid="charge-feedback">
            {error}
          </p>
        )}
        {options === null ? (
          <p className="driver-charge-form__hint">Carregando manutenções do veículo...</p>
        ) : options.length > 0 ? (
          <FormSelect
            label="Manutenção"
            required
            options={options.map((option) => ({ value: `${option.id}`, label: optionLabel(option) }))}
            initialValue={maintenanceId ? `${maintenanceId}` : ""}
            errorsObj={maintenanceError ? { maintenanceId: { type: "validate", message: maintenanceError } } : {}}
            errorName="maintenanceId"
            changeCallback={(value: string) => setMaintenanceId(value ? Number(value) : null)}
          />
        ) : (
          <FormError id="form-error-maintenanceId" message={maintenanceError || ""} />
        )}
        {selected && (
          <dl className="driver-charge-form__summary" data-testid="maintenance-summary">
            <div>
              <dt>Custo da manutenção</dt>
              <dd>{currencyFormat(selected.cost)}</dd>
            </div>
            <div>
              <dt>Já atribuído</dt>
              <dd>{currencyFormat(selected.assignedAmount)}</dd>
            </div>
            <div>
              <dt>Disponível para atribuição</dt>
              <dd>{currencyFormat(selected.availableAmount)}</dd>
            </div>
          </dl>
        )}
        <FormCurrency
          label="Valor de responsabilidade do motorista"
          required
          data-field="amount"
          initialValue={amount}
          errorsObj={amountError ? { amount: { type: "validate", message: amountError } } : {}}
          errorName="amount"
          changeCallback={(value: number) => setAmount(Number(value) || 0)}
        />
        <FormInputArea label="Observação" maxlength={255} initialValue={note} changeCallback={(value: string) => setNote(value)} />
        <p className="driver-charge-form__hint">A manutenção continua com o custo integral no veículo; só este valor vira pendência do motorista.</p>
      </div>
    );
  }

  return { fields, problem, busy, submit };
}

function mode(kind: PendencyKind) {
  return kind === "FINE" ? "fine" : "maintenance";
}
