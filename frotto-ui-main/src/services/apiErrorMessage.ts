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

  if (errorCode === "VEHICLE_LIMIT_REACHED" && (data as any)?.currentPlan === "PLATINUM") {
    return "O Platinum permite até 100 veículos cadastrados. Faça upgrade para Frotta em Meu Plano antes de adicionar o próximo veículo.";
  }

  if (errorCode && overrides[errorCode]) {
    return overrides[errorCode];
  }

  const fleetMessages: ApiErrorOverrides = {
    VEHICLE_PREVIOUSLY_DELETED: "Este veículo já foi excluído anteriormente. Para restaurá-lo, entre em contato com o suporte.",
    VEHICLE_PLATE_EXISTS: "Já existe um veículo com esta placa nesta conta.",
    VEHICLE_DELETED: "Veículo excluído: o histórico está disponível somente para consulta.",
    BILLING_PENDING_DOWNGRADE_FLEET_LIMIT: "Há um downgrade agendado incompatível com essa quantidade de veículos. Desfaça o downgrade antes de adicionar este veículo.",
    BILLING_PLAN_UPGRADE_IN_PROGRESS: "Há um upgrade em processamento. Aguarde a conclusão antes de alterar a quantidade de veículos.",
    RESTORE_REASON_REQUIRED: "Informe o motivo da restauração (até 500 caracteres).",
    BILLING_RENEWAL_LOCKED: "A próxima renovação já está fechada. Aguarde a confirmação da cobrança para mudar o plano.",
  };
  if (errorCode && fleetMessages[errorCode]) return fleetMessages[errorCode];

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
