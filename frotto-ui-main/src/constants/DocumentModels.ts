import { ReserveReturnResultModel } from "./CarModels";

export type DocumentType =
  | "MULTA"
  | "MANUTENCAO_COMPARTILHADA"
  | "RECIBO_ALUGUEL"
  | "CONFISSAO_DIVIDA"
  | "ENTREGA_DEVOLUCAO_CHECKLIST";

export type DocumentStatus = "DRAFT" | "FINAL" | "SENT" | "CANCELED";

export interface DriverSearchModel {
  id: number;
  name: string;
  cpf?: string;
  active?: boolean;
}

export interface CarSearchModel {
  id: number;
  plate?: string;
  name?: string;
  model?: string;
  active?: boolean;
}

export interface DocumentModel {
  id?: number;
  type: DocumentType;
  status: DocumentStatus;
  createdAt?: string;
  updatedAt?: string;
  driverId: number;
  driverName?: string;
  driverCpf?: string;
  carId?: number | null;
  carPlate?: string;
  carModel?: string;
  pdfUrl?: string | null;
  payload?: Record<string, any>;
  /** Stored attachment keys. */
  attachments?: string[];
  /**
   * Reference (attachment key or checklist photo reference) → URL resolved by the backend ("" = unavailable).
   * References absent from the map keep the legacy resolution. Only in the document detail.
   */
  attachmentUrls?: Record<string, string>;
  /** Entrega/Devolução checklist: ENTREGA | DEVOLUCAO (structured checklists; null on documents of the old wizard). */
  checklistType?: "ENTREGA" | "DEVOLUCAO" | null;
  /** The contract the checklist delivers / returns (set by the backend on finalization of an Entrega). */
  driverCarId?: number | null;
  /** The inspection created by the finalization of the checklist (only in the document detail). */
  inspectionId?: number | null;
  /** Only in the response of the finalization of a Devolução of a reserve car. */
  reserveReturn?: ReserveReturnResultModel | null;
}

export interface DocumentSavePayload {
  type: DocumentType;
  status?: DocumentStatus;
  driverId: number;
  carId?: number | null;
  payload?: Record<string, any>;
  attachments?: string[];
  pdfUrl?: string | null;
  checklistType?: "ENTREGA" | "DEVOLUCAO" | null;
  driverCarId?: number | null;
}

export const DOCUMENT_TYPES: Array<{ value: DocumentType; label: string }> = [
  { value: "MULTA", label: "Multa" },
  { value: "MANUTENCAO_COMPARTILHADA", label: "Manutenção Compartilhada" },
  { value: "RECIBO_ALUGUEL", label: "Recibo de Aluguel" },
  { value: "CONFISSAO_DIVIDA", label: "Confissão de Dívida" },
  { value: "ENTREGA_DEVOLUCAO_CHECKLIST", label: "Entrega/Devolução Checklist" },
];

/** Born in Pendências (the debt), their documents issued from the pendency: never created here anymore. */
export const DOCUMENT_TYPES_FROM_PENDENCIES: DocumentType[] = ["MULTA", "MANUTENCAO_COMPARTILHADA"];

/** Types a new document can be created with in Documentos (the history and filters still show every type). */
export const DOCUMENT_TYPES_CREATABLE = DOCUMENT_TYPES.filter((item) => !DOCUMENT_TYPES_FROM_PENDENCIES.includes(item.value));

/**
 * Options of the wizard's type selector: only the creatable types; an old draft of a fine / shared maintenance being
 * edited keeps showing its own type so it can still be completed (the backend refuses turning a draft into one).
 */
export function wizardTypeOptions(editingSavedDraft: boolean, currentType: DocumentType | "") {
  if (editingSavedDraft && currentType && DOCUMENT_TYPES_FROM_PENDENCIES.includes(currentType)) {
    return DOCUMENT_TYPES.filter((item) => item.value === currentType || DOCUMENT_TYPES_CREATABLE.includes(item));
  }
  return DOCUMENT_TYPES_CREATABLE;
}

export const DOCUMENT_STATUSES: Array<{ value: DocumentStatus; label: string }> = [
  { value: "DRAFT", label: "Rascunho" },
  { value: "FINAL", label: "Finalizado" },
  { value: "SENT", label: "Enviado" },
  { value: "CANCELED", label: "Cancelado" },
];

export const DOCUMENT_TYPES_REQUIRING_CAR: DocumentType[] = [
  "MULTA",
  "MANUTENCAO_COMPARTILHADA",
  "RECIBO_ALUGUEL",
  "ENTREGA_DEVOLUCAO_CHECKLIST",
];
