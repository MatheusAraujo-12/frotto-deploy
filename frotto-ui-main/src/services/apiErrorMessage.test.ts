import { getApiErrorMessage } from "./apiErrorMessage";

const errorWithStatus = (status: number, data?: any) => ({
  response: { status, data },
});

describe("getApiErrorMessage - Billing Etapa 4A admin panel error handling", () => {
  it("maps 401 to a session-expired message", () => {
    expect(getApiErrorMessage(errorWithStatus(401))).toMatch(/sessão expirou/i);
  });

  it("maps 403 to a permission message", () => {
    expect(getApiErrorMessage(errorWithStatus(403))).toMatch(/permissão/i);
  });

  it("maps 400 to a validation message, appending a field error when present", () => {
    const message = getApiErrorMessage(
      errorWithStatus(400, { fieldErrors: [{ field: "planCode", message: "não pode ser nulo" }] })
    );
    expect(message).toMatch(/verifique os campos/i);
    expect(message).toContain("planCode");
  });

  it("maps 500 to a generic server-error message", () => {
    expect(getApiErrorMessage(errorWithStatus(500))).toMatch(/erro interno/i);
  });

  it("falls back to a connectivity message when there is no response at all", () => {
    expect(getApiErrorMessage({})).toMatch(/não foi possível conectar/i);
  });

  it("uses the provided fallback for an unmapped status", () => {
    expect(getApiErrorMessage(errorWithStatus(418), "Fallback message")).toBe("Fallback message");
  });
});

describe("getApiErrorMessage - vehicle limit", () => {
  it("uses the specific upgrade message for VehicleLimitReachedException/409", () => {
    const message =
      "Você atingiu o limite de veículos do seu plano. Faça upgrade para cadastrar outro veículo.";
    const error = {
      response: {
        status: 409,
        data: { message: "error.VEHICLE_LIMIT_REACHED" },
      },
    };

    expect(
      getApiErrorMessage(error, "Falha ao salvar", {
        VEHICLE_LIMIT_REACHED: message,
      })
    ).toBe(message);
  });
});
