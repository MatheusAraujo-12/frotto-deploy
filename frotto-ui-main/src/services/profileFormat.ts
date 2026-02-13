export const sanitizeDigits = (value: string): string =>
  `${value || ""}`.replace(/\D/g, "");

const applyMask = (digits: string, pattern: string): string => {
  let index = 0;
  return pattern.replace(/#/g, () => digits[index++] || "");
};

export const maskCPF = (value: string): string => {
  const digits = sanitizeDigits(value).slice(0, 11);
  if (!digits) return "";
  return applyMask(digits, "###.###.###-##").replace(/[.\-/\s]+$/g, "");
};

export const maskCNPJ = (value: string): string => {
  const digits = sanitizeDigits(value).slice(0, 14);
  if (!digits) return "";
  return applyMask(digits, "##.###.###/####-##").replace(/[.\-/\s]+$/g, "");
};

export const maskPhone = (value: string): string => {
  const digits = sanitizeDigits(value).slice(0, 11);
  if (!digits) return "";

  if (digits.length <= 10) {
    return applyMask(digits, "(##) ####-####").replace(/[()\-\s]+$/g, "");
  }

  return applyMask(digits, "(##) #####-####").replace(/[()\-\s]+$/g, "");
};
