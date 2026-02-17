export type DecimalValue = string | number | null | undefined;

const CURRENCY_FORMATTER = new Intl.NumberFormat("pt-BR", {
  style: "currency",
  currency: "BRL",
  minimumFractionDigits: 2,
  maximumFractionDigits: 2,
});

const DECIMAL_FORMATTER = new Intl.NumberFormat("pt-BR", {
  minimumFractionDigits: 2,
  maximumFractionDigits: 2,
});

export function sanitizeDecimalInput(value: DecimalValue): string {
  const raw = `${value ?? ""}`;
  const cleaned = raw.replace(/[^\d.,-]/g, "");
  return cleaned.replace(/(?!^)-/g, "");
}

export function parseDecimal(value: DecimalValue): number | null {
  if (value === null || value === undefined) {
    return null;
  }
  if (typeof value === "number") {
    return Number.isFinite(value) ? value : null;
  }

  const raw = sanitizeDecimalInput(value).trim();
  if (!raw || raw === "-" || raw === "," || raw === ".") {
    return null;
  }

  const lastComma = raw.lastIndexOf(",");
  const lastDot = raw.lastIndexOf(".");
  const decimalSeparatorIndex = Math.max(lastComma, lastDot);
  const hasBothSeparators = lastComma >= 0 && lastDot >= 0;

  let normalized = "";
  if (decimalSeparatorIndex >= 0) {
    const fractionLength = raw.length - decimalSeparatorIndex - 1;
    const useDecimalSeparator = hasBothSeparators || fractionLength <= 2;
    if (useDecimalSeparator) {
      const integerPart = raw.substring(0, decimalSeparatorIndex).replace(/[^\d-]/g, "");
      const fractionPart = raw.substring(decimalSeparatorIndex + 1).replace(/\D/g, "");
      normalized = fractionPart ? `${integerPart}.${fractionPart}` : integerPart;
    } else {
      normalized = raw.replace(/[^\d-]/g, "");
    }
  } else {
    normalized = raw.replace(/[^\d-]/g, "");
  }

  if (!normalized || normalized === "-") {
    return null;
  }

  const parsed = Number(normalized);
  return Number.isFinite(parsed) ? parsed : null;
}

export function formatDecimalInput(value: DecimalValue): string {
  const parsed = parseDecimal(value);
  if (parsed === null) {
    return "";
  }
  return DECIMAL_FORMATTER.format(parsed);
}

export function formatCurrencyPtBr(value: DecimalValue): string {
  const parsed = parseDecimal(value);
  return CURRENCY_FORMATTER.format(parsed ?? 0);
}
