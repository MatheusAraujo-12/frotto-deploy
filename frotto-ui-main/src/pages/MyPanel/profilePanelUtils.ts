import { MeResponseDTO, TaxPersonType } from "../../services/profileService";
import { maskCNPJ, maskCPF, maskPhone, sanitizeDigits } from "../../services/profileFormat";

export type PanelTab = "pessoal" | "fiscal" | "seguranca" | "cadastro";
export type FormErrors<T extends string> = Partial<Record<T, string>>;

export interface PersonalForm {
  personalName: string;
  personalCpf: string;
  personalBirthDate: string;
  personalEmail: string;
  personalPhone: string;
}

export interface FiscalForm {
  taxPersonType: TaxPersonType;
  taxLandlordName: string;
  taxCpf: string;
  taxEmail: string;
  taxPhone: string;
  taxCompanyName: string;
  taxCnpj: string;
  taxIe: string;
  taxContactPhone: string;
  taxAddress: string;
}

export interface SecurityForm {
  oldPassword: string;
  newPassword: string;
  confirmPassword: string;
}

export interface CadastroForm {
  firstName: string;
  lastName: string;
  imageUrl: string;
  langKey: string;
}

export const PASSWORD_MIN_LENGTH = 4;
const EMAIL_REGEX = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

export const EMPTY_PERSONAL_FORM: PersonalForm = {
  personalName: "",
  personalCpf: "",
  personalBirthDate: "",
  personalEmail: "",
  personalPhone: "",
};

export const EMPTY_FISCAL_FORM: FiscalForm = {
  taxPersonType: "CPF",
  taxLandlordName: "",
  taxCpf: "",
  taxEmail: "",
  taxPhone: "",
  taxCompanyName: "",
  taxCnpj: "",
  taxIe: "",
  taxContactPhone: "",
  taxAddress: "",
};

export const EMPTY_SECURITY_FORM: SecurityForm = {
  oldPassword: "",
  newPassword: "",
  confirmPassword: "",
};

export const EMPTY_CADASTRO_FORM: CadastroForm = {
  firstName: "",
  lastName: "",
  imageUrl: "",
  langKey: "",
};

export const EMPTY_PERSONAL_TOUCHED: Record<keyof PersonalForm, boolean> = {
  personalName: false,
  personalCpf: false,
  personalBirthDate: false,
  personalEmail: false,
  personalPhone: false,
};

export const EMPTY_FISCAL_TOUCHED: Record<keyof FiscalForm, boolean> = {
  taxPersonType: false,
  taxLandlordName: false,
  taxCpf: false,
  taxEmail: false,
  taxPhone: false,
  taxCompanyName: false,
  taxCnpj: false,
  taxIe: false,
  taxContactPhone: false,
  taxAddress: false,
};

export const EMPTY_SECURITY_TOUCHED: Record<keyof SecurityForm, boolean> = {
  oldPassword: false,
  newPassword: false,
  confirmPassword: false,
};

export const EMPTY_CADASTRO_TOUCHED: Record<keyof CadastroForm, boolean> = {
  firstName: false,
  lastName: false,
  imageUrl: false,
  langKey: false,
};

export const normalizeText = (value: string): string => value.trim();

export const toNullable = (value: string): string | null => {
  const normalized = normalizeText(value);
  return normalized.length ? normalized : null;
};

export const toNullableDigits = (value: string): string | null => {
  const digits = sanitizeDigits(value);
  return digits.length ? digits : null;
};

const asText = (value: string | null | undefined): string => value ?? "";

export const mapPersonalForm = (data: MeResponseDTO): PersonalForm => ({
  personalName: asText(data.personalName),
  personalCpf: maskCPF(asText(data.personalCpf)),
  personalBirthDate: asText(data.personalBirthDate),
  personalEmail: asText(data.personalEmail),
  personalPhone: maskPhone(asText(data.personalPhone)),
});

export const mapFiscalForm = (data: MeResponseDTO): FiscalForm => ({
  taxPersonType: data.taxPersonType === "CNPJ" ? "CNPJ" : "CPF",
  taxLandlordName: asText(data.taxLandlordName),
  taxCpf: maskCPF(asText(data.taxCpf)),
  taxEmail: asText(data.taxEmail),
  taxPhone: maskPhone(asText(data.taxPhone)),
  taxCompanyName: asText(data.taxCompanyName),
  taxCnpj: maskCNPJ(asText(data.taxCnpj)),
  taxIe: asText(data.taxIe),
  taxContactPhone: maskPhone(asText(data.taxContactPhone)),
  taxAddress: asText(data.taxAddress),
});

export const mapCadastroForm = (data: MeResponseDTO): CadastroForm => ({
  firstName: asText(data.firstName),
  lastName: asText(data.lastName),
  imageUrl: asText(data.imageUrl),
  langKey: asText(data.langKey),
});

export const serializePersonalForm = (form: PersonalForm): string =>
  JSON.stringify({
    personalName: normalizeText(form.personalName),
    personalCpf: sanitizeDigits(form.personalCpf),
    personalBirthDate: form.personalBirthDate,
    personalEmail: normalizeText(form.personalEmail).toLowerCase(),
    personalPhone: sanitizeDigits(form.personalPhone),
  });

export const serializeFiscalForm = (form: FiscalForm): string =>
  JSON.stringify({
    taxPersonType: form.taxPersonType,
    taxLandlordName: normalizeText(form.taxLandlordName),
    taxCpf: sanitizeDigits(form.taxCpf),
    taxEmail: normalizeText(form.taxEmail).toLowerCase(),
    taxPhone: sanitizeDigits(form.taxPhone),
    taxCompanyName: normalizeText(form.taxCompanyName),
    taxCnpj: sanitizeDigits(form.taxCnpj),
    taxIe: normalizeText(form.taxIe),
    taxContactPhone: sanitizeDigits(form.taxContactPhone),
    taxAddress: normalizeText(form.taxAddress),
  });

export const serializeCadastroForm = (form: CadastroForm): string =>
  JSON.stringify({
    firstName: normalizeText(form.firstName),
    lastName: normalizeText(form.lastName),
    imageUrl: normalizeText(form.imageUrl),
    langKey: normalizeText(form.langKey),
  });

export const hasPersonalData = (form: PersonalForm): boolean =>
  Boolean(
    normalizeText(form.personalName) ||
      sanitizeDigits(form.personalCpf) ||
      form.personalBirthDate ||
      normalizeText(form.personalEmail) ||
      sanitizeDigits(form.personalPhone)
  );

export const hasFiscalData = (form: FiscalForm): boolean =>
  Boolean(
    form.taxPersonType === "CPF"
      ? normalizeText(form.taxLandlordName) ||
          sanitizeDigits(form.taxCpf) ||
          normalizeText(form.taxEmail) ||
          sanitizeDigits(form.taxPhone)
      : normalizeText(form.taxCompanyName) ||
          sanitizeDigits(form.taxCnpj) ||
          normalizeText(form.taxIe) ||
          sanitizeDigits(form.taxContactPhone) ||
          normalizeText(form.taxAddress)
  );

export const hasCadastroData = (form: CadastroForm): boolean =>
  Boolean(
    normalizeText(form.firstName) ||
      normalizeText(form.lastName) ||
      normalizeText(form.imageUrl) ||
      normalizeText(form.langKey)
  );

export const validatePersonal = (form: PersonalForm): FormErrors<keyof PersonalForm> => {
  const errors: FormErrors<keyof PersonalForm> = {};
  const cpf = sanitizeDigits(form.personalCpf);
  const phone = sanitizeDigits(form.personalPhone);
  const email = normalizeText(form.personalEmail);

  if (cpf && cpf.length !== 11) errors.personalCpf = "CPF deve conter 11 dígitos.";
  if (email && !EMAIL_REGEX.test(email)) errors.personalEmail = "Informe um e-mail válido.";
  if (phone && (phone.length < 10 || phone.length > 11)) errors.personalPhone = "Telefone deve conter 10 ou 11 dígitos.";
  if (form.personalBirthDate && !/^\d{4}-\d{2}-\d{2}$/.test(form.personalBirthDate)) errors.personalBirthDate = "Data de nascimento inválida.";

  return errors;
};

export const validateFiscal = (form: FiscalForm): FormErrors<keyof FiscalForm> => {
  const errors: FormErrors<keyof FiscalForm> = {};
  if (form.taxPersonType === "CPF") {
    if (!normalizeText(form.taxLandlordName)) errors.taxLandlordName = "Informe o nome do locador.";
    if (sanitizeDigits(form.taxCpf).length !== 11) errors.taxCpf = "CPF deve conter 11 dígitos.";
    const email = normalizeText(form.taxEmail);
    if (email && !EMAIL_REGEX.test(email)) errors.taxEmail = "Informe um e-mail válido.";
  } else {
    if (!normalizeText(form.taxCompanyName)) errors.taxCompanyName = "Informe a razão social.";
    if (sanitizeDigits(form.taxCnpj).length !== 14) errors.taxCnpj = "CNPJ deve conter 14 dígitos.";
  }
  return errors;
};

export const validateSecurity = (form: SecurityForm): FormErrors<keyof SecurityForm> => {
  const errors: FormErrors<keyof SecurityForm> = {};
  if (!form.oldPassword) errors.oldPassword = "Informe a senha antiga.";
  if (!form.newPassword || form.newPassword.length < PASSWORD_MIN_LENGTH) {
    errors.newPassword = `A nova senha deve ter ao menos ${PASSWORD_MIN_LENGTH} caracteres.`;
  }
  if (!form.confirmPassword) errors.confirmPassword = "Confirme a nova senha.";
  if (form.confirmPassword && form.newPassword !== form.confirmPassword) {
    errors.confirmPassword = "A confirmação da senha não confere.";
  }
  return errors;
};

export const validateCadastro = (form: CadastroForm): FormErrors<keyof CadastroForm> => {
  const errors: FormErrors<keyof CadastroForm> = {};
  if (form.langKey && normalizeText(form.langKey).length > 10) errors.langKey = "Idioma deve ter no máximo 10 caracteres.";
  return errors;
};
