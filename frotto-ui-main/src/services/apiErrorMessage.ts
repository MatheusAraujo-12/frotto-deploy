import { TEXT } from "../constants/texts";

type ApiErrorOverrides = Record<string, string>;

type ApiFieldError = {
  field?: string;
  message?: string;
};

type ApiErrorData = {
  errorKey?: string;
  fieldErrors?: ApiFieldError[];
  message?: string;
  status?: number;
  title?: string;
  violations?: ApiFieldError[];
};

const HTTP_STATUS_MESSAGES: Record<number, string> = {
  400: "Não foi possível salvar. Verifique os campos e tente novamente.",
  401: "Sua sessão expirou. Faça login novamente.",
  403: "Você não tem permissão para salvar este registro.",
  404: "Registro não encontrado. Atualize a página e tente novamente.",
  409: "Conflito ao salvar. Atualize os dados e tente novamente.",
  422: "Algum campo informado é inválido. Corrija os campos destacados.",
  500: "Erro interno no servidor. Tente novamente em instantes.",
};

export function getApiErrorMessage(
  error: unknown,
  fallback = TEXT.saveFailed,
  overrides: ApiErrorOverrides = {}
): string {
  const response = (error as any)?.response;
  const status = Number(response?.status || 0);
  const data = response?.data as ApiErrorData | undefined;
  const errorCode = getErrorCode(data);

  if (errorCode && overrides[errorCode]) {
    return overrides[errorCode];
  }

  const statusMessage = HTTP_STATUS_MESSAGES[status];
  const fieldError = getFieldErrorMessage(data);
  if (fieldError && (status === 400 || status === 422)) {
    return `${statusMessage || fallback} ${fieldError}`;
  }

  if (statusMessage) {
    return statusMessage;
  }

  if (!response) {
    return "Não foi possível conectar ao servidor. Verifique sua conexão e tente novamente.";
  }

  return fallback;
}

function getErrorCode(data?: ApiErrorData): string | undefined {
  const candidates = [data?.errorKey, data?.message];

  for (const candidate of candidates) {
    if (typeof candidate !== "string") {
      continue;
    }

    const direct = candidate.trim();
    if (!direct) {
      continue;
    }

    const parts = direct.split(".");
    return parts[parts.length - 1] || direct;
  }

  return undefined;
}

function getFieldErrorMessage(data?: ApiErrorData): string | undefined {
  const fields = data?.fieldErrors || data?.violations;
  if (!Array.isArray(fields) || fields.length === 0) {
    return undefined;
  }

  const first = fields[0];
  const field = first?.field ? `Campo ${first.field}` : "Um campo";
  const message = first?.message ? `: ${first.message}` : " está inválido.";

  return `${field}${message}`;
}
