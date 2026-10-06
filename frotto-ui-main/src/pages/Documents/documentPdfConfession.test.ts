import { resolveConfissaoDebtItems } from "./documentPdf";
import { STORED_CONFESSION } from "../../services/debtConfessionContract.fixtures";

jest.mock("../../services/axios/axios");

describe("PDF da Confissão de Dívida - itens", () => {
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
});
