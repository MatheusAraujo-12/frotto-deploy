import endpoints from "../constants/endpoints";
import {
  CarDriverModel,
  DriverAssignmentType,
  DriverCarStatus,
  ReserveReturnResultModel,
} from "../constants/CarModels";
import { FrottoBadgeVariant } from "../components/UI/FrottoBadge";
import { getApiErrorCode } from "./apiErrorMessage";
import api from "./axios/axios";

/**
 * Driver ↔ car assignment as exposed by the backend (the source of truth): this module only reads its states and
 * errors and calls its endpoints - it never decides RESERVE/PERMANENT nor changes a contract's state by itself.
 */

/** Data of the 409 "driverassignmentrequired": the open contract that requires the user's choice. */
export interface AssignmentConflict {
  conflictingDriverCarId?: number;
  conflictingCarPlate?: string;
}

export type FeedbackTone = "success" | "warning";

export interface Feedback {
  tone: FeedbackTone;
  message: string;
}

export const ASSIGNMENT_MESSAGES: Record<DriverAssignmentType, string> = {
  PERMANENT: "Motorista transferido com sucesso.",
  RESERVE: "Motorista vinculado temporariamente ao carro reserva.",
};

export const RESTORE_SUCCESS_MESSAGE = "Vínculo reativado com sucesso.";
export const RESTORE_CONFLICT_MESSAGE =
  "Não é possível reativar este vínculo porque o veículo ou o motorista possui outro vínculo ativo.";

/** Error messages for the driver-car error codes of the backend. */
export const DRIVER_CAR_ERROR_MESSAGES: Record<string, string> = {
  drivercarrestoreconflict: RESTORE_CONFLICT_MESSAGE,
  drivercarnotsuspended: "Somente um vínculo principal suspenso pode ser reativado.",
  drivercarnotreserve: "Este vínculo não é de carro reserva.",
  drivercarreservealreadyreturned: "Este carro reserva já foi devolvido.",
  drivercarreservecannotreopen: "Um carro reserva devolvido não pode ser reaberto.",
  drivercarreserverequiresprimary: "Carro reserva exige um vínculo principal do motorista.",
  driverhasmultipleopencontracts:
    "O motorista possui mais de um vínculo principal aberto. Use a transferência definitiva.",
  driverhasopencontractoncar: "O motorista já possui um vínculo aberto neste veículo.",
  driveractiveelsewhere: "O motorista já possui um vínculo ativo em outro veículo.",
  drivercarchangeforbidden:
    "O motorista de um vínculo existente não pode ser trocado. Encerre este vínculo e cadastre um novo para o outro motorista.",
  drivernotfound: "Motorista não encontrado para esta conta.",
  drivercarhaspendencies: "O vínculo possui pendências e não pode ser excluído. Encerre o vínculo.",
  drivercarhasreserves: "O vínculo está suspenso ou ligado a um carro reserva e não pode ser excluído.",
  drivercarreturnedbychecklist:
    "Este vínculo foi encerrado pelo checklist de devolução finalizado e não pode ser reaberto. Cadastre um novo vínculo.",
  driveremergencycontactschanged:
    "Os contatos de emergência do motorista foram alterados enquanto este formulário estava aberto (por exemplo, por um checklist de entrega). Feche e abra o vínculo novamente para editá-los.",
};

export const DRIVER_CAR_STATUS_LABEL: Record<DriverCarStatus, string> = {
  ACTIVE: "Ativo",
  SUSPENDED: "Suspenso",
  CONCLUDED: "Finalizado",
};

export const DRIVER_CAR_STATUS_VARIANT: Record<DriverCarStatus, FrottoBadgeVariant> = {
  ACTIVE: "success",
  SUSPENDED: "warning",
  CONCLUDED: "neutral",
};

/** The backend status; only contracts read before it existed fall back to the concluded flag. */
export function driverCarStatus(driverCar?: CarDriverModel): DriverCarStatus {
  if (driverCar?.status) {
    return driverCar.status;
  }
  return driverCar?.concluded ? "CONCLUDED" : "ACTIVE";
}

/**
 * Contracts of one car split for display: open ones (ACTIVE first, then SUSPENDED - a car can hold the suspended
 * primary of a driver on a reserve car and the contract of whoever drives it now) and the concluded history.
 */
export function groupDriverCars(list: CarDriverModel[]): { open: CarDriverModel[]; done: CarDriverModel[] } {
  const open = list.filter((item) => driverCarStatus(item) !== "CONCLUDED");
  const done = list.filter((item) => driverCarStatus(item) === "CONCLUDED");
  open.sort((first, second) => Number(driverCarStatus(first) === "SUSPENDED") - Number(driverCarStatus(second) === "SUSPENDED"));
  return { open, done };
}

/** An active reserve contract: it ends by returning the reserve car. */
export function canReturnReserve(driverCar?: CarDriverModel): boolean {
  return Boolean(driverCar?.id && driverCar.reserve && driverCarStatus(driverCar) === "ACTIVE");
}

/** A suspended primary contract: it can be reactivated (the backend refuses while the car or the driver is busy). */
export function canRestorePrimary(driverCar?: CarDriverModel): boolean {
  return Boolean(driverCar?.id && !driverCar.reserve && driverCarStatus(driverCar) === "SUSPENDED");
}

/** The 409 that asks for an explicit RESERVE/PERMANENT choice, or null for any other error. */
export function getAssignmentConflict(error: unknown): AssignmentConflict | null {
  const response = (error as any)?.response;
  if (Number(response?.status) !== 409 || getApiErrorCode(error) !== "driverassignmentrequired") {
    return null;
  }
  const data = response?.data || {};
  return {
    conflictingDriverCarId: typeof data.conflictingDriverCarId === "number" ? data.conflictingDriverCarId : undefined,
    conflictingCarPlate:
      typeof data.conflictingCarPlate === "string" && data.conflictingCarPlate.trim() ? data.conflictingCarPlate.trim() : undefined,
  };
}

export function isRestoreConflict(error: unknown): boolean {
  return getApiErrorCode(error) === "drivercarrestoreconflict";
}

/** POST /api/driver-cars/car/{carId}[?assignment=RESERVE|PERMANENT] */
export async function createDriverCar(
  carId: string | number,
  body: CarDriverModel,
  assignment?: DriverAssignmentType
): Promise<CarDriverModel> {
  const url = endpoints.DRIVERS({ pathVariables: { id: carId }, ...(assignment ? { query: { assignment } } : {}) });
  const { data } = await api.post(url, body);
  return data;
}

export interface NewDriverCarResult {
  driverCar?: CarDriverModel;
  assignment?: DriverAssignmentType;
  cancelled?: boolean;
}

/**
 * Creates the contract as today; only when the backend answers that the driver already has an open contract
 * elsewhere, asks the user (never inferred) and sends the same request again with the chosen assignment. Any other
 * error is rethrown untouched. Cancelling sends nothing more.
 */
export async function saveNewDriverCar(
  carId: string | number,
  body: CarDriverModel,
  chooseAssignment: (conflict: AssignmentConflict) => Promise<DriverAssignmentType | null>
): Promise<NewDriverCarResult> {
  try {
    return { driverCar: await createDriverCar(carId, body) };
  } catch (error) {
    const conflict = getAssignmentConflict(error);
    if (!conflict) {
      throw error;
    }
    const assignment = await chooseAssignment(conflict);
    if (!assignment) {
      return { cancelled: true };
    }
    return { driverCar: await createDriverCar(carId, body, assignment), assignment };
  }
}

/** POST /api/driver-cars/{id}/return[?endDate=yyyy-MM-dd] */
export async function returnReserveCar(driverCarId: number, endDate?: string): Promise<ReserveReturnResultModel> {
  const url = endpoints.DRIVER_CAR_RETURN({ pathVariables: { id: driverCarId }, ...(endDate ? { query: { endDate } } : {}) });
  const { data } = await api.post(url);
  return data || {};
}

/** POST /api/driver-cars/{id}/restore */
export async function restorePrimaryContract(driverCarId: number): Promise<CarDriverModel> {
  const { data } = await api.post(endpoints.DRIVER_CAR_RESTORE({ pathVariables: { id: driverCarId } }));
  return data;
}

/** Message for each outcome of a reserve return. The reserve itself is always returned: never an error. */
export function describeReserveReturn(result: ReserveReturnResultModel, driverName?: string): Feedback {
  const name = `${driverName || ""}`.trim();
  switch (result?.outcome) {
    case "RESTORED":
      return { tone: "success", message: "Carro reserva devolvido. O motorista retornou ao veículo principal." };
    case "PRIMARY_CAR_OCCUPIED": {
      const plate = `${result.primaryCarPlate || ""}`.trim();
      const occupying = `${result.occupyingDriverName || ""}`.trim();
      const primaryCar = plate ? `O veículo principal ${plate}` : "O veículo principal";
      const occupiedBy = occupying ? `está atualmente vinculado a ${occupying}.` : "está atualmente vinculado a outro motorista.";
      const primary = name ? `O vínculo principal de ${name} permanece suspenso.` : "O vínculo principal permanece suspenso.";
      return { tone: "warning", message: `Carro reserva devolvido. ${primaryCar} ${occupiedBy} ${primary}` };
    }
    case "DRIVER_ACTIVE_ELSEWHERE":
      return {
        tone: "warning",
        message: "Carro reserva devolvido, mas o motorista possui outro vínculo ativo. O vínculo principal permanece suspenso.",
      };
    case "PRIMARY_CONCLUDED":
      return {
        tone: "warning",
        message: "Carro reserva devolvido. O vínculo principal anterior já está finalizado e não foi reativado.",
      };
    default:
      return { tone: "success", message: "Carro reserva devolvido." };
  }
}

/** Runs one action at a time: calls made while one is in flight are ignored (no duplicated requests). */
export function createSingleFlight() {
  let running = false;
  return async function run<T>(action: () => Promise<T>): Promise<T | undefined> {
    if (running) {
      return undefined;
    }
    running = true;
    try {
      return await action();
    } finally {
      running = false;
    }
  };
}
