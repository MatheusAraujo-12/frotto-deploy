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
  attachments?: string[];
}

export interface DocumentSavePayload {
  type: DocumentType;
  status?: DocumentStatus;
  driverId: number;
  carId?: number | null;
  payload?: Record<string, any>;
  attachments?: string[];
  pdfUrl?: string | null;
}

export const DOCUMENT_TYPES: Array<{ value: DocumentType; label: string }> = [
  { value: "MULTA", label: "Multa" },
  { value: "MANUTENCAO_COMPARTILHADA", label: "Manutenção Compartilhada" },
  { value: "RECIBO_ALUGUEL", label: "Recibo de Aluguel" },
  { value: "CONFISSAO_DIVIDA", label: "Confissão de Dívida" },
  { value: "ENTREGA_DEVOLUCAO_CHECKLIST", label: "Entrega/Devolução Checklist" },
];

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
