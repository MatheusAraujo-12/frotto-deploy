import * as pdfMake from "pdfmake/build/pdfmake";
import { createDocumentPdfBlob } from "./documentPdf";
import { buildPdfLetterhead, loadPdfLetterheadData } from "../../services/pdfLetterhead";
import {
  formatChecklistDate,
  parseChecklistKm,
  structuredChecklistErrors,
} from "./checklistUtils";

jest.mock("../../services/axios/axios");
jest.mock("pdfmake/build/pdfmake", () => ({ createPdf: jest.fn() }));
jest.mock("../../services/pdfLetterhead", () => ({
  ...jest.requireActual("../../services/pdfLetterhead"),
  loadPdfLetterheadData: jest.fn(),
  buildPdfLetterhead: jest.fn(),
}));

function textsOf(node: any): string[] {
  if (node === null || node === undefined) return [];
  if (typeof node === "string" || typeof node === "number") return [`${node}`];
  if (Array.isArray(node)) return node.flatMap(textsOf);
  if (typeof node !== "object") return [];
  return [...textsOf(node.text), ...textsOf(node.ul), ...textsOf(node.stack), ...textsOf(node.columns), ...textsOf(node.table?.body)];
}

async function printed(document: any): Promise<string> {
  let definition: any;
  (pdfMake.createPdf as jest.Mock).mockImplementation((value: any) => {
    definition = value;
    return { getBlob: (callback: (blob: Blob) => void) => callback(new Blob()) };
  });
  await createDocumentPdfBlob(document);
  return textsOf(definition.content).join("\n");
}

const base = { id: 1, type: "ENTREGA_DEVOLUCAO_CHECKLIST", status: "FINAL", driverId: 1, driverName: "João", carPlate: "ABC1D23" };

describe("PDF do checklist de Entrega/Devolução", () => {
  beforeEach(() => {
    (loadPdfLetterheadData as jest.Mock).mockResolvedValue({ profile: { taxPersonType: "CPF", taxName: "Locadora" }, logoDataUrl: null });
    (buildPdfLetterhead as jest.Mock).mockReturnValue([]);
  });

  it("a structured checklist prints the operation, the structured date / time and the fuel label", async () => {
    const text = await printed({
      ...base,
      checklistType: "DEVOLUCAO",
      payload: { tipo: "DEVOLUCAO", dataVistoria: "2026-10-07", horaVistoria: "08:30", km: 15000, combustivel: "THREE_QUARTERS" },
    });
    expect(text).toMatch(/registram a DEVOLUÇÃO do veículo ABC1D23 em 07\/10\/2026 às 08:30\./);
    expect(text).toMatch(/Quilometragem: 15000 km\. Combustível: 3\/4\./);
  });

  it("the new form prints the cleaning, the tires checked per position and the confirmed damages", async () => {
    const text = await printed({
      ...base,
      checklistType: "ENTREGA",
      payload: {
        dataVistoria: "2026-10-07",
        km: 15000,
        combustivel: "HALF",
        limpezaInterna: "Aceitavel",
        limpezaExterna: "Ótima",
        tires: {
          source: "CHECKLIST",
          positions: [
            { posicao: "Dianteiro esquerdo", marca: "PIRELLI", estado: "70-90%" },
            { posicao: "Traseiro direito" },
            { posicao: "Estepe", estado: "30-50%" },
          ],
        },
        existingDamages: [{ id: 11, part: "Porta dianteira", date: "2026-09-01" }],
      },
    });
    expect(text).toContain("Limpeza interna: Aceitável. Limpeza externa: Ótima.");
    expect(text).toContain("Integridade");
    expect(text).toContain("Dianteiro esquerdo\nPIRELLI\n70-90%");
    expect(text).toContain("Estepe\n-\n30-50%");
    expect(text).not.toContain("Traseiro direito"); // a position not checked is not printed
    expect(text).not.toContain("Estado: ");
    expect(text).toContain("Danos já registrados, confirmados na vistoria:\nPorta dianteira (01/09/2026)");
  });

  it("an old checklist keeps its tire block (marca / estado Bom - Meia vida - Ruim) as before", async () => {
    const text = await printed({
      ...base,
      checklistType: null,
      payload: { tipo: "ENTREGA", dataHora: "ontem", tires: { marca: "Pirelli", estado: "MEIA_VIDA", observacoes: "ok" } },
    });
    expect(text).toContain("Marca: Pirelli\nEstado: Meia vida\nObservações: ok");
    expect(text).not.toMatch(/Limpeza interna/);
  });

  it("an old checklist (no checklistType) prints its free-text fields exactly as before", async () => {
    const text = await printed({
      ...base,
      checklistType: null,
      payload: { tipo: "ENTREGA", dataHora: "ontem 10h", km: "12.000", combustivel: "meio tanque" },
    });
    expect(text).toMatch(/registram a ENTREGA do veículo ABC1D23 em ontem 10h\./);
    expect(text).toMatch(/Quilometragem: 12\.000 km\. Combustível: meio tanque\./);
  });
});

describe("checklistUtils - regras do checklist estruturado", () => {
  it("km: digits with at most one decimal (dot or comma); anything ambiguous is refused", () => {
    expect(parseChecklistKm("15000")).toBe(15000);
    expect(parseChecklistKm("15000,5")).toBe(15000.5);
    expect(parseChecklistKm(120)).toBe(120);
    expect(parseChecklistKm("15.000")).toBeNull();
    expect(parseChecklistKm("-1")).toBeNull();
    expect(parseChecklistKm("")).toBeNull();
    expect(parseChecklistKm("12 km")).toBeNull();
  });

  it("the same requirements as the backend, in form order", () => {
    expect(structuredChecklistErrors("", {}, null)).toEqual([
      "Informe se o checklist é de Entrega ou de Devolução.",
      "Informe a data da vistoria.",
      "Informe o KM do veículo (somente números).",
      "Selecione o nível de combustível.",
      "Informe a limpeza interna do veículo.",
      "Informe a limpeza externa do veículo.",
    ]);
    expect(structuredChecklistErrors("DEVOLUCAO", { dataVistoria: "2026-10-07", km: 1, combustivel: "FULL", limpezaInterna: "Boa", limpezaExterna: "Ótima" }, null)).toEqual([
      "Selecione o vínculo que está sendo devolvido.",
    ]);
    expect(structuredChecklistErrors("ENTREGA", { dataVistoria: "07/10/2026", km: 1, combustivel: "FULL", limpezaInterna: "Boa", limpezaExterna: "Ótima" }, null)).toEqual([
      "Informe a data da vistoria.",
    ]);
    expect(structuredChecklistErrors("ENTREGA", { dataVistoria: "2026-10-07", km: "0", combustivel: "EMPTY", limpezaInterna: "Aceitavel", limpezaExterna: "Péssima" }, null)).toEqual([]);
  });

  it("formats the structured date with the optional time", () => {
    expect(formatChecklistDate("2026-10-07")).toBe("07/10/2026");
    expect(formatChecklistDate("2026-10-07", "08:30")).toBe("07/10/2026 às 08:30");
    expect(formatChecklistDate("ontem")).toBe("");
  });
});
