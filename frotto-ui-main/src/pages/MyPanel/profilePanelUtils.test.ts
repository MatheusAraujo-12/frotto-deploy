import {
  EMPTY_FISCAL_FORM,
  EMPTY_PERSONAL_FORM,
  FiscalForm,
  getVisiblePersonalFields,
  PersonalForm,
} from "./profilePanelUtils";

const pfFiscal: FiscalForm = {
  ...EMPTY_FISCAL_FORM,
  taxPersonType: "CPF",
  taxLandlordName: "Maria Souza",
  taxCpf: "529.982.247-25",
  taxEmail: "maria@example.com",
  taxPhone: "(11) 98765-4321",
};

describe("getVisiblePersonalFields", () => {
  it("PF: hides empty personal duplicates and keeps only birth date", () => {
    expect(getVisiblePersonalFields(EMPTY_PERSONAL_FORM, pfFiscal)).toEqual(["personalBirthDate"]);
  });

  it("PF: hides personal duplicates equal to the fiscal data (normalized: mask, case, spaces)", () => {
    const personal: PersonalForm = {
      personalName: "  Maria Souza ",
      personalCpf: "52998224725",
      personalBirthDate: "1990-05-20",
      personalEmail: "MARIA@example.com",
      personalPhone: "11987654321",
    };
    expect(getVisiblePersonalFields(personal, pfFiscal)).toEqual(["personalBirthDate"]);
  });

  it("PF: keeps a personal field visible when its saved value diverges from the fiscal one", () => {
    const personal: PersonalForm = { ...EMPTY_PERSONAL_FORM, personalName: "Maria S. Legado", personalEmail: "maria@example.com" };
    expect(getVisiblePersonalFields(personal, pfFiscal)).toEqual(["personalName", "personalBirthDate"]);
  });

  it("PF: shows a personal value when the fiscal counterpart is still empty (nothing hidden)", () => {
    const personal: PersonalForm = { ...EMPTY_PERSONAL_FORM, personalPhone: "11912345678" };
    expect(getVisiblePersonalFields(personal, { ...EMPTY_FISCAL_FORM, taxPersonType: "CPF" })).toEqual([
      "personalBirthDate",
      "personalPhone",
    ]);
  });

  it("PJ: always shows every personal field (account holder is not the company)", () => {
    expect(getVisiblePersonalFields(EMPTY_PERSONAL_FORM, { ...EMPTY_FISCAL_FORM, taxPersonType: "CNPJ" })).toEqual([
      "personalName",
      "personalCpf",
      "personalBirthDate",
      "personalEmail",
      "personalPhone",
    ]);
  });
});
