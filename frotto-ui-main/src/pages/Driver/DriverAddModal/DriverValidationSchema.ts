import { CarDriverModel } from "../../../constants/CarModels";
import * as Yup from "yup";
import { TEXT } from "../../../constants/texts";
import { DATE_TODAY } from "../../../constants/form";
import { isValidCPF } from "../../../constants/validations";

export const initialDriverValues = (initialValues: CarDriverModel) => {
  return {
    id: initialValues.id || undefined,
    startDate: initialValues.startDate || DATE_TODAY,
    warranty: initialValues.warranty || 0,
    concluded: initialValues.concluded || false,
    endDate: initialValues.endDate || DATE_TODAY,
    score: initialValues.score || 1,
    debt: initialValues.debt || 0,

    driverId: initialValues.driver?.id || undefined,
    driverName: initialValues.driver?.name || "",
    driverCpf: initialValues.driver?.cpf || "",
    driverEmail: initialValues.driver?.email || "",
    driverContact: initialValues.driver?.contact || "",
    driverEmergencyContact: initialValues.driver?.emergencyContact || "",
    driverEmergencyContactSecond:
      initialValues.driver?.emergencyContactSecond || "",
    driverDocumentDriverLicense:
      initialValues.driver?.documentDriverLicense || "",
    driverDocumentDriverRegister:
      initialValues.driver?.documentDriverRegister || "",
    driverPublicScore: initialValues.driver?.publicScore || "",

    driverAddressId: initialValues.driver?.address?.id || undefined,
    driverAddressCountry: initialValues.driver?.address?.country || "Brasil",
    driverAddressZip: initialValues.driver?.address?.zip || "70000000",
    driverAddressState: initialValues.driver?.address?.state || "DF",
    driverAddressCity: initialValues.driver?.address?.city || "Brasília",
    driverAddressDistrict: initialValues.driver?.address?.district || "",
    driverAddressName: initialValues.driver?.address?.name || "",
  };
};

export const driverAddValidationSchema = Yup.object().shape({
  id: Yup.number().nullable(),
  startDate: Yup.string().required(TEXT.requiredField),
  endDate: Yup.string(),
  warranty: Yup.number()
    .typeError(TEXT.requiredField)
    .required(TEXT.requiredField),
  score: Yup.number().typeError(TEXT.requiredField),
  debt: Yup.number().typeError(TEXT.requiredField),
  concluded: Yup.boolean(),

  driverId: Yup.number().nullable(),
  driverName: Yup.string().required(TEXT.requiredField),
  driverCpf: Yup.string()
    .required(TEXT.requiredField)
    .test({
      name: "isValidCpf",
      message: TEXT.invalidCPF,
      test: (value) => isValidCPF(value),
    }),
  driverContact: Yup.string().required(TEXT.requiredField),
  driverEmail: Yup.string().email(TEXT.invalidEmail),
  driverEmergencyContact: Yup.string(),
  driverEmergencyContactSecond: Yup.string(),
  driverDocumentDriverLicense: Yup.string(),
  driverDocumentDriverRegister: Yup.string(),
  driverPublicScore: Yup.string(),

  driverAddressId: Yup.number().nullable(),
  driverAddressCountry: Yup.string().required(TEXT.requiredField),
  driverAddressZip: Yup.string().required(TEXT.requiredField),
  driverAddressState: Yup.string().required(TEXT.requiredField),
  driverAddressCity: Yup.string().required(TEXT.requiredField),
  driverAddressDistrict: Yup.string().required(TEXT.requiredField),
  driverAddressName: Yup.string().required(TEXT.requiredField),
});

export interface DriverForm {
  id?: number;
  startDate?: string;
  warranty?: number;
  concluded?: boolean;
  endDate?: string;
  score?: number;
  debt?: number;

  driverId?: number;
  driverName?: string;
  driverCpf?: string;
  driverEmail?: string;
  driverContact?: string;
  driverEmergencyContact?: string;
  driverEmergencyContactSecond?: string;
  driverDocumentDriverLicense?: string;
  driverDocumentDriverRegister?: string;
  driverPublicScore?: string;

  driverAddressId?: number;
  driverAddressCountry?: string;
  driverAddressZip?: string;
  driverAddressState?: string;
  driverAddressCity?: string;
  driverAddressDistrict?: string;
  driverAddressName?: string;
}

export const driverFormtoDriver = (driverForm: DriverForm): CarDriverModel => {
  const driver: CarDriverModel = {};

  driver.id = driverForm.id;
  driver.startDate = driverForm.startDate;
  driver.warranty = driverForm.warranty;
  driver.concluded = driverForm.concluded;
  if (driverForm.concluded) {
    driver.endDate = driverForm.endDate;
    driver.score = driverForm.score;
    driver.debt = driverForm.debt;
  }

  driver.driver = {
    id: driverForm.driverId,
    name: driverForm.driverName,
    cpf: driverForm.driverCpf,
    email: driverForm.driverEmail,
    contact: driverForm.driverContact,
    emergencyContact: driverForm.driverEmergencyContact,
    emergencyContactSecond: driverForm.driverEmergencyContactSecond,
    documentDriverLicense: driverForm.driverDocumentDriverLicense,
    documentDriverRegister: driverForm.driverDocumentDriverRegister,
    publicScore: driverForm.driverPublicScore,

    address: {
      id: driverForm.driverAddressId,
      country: driverForm.driverAddressCountry,
      zip: driverForm.driverAddressZip,
      state: driverForm.driverAddressState,
      city: driverForm.driverAddressCity,
      district: driverForm.driverAddressDistrict,
      name: driverForm.driverAddressName,
    },
  };

  return driver;
};
