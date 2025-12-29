import uniq from "lodash/uniq";
import qs from "qs";

/**
 * Remove barras duplicadas
 */
function safePath(path: string): string {
  return `${path}`.replace(/\/{2,}/g, "/");
}

/**
 * Normaliza URLs absolutas ou relativas
 */
export function safeUrl(url: string): string {
  const string = `${url}`;
  const match = string.match(/^(https?)/);

  const clean = safePath(
    string.replace(/^https?:\/\//, "").replace(/\/{2,}/g, "/")
  );

  const protocol = match ? `${match[0]}://` : "";

  return `${protocol}${clean}`.replace(/\/$/, "");
}

function apiEndpoint(path: string, scope = "api", staticQuery = {}) {
  const pathWildcards = `${path}`.match(/{(\w+)}/g);

  if (
    process.env.NODE_ENV === "development" &&
    Array.isArray(pathWildcards) &&
    uniq(pathWildcards).length !== pathWildcards.length
  ) {
    console.error(
      new Error(
        `URL has two or more identical wildcards (${JSON.stringify(
          pathWildcards
        )}).`
      )
    );
  }

  interface CustomObj {
    [key: string]: string;
  }

  interface QueryPathObj {
    pathVariables?: CustomObj;
    query?: CustomObj;
  }

  function transformPath({
    pathVariables = {},
    query = {},
  }: QueryPathObj = {}) {
    let finalPath = path;

    // Substitui wildcards
    if (Array.isArray(pathWildcards)) {
      pathWildcards.forEach((wildcard) => {
        const key = wildcard.replace(/({|})/g, "");
        finalPath = finalPath.replace(wildcard, pathVariables[key] || "");
      });
    }

    if (Object.keys(query).length) {
      finalPath = `${finalPath}?${qs.stringify({
        ...staticQuery,
        ...query,
      })}`;
    }

    // Normalização segura
    const normalizedPath = finalPath.startsWith("/")
      ? finalPath
      : `/${finalPath}`;

    const normalizedScope = scope.replace(/^\/|\/$/g, "");

    // 🔴 EVITA /api/api
    const finalUrl = normalizedPath.startsWith(`/${normalizedScope}/`)
      ? normalizedPath
      : `/${normalizedScope}${normalizedPath}`;

    return safeUrl(finalUrl);
  }

  transformPath.path = path;
  return transformPath;
}

/**
 * ENDPOINTS
 * Resultado FINAL:
 * AUTH() -> /api/authenticate
 * CARS() -> /api/cars
 */
const endpoints = {
  REGISTER: apiEndpoint("/register"),
  AUTH: apiEndpoint("/authenticate"),
  USER: apiEndpoint("/user/{id}"),

  CARS: apiEndpoint("/cars"),
  CARS_ACTIVE: apiEndpoint("/cars-drivers/active"),
  CARS_ACTIVE_GROUPS: apiEndpoint("/cars/active/groups"),
  CAR: apiEndpoint("/cars/{id}"),

  BODY_DAMAGE: apiEndpoint("/car-body-damages/car/{id}"),
  BODY_DAMAGE_ACTIVE: apiEndpoint("/car-body-damages/car/{id}/active"),
  BODY_DAMAGE_EDIT: apiEndpoint("/car-body-damages/{id}"),

  DRIVER_CPF: apiEndpoint("/drivers/{cpf}"),
  DRIVERS: apiEndpoint("/driver-cars/car/{id}"),
  DRIVERS_EDIT: apiEndpoint("/driver-cars/{id}"),

  INSPECTIONS: apiEndpoint("/inspections/car/{id}"),
  INSPECTIONS_EDIT: apiEndpoint("/inspections/{id}"),

  INCOMES: apiEndpoint("/incomes/car/{id}"),
  INCOMES_EDIT: apiEndpoint("/incomes/{id}"),

  REMINDERS: apiEndpoint("/reminders/car/{id}"),
  REMINDERS_EDIT: apiEndpoint("/reminders/{id}"),

  CAR_EXPENSES: apiEndpoint("/car-expenses/car/{id}"),
  CAR_EXPENSES_ALL: apiEndpoint("/car-expenses/car-all"),
  CAR_EXPENSES_EDIT: apiEndpoint("/car-expenses/{id}"),

  MAINTENANCES: apiEndpoint("/maintenances/car/{id}"),
  MAINTENANCES_EDIT: apiEndpoint("/maintenances/{id}"),

  DRIVER_PENDENCIES: apiEndpoint("/pendencies/car-driver/{id}"),
  DRIVER_PENDENCIES_EDIT: apiEndpoint("/pendencies/{id}"),

  REPORTS: apiEndpoint("/reports"),
  REPORTS_HISTORY: apiEndpoint("/reports-history"),
  REPORTS_MAINTENANCE: apiEndpoint("/reports/maintenance"),
};

export default endpoints;
