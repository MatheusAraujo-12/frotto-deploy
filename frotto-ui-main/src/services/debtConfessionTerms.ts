/**
 * Payment terms of a Confissão de Dívida generated from Pendências: the AGREEMENT to pay the debt, stored inside the
 * document (payload.condicoesPagamento) so the PDF can be generated again with exactly the same conditions. They
 * never touch the pendencies (payment method, balance, status...) and never create installments or receivables.
 * The backend applies the same rules (DebtConfessionService) before storing them.
 */

export const TERMS_KEY = "condicoesPagamento";

export type ConfessionPaymentForm = "PIX" | "TRANSFERENCIA" | "DINHEIRO" | "PARCELADO" | "OUTRO";

export const PAYMENT_FORM_OPTIONS: Array<{ value: ConfessionPaymentForm; label: string }> = [
  { value: "PIX", label: "PIX" },
  { value: "TRANSFERENCIA", label: "Transferência bancária" },
  { value: "DINHEIRO", label: "Dinheiro" },
  { value: "PARCELADO", label: "Parcelado" },
  { value: "OUTRO", label: "Outro" },
];

export const MAX_INSTALLMENTS = 120;
export const MAX_NOTE_LENGTH = 1000;

/** What the user fills before generating. Dates are YYYY-MM-DD. */
export interface DebtConfessionTerms {
  formaPagamento?: ConfessionPaymentForm | "";
  /** Deadline for paying the whole debt (with installments: for the last one). */
  prazoPagamento?: string;
  parcelasQtd?: number | null;
  primeiroVencimento?: string;
  observacao?: string;
}

/** As stored in the document: only the keys that apply, observation only when filled. */
export interface StoredConfessionTerms {
  formaPagamento: ConfessionPaymentForm;
  prazoPagamento: string;
  parcelasQtd?: number;
  primeiroVencimento?: string;
  observacao?: string;
}

export type ConfessionTermsErrors = Partial<Record<keyof DebtConfessionTerms, string>>;

export function paymentFormLabel(value?: string | null): string {
  return PAYMENT_FORM_OPTIONS.find((option) => option.value === value)?.label || `${value || "-"}`;
}

/** Real calendar date in YYYY-MM-DD (no time, no timezone). */
export function isIsoDate(value?: string | null): boolean {
  const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(`${value || ""}`);
  if (!match) return false;
  const [year, month, day] = [Number(match[1]), Number(match[2]), Number(match[3])];
  const date = new Date(Date.UTC(year, month - 1, day));
  return date.getUTCFullYear() === year && date.getUTCMonth() === month - 1 && date.getUTCDate() === day;
}

/** "2026-10-20" -> "20/10/2026" without going through Date (no timezone shift). */
export function formatTermsDate(value?: string | null): string {
  return isIsoDate(value) ? `${value}`.split("-").reverse().join("/") : `${value || "-"}`;
}

export function validateConfessionTerms(terms: DebtConfessionTerms): ConfessionTermsErrors {
  const errors: ConfessionTermsErrors = {};
  if (!PAYMENT_FORM_OPTIONS.some((option) => option.value === terms.formaPagamento)) {
    errors.formaPagamento = "Selecione a forma de pagamento.";
  }
  if (!isIsoDate(terms.prazoPagamento)) {
    errors.prazoPagamento = "Informe o prazo para pagamento.";
  }
  if (terms.formaPagamento === "PARCELADO") {
    const installments = terms.parcelasQtd;
    if (typeof installments !== "number" || !Number.isInteger(installments) || installments < 2 || installments > MAX_INSTALLMENTS) {
      errors.parcelasQtd = `Informe a quantidade de parcelas (de 2 a ${MAX_INSTALLMENTS}).`;
    }
    if (!isIsoDate(terms.primeiroVencimento)) {
      errors.primeiroVencimento = "Informe o primeiro vencimento.";
    } else if (isIsoDate(terms.prazoPagamento) && `${terms.primeiroVencimento}` > `${terms.prazoPagamento}`) {
      errors.primeiroVencimento = "O primeiro vencimento não pode ser depois do prazo para pagamento.";
    }
  }
  if (`${terms.observacao || ""}`.trim().length > MAX_NOTE_LENGTH) {
    errors.observacao = `A observação pode ter no máximo ${MAX_NOTE_LENGTH} caracteres.`;
  }
  return errors;
}

/** Valid terms in the stored shape, or null when anything required is missing or invalid. */
export function normalizeConfessionTerms(terms: DebtConfessionTerms): StoredConfessionTerms | null {
  if (Object.keys(validateConfessionTerms(terms)).length) {
    return null;
  }
  const stored: StoredConfessionTerms = {
    formaPagamento: terms.formaPagamento as ConfessionPaymentForm,
    prazoPagamento: terms.prazoPagamento as string,
  };
  if (terms.formaPagamento === "PARCELADO") {
    stored.parcelasQtd = terms.parcelasQtd as number;
    stored.primeiroVencimento = terms.primeiroVencimento;
  }
  const note = `${terms.observacao || ""}`.trim();
  if (note) stored.observacao = note;
  return stored;
}

/** Terms stored in a document, or null for confessions saved before them (legacy flat fields). */
export function readStoredConfessionTerms(payload: Record<string, any> | null | undefined): StoredConfessionTerms | null {
  const terms = payload?.[TERMS_KEY];
  return terms && typeof terms === "object" && !Array.isArray(terms) ? (terms as StoredConfessionTerms) : null;
}
