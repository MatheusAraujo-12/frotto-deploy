import { CarDriverModel } from "../../../constants/CarModels";
import * as Yup from "yup";
import { DATE_TODAY } from "../../../constants/form";

export const initialDriverValues = (initialValues: CarDriverModel) => {
  return {
    id: initialValues.id || undefined,
    startDate: initialValues.startDate || DATE_TODAY,
    warranty: initialValues.warranty || 0,
    contractNumber: initialValues.contractNumber || "",
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
    driverPublicScore:
      initialValues.driver?.publicScore !== undefined &&
      initialValues.driver?.publicScore !== null
        ? String(initialValues.driver.publicScore)
        : "",

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
  id: optionalNumber(),
  startDate: optionalString(),
  endDate: optionalString(),
  warranty: optionalNumber(),
  contractNumber: optionalString(),
  score: optionalNumber(),
  debt: optionalNumber(),
  concluded: Yup.boolean().nullable().notRequired(),

  driverId: optionalNumber(),
  driverName: optionalString(),
  driverCpf: optionalString(),
  driverContact: optionalString(),
  driverEmail: optionalString(),
  driverEmergencyContact: optionalString(),
  driverEmergencyContactSecond: optionalString(),
  driverDocumentDriverLicense: optionalString(),
  driverDocumentDriverRegister: optionalString(),
  driverPublicScore: optionalString(),

  driverAddressId: optionalNumber(),
  driverAddressCountry: optionalString(),
  driverAddressZip: optionalString(),
  driverAddressState: optionalString(),
  driverAddressCity: optionalString(),
  driverAddressDistrict: optionalString(),
  driverAddressName: optionalString(),
});

export interface DriverForm {
  id?: number;
  startDate?: string;
  warranty?: number;
  contractNumber?: string;
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
  driverPublicScore?: string | number;

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
  driver.contractNumber = driverForm.contractNumber?.trim() || undefined;
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
    publicScore: normalizeOptionalNumber(driverForm.driverPublicScore),

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

function normalizeOptionalNumber(value?: string | number): number | undefined {
  if (value === undefined || value === null || `${value}`.trim() === "") {
    return undefined;
  }

  const parsedValue = Number(value);
  return Number.isFinite(parsedValue) ? parsedValue : undefined;
}

function optionalString() {
  return Yup.string().nullable().notRequired();
}

function optionalNumber() {
  return Yup.number()
    .transform((value, originalValue) => {
      if (originalValue === "" || originalValue === null || originalValue === undefined) {
        return undefined;
      }

      return Number.isNaN(value) ? undefined : value;
    })
    .nullable()
    .notRequired();
}
