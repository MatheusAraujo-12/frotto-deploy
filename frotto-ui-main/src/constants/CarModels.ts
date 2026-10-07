import { Url } from "url";

export type CommissionType = "PERCENT_PROFIT" | "FIXED";
export type CarAdminStatus =
  | "ATIVO"
  | "RETIRADO"
  | "A_VENDA"
  | "MANUTENCAO"
  | "BLOQUEADO";

export interface CommissionConfig {
  commissionType?: CommissionType;
  commissionPercent?: number;
  commissionFixed?: number;
  administrationFee?: number;
}

export interface CarModel extends CommissionConfig {
  id?: number;
  name?: string;
  brand?: string;
  marca?: string;
  model?: string;
  color?: string;
  plate?: string;
  odometer?: number;
  year?: number;
  group?: string;
  initialValue?: number;
  active?: boolean;
  deleted?: boolean;
  deletedAt?: string;
  deletedByUserId?: number;
  restoredAt?: string;
  restoredByUserId?: number;
  restoreReason?: string;
  driverName?:string;
  adminStatus?: CarAdminStatus;
  createdDate?: string;
}

export interface CarBodyDamageModel {
  id?: number;
  date?: string;
  responsible?: string;
  part?: string;
  /** Stored keys (unchanged contract). */
  imagePath?: string;
  imagePath2?: string;
  /** URLs resolved by the backend for imagePath/imagePath2 ("" = no photo); absent in payloads that only carry keys. */
  imageUrl?: string | null;
  imageUrl2?: string | null;
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
  delete?: boolean;
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
  delete?: boolean;
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
  publicScore?: string | number;
  averageKm?: number;
  averageInspectioScore?: number;
  averageDriverCarScore?: number;
  address?: AdressModel;
}
export interface CarDriverModel {
  id?: number;
  startDate?: string;
  endDate?: string | null;
  warranty?: number;
  score?: number;
  debt?: number;
  contractNumber?: string;
  outstandingDebtTotal?: number;
  openPendenciesCount?: number;
  concluded?: boolean;
  driver?: DriverModel;
  /** Read-only, computed by the backend: ACTIVE (operational), SUSPENDED (primary while on a reserve car) or CONCLUDED. */
  status?: DriverCarStatus;
  /** Read-only: the car of the contract. */
  carId?: number | null;
  /** Read-only: true for a temporary reserve-car contract. */
  reserve?: boolean;
  /** Read-only: the primary contract a reserve temporarily replaces. */
  primaryDriverCarId?: number | null;
  /** Read-only. */
  suspended?: boolean;
}

export type DriverCarStatus = "ACTIVE" | "SUSPENDED" | "CONCLUDED";

/** Explicit choice when the driver already has an open contract in another car. */
export type DriverAssignmentType = "RESERVE" | "PERMANENT";

export type ReserveReturnOutcome = "RESTORED" | "PRIMARY_CAR_OCCUPIED" | "DRIVER_ACTIVE_ELSEWHERE" | "PRIMARY_CONCLUDED";

/** POST /api/driver-cars/{id}/return */
export interface ReserveReturnResultModel {
  returnedDriverCarId?: number;
  primaryDriverCarId?: number | null;
  primaryCarPlate?: string | null;
  outcome?: ReserveReturnOutcome;
  primaryRestored?: boolean;
  occupyingDriverCarId?: number | null;
  occupyingDriverName?: string | null;
}

export interface IncomeModel {
  id?: number;
  date?: string;
  name?: string;
  cost?: number;
  delete?: boolean;
}

export interface CarExpenseModel {
  id?: number;
  date?: string;
  name?: string;
  cost?: number;
  delete?: boolean;
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
  note?: string;
  status?: DriverPendencyStatus;
  paidAt?: string;
  paidAmount?: number;
  remainingAmount?: number;
  paymentMethod?: string;
  delete?: boolean;
  /** Read-only: the driver who owes the debt (null for legacy rows whose debtor could not be determined). */
  debtorDriverId?: number | null;
  /** Read-only: the contract (driver_car) during which the debt was recorded - its historical origin. */
  driverCarId?: number | null;
  /** Read-only, structural: what kind of debt it is (null for rows recorded before the origin existed). */
  originType?: PendencyOriginType | null;
  /** Read-only: the car maintenance a shared-maintenance charge comes from. */
  originMaintenanceId?: number | null;
  /** Read-only: the Documentos document whose finalization created this debt (legacy flow). */
  originDocumentId?: number | null;
  /** Read-only: infraction data of a FINE. */
  fineAit?: string | null;
  fineAgency?: string | null;
  fineLocation?: string | null;
  fineClassification?: string | null;
  fineInfractionTime?: string | null;
  fineDueDate?: string | null;
}

export type PendencyOriginType = "FINE" | "SHARED_MAINTENANCE";

export type DriverPendencyStatus = "OPEN" | "PARTIALLY_PAID" | "PAID";

export interface DriverDebtSummaryModel {
  driverCarId?: number;
  totalOutstanding?: number;
  openPendenciesCount?: number;
  totalPendenciesCount?: number;
  paidPendenciesCount?: number;
}
