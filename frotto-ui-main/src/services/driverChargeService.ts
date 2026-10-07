import endpoints from "../constants/endpoints";
import { DocumentModel } from "../constants/DocumentModels";
import { DriverPendencyModel } from "../constants/CarModels";
import { generateDocumentPdf } from "../pages/Documents/documentPdf";
import api from "./axios/axios";
import documentService from "./documentService";

/**
 * Fines and shared maintenance as debts of the driver (Pendências). The backend owns every rule: who owes, the
 * maintenance limit, ownership and idempotency. The screen only sends one operation key per creation and reuses it
 * on every retry, so a double click, a timeout or two tabs never create a second charge.
 */

/** One key per operation of the screen (16-80 chars of [A-Za-z0-9_-], as the backend requires). */
export function newOperationKey(): string {
  const cryptoApi: Crypto | undefined = typeof window !== "undefined" ? window.crypto : undefined;
  if (cryptoApi && typeof cryptoApi.randomUUID === "function") {
    return `op-${cryptoApi.randomUUID()}`;
  }
  const bytes = new Uint8Array(16);
  if (cryptoApi && typeof cryptoApi.getRandomValues === "function") {
    cryptoApi.getRandomValues(bytes);
  } else {
    bytes.forEach((_, index) => (bytes[index] = Math.floor(Math.random() * 256)));
  }
  return `op-${Array.from(bytes, (value) => value.toString(16).padStart(2, "0")).join("")}`;
}

export interface FineChargeInput {
  amount: number;
  /** YYYY-MM-DD */
  infractionDate: string;
  /** HH:mm, optional (never invented when absent) */
  infractionTime?: string;
  ait?: string;
  agency?: string;
  location?: string;
  classification?: string;
  /** YYYY-MM-DD */
  dueDate?: string;
  note?: string;
}

export interface SharedMaintenanceChargeInput {
  maintenanceId: number;
  amount: number;
  note?: string;
}

const optional = (value?: string) => {
  const text = `${value ?? ""}`.trim();
  return text ? text : undefined;
};

export async function chargeFine(driverCarId: number | string, input: FineChargeInput, idempotencyKey: string): Promise<DriverPendencyModel> {
  const { data } = await api.post(endpoints.DRIVER_PENDENCIES_FINES({ pathVariables: { id: driverCarId } }), {
    idempotencyKey,
    amount: input.amount,
    infractionDate: input.infractionDate,
    infractionTime: optional(input.infractionTime),
    ait: optional(input.ait),
    agency: optional(input.agency),
    location: optional(input.location),
    classification: optional(input.classification),
    dueDate: optional(input.dueDate),
    note: optional(input.note),
  });
  return data;
}

export async function chargeSharedMaintenance(
  driverCarId: number | string,
  input: SharedMaintenanceChargeInput,
  idempotencyKey: string
): Promise<DriverPendencyModel> {
  const { data } = await api.post(endpoints.DRIVER_PENDENCIES_SHARED_MAINTENANCE({ pathVariables: { id: driverCarId } }), {
    idempotencyKey,
    maintenanceId: input.maintenanceId,
    amount: input.amount,
    note: optional(input.note),
  });
  return data;
}

/** GET /api/pendencies/car-driver/{id}/chargeable-maintenances (MaintenanceChargeOptionDTO). */
export interface MaintenanceChargeOption {
  id: number;
  date?: string | null;
  local?: string | null;
  /** From the maintenance's own services. */
  description?: string | null;
  cost: number;
  /** Responsibility already assigned to drivers (paid or not). */
  assignedAmount: number;
  availableAmount: number;
  chargeable: boolean;
}

/** The maintenances of the contract's car (same account) a shared-maintenance charge can come from. */
export async function listChargeableMaintenances(driverCarId: number | string): Promise<MaintenanceChargeOption[]> {
  const { data } = await api.get(endpoints.DRIVER_PENDENCIES_CHARGEABLE_MAINTENANCES({ pathVariables: { id: driverCarId } }));
  return Array.isArray(data) ? data : [];
}

/**
 * The document of the debt (notification of the fine / agreement of the maintenance share), archived in
 * Documentos: issued by the backend the first time, the same one afterwards. The PDF is built from the stored
 * document, as everywhere else. No pendency is ever created or changed by it.
 */
export async function issuePendencyDocument(pendencyId: number): Promise<DocumentModel> {
  const { data } = await api.post(endpoints.PENDENCY_DOCUMENT({ pathVariables: { id: pendencyId } }));
  const document = await documentService.getDocument(data.id);
  await generateDocumentPdf(document);
  await documentService.generateDocumentPdf(data.id);
  return document;
}

/** Visual origin, structural only (never guessed from the name). Legacy rows have none. */
export function pendencyOriginLabel(pendency: DriverPendencyModel): string | null {
  if (pendency.originType === "FINE") return "Multa";
  if (pendency.originType === "SHARED_MAINTENANCE") return "Manutenção compartilhada";
  return null;
}

/** Only debts whose document the backend knows how to issue (or that already came from one). */
export function canIssuePendencyDocument(pendency: DriverPendencyModel): boolean {
  return pendency.originType === "FINE" || pendency.originType === "SHARED_MAINTENANCE" || Boolean(pendency.originDocumentId);
}

export const DRIVER_CHARGE_ERROR_MESSAGES: Record<string, string> = {
  maintenancechargeexceeded: "A soma das cobranças desta manutenção ultrapassaria o custo da manutenção.",
  maintenancecarmismatch: "A manutenção precisa ser do veículo deste contrato.",
  maintenancerequired: "Selecione a manutenção.",
  drivercarwithoutdriver: "Este contrato não tem motorista: não há devedor para a cobrança.",
  chargeamountinvalid: "Informe um valor maior que zero, com no máximo 2 casas decimais.",
  fineinfractionrequired: "Informe a data da infração.",
  chargetexttoolong: "Um dos textos está longo demais.",
  idempotencykeyinvalid: "Abra a tela novamente para registrar a cobrança.",
  idempotencykeyconflict: "Esta operação já foi usada para outra cobrança. Abra a tela novamente para registrar uma nova.",
  pendencydocumentunsupported: "Esta pendência não tem um documento correspondente.",
  VEHICLE_DELETED: "Veículo excluído: o histórico está disponível somente para consulta.",
};
