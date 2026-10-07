import { act, fireEvent, render } from "@testing-library/react";
import { IonApp } from "@ionic/react";
import { useState } from "react";
import FormCurrency from "./FormCurrency";
import { centsFromTypedText, currencyFormat, isUnidentifiedKey } from "../../services/currencyFormat";

const Harness: React.FC<{ onValue: (value: number) => void }> = ({ onValue }) => {
  const [value, setValue] = useState(0);
  return (
    <FormCurrency
      label="Valor"
      data-field="amount"
      initialValue={value}
      changeCallback={(next: string) => {
        setValue(Number(next));
        onValue(Number(next));
      }}
    />
  );
};

const renderField = () => {
  const onValue = jest.fn();
  render(
    <IonApp>
      <Harness onValue={onValue} />
    </IonApp>
  );
  return { onValue, field: document.querySelector('[data-field="amount"]') as HTMLIonInputElement };
};

describe("FormCurrency: teclado físico e teclado de celular", () => {
  it("helpers: mobile keys are recognized and typed text is read as cents", () => {
    expect(isUnidentifiedKey("Unidentified")).toBe(true);
    expect(isUnidentifiedKey("Process")).toBe(true);
    expect(isUnidentifiedKey("a", 229)).toBe(true);
    expect(isUnidentifiedKey("5", 53)).toBe(false);
    expect(centsFromTypedText("R$ 0,004")).toBe("0.04");
    expect(centsFromTypedText("R$ 4,000")).toBe("40.00");
    expect(centsFromTypedText("")).toBe("0.00");
  });

  it("physical keyboard: digits typed as keys build the value as before", async () => {
    const { onValue, field } = renderField();
    for (const key of "40000") {
      await act(async () => {
        fireEvent.keyDown(field, { key });
      });
    }
    expect(onValue).toHaveBeenLastCalledWith(400);
  });

  it("mobile keyboard (keydown 'Unidentified'): the typed text is used, the value is not lost", async () => {
    const { onValue, field } = renderField();
    let shown = "R$ 0,00";
    for (const digit of "40000") {
      await act(async () => {
        fireEvent.keyDown(field, { key: "Unidentified", keyCode: 229 });
      });
      // The keyboard inserts the character into the displayed text; Ionic then emits ionInput.
      field.value = `${shown}${digit}`;
      await act(async () => {
        fireEvent(field, new CustomEvent("ionInput", { detail: { value: field.value }, bubbles: true }));
      });
      shown = `${field.value}`;
    }
    expect(onValue).toHaveBeenLastCalledWith(400);
    expect(onValue).not.toHaveBeenCalledWith(NaN);
  });

  it("physical keyboard: Backspace edits as before and the keys are consumed (no duplicate input)", async () => {
    const { onValue, field } = renderField();
    for (const key of "40000") {
      await act(async () => {
        // fireEvent returns false when the handler called preventDefault: the browser never inserts the digit again.
        expect(fireEvent.keyDown(field, { key })).toBe(false);
      });
    }
    await act(async () => {
      fireEvent.keyDown(field, { key: "Backspace" });
    });
    expect(onValue).toHaveBeenLastCalledWith(40);
    expect(onValue).toHaveBeenCalledTimes(6);
  });

  it("mobile keyboard: the key is not blocked, each digit counts once and deleting shifts the cents back", async () => {
    const { onValue, field } = renderField();
    await act(async () => {
      // Not prevented: the IME must be able to insert the character.
      expect(fireEvent.keyDown(field, { key: "Unidentified", keyCode: 229 })).toBe(true);
    });
    expect(onValue).not.toHaveBeenCalled();

    const input = async (text: string) => {
      field.value = text;
      await act(async () => {
        fireEvent(field, new CustomEvent("ionInput", { detail: { value: text }, bubbles: true }));
      });
    };
    await input("R$ 0,001");
    await input("R$ 0,015");
    await input("R$ 0,150");
    await input("R$ 1,500");
    await input("R$ 15,000");
    expect(onValue).toHaveBeenLastCalledWith(150);
    expect(onValue).toHaveBeenCalledTimes(5);
    expect(field.value).toBe(currencyFormat(150));

    // Deleting one character (deleteContentBackward) moves the cents back: 150,00 -> 15,00.
    await input("R$ 150,0");
    expect(onValue).toHaveBeenLastCalledWith(15);
  });
});
