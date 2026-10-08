import { InspectionModel } from "../../constants/CarModels";

/**
 * A new Inspeção Avulsa prefilled from the car's last inspection (odometer, cleaning, tires): never the identity of
 * that record nor the trace of the checklist it may have come from (origin document, contract, fuel), so the new
 * inspection is an ordinary, editable one.
 */
export function inspectionCopyFrom(inspection?: InspectionModel): InspectionModel {
  if (!inspection) {
    return {};
  }
  const { originDocumentId: _origin, driverCarId: _contract, fuelLevel: _fuel, ...copy } = inspection;
  return {
    ...copy,
    id: undefined,
    date: undefined,
    leftBack: { ...inspection.leftBack, id: undefined },
    rightBack: { ...inspection.rightBack, id: undefined },
    leftFront: { ...inspection.leftFront, id: undefined },
    rightFront: { ...inspection.rightFront, id: undefined },
    spare: { ...inspection.spare, id: undefined },
    carBodyDamages: [],
    expenses: [],
  };
}
