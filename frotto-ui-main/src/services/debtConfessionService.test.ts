import api from "./axios/axios";
import { generateDocumentPdf } from "../pages/Documents/documentPdf";
import {
  DIFFERENT_DEBTORS_MESSAGE,
  INVALID_TERMS_MESSAGE,
  WITHOUT_DEBTOR_MESSAGE,
  buildConfessionPayload,
  generateDebtConfession,
  isConfessionEligible,
  isOutdatedConfession,
  previewDebtConfession,
  selectionBlockReason,
} from "./debtConfessionService";
import {
  ERROR_DIFFERENT_DEBTORS_400,
  ERROR_FOREIGN_404,
  ERROR_NOT_OPEN_400,
  ERROR_TERMS_INVALID_400,
  ERROR_OUTDATED_409,
  JOAO_PENDENCIES,
  LEGACY_WITHOUT_DEBTOR,
  PREVIEW_MULTI_CAR,
  STORED_CONFESSION,
} from "./debtConfessionContract.fixtures";
import { apiError } from "./driverAssignmentContract.fixtures";

jest.mock("./axios/axios");
jest.mock("../pages/Documents/documentPdf", () => ({ generateDocumentPdf: jest.fn() }));
const mockedApi = api as jest.Mocked<typeof api>;
const mockedPdf = generateDocumentPdf as jest.Mock;

const TERMS_PIX = { formaPagamento: "PIX" as const, prazoPagamento: "2026-10-20" };
const byId = (id: number) => JOAO_PENDENCIES.find((pendency) => pendency.id === id)! as any;

describe("Confissão de Dívida a partir de Pendências - contrato real", () => {
  beforeEach(() => jest.resetAllMocks());

  it("preview sends only the selected ids", async () => {
    mockedApi.post.mockResolvedValueOnce({ data: PREVIEW_MULTI_CAR });

    await expect(previewDebtConfession([3, 1, 2])).resolves.toBe(PREVIEW_MULTI_CAR);
    expect(mockedApi.post).toHaveBeenCalledWith("/api/pendencies/confissao-divida/preview", { pendencyIds: [3, 1, 2] });
  });

  it("payload follows the PENDENCIAS origin contract with the backend values and the agreement terms, nothing recomputed", () => {
    const payload = buildConfessionPayload(PREVIEW_MULTI_CAR as any, {
      formaPagamento: "PARCELADO",
      prazoPagamento: "2027-02-20",
      parcelasQtd: 5,
      primeiroVencimento: "2026-10-20",
      observacao: "  Em caso de atraso, o acordo deverá ser renegociado.  ",
    });

    expect(payload).toEqual({
      origem: { tipo: "PENDENCIAS", driverId: 1, pendencyIds: [1, 2, 3] },
      itensDaDivida: [
        { sourcePendencyId: 1, valorItem: 200 },
        { sourcePendencyId: 2, valorItem: 350 },
        { sourcePendencyId: 3, valorItem: 600 },
      ],
      valorTotal: 1150,
      condicoesPagamento: {
        formaPagamento: "PARCELADO",
        prazoPagamento: "2027-02-20",
        parcelasQtd: 5,
        primeiroVencimento: "2026-10-20",
        observacao: "Em caso de atraso, o acordo deverá ser renegociado.",
      },
    });
  });

  it("new confessions carry no witnesses and none of the old flat terms", () => {
    const payload = buildConfessionPayload(PREVIEW_MULTI_CAR as any, TERMS_PIX);
    expect(Object.keys(payload).sort()).toEqual(["condicoesPagamento", "itensDaDivida", "origem", "valorTotal"]);
    expect(payload.condicoesPagamento).toEqual({ formaPagamento: "PIX", prazoPagamento: "2026-10-20" });
  });

  it("invalid terms: nothing is created", async () => {
    await expect(generateDebtConfession(PREVIEW_MULTI_CAR as any, { formaPagamento: "PIX" })).rejects.toThrow(INVALID_TERMS_MESSAGE);
    expect(mockedApi.post).not.toHaveBeenCalled();
  });

  it("generation: draft (debtor, no single car) -> finalize -> PDF of the stored document -> mark; no pendency call", async () => {
    mockedApi.post
      .mockResolvedValueOnce({ data: { id: 1, status: "DRAFT" } })
      .mockResolvedValueOnce({ data: { id: 1, status: "FINAL" } })
      .mockResolvedValueOnce({ data: { id: 1, status: "FINAL" } });
    mockedApi.get.mockResolvedValueOnce({ data: STORED_CONFESSION });

    await expect(generateDebtConfession(PREVIEW_MULTI_CAR as any, TERMS_PIX)).resolves.toBe(STORED_CONFESSION);

    expect(mockedApi.post.mock.calls.map((call) => call[0])).toEqual([
      "/api/documents",
      "/api/documents/1/finalize",
      "/api/documents/1/generate-pdf",
    ]);
    const draft = mockedApi.post.mock.calls[0][1] as any;
    expect(draft).toMatchObject({ type: "CONFISSAO_DIVIDA", status: "DRAFT", driverId: 1, carId: null });
    expect(draft.payload.condicoesPagamento).toEqual({ formaPagamento: "PIX", prazoPagamento: "2026-10-20" });
    expect(mockedApi.get).toHaveBeenCalledWith("/api/documents/1");
    expect(mockedPdf).toHaveBeenCalledWith(STORED_CONFESSION);
    expect(mockedApi.put).not.toHaveBeenCalled();
    expect([...mockedApi.post.mock.calls, ...mockedApi.get.mock.calls].some((call) => `${call[0]}`.includes("/pendencies"))).toBe(false);
  });

  it("finalization refused (pendency changed): the draft is removed, no PDF, the error is rethrown", async () => {
    const outdated = apiError(409, ERROR_OUTDATED_409);
    mockedApi.post.mockResolvedValueOnce({ data: { id: 9, status: "DRAFT" } }).mockRejectedValueOnce(outdated);
    mockedApi.delete.mockResolvedValueOnce({ data: null });

    await expect(generateDebtConfession(PREVIEW_MULTI_CAR as any, TERMS_PIX)).rejects.toBe(outdated);
    expect(mockedApi.delete).toHaveBeenCalledWith("/api/documents/9");
    expect(mockedPdf).not.toHaveBeenCalled();
  });

  it("creation refused: nothing else is called", async () => {
    const outdated = apiError(409, ERROR_OUTDATED_409);
    mockedApi.post.mockRejectedValueOnce(outdated);

    await expect(generateDebtConfession(PREVIEW_MULTI_CAR as any, TERMS_PIX)).rejects.toBe(outdated);
    expect(mockedApi.post).toHaveBeenCalledTimes(1);
    expect(mockedApi.delete).not.toHaveBeenCalled();
    expect(mockedPdf).not.toHaveBeenCalled();
  });

  it("outdated errors of the real backend are recognized", () => {
    expect(isOutdatedConfession(apiError(409, ERROR_OUTDATED_409))).toBe(true);
    expect(isOutdatedConfession(apiError(400, ERROR_NOT_OPEN_400))).toBe(true);
    expect(isOutdatedConfession(apiError(404, ERROR_FOREIGN_404))).toBe(true);
    expect(isOutdatedConfession(apiError(400, ERROR_DIFFERENT_DEBTORS_400))).toBe(false);
    expect(isOutdatedConfession(apiError(400, ERROR_TERMS_INVALID_400))).toBe(false);
  });

  describe("selection (the backend checks again)", () => {
    it("open debts with a debtor are eligible; paid ones and rows without debtor are not", () => {
      expect(isConfessionEligible(byId(1))).toBe(true);
      expect(isConfessionEligible(byId(3))).toBe(true); // partially paid: balance 600
      expect(isConfessionEligible(byId(4))).toBe(false); // paid
      expect(isConfessionEligible(LEGACY_WITHOUT_DEBTOR as any)).toBe(false);
    });

    it("explains why a debt cannot join the current selection", () => {
      expect(selectionBlockReason(byId(1), null)).toBeNull();
      expect(selectionBlockReason(byId(2), 1)).toBeNull(); // same debtor, another car
      expect(selectionBlockReason({ ...byId(1), debtorDriverId: 2 }, 1)).toBe(DIFFERENT_DEBTORS_MESSAGE);
      expect(selectionBlockReason(LEGACY_WITHOUT_DEBTOR as any, null)).toBe(WITHOUT_DEBTOR_MESSAGE);
      expect(selectionBlockReason(byId(4), null)).toMatch(/saldo em aberto/);
    });
  });
});
