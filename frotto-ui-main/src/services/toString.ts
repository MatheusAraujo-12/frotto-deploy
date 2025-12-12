import { TEXT } from "../constants/texts";
import { MaintenanceServiceModel } from "./../constants/CarModels";
export const servicesToString = (
  services: MaintenanceServiceModel[] | undefined
): string => {
  if (!services || services.length === 0) {
    return TEXT.noServices;
  }
  let stringServices = "";
  for (let i = 0; i < services.length; i++) {
    if (i !== 0) {
      stringServices = stringServices + ", ";
    }
    stringServices = stringServices + services[i].name;
  }
  return stringServices;
};
