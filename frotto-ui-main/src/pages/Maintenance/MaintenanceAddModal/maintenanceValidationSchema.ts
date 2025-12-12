import { MaintenanceModel } from "./../../../constants/CarModels";
import * as Yup from "yup";
import { TEXT } from "../../../constants/texts";
import { DATE_TODAY } from "../../../constants/form";

export const initialMaintenanceValues = (initialValues: MaintenanceModel) => {
  return {
    id: initialValues.id || undefined,
    date: initialValues.date || DATE_TODAY,
    odometer: initialValues.odometer || 0,
    local: initialValues.local || "",
    services: initialValues.services || [],
  };
};

export const maintenanceAddValidationSchema = Yup.object().shape({
  id: Yup.number().nullable(),
  date: Yup.string().required(TEXT.requiredField),
  odometer: Yup.number()
    .typeError(TEXT.requiredField)
    .required(TEXT.requiredField),
  local: Yup.string().required(TEXT.requiredField),
});

export const calculateMaintenanceCost = (maintenance: MaintenanceModel): number => {
  let finalCost = 0;
  maintenance.services?.forEach((service) => {
    if (service.cost) {
      finalCost += service.cost;
    }
  });
  return finalCost;
};
