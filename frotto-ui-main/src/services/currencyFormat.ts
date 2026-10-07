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

/**
 * Mobile keyboards (Android/IME) report keydown as "Unidentified"/"Process" (keyCode 229): the typed character only
 * arrives in the input event, so the key-based mask must not handle (nor block) those keys.
 */
export const isUnidentifiedKey = (key: string | undefined, keyCode?: number) =>
  key === "Unidentified" || key === "Process" || keyCode === 229;

/** Value of the typed text read as cents (the same mask as the keyboard path): "R$ 0,004" -> "0.04". */
export const centsFromTypedText = (text: string | null | undefined) => {
  const digits = `${text ?? ""}`.replace(/\D/g, "").slice(-15);
  return (parseInt(digits || "0", 10) / 100).toFixed(2);
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
