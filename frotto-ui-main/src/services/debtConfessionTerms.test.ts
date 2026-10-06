import { formatTermsDate, isIsoDate, normalizeConfessionTerms, paymentFormLabel, validateConfessionTerms } from "./debtConfessionTerms";

describe("Condições de pagamento da Confissão de Dívida", () => {
  it("A) PIX + prazo + observação: valid, stored as filled", () => {
    const terms = { formaPagamento: "PIX" as const, prazoPagamento: "2026-10-20", observacao: "  Pagamento via PIX para a conta da empresa.  " };
    expect(validateConfessionTerms(terms)).toEqual({});
    expect(normalizeConfessionTerms(terms)).toEqual({
      formaPagamento: "PIX",
      prazoPagamento: "2026-10-20",
      observacao: "Pagamento via PIX para a conta da empresa.",
    });
  });

  it("B) PIX + prazo without observação: valid, no observation is invented", () => {
    expect(normalizeConfessionTerms({ formaPagamento: "PIX", prazoPagamento: "2026-10-20", observacao: "   " })).toEqual({
      formaPagamento: "PIX",
      prazoPagamento: "2026-10-20",
    });
  });

  it("C) Parcelado keeps the number of installments and the first due date; other forms never carry them", () => {
    expect(
      normalizeConfessionTerms({ formaPagamento: "PARCELADO", prazoPagamento: "2027-02-20", parcelasQtd: 5, primeiroVencimento: "2026-10-20" })
    ).toEqual({ formaPagamento: "PARCELADO", prazoPagamento: "2027-02-20", parcelasQtd: 5, primeiroVencimento: "2026-10-20" });
    expect(
      normalizeConfessionTerms({ formaPagamento: "DINHEIRO", prazoPagamento: "2026-10-20", parcelasQtd: 5, primeiroVencimento: "2026-10-01" })
    ).toEqual({ formaPagamento: "DINHEIRO", prazoPagamento: "2026-10-20" });
  });

  it.each([
    ["D) Parcelado without quantity", { parcelasQtd: null }, "parcelasQtd"],
    ["E) Parcelado with 1 installment", { parcelasQtd: 1 }, "parcelasQtd"],
    ["Parcelado with a fraction", { parcelasQtd: 2.5 }, "parcelasQtd"],
    ["Parcelado above the limit", { parcelasQtd: 121 }, "parcelasQtd"],
    ["F) Parcelado without first due date", { primeiroVencimento: "" }, "primeiroVencimento"],
    ["first due date after the deadline", { primeiroVencimento: "2027-03-01" }, "primeiroVencimento"],
  ])("%s is blocked", (_label, patch, field) => {
    const terms = { formaPagamento: "PARCELADO" as const, prazoPagamento: "2027-02-20", parcelasQtd: 5, primeiroVencimento: "2026-10-20", ...patch };
    expect(Object.keys(validateConfessionTerms(terms))).toEqual([field]);
    expect(normalizeConfessionTerms(terms)).toBeNull();
  });

  it("G) payment form missing (or unknown, like the old A_VISTA) is blocked", () => {
    expect(validateConfessionTerms({ prazoPagamento: "2026-10-20" })).toHaveProperty("formaPagamento");
    expect(validateConfessionTerms({ formaPagamento: "A_VISTA" as any, prazoPagamento: "2026-10-20" })).toHaveProperty("formaPagamento");
  });

  it("H) deadline missing or not a real date is blocked (never assumed as today)", () => {
    expect(validateConfessionTerms({ formaPagamento: "PIX" })).toHaveProperty("prazoPagamento");
    expect(validateConfessionTerms({ formaPagamento: "PIX", prazoPagamento: "2026-02-30" })).toHaveProperty("prazoPagamento");
    expect(validateConfessionTerms({ formaPagamento: "PIX", prazoPagamento: "20/10/2026" })).toHaveProperty("prazoPagamento");
  });

  it("observation longer than 1000 characters is blocked", () => {
    expect(validateConfessionTerms({ formaPagamento: "PIX", prazoPagamento: "2026-10-20", observacao: "x".repeat(1001) })).toHaveProperty(
      "observacao"
    );
  });

  it("labels and dates for the PDF, with no timezone shift", () => {
    expect(paymentFormLabel("TRANSFERENCIA")).toBe("Transferência bancária");
    expect(formatTermsDate("2026-10-20")).toBe("20/10/2026");
    expect(isIsoDate("2024-02-29")).toBe(true);
    expect(isIsoDate("2026-10-20T00:00:00Z")).toBe(false);
  });
});
