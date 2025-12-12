import IMask from "imask";

const maskedCPF = IMask.createMask({
  mask: "000.000.000-00",
});

const maskedTel = IMask.createMask({
  mask: "(00) 00000-00000",
});

const maskedCEP = IMask.createMask({
  mask: "00.000-000",
});

export const formatCPF = (cpf: string | undefined) => {
  if (!cpf || cpf === "" || cpf === undefined) {
    return "";
  }
  maskedCPF.resolve(cpf);
  return maskedCPF.value;
};

export const formatTel = (tel: string | undefined) => {
  if (!tel || tel === "" || tel === undefined) {
    return "";
  }
  maskedTel.resolve(tel);
  return maskedTel.value;
};

export const formatCEP = (cep: string | undefined) => {
  if (!cep || cep === "" || cep === undefined) {
    return "";
  }
  maskedCEP.resolve(cep);
  return maskedCEP.value;
};
