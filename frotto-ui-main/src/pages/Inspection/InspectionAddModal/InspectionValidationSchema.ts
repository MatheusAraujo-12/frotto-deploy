import {
  CarBodyDamageModel,
  InspectionExpenseModel,
  InspectionModel,
} from "./../../../constants/CarModels";
import * as Yup from "yup";
import { TEXT } from "../../../constants/texts";
import { DATE_TODAY } from "../../../constants/form";

export const initialInspectionValues = (initialValues: InspectionModel) => {
  return {
    id: initialValues.id || undefined,
    date: initialValues.date || DATE_TODAY,
    driverName: initialValues.driverName || "",
    odometer: initialValues.odometer || 0,
    internalCleaning: initialValues.internalCleaning || "",
    externalCleaning: initialValues.externalCleaning || "",

    leftFrontModel: initialValues.leftFront?.model || "",
    rightFrontModel: initialValues.rightFront?.model || "",
    leftBackModel: initialValues.leftBack?.model || "",
    rightBackModel: initialValues.rightBack?.model || "",
    spareModel: initialValues.spare?.model || "",

    leftFrontIntegrity: initialValues.leftFront?.integrity || "",
    rightFrontIntegrity: initialValues.rightFront?.integrity || "",
    leftBackIntegrity: initialValues.leftBack?.integrity || "",
    rightBackIntegrity: initialValues.rightBack?.integrity || "",
    spareIntegrity: initialValues.spare?.integrity || "",

    leftFrontId: initialValues.leftFront?.id || undefined,
    rightFrontId: initialValues.rightFront?.id || undefined,
    leftBackId: initialValues.leftBack?.id || undefined,
    rightBackId: initialValues.rightBack?.id || undefined,
    spareId: initialValues.spare?.id || undefined,

    expenses: initialValues.expenses || [],
    carBodyDamages: initialValues.carBodyDamages || [],
  };
};

export const inspectionAddValidationSchema = Yup.object().shape({
  id: Yup.number().nullable(),
  date: Yup.string().required(TEXT.requiredField),
  driverName: Yup.string().required(TEXT.requiredField),
  odometer: Yup.number()
    .typeError(TEXT.requiredField)
    .required(TEXT.requiredField),
  internalCleaning: Yup.string().required(TEXT.requiredField),
  externalCleaning: Yup.string().required(TEXT.requiredField),
  leftFrontModel: Yup.string().required(TEXT.requiredField),
  rightFrontModel: Yup.string().required(TEXT.requiredField),
  leftBackModel: Yup.string().required(TEXT.requiredField),
  rightBackModel: Yup.string().required(TEXT.requiredField),
  spareModel: Yup.string().required(TEXT.requiredField),
  leftFrontIntegrity: Yup.string().required(TEXT.requiredField),
  rightFrontIntegrity: Yup.string().required(TEXT.requiredField),
  leftBackIntegrity: Yup.string().required(TEXT.requiredField),
  rightBackIntegrity: Yup.string().required(TEXT.requiredField),
  spareIntegrity: Yup.string().required(TEXT.requiredField),
});

export interface InspectionForm {
  id?: number;
  date?: string;
  driverName?: string;
  odometer?: number;
  internalCleaning?: string;
  externalCleaning?: string;

  leftFrontId?: number;
  rightFrontId?: number;
  leftBackId?: number;
  rightBackId?: number;
  spareId?: number;

  leftFrontModel?: string;
  rightFrontModel?: string;
  leftBackModel?: string;
  rightBackModel?: string;
  spareModel?: string;

  leftFrontIntegrity?: string;
  rightFrontIntegrity?: string;
  leftBackIntegrity?: string;
  rightBackIntegrity?: string;
  spareIntegrity?: string;

  expenses?: InspectionExpenseModel[];
  carBodyDamages?: CarBodyDamageModel[];
}

export const inspectionFormtoInspection = (
  inspectionForm: InspectionForm
): InspectionModel => {
  const inspection: InspectionModel = {};
  inspection.id = inspectionForm.id;
  inspection.date = inspectionForm.date;
  inspection.odometer = inspectionForm.odometer;
  inspection.driverName = inspectionForm.driverName;
  inspection.internalCleaning = inspectionForm.internalCleaning;
  inspection.externalCleaning = inspectionForm.externalCleaning;
  inspection.expenses = inspectionForm.expenses;
  inspection.carBodyDamages = inspectionForm.carBodyDamages;

  inspection.leftBack = {
    id: inspectionForm.leftBackId,
    model: inspectionForm.leftBackModel,
    integrity: inspectionForm.leftBackIntegrity,
  };
  inspection.leftFront = {
    id: inspectionForm.leftFrontId,
    model: inspectionForm.leftFrontModel,
    integrity: inspectionForm.leftFrontIntegrity,
  };
  inspection.rightBack = {
    id: inspectionForm.rightBackId,
    model: inspectionForm.rightBackModel,
    integrity: inspectionForm.rightBackIntegrity,
  };
  inspection.rightFront = {
    id: inspectionForm.rightFrontId,
    model: inspectionForm.rightFrontModel,
    integrity: inspectionForm.rightFrontIntegrity,
  };
  inspection.spare = {
    id: inspectionForm.spareId,
    model: inspectionForm.spareModel,
    integrity: inspectionForm.spareIntegrity,
  };

  inspection.cost = calculateInspectionCost(inspectionForm);
  inspection.score = calculateScore();

  return inspection;
};

export const calculateInspectionCost = (inspectionForm: InspectionForm): number => {
  let finalCost = 0;
  inspectionForm.expenses?.forEach((expense) => {
    if (expense.cost) {
      finalCost += expense.cost;
    }
  });
  return finalCost;
};

export const calculateScore = (): number => {
  return 10;
};
