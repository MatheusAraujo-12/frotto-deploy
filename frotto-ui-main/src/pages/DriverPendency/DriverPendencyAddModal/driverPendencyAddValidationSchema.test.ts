import { PENDENCY_COST_REQUIRED, driverPendencyAddValidationSchema } from "./driverPendencyAddValidationSchema";

const base = { name: "Dano", date: "2026-10-07", note: "", paidAmount: 0, paymentMethod: "" };

describe("Pendência comum: valor", () => {
  it("A) a new pendency of 0 (or less) is invalid with a clear message", async () => {
    await expect(driverPendencyAddValidationSchema.validate({ ...base, cost: 0 })).rejects.toThrow(PENDENCY_COST_REQUIRED);
    await expect(driverPendencyAddValidationSchema.validate({ ...base, cost: -1 })).rejects.toThrow();
  });

  it("B) a new pendency with a value is valid", async () => {
    await expect(driverPendencyAddValidationSchema.validate({ ...base, cost: 150 })).resolves.toBeTruthy();
  });

  it("E) an existing (legacy) pendency of 0 can still be edited", async () => {
    await expect(driverPendencyAddValidationSchema.validate({ ...base, id: 9, cost: 0 })).resolves.toBeTruthy();
  });
});
