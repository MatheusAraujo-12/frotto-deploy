import api from "./axios/axios";
import {
  canRestorePrimary,
  canReturnReserve,
  createSingleFlight,
  describeReserveReturn,
  driverCarStatus,
  getAssignmentConflict,
  groupDriverCars,
  isRestoreConflict,
  restorePrimaryContract,
  returnReserveCar,
  saveNewDriverCar,
} from "./driverAssignmentService";
import {
  ACTIVE_DRIVER_EXISTS_400,
  ASSIGNMENT_REQUIRED_409,
  PRIMARY_ACTIVE,
  PRIMARY_SUSPENDED,
  RESERVE_ACTIVE,
  RESERVE_CONCLUDED,
  RESTORE_CONFLICT_409,
  RETURN_DRIVER_ACTIVE_ELSEWHERE,
  RETURN_PRIMARY_CAR_OCCUPIED,
  RETURN_PRIMARY_CONCLUDED,
  RETURN_RESTORED,
  apiError,
} from "./driverAssignmentContract.fixtures";

jest.mock("./axios/axios");
const mockedApi = api as jest.Mocked<typeof api>;
const body = { startDate: "2026-09-10", concluded: false, driver: { name: "João Silva", cpf: "11111111111" } };

describe("driverAssignmentService - contrato real do backend", () => {
  beforeEach(() => jest.resetAllMocks());

  describe("409 driverassignmentrequired", () => {
    it("is recognized with the conflicting contract and plate", () => {
      expect(getAssignmentConflict(apiError(409, ASSIGNMENT_REQUIRED_409))).toEqual({ conflictingDriverCarId: 1, conflictingCarPlate: "ONX1A11" });
    });

    it("no other error opens the choice", () => {
      expect(getAssignmentConflict(apiError(400, ACTIVE_DRIVER_EXISTS_400))).toBeNull();
      expect(getAssignmentConflict(apiError(409, RESTORE_CONFLICT_409))).toBeNull();
      expect(getAssignmentConflict(apiError(500, {}))).toBeNull();
      expect(getAssignmentConflict(new Error("offline"))).toBeNull();
    });

    it("missing plate is not invented", () => {
      expect(getAssignmentConflict(apiError(409, { ...ASSIGNMENT_REQUIRED_409, conflictingCarPlate: null }))?.conflictingCarPlate).toBeUndefined();
    });
  });

  describe("saveNewDriverCar", () => {
    it("without conflict: one POST, no question", async () => {
      mockedApi.post.mockResolvedValueOnce({ data: PRIMARY_ACTIVE });
      const choose = jest.fn();

      const result = await saveNewDriverCar(10, body, choose);

      expect(result).toEqual({ driverCar: PRIMARY_ACTIVE });
      expect(choose).not.toHaveBeenCalled();
      expect(mockedApi.post).toHaveBeenCalledTimes(1);
      expect(mockedApi.post.mock.calls[0][0]).toBe("/api/driver-cars/car/10");
      expect(mockedApi.post.mock.calls[0][1]).toBe(body);
    });

    it.each(["PERMANENT", "RESERVE"] as const)("on the 409 resends the same body with assignment=%s", async (assignment) => {
      mockedApi.post.mockRejectedValueOnce(apiError(409, ASSIGNMENT_REQUIRED_409)).mockResolvedValueOnce({ data: RESERVE_ACTIVE });
      const choose = jest.fn().mockResolvedValue(assignment);

      const result = await saveNewDriverCar(20, body, choose);

      expect(choose).toHaveBeenCalledWith({ conflictingDriverCarId: 1, conflictingCarPlate: "ONX1A11" });
      expect(mockedApi.post).toHaveBeenCalledTimes(2);
      expect(mockedApi.post.mock.calls[1][0]).toBe(`/api/driver-cars/car/20?assignment=${assignment}`);
      expect(mockedApi.post.mock.calls[1][1]).toBe(body);
      expect(result).toEqual({ driverCar: RESERVE_ACTIVE, assignment });
    });

    it("cancelling sends nothing more", async () => {
      mockedApi.post.mockRejectedValueOnce(apiError(409, ASSIGNMENT_REQUIRED_409));

      const result = await saveNewDriverCar(20, body, jest.fn().mockResolvedValue(null));

      expect(result).toEqual({ cancelled: true });
      expect(mockedApi.post).toHaveBeenCalledTimes(1);
      expect(mockedApi.put).not.toHaveBeenCalled();
    });

    it("any other error is rethrown without asking", async () => {
      const failure = apiError(400, ACTIVE_DRIVER_EXISTS_400);
      mockedApi.post.mockRejectedValueOnce(failure);
      const choose = jest.fn();

      await expect(saveNewDriverCar(20, body, choose)).rejects.toBe(failure);
      expect(choose).not.toHaveBeenCalled();
    });

    it("an error of the second request is rethrown (nothing is retried silently)", async () => {
      const failure = apiError(400, ACTIVE_DRIVER_EXISTS_400);
      mockedApi.post.mockRejectedValueOnce(apiError(409, ASSIGNMENT_REQUIRED_409)).mockRejectedValueOnce(failure);

      await expect(saveNewDriverCar(20, body, jest.fn().mockResolvedValue("RESERVE"))).rejects.toBe(failure);
      expect(mockedApi.post).toHaveBeenCalledTimes(2);
    });
  });

  describe("return / restore endpoints", () => {
    it("return calls POST /driver-cars/{id}/return once", async () => {
      mockedApi.post.mockResolvedValueOnce({ data: RETURN_RESTORED });

      await expect(returnReserveCar(2)).resolves.toEqual(RETURN_RESTORED);
      expect(mockedApi.post).toHaveBeenCalledTimes(1);
      expect(mockedApi.post.mock.calls[0][0]).toBe("/api/driver-cars/2/return");
    });

    it("restore calls POST /driver-cars/{id}/restore", async () => {
      mockedApi.post.mockResolvedValueOnce({ data: PRIMARY_ACTIVE });

      await expect(restorePrimaryContract(1)).resolves.toEqual(PRIMARY_ACTIVE);
      expect(mockedApi.post.mock.calls[0][0]).toBe("/api/driver-cars/1/restore");
    });

    it("restore conflict is recognized", () => {
      expect(isRestoreConflict(apiError(409, RESTORE_CONFLICT_409))).toBe(true);
      expect(isRestoreConflict(apiError(409, ASSIGNMENT_REQUIRED_409))).toBe(false);
    });
  });

  describe("outcome of a reserve return", () => {
    it("RESTORED", () => {
      expect(describeReserveReturn(RETURN_RESTORED, "João Silva")).toEqual({
        tone: "success",
        message: "Carro reserva devolvido. O motorista retornou ao veículo principal.",
      });
    });

    it("PRIMARY_CAR_OCCUPIED uses the real plate and occupying driver", () => {
      expect(describeReserveReturn(RETURN_PRIMARY_CAR_OCCUPIED, "João Silva")).toEqual({
        tone: "warning",
        message:
          "Carro reserva devolvido. O veículo principal ONX1A11 está atualmente vinculado a Maria Souza. O vínculo principal de João Silva permanece suspenso.",
      });
    });

    it("PRIMARY_CAR_OCCUPIED never invents missing data", () => {
      const { message } = describeReserveReturn({ ...RETURN_PRIMARY_CAR_OCCUPIED, primaryCarPlate: null, occupyingDriverName: null });
      expect(message).toBe(
        "Carro reserva devolvido. O veículo principal está atualmente vinculado a outro motorista. O vínculo principal permanece suspenso."
      );
    });

    it("DRIVER_ACTIVE_ELSEWHERE", () => {
      expect(describeReserveReturn(RETURN_DRIVER_ACTIVE_ELSEWHERE)).toEqual({
        tone: "warning",
        message: "Carro reserva devolvido, mas o motorista possui outro vínculo ativo. O vínculo principal permanece suspenso.",
      });
    });

    it("PRIMARY_CONCLUDED", () => {
      expect(describeReserveReturn(RETURN_PRIMARY_CONCLUDED)).toEqual({
        tone: "warning",
        message: "Carro reserva devolvido. O vínculo principal anterior já está finalizado e não foi reativado.",
      });
    });
  });

  describe("states as the backend reports them", () => {
    it("status and the actions they allow", () => {
      expect(driverCarStatus(PRIMARY_ACTIVE)).toBe("ACTIVE");
      expect(driverCarStatus(PRIMARY_SUSPENDED)).toBe("SUSPENDED");
      expect(driverCarStatus(RESERVE_CONCLUDED)).toBe("CONCLUDED");
      expect(canReturnReserve(RESERVE_ACTIVE)).toBe(true);
      expect(canReturnReserve(RESERVE_CONCLUDED)).toBe(false);
      expect(canReturnReserve(PRIMARY_ACTIVE)).toBe(false);
      expect(canRestorePrimary(PRIMARY_SUSPENDED)).toBe(true);
      expect(canRestorePrimary(PRIMARY_ACTIVE)).toBe(false);
      expect(canRestorePrimary(RESERVE_ACTIVE)).toBe(false);
    });

    it("contracts read before the status existed fall back to the concluded flag only", () => {
      expect(driverCarStatus({ concluded: true })).toBe("CONCLUDED");
      expect(driverCarStatus({ concluded: false })).toBe("ACTIVE");
      expect(driverCarStatus({})).toBe("ACTIVE");
    });
  });

  it("a car's list keeps both the suspended primary and the current driver open, concluded ones in history", () => {
    const mariaActive = { ...PRIMARY_ACTIVE, id: 6, driver: { id: 2, name: "Maria Souza" } };
    const { open, done } = groupDriverCars([PRIMARY_SUSPENDED, RESERVE_CONCLUDED, mariaActive]);

    expect(open.map((item) => item.id)).toEqual([6, 1]);
    expect(done.map((item) => item.id)).toEqual([2]);
  });

  it("single flight ignores calls made while one is running", async () => {
    const run = createSingleFlight();
    let release: () => void = () => undefined;
    const action = jest.fn(() => new Promise<string>((resolve) => (release = () => resolve("done"))));

    const first = run(action);
    const second = await run(action);
    release();

    await expect(first).resolves.toBe("done");
    expect(second).toBeUndefined();
    expect(action).toHaveBeenCalledTimes(1);
    // Once finished, the next call runs again.
    const third = run(action);
    release();
    await expect(third).resolves.toBe("done");
    expect(action).toHaveBeenCalledTimes(2);
  });
});
