import { useState } from "react";
import { fireEvent, render } from "@testing-library/react";
import { clearFieldsForTaxType, FiscalCadastralFields } from "./FiscalTab";
import { EMPTY_FISCAL_FORM, EMPTY_FISCAL_TOUCHED } from "./profilePanelUtils";

const saved = {
  ...EMPTY_FISCAL_FORM,
  taxLandlordName: "Maria Souza",
  taxCpf: "529.982.247-25",
  taxEmail: "maria@example.com",
  taxPhone: "(11) 98765-4321",
};

it("preserves loaded PF values through PF → PJ → PF without saving", () => {
  const Editor = () => {
    const [form, setForm] = useState(saved);
    return <FiscalCadastralFields form={form} touched={EMPTY_FISCAL_TOUCHED}
      errors={{}} onTouch={() => {}} onChange={setForm} />;
  };
  const { container } = render(<Editor />);
  const segment = container.querySelector("ion-segment")!;
  const values = () => Array.from(container.querySelectorAll("ion-input")).map(input => input.value);
  const expected = [saved.taxLandlordName, saved.taxCpf, saved.taxEmail, saved.taxPhone];
  expect(values()).toEqual(expected);
  fireEvent(segment, new CustomEvent("ionChange", { detail: { value: "CNPJ" }, bubbles: true }));
  fireEvent(segment, new CustomEvent("ionChange", { detail: { value: "CPF" }, bubbles: true }));
  expect(values()).toEqual(expected);
});

it("clears only the opposite branch at submission without mutating the draft", () => {
  const draft = { ...saved, taxCompanyName: "Empresa", taxCnpj: "11.222.333/0001-81" };
  expect(clearFieldsForTaxType(draft, "CNPJ")).toMatchObject({
    taxPersonType: "CNPJ", taxLandlordName: "", taxCpf: "", taxEmail: "", taxPhone: "",
    taxCompanyName: "Empresa", taxCnpj: draft.taxCnpj,
  });
  expect(clearFieldsForTaxType(draft, "CPF")).toMatchObject({
    ...saved, taxCompanyName: "", taxCnpj: "",
  });
  expect(draft.taxLandlordName).toBe(saved.taxLandlordName);
  expect(draft.taxCompanyName).toBe("Empresa");
});
