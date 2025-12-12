export const isValidCPF = (cpf: string | undefined) => {
  if (typeof cpf !== "string") return false;
  cpf = cpf.replace(/[^\d]+/g, "");
  if (cpf.length !== 11 || !!cpf.match(/(\d)\1{10}/)) return false;
  const splitedCpf = cpf.split("");
  const validator = splitedCpf
    .filter(
      (digit: any, index: number, array: string | any[]) =>
        index >= array.length - 2 && digit
    )
    .map((el: string | number) => +el);
  const toValidate = (pop: number) =>
    splitedCpf
      .filter(
        (digit: any, index: number, array: string | any[]) =>
          index < array.length - pop && digit
      )
      .map((el: string | number) => +el);
  const rest = (count: number, pop: number) =>
    ((toValidate(pop).reduce(
      (soma: number, el: number, i: number) => soma + el * (count - i),
      0
    ) *
      10) %
      11) %
    10;
  return !(rest(10, 2) !== validator[0] || rest(11, 1) !== validator[1]);
};
