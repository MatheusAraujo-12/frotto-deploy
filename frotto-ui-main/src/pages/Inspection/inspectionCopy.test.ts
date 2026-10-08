import { inspectionCopyFrom } from "./inspectionCopy";

describe("Inspeção Avulsa a partir da última inspeção", () => {
  const FROM_CHECKLIST = {
    id: 6,
    date: "2026-10-07",
    driverName: "João",
    odometer: 15000,
    internalCleaning: "Boa",
    originDocumentId: 9,
    driverCarId: 3,
    fuelLevel: "HALF",
    leftFront: { id: 40, model: "Pirelli", integrity: "70-90%" },
    expenses: [{ id: 1, name: "Lavagem", cost: 10 }],
    carBodyDamages: [{ id: 2 }],
  };

  it("never carries the identity nor the checklist trace of the last inspection", () => {
    const copy = inspectionCopyFrom(FROM_CHECKLIST as any);

    expect(copy).not.toHaveProperty("originDocumentId");
    expect(copy).not.toHaveProperty("driverCarId");
    expect(copy).not.toHaveProperty("fuelLevel");
    expect(copy.id).toBeUndefined();
    expect(copy.date).toBeUndefined();
    expect(copy.leftFront).toEqual({ id: undefined, model: "Pirelli", integrity: "70-90%" });
    expect(copy.expenses).toEqual([]);
    expect(copy.carBodyDamages).toEqual([]);
  });

  it("keeps what a new inspection is prefilled with (odometer, driver, cleaning)", () => {
    const copy = inspectionCopyFrom(FROM_CHECKLIST as any);
    expect(copy).toMatchObject({ odometer: 15000, driverName: "João", internalCleaning: "Boa" });
    expect(inspectionCopyFrom(undefined)).toEqual({});
  });
});
