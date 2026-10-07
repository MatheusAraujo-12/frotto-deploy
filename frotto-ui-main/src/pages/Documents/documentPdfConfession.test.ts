import * as pdfMake from "pdfmake/build/pdfmake";
import { createDocumentPdfBlob, resolveConfissaoDebtItems } from "./documentPdf";
import { STORED_CONFESSION, STORED_CONFESSION_WITH_TERMS } from "../../services/debtConfessionContract.fixtures";
import { buildPdfLetterhead, loadPdfLetterheadData } from "../../services/pdfLetterhead";
import api from "../../services/axios/axios";

jest.mock("../../services/axios/axios");
jest.mock("pdfmake/build/pdfmake", () => ({ createPdf: jest.fn() }));
jest.mock("../../services/pdfLetterhead", () => ({
  ...jest.requireActual("../../services/pdfLetterhead"),
  loadPdfLetterheadData: jest.fn(),
  buildPdfLetterhead: jest.fn(),
}));

const INSTALLMENT_CLAUSE =
  "Inadimplemento: o não pagamento de qualquer parcela implicará vencimento antecipado das demais, " +
  "multa de 2%, juros de 1% ao mês, correção monetária e demais encargos legais.";
const DEADLINE_CLAUSE =
  "Inadimplemento: o não pagamento do valor devido até o prazo estabelecido nesta Confissão de Dívida " +
  "caracterizará o inadimplemento do acordo, ficando o débito sujeito às medidas de cobrança cabíveis.";

/** Every text the PDF would print, in order (paragraphs, list items, table cells, signature labels). */
function textsOf(node: any): string[] {
  if (node === null || node === undefined) return [];
  if (typeof node === "string" || typeof node === "number") return [`${node}`];
  if (Array.isArray(node)) return node.flatMap(textsOf);
  if (typeof node !== "object") return [];
  return [
    ...textsOf(node.text),
    ...textsOf(node.ul),
    ...textsOf(node.stack),
    ...textsOf(node.columns),
    ...textsOf(node.table?.body),
  ];
}

async function printedTexts(document: any): Promise<string[]> {
  let definition: any;
  (pdfMake.createPdf as jest.Mock).mockImplementation((value: any) => {
    definition = value;
    return { getBlob: (callback: (blob: Blob) => void) => callback(new Blob()) };
  });
  await createDocumentPdfBlob(document);
  return textsOf(definition.content);
}

describe("PDF da Confissão de Dívida", () => {
  beforeEach(() => {
    (loadPdfLetterheadData as jest.Mock).mockResolvedValue({
      profile: { taxPersonType: "CNPJ", taxCompanyName: "Frotas Exemplo Ltda", taxCnpj: "12345678000190" },
      logoDataUrl: null,
    });
    (buildPdfLetterhead as jest.Mock).mockReturnValue([]);
  });

  it("a confession from pendencies prints each debt with its own vehicle of origin and the backend values", () => {
    expect(resolveConfissaoDebtItems(STORED_CONFESSION.payload)).toEqual([
      { descricao: "Outros - Multa (20/08/2026). AIT 123. Veículo de origem: Onix - ONX1A11", valorItem: 200 },
      { descricao: "Danos/Avarias - Danos/Avarias (05/09/2026). Veículo de origem: HB20 - HBV2B22", valorItem: 350 },
      {
        descricao:
          "Outros - Multa (25/09/2026) - saldo remanescente; valor original R$ 1.000,00, já pago R$ 400,00. Veículo de origem: Argo - ARG3C33",
        valorItem: 600,
      },
    ]);
  });

  it("confessions without the PENDENCIAS origin print exactly as before", () => {
    const manual = {
      itensDaDivida: [{ typeNameSnapshot: "Outros", descricaoItem: "Acordo", valorItem: 250, sourcePendencyId: 1 }],
      valorTotal: 250,
    };
    expect(resolveConfissaoDebtItems(manual)).toEqual([{ descricao: "Outros - Acordo", valorItem: 250 }]);
    expect(resolveConfissaoDebtItems({ tipoItem: "Danos", valorTotal: 90 })).toEqual([{ descricao: "Danos", valorItem: 90 }]);
  });

  it("M/N) new confession: the stored conditions, the observation, only creditor and debtor sign - no witnesses", async () => {
    const texts = await printedTexts(STORED_CONFESSION_WITH_TERMS);
    const all = texts.join("\n");

    expect(texts).toEqual(
      expect.arrayContaining([
        "Condições de pagamento:",
        "Forma de pagamento: Parcelado",
        "Prazo para pagamento: 20/02/2027",
        "Quantidade de parcelas: 5",
        "Primeiro vencimento: 20/10/2026",
        "Observação: Em caso de atraso, o acordo deverá ser renegociado com a empresa.",
        "CREDOR / EMPRESA",
        "DEVEDOR / MOTORISTA",
        // A) installments keep the existing clause, acceleration of the remaining installments included.
        INSTALLMENT_CLAUSE,
      ])
    );
    expect(all).not.toContain(DEADLINE_CLAUSE);
    expect(all).toContain("Devedor: João Silva, CPF 11111111111.");
    expect(all).toContain("Veículo de origem: Argo - ARG3C33");
    expect(all).toContain("Valor total reconhecido: R$");
    expect(all).not.toMatch(/Testemunha/i);
    expect(all).not.toContain("Credor/Locador");
    // Old flat terms are not printed for the new structure.
    expect(all).not.toContain("Pagamento à vista");
    expect(all).not.toContain("Parcelamento:");
    // Every party signs exactly once: the two of the agreement.
    expect(texts.filter((text) => /^_{10,}$/.test(text))).toHaveLength(2);
  });

  it("B) without observation nothing is invented; a non-installment form prints no installment lines", async () => {
    const document = {
      ...STORED_CONFESSION_WITH_TERMS,
      payload: { ...STORED_CONFESSION_WITH_TERMS.payload, condicoesPagamento: { formaPagamento: "PIX", prazoPagamento: "2026-10-20" } },
    };
    const texts = await printedTexts(document);

    expect(texts).toEqual(expect.arrayContaining(["Forma de pagamento: PIX", "Prazo para pagamento: 20/10/2026"]));
    expect(texts.some((text) => text.startsWith("Observação"))).toBe(false);
    expect(texts.some((text) => text.startsWith("Quantidade de parcelas") || text.startsWith("Primeiro vencimento"))).toBe(false);
  });

  it.each(["PIX", "TRANSFERENCIA", "DINHEIRO", "OUTRO"])(
    "B/C/D/E/H) %s: the deadline clause, nothing about installments, no new charge, no witnesses",
    async (formaPagamento) => {
      const document = {
        ...STORED_CONFESSION_WITH_TERMS,
        payload: { ...STORED_CONFESSION_WITH_TERMS.payload, condicoesPagamento: { formaPagamento, prazoPagamento: "2026-10-20" } },
      };
      const texts = await printedTexts(document);
      const all = texts.join("\n");

      expect(texts).toContain(DEADLINE_CLAUSE);
      expect(all).not.toMatch(/parcela/i);
      expect(all).not.toMatch(/vencimento antecipado/i);
      // The items include a traffic fine ("Multa"): what must not appear are the charges of the old clause.
      expect(all).not.toMatch(/multa de|juros de|correção monetária|encargos legais|honorários/i);
      expect(all).not.toMatch(/Testemunha/i);
      expect(texts).toEqual(expect.arrayContaining(["CREDOR / EMPRESA", "DEVEDOR / MOTORISTA"]));
      // I) printing is local: no request, so nothing financial can be written.
      expect((api as any).post).not.toHaveBeenCalled();
      expect((api as any).put).not.toHaveBeenCalled();
      expect((api as any).delete).not.toHaveBeenCalled();
    }
  );

  it("L) generating again from the stored document prints exactly the same conditions", async () => {
    const first = await printedTexts(STORED_CONFESSION_WITH_TERMS);
    const again = await printedTexts(JSON.parse(JSON.stringify(STORED_CONFESSION_WITH_TERMS)));
    const conditionsOf = (texts: string[]) => texts.filter((text) => /^(Forma de pagamento|Prazo|Quantidade|Primeiro|Observação)/.test(text));

    expect(conditionsOf(again)).toEqual(conditionsOf(first));
    expect(conditionsOf(first)).toHaveLength(5);
  });

  it("F) an old à vista confession without condicoesPagamento keeps the legacy wording (no deadline clause)", async () => {
    const old = {
      ...STORED_CONFESSION,
      payload: { ...STORED_CONFESSION.payload, formaPagamento: "A_VISTA", parcelasQtd: undefined, valorParcela: undefined },
    };
    const all = (await printedTexts(old)).join("\n");

    expect(all).toContain("Forma de pagamento: A_VISTA.");
    expect(all).toContain("Pagamento à vista com vencimento em");
    expect(all).toContain(INSTALLMENT_CLAUSE);
    expect(all).not.toContain(DEADLINE_CLAUSE);
  });

  it("G/P) an old confession keeps printing as it was saved: its own terms and its witnesses", async () => {
    const old = {
      ...STORED_CONFESSION,
      payload: { ...STORED_CONFESSION.payload, testemunha1Nome: "Carlos Lima", testemunha1Cpf: "333.333.333-33", observacoes: "Acordo antigo" },
    };
    const all = (await printedTexts(old)).join("\n");

    expect(all).toContain("Forma de pagamento: PARCELADO.");
    expect(all).toContain("Parcelamento: 3 parcelas de R$");
    expect(all).toContain("Testemunhas:");
    expect(all).toContain("1) Carlos Lima - CPF 333.333.333-33");
    expect(all).toContain("Observações: Acordo antigo");
    expect(all).toContain("Credor/Locador");
    // F) the old renderer keeps its own clause, untouched by the new choice.
    expect(all).toContain(INSTALLMENT_CLAUSE);
    expect(all).not.toContain(DEADLINE_CLAUSE);
    expect(all).not.toContain("CREDOR / EMPRESA");
  });
});

describe("PDF de Multa emitida a partir da pendência (payload real do backend)", () => {
  beforeEach(() => {
    (loadPdfLetterheadData as jest.Mock).mockResolvedValue({ profile: { taxPersonType: "CNPJ", taxCompanyName: "Frotas Exemplo Ltda", taxCnpj: "12345678000190" }, logoDataUrl: null });
    (buildPdfLetterhead as jest.Mock).mockReturnValue([]);
  });

  it("prints the infraction day/time and the due date without any timezone shift", async () => {
    const issued = {
      id: 31,
      type: "MULTA",
      status: "FINAL",
      driverName: "João Silva",
      carPlate: "ONX1A11",
      payload: {
        origem: { tipo: "PENDENCIA", pendencyId: 1 },
        driverName: "João Silva",
        driverCpf: "11111111111",
        carPlate: "ONX1A11",
        carModel: "Onix",
        dataHora: "2026-08-20T10:30",
        local: "Av. Brasil",
        ait: "AIT-1",
        orgao: "DETRAN",
        enquadramento: null,
        valor: 200,
        vencimento: "2026-10-20T12:00",
        responsavelPagamento: "João Silva",
        observacoes: null,
      },
    };
    const all = (await printedTexts(issued)).join("\n");

    // Documents issued before the simplification (dataHora / vencimento with time) print the same day and time.
    expect(all).toContain("ocorrida em 20/08/2026 às 10:30, no local Av. Brasil.");
    expect(all).toMatch(/Dados da infração: AIT AIT-1; Órgão autuador DETRAN; Valor R\$\s?200,00; Vencimento 20\/10\/2026\./);
    expect(all).not.toContain("Enquadramento");
    expect(all).not.toContain("Observações");
  });

  it("B/D) only value and day: no time invented, no empty details, no '-', 'null' or 'undefined'", async () => {
    const minimal = {
      id: 32,
      type: "MULTA",
      status: "FINAL",
      payload: {
        origem: { tipo: "PENDENCIA", pendencyId: 2 },
        driverName: "João Silva",
        carPlate: "ONX1A11",
        dataInfracao: "2026-09-03",
        valor: 130,
        responsavelPagamento: "João Silva",
      },
    };
    const texts = await printedTexts(minimal);
    const all = texts.join("\n");

    expect(all).toContain("vinculada ao veículo ONX1A11, ocorrida em 03/09/2026.");
    expect(all).toMatch(/Dados da infração: Valor R\$\s?130,00\./);
    expect(all).not.toMatch(/ às |00:00|12:00|AIT|Órgão|Enquadramento|Vencimento|no local|Observações|undefined|null/);
    expect(texts.filter((text) => text.includes("Dados da infração") || text.includes("notifica")).join(" ")).not.toContain(" -");
  });

  it("C) a complete fine prints every detail, time included", async () => {
    const complete = {
      id: 33,
      type: "MULTA",
      status: "FINAL",
      payload: {
        origem: { tipo: "PENDENCIA", pendencyId: 3 },
        driverName: "João Silva",
        driverCpf: "11111111111",
        carPlate: "ONX1A11",
        dataInfracao: "2026-08-20",
        horaInfracao: "10:30",
        local: "Av. Brasil",
        ait: "AIT-1",
        orgao: "DETRAN",
        enquadramento: "Art. 218",
        valor: 195,
        vencimento: "2026-10-20",
        responsavelPagamento: "João Silva",
        observacoes: "Pagar até o vencimento",
      },
    };
    const all = (await printedTexts(complete)).join("\n");
    expect(all).toContain("ocorrida em 20/08/2026 às 10:30, no local Av. Brasil.");
    expect(all).toMatch(/AIT AIT-1; Órgão autuador DETRAN; Enquadramento Art\. 218; Valor R\$\s?195,00; Vencimento 20\/10\/2026\./);
    expect(all).toContain("Observações: Pagar até o vencimento");
  });

  it("O) a maintenance share document prints what the maintenance has, nothing invented", async () => {
    const share = {
      id: 34,
      type: "MANUTENCAO_COMPARTILHADA",
      status: "FINAL",
      payload: {
        origem: { tipo: "PENDENCIA", pendencyId: 4 },
        driverName: "João Silva",
        data: "2026-09-10",
        descricao: "Freio, Mão de obra",
        valorTotal: 1200,
        formaDivisao: "Valor atribuído ao motorista",
        parteMotoristaValor: 400,
      },
    };
    const all = (await printedTexts(share)).join("\n");
    expect(all).toContain("Data: 10/09/2026; Descrição: Freio, Mão de obra.");
    expect(all).toMatch(/Valor total: R\$\s?1\.200,00\. Forma de divisão: Valor atribuído ao motorista\. Parcela do motorista: R\$\s?400,00\./);
    expect(all).not.toMatch(/Oficina|Observações|undefined|null/);
  });
});

describe("G) PDFs de Multa e Manutenção Compartilhada antigos continuam sendo gerados", () => {
  beforeEach(() => {
    (loadPdfLetterheadData as jest.Mock).mockResolvedValue({ profile: { taxPersonType: "CNPJ", taxCompanyName: "Frotas Exemplo Ltda" }, logoDataUrl: null });
    (buildPdfLetterhead as jest.Mock).mockReturnValue([]);
  });

  it("an old wizard MULTA (no origin) prints as before", async () => {
    const all = (
      await printedTexts({
        id: 9,
        type: "MULTA",
        status: "FINAL",
        driverName: "João Silva",
        carPlate: "ONX1A11",
        payload: { ait: "123", orgao: "DETRAN", valor: 195, local: "Av. Brasil", vencimento: "20/10/2026", responsavelPagamento: "Motorista" },
      })
    ).join("\n");
    expect(all).toContain("NOTIFICAÇÃO E TERMO DE CIÊNCIA E RESPONSABILIDADE POR INFRAÇÃO DE TRÂNSITO");
    expect(all).toContain("AIT 123; Órgão autuador DETRAN");
    expect(all).toMatch(/Valor R\$\s?195,00/);
  });

  it("an old wizard MANUTENCAO_COMPARTILHADA (no origin) prints as before", async () => {
    const all = (
      await printedTexts({
        id: 10,
        type: "MANUTENCAO_COMPARTILHADA",
        status: "FINAL",
        driverName: "João Silva",
        payload: { valorTotal: 900, parteMotoristaValor: 300, oficina: "Oficina X", descricao: "Freio", formaDivisao: "1/3" },
      })
    ).join("\n");
    expect(all).toContain("ACORDO DE RATEIO DE DESPESAS DE MANUTENÇÃO");
    expect(all).toMatch(/Valor total: R\$\s?900,00/);
    expect(all).toMatch(/Parcela do motorista: R\$\s?300,00/);
  });
});
