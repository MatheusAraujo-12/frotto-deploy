const options = {
  style: "currency",
  currency: "BRL",
  minimumFractionDigits: 2,
  maximumFractionDigits: 3,
};
const formatNumber = new Intl.NumberFormat("pt-BR", options);

export const currencyFormat = (number: string | number | undefined) => {
  if (!number) {
    return formatNumber.format(0);
  }
  return formatNumber.format(+number);
};

export const updateNumberByKeyandPrevious = (key: string, previous: string) => {
  const toFixedPrevious = parseFloat(previous).toFixed(2);
  if (key === "Backspace") {
    const finalValue = toFixedPrevious.replace(/\D/g, "");
    const parsedValue =
      parseFloat(finalValue.substring(0, finalValue.length - 1)) / 100;
    return parsedValue.toFixed(2);
  } else {
    const finalValue = [];
    finalValue.push(toFixedPrevious.replace(/\D/g, ""), key);
    const toFixedValue = parseFloat(finalValue.join("")) / 100;
    return toFixedValue.toFixed(2);
  }
};
