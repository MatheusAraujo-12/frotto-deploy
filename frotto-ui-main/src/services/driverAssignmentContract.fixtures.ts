/**
 * Real responses of the backend (commit 770d4bc), captured from a local run of the reserve/transfer scenarios.
 * Kept verbatim (only the driver objects trimmed) so the frontend is tested against the actual contract.
 */

export const ASSIGNMENT_REQUIRED_409 = {
  errorKey: "driverassignmentrequired",
  type: "https://www.jhipster.tech/problem/problem-with-message",
  title: "O motorista já possui vínculo em outro veículo: informe carro reserva (RESERVE) ou transferência definitiva (PERMANENT).",
  status: 409,
  conflictingCarPlate: "ONX1A11",
  conflictingDriverCarId: 1,
  message: "error.driverassignmentrequired",
  params: "driverCar",
};

export const RESTORE_CONFLICT_409 = {
  errorKey: "drivercarrestoreconflict",
  type: "https://www.jhipster.tech/problem/problem-with-message",
  title: "O veículo do vínculo principal está em uso por outro motorista.",
  status: 409,
  conflictingCarPlate: "ONX1A11",
  conflictingDriverCarId: 6,
  message: "error.drivercarrestoreconflict",
  params: "driverCar",
};

export const ACTIVE_DRIVER_EXISTS_400 = {
  entityName: "driverCar",
  errorKey: "activedriverexists",
  type: "https://www.jhipster.tech/problem/problem-with-message",
  title: "Active Driver-Car exists, may not add another active driver",
  status: 400,
  message: "error.activedriverexists",
  params: "driverCar",
};

const JOAO = { id: 1, name: "João Silva", cpf: "11111111111" };

export const PRIMARY_ACTIVE = {
  id: 1,
  startDate: "2026-08-01",
  endDate: null,
  concluded: false,
  driver: JOAO,
  suspended: false,
  status: "ACTIVE" as const,
  reserve: false,
  primaryDriverCarId: null,
};

export const PRIMARY_SUSPENDED = { ...PRIMARY_ACTIVE, suspended: true, status: "SUSPENDED" as const };

export const RESERVE_ACTIVE = {
  id: 2,
  startDate: "2026-09-10",
  endDate: null,
  concluded: false,
  driver: JOAO,
  suspended: false,
  status: "ACTIVE" as const,
  reserve: true,
  primaryDriverCarId: 1,
};

export const RESERVE_CONCLUDED = { ...RESERVE_ACTIVE, concluded: true, endDate: "2026-09-15", status: "CONCLUDED" as const };

export const RETURN_RESTORED = {
  returnedDriverCarId: 2,
  primaryDriverCarId: 1,
  primaryCarPlate: "ONX1A11",
  outcome: "RESTORED" as const,
  primaryRestored: true,
  occupyingDriverCarId: null,
  occupyingDriverName: null,
};

export const RETURN_PRIMARY_CAR_OCCUPIED = {
  returnedDriverCarId: 5,
  primaryDriverCarId: 1,
  primaryCarPlate: "ONX1A11",
  outcome: "PRIMARY_CAR_OCCUPIED" as const,
  primaryRestored: false,
  occupyingDriverCarId: 6,
  occupyingDriverName: "Maria Souza",
};

export const RETURN_PRIMARY_CONCLUDED = {
  returnedDriverCarId: 7,
  primaryDriverCarId: 1,
  primaryCarPlate: "ONX1A11",
  outcome: "PRIMARY_CONCLUDED" as const,
  primaryRestored: false,
  occupyingDriverCarId: null,
  occupyingDriverName: null,
};

/** Not reachable through the API in the local run (the backend never lets a driver operate two cars); same shape. */
export const RETURN_DRIVER_ACTIVE_ELSEWHERE = {
  ...RETURN_PRIMARY_CONCLUDED,
  outcome: "DRIVER_ACTIVE_ELSEWHERE" as const,
};

/** Axios-like error as the interceptors deliver it. */
export const apiError = (status: number, data: unknown) => Object.assign(new Error(`HTTP ${status}`), { response: { status, data } });
