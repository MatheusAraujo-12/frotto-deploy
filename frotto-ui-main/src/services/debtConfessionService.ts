import endpoints from "../constants/endpoints";
import { DocumentModel } from "../constants/DocumentModels";
import { DriverPendencyModel } from "../constants/CarModels";
import { generateDocumentPdf } from "../pages/Documents/documentPdf";
import { getApiErrorCode } from "./apiErrorMessage";
import api from "./axios/axios";
import documentService from "./documentService";
import { DebtConfessionTerms, TERMS_KEY, normalizeConfessionTerms } from "./debtConfessionTerms";

export type { DebtConfessionTerms } from "./debtConfessionTerms";

/**
 * Confissão de Dívida from Pendências. The backend is the authority: the preview, the totals, the debtor and the
 * revalidation all come from it. This module only sends the selected ids, shows what comes back and runs the
 * document flow that already exists (draft -> finalize -> PDF in the browser), never computing a financial value.
 */

/** POST /api/pendencies/confissao-divida/preview - one item (DebtConfessionPreviewDTO.Item). */
export interface DebtConfessionPreviewItem {
  pendencyId: number;
  name?: string | null;
  date?: string | null;
  note?: string | null;
  status?: string | null;
  cost?: number | null;
  paidAmount?: number | null;
  remainingAmount?: number | null;
  typeId?: number | null;
  typeNameSnapshot?: string | null;
  descricaoItem?: string | null;
  valorItem: number;
  originDriverCarId?: number | null;
  originContractNumber?: string | null;
  originCarId?: number | null;
  originCarPlate?: string | null;
  originCarModel?: string | null;
}

/** POST /api/pendencies/confissao-divida/preview (DebtConfessionPreviewDTO). */
export interface DebtConfessionPreview {
  driverId: number;
  driverName?: string | null;
  driverCpf?: string | null;
  /** Only when every item shares one contract / one car; null otherwise. */
  driverCarId?: number | null;
  contractNumber?: string | null;
  carId?: number | null;
  carPlate?: string | null;
  carModel?: string | null;
  origemDaDivida?: string | null;
  items: DebtConfessionPreviewItem[];
  valorTotal: number;
}

export const DIFFERENT_DEBTORS_MESSAGE = "Selecione apenas pendências do mesmo motorista para gerar a Confissão de Dívida.";
export const OUTDATED_MESSAGE = "As pendências foram alteradas desde a prévia. Atualize os dados antes de gerar o documento.";
export const WITHOUT_DEBTOR_MESSAGE =
  "Esta pendência não tem um motorista devedor identificado com segurança e não pode entrar na Confissão de Dívida.";

/** Messages for the error codes the preview / generation can return. */
export const DEBT_CONFESSION_ERROR_MESSAGES: Record<string, string> = {
  pendenciesdifferentdebtors: DIFFERENT_DEBTORS_MESSAGE,
  pendencywithoutdebtor: WITHOUT_DEBTOR_MESSAGE,
  pendencynotopen: OUTDATED_MESSAGE,
  confessionpendencieschanged: OUTDATED_MESSAGE,
  pendencyidsrequired: "Selecione ao menos uma pendência.",
  pendencyidsduplicated: "Pendência selecionada mais de uma vez.",
  toomanypendencies: "Selecione no máximo 50 pendências.",
  VEHICLE_DELETED: "Uma das pendências é de um veículo excluído e não pode entrar na Confissão de Dívida.",
  confessionoriginmismatch: OUTDATED_MESSAGE,
  confessionorigininvalid: OUTDATED_MESSAGE,
  confessiontermsinvalid: "Confira as condições de pagamento: forma, prazo e, se parcelado, parcelas e primeiro vencimento.",
};

export const INVALID_TERMS_MESSAGE = DEBT_CONFESSION_ERROR_MESSAGES.confessiontermsinvalid;

/** Open pendency with a known debtor: the only ones that can be selected (the backend checks again). */
export function isConfessionEligible(pendency: DriverPendencyModel): boolean {
  if (!pendency.id || pendency.status === "PAID" || !pendency.debtorDriverId) {
    return false;
  }
  const remaining = typeof pendency.remainingAmount === "number" ? pendency.remainingAmount : pendency.cost;
  return typeof remaining === "number" && remaining > 0;
}

/** Why a pendency cannot be selected right now (selection of another debtor, paid, no debtor), or null. */
export function selectionBlockReason(pendency: DriverPendencyModel, selectedDebtorId: number | null): string | null {
  if (pendency.id && pendency.status !== "PAID" && !pendency.debtorDriverId) {
    return WITHOUT_DEBTOR_MESSAGE;
  }
  if (!isConfessionEligible(pendency)) {
    return "Somente pendências com saldo em aberto podem entrar na Confissão de Dívida.";
  }
  if (selectedDebtorId !== null && pendency.debtorDriverId !== selectedDebtorId) {
    return DIFFERENT_DEBTORS_MESSAGE;
  }
  return null;
}

/** True when the error means the pendencies changed since the preview (paid, other balance, removed...). */
export function isOutdatedConfession(error: unknown): boolean {
  const code = getApiErrorCode(error);
  return (
    Number((error as any)?.response?.status) === 409 ||
    code === "pendencynotopen" ||
    code === "confessionpendencieschanged" ||
    Number((error as any)?.response?.status) === 404
  );
}

/** Payment terms refused (here or by the backend): the user can fix them in the same preview. */
export function isInvalidTermsError(error: unknown): boolean {
  return getApiErrorCode(error) === "confessiontermsinvalid" || (error as Error)?.message === INVALID_TERMS_MESSAGE;
}

export async function previewDebtConfession(pendencyIds: number[]): Promise<DebtConfessionPreview> {
  const { data } = await api.post(endpoints.DEBT_CONFESSION_PREVIEW(), { pendencyIds });
  return data;
}

/**
 * Payload of a Confissão de Dívida originated from pendencies, in the backend contract: the debtor and the ids
 * (origem), each item with its pendency and the balance the user saw, and the payment terms of the agreement
 * (condicoesPagamento). The backend reloads everything, refuses values that no longer match the database, validates
 * the terms and rebuilds descriptions, origins and totals. No witnesses: new confessions are signed by the two parties.
 */
export function buildConfessionPayload(preview: DebtConfessionPreview, terms: DebtConfessionTerms): Record<string, unknown> {
  const stored = normalizeConfessionTerms(terms);
  if (!stored) {
    throw new Error(INVALID_TERMS_MESSAGE);
  }
  return {
    origem: {
      tipo: "PENDENCIAS",
      driverId: preview.driverId,
      pendencyIds: preview.items.map((item) => item.pendencyId),
    },
    itensDaDivida: preview.items.map((item) => ({ sourcePendencyId: item.pendencyId, valorItem: item.valorItem })),
    valorTotal: preview.valorTotal,
    [TERMS_KEY]: stored,
  };
}

/**
 * Same steps as the Documentos wizard: the draft (validated against the database), the finalization (revalidated
 * again - pendencies that changed meanwhile refuse it), the PDF built in the browser from the stored document and
 * the PDF mark. Pendencies are never written. If the finalization is refused, the draft is removed.
 */
export async function generateDebtConfession(preview: DebtConfessionPreview, terms: DebtConfessionTerms): Promise<DocumentModel> {
  // Invalid terms throw here, before anything is created.
  const payload = buildConfessionPayload(preview, terms);
  const draft = await documentService.createDocument({
    type: "CONFISSAO_DIVIDA",
    status: "DRAFT",
    driverId: preview.driverId,
    carId: preview.carId ?? null,
    payload,
  });
  try {
    await documentService.finalizeDocument(draft.id as number);
  } catch (error) {
    try {
      await documentService.deleteDocument(draft.id as number);
    } catch {
      // The draft stays in Documentos as a draft; the error below is what the user must see.
    }
    throw error;
  }
  const document = await documentService.getDocument(draft.id as number);
  await generateDocumentPdf(document);
  await documentService.generateDocumentPdf(draft.id as number);
  return document;
}
