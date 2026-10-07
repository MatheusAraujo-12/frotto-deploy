import api from "./axios/axios";
import { generateDocumentPdf } from "../pages/Documents/documentPdf";
import {
  canIssuePendencyDocument,
  chargeFine,
  chargeSharedMaintenance,
  issuePendencyDocument,
  listChargeableMaintenances,
  newOperationKey,
  pendencyOriginLabel,
} from "./driverChargeService";

jest.mock("./axios/axios");
jest.mock("../pages/Documents/documentPdf", () => ({ generateDocumentPdf: jest.fn() }));
const mockedApi = api as jest.Mocked<typeof api>;

describe("Multas e manutenção compartilhada como pendências do motorista", () => {
  beforeEach(() => jest.resetAllMocks());

  it("operation keys are unique and in the format the backend accepts", () => {
    const keys = new Set(Array.from({ length: 50 }, () => newOperationKey()));
    expect(keys.size).toBe(50);
    keys.forEach((key) => expect(key).toMatch(/^[A-Za-z0-9_-]{16,80}$/));
  });

  it("a fine is sent with its infraction data and the operation key; a retry reuses the same key", async () => {
    mockedApi.post.mockRejectedValueOnce(new Error("timeout")).mockResolvedValueOnce({ data: { id: 7, originType: "FINE" } });
    const input = {
      amount: 195,
      infractionDate: "2026-08-20",
      infractionTime: "10:30",
      ait: " AIT-1 ",
      agency: "DETRAN",
      location: "",
      dueDate: "2026-10-20",
    };

    await expect(chargeFine(3, input, "op-key-000000000001")).rejects.toThrow("timeout");
    await expect(chargeFine(3, input, "op-key-000000000001")).resolves.toEqual({ id: 7, originType: "FINE" });

    expect(mockedApi.post).toHaveBeenCalledTimes(2);
    expect(mockedApi.post.mock.calls[0]).toEqual(mockedApi.post.mock.calls[1]);
    expect(mockedApi.post.mock.calls[0]).toEqual([
      "/api/pendencies/car-driver/3/fines",
      {
        idempotencyKey: "op-key-000000000001",
        amount: 195,
        infractionDate: "2026-08-20",
        infractionTime: "10:30",
        ait: "AIT-1",
        agency: "DETRAN",
        location: undefined,
        classification: undefined,
        dueDate: "2026-10-20",
        note: undefined,
      },
    ]);
  });

  it("A/B) a minimal fine sends only value and day: no time, no empty details", async () => {
    mockedApi.post.mockResolvedValueOnce({ data: { id: 9 } });
    await chargeFine(3, { amount: 130, infractionDate: "2026-09-03" }, "op-key-000000000009");
    expect(mockedApi.post.mock.calls[0][1]).toEqual({
      idempotencyKey: "op-key-000000000009",
      amount: 130,
      infractionDate: "2026-09-03",
      infractionTime: undefined,
      ait: undefined,
      agency: undefined,
      location: undefined,
      classification: undefined,
      dueDate: undefined,
      note: undefined,
    });
  });

  it("chargeable maintenances come from the contract (its car, its account) with what is left", async () => {
    mockedApi.get.mockResolvedValueOnce({ data: [{ id: 101, cost: 1200, assignedAmount: 800, availableAmount: 400, chargeable: true }] });
    await expect(listChargeableMaintenances(3)).resolves.toHaveLength(1);
    expect(mockedApi.get).toHaveBeenCalledWith("/api/pendencies/car-driver/3/chargeable-maintenances");
  });

  it("a maintenance share sends only the maintenance, the share and the key (the maintenance itself is not touched)", async () => {
    mockedApi.post.mockResolvedValueOnce({ data: { id: 8 } });
    await chargeSharedMaintenance(3, { maintenanceId: 101, amount: 400, note: "Freio" }, "op-key-000000000002");

    expect(mockedApi.post).toHaveBeenCalledWith("/api/pendencies/car-driver/3/shared-maintenance", {
      idempotencyKey: "op-key-000000000002",
      maintenanceId: 101,
      amount: 400,
      note: "Freio",
    });
    expect(mockedApi.put).not.toHaveBeenCalled();
  });

  it("issuing a document: backend issues (or returns) it, the PDF comes from the stored document, nothing else is written", async () => {
    const stored = { id: 31, type: "MULTA", status: "FINAL", payload: { origem: { tipo: "PENDENCIA", pendencyId: 7 } } };
    mockedApi.post.mockResolvedValueOnce({ data: { id: 31, type: "MULTA", status: "FINAL" } }).mockResolvedValueOnce({ data: stored });
    mockedApi.get.mockResolvedValueOnce({ data: stored });

    await expect(issuePendencyDocument(7)).resolves.toBe(stored);

    expect(mockedApi.post.mock.calls.map((call) => call[0])).toEqual(["/api/pendencies/7/document", "/api/documents/31/generate-pdf"]);
    expect(mockedApi.get).toHaveBeenCalledWith("/api/documents/31");
    expect(generateDocumentPdf).toHaveBeenCalledWith(stored);
    expect(mockedApi.put).not.toHaveBeenCalled();
    expect(mockedApi.delete).not.toHaveBeenCalled();
  });

  it("the origin shown comes only from the structure, never from the name", () => {
    expect(pendencyOriginLabel({ originType: "FINE", name: "Qualquer nome" })).toBe("Multa");
    expect(pendencyOriginLabel({ originType: "SHARED_MAINTENANCE" })).toBe("Manutenção compartilhada");
    expect(pendencyOriginLabel({ name: "Multa AIT 123" })).toBeNull();
    expect(pendencyOriginLabel({ name: "Manutenção compartilhada" })).toBeNull();

    expect(canIssuePendencyDocument({ originType: "FINE" })).toBe(true);
    expect(canIssuePendencyDocument({ originType: null, originDocumentId: 92901 })).toBe(true);
    expect(canIssuePendencyDocument({ name: "Multa AIT 123" })).toBe(false);
  });
});
