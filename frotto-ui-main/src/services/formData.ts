import { CarBodyDamageModel } from "../constants/CarModels";

export const newFormDataFromBodyDamage = (
  newCarDamage: CarBodyDamageModel,
  file: File | undefined,
  file2: File | undefined
) => {
  let formData = new FormData();
  if (newCarDamage.id) {
    formData.append("id", newCarDamage.id.toString());
  }
  if (newCarDamage.date) {
    formData.append("date", newCarDamage.date);
  }
  if (newCarDamage.responsible) {
    formData.append("responsible", newCarDamage.responsible.toString());
  }
  if (newCarDamage.part) {
    formData.append("part", newCarDamage.part.toString());
  }
  if (newCarDamage.cost) {
    formData.append("cost", newCarDamage.cost.toString());
  }
  if (newCarDamage.resolved !== undefined) {
    formData.append("resolved", newCarDamage.resolved.toString());
  }
  if (file) {
    formData.append("file", file);
  }
  if (file2) {
    formData.append("file2", file2);
  }
  return formData;
};
