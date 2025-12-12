import { Url } from "url";

export interface CarModel {
  id?: number;
  name?: string;
  model?: string;
  color?: string;
  plate?: string;
  odometer?: number;
  administrationFee?: number;
  year?: number;
  group?: string;
  initialValue?: number;
  active?: boolean;
  driverName?:string;
}

export interface CarBodyDamageModel {
  id?: number;
  date?: string;
  responsible?: string;
  part?: string;
  imagePath?: string;
  imagePath2?: string;
  cost?: number;
  resolved?: boolean;
  imageTempUrl?: Url;
}

export interface MaintenanceServiceModel {
  id?: number;
  name?: string;
  cost?: number;
}

export interface MaintenanceModel {
  id?: number;
  date?: string;
  local?: string;
  odometer?: number;
  cost?: number;
  services?: MaintenanceServiceModel[];
}

export interface TireModel {
  id?: number;
  model?: string;
  integrity?: string;
}

export interface InspectionExpenseModel {
  id?: number;
  name?: string;
  ammount?: number;
  cost?: number;
}

export interface InspectionModel {
  id?: number;
  date?: string;
  driverName?: string;
  odometer?: number;
  internalCleaning?: string;
  externalCleaning?: string;
  comment?: string;
  score?: number;
  cost?: number;
  leftFront?: TireModel;
  rightFront?: TireModel;
  leftBack?: TireModel;
  rightBack?: TireModel;
  spare?: TireModel;
  expenses?: InspectionExpenseModel[];
  carBodyDamages?: CarBodyDamageModel[];
}

export interface AdressModel {
  id?: number;
  country?: string;
  zip?: string;
  state?: string;
  city?: string;
  district?: string;
  publicScore?: string;
  name?: string;
}

export interface DriverModel {
  id?: number;
  name?: string;
  cpf?: string;
  email?: string;
  contact?: string;
  emergencyContact?: string;
  emergencyContactSecond?: string;
  documentDriverLicense?: string;
  documentDriverRegister?: string;
  publicScore?: string;
  averageKm?: number;
  averageInspectioScore?: number;
  averageDriverCarScore?: number;
  address?: AdressModel;
}
export interface CarDriverModel {
  id?: number;
  startDate?: string;
  endDate?: string;
  warranty?: number;
  score?: number;
  debt?: number;
  concluded?: boolean;
  driver?: DriverModel;
}

export interface IncomeModel {
  id?: number;
  date?: string;
  name?: string;
  cost?: number;
}

export interface CarExpenseModel {
  id?: number;
  date?: string;
  name?: string;
  cost?: number;
}

export interface ReminderModel {
  id?: number;
  message?: string;
}

export interface DriverPendencyModel {
  id?: number;
  date?: string;
  name?: string;
  cost?: number;
}
