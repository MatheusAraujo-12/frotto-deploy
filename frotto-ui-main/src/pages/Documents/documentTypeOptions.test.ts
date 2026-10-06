import { DOCUMENT_TYPES, wizardTypeOptions } from "../../constants/DocumentModels";

describe("Documentos: Multa e Manutenção Compartilhada nascem em Pendências", () => {
  const values = (options: Array<{ value: string }>) => options.map((option) => option.value);

  it("H/I) Novo Documento no longer offers Multa nor Manutenção Compartilhada", () => {
    expect(values(wizardTypeOptions(false, ""))).toEqual(["RECIBO_ALUGUEL", "CONFISSAO_DIVIDA", "ENTREGA_DEVOLUCAO_CHECKLIST"]);
    // Even if the type was somehow preselected on a new document.
    expect(values(wizardTypeOptions(false, "MULTA"))).not.toContain("MULTA");
  });

  it("P) an old draft of a fine / shared maintenance keeps its own type while being edited (no data lost)", () => {
    expect(values(wizardTypeOptions(true, "MULTA"))).toEqual(["MULTA", "RECIBO_ALUGUEL", "CONFISSAO_DIVIDA", "ENTREGA_DEVOLUCAO_CHECKLIST"]);
    expect(values(wizardTypeOptions(true, "MANUTENCAO_COMPARTILHADA"))).toContain("MANUTENCAO_COMPARTILHADA");
    expect(values(wizardTypeOptions(true, "RECIBO_ALUGUEL"))).not.toContain("MULTA");
  });

  it("E/F) the history and its filter still know every type, the old ones included", () => {
    expect(values(DOCUMENT_TYPES)).toEqual(expect.arrayContaining(["MULTA", "MANUTENCAO_COMPARTILHADA"]));
  });
});
