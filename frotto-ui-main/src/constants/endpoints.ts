import uniq from "lodash/uniq";
import qs from "qs";

function safePath(path: string): string {
  return `${path}`.replace(/\/{2,}/g, "/");
}

export function safeUrl(url: string): string {
  // Makes sure its a string;
  const string = `${url}`;

  // Is this an absolute url?
  const match = string.match(/^(https?)/);

  const clean = safePath(
    // clean up protocol and replaces '/{2,}' for '/'
    string.replace(/^https?:\/\//, "").replace(/\/{2,}/g, "/")
  );

  const protocol = match ? `${match[0]}://` : "";

  return `${protocol}${clean}`.replace(/\/$/, "");
}

function apiEndpoint(path: string, scope = "api", staticQuery = {}): Function {
  const pathWildcards = `${path}`.match(/{(\w+)}/g);

  // Are there wildcards in the path?
  if (Array.isArray(pathWildcards)) {
    if (
      process.env.NODE_ENV === "development" &&
      uniq(pathWildcards).length !== pathWildcards.length
    ) {
      // eslint-disable-next-line no-console
      console.error(
        new Error(
          `URL has two or more identical wildcards (${JSON.stringify(
            pathWildcards
          )}). This causes undeterministic behaviour.`
        )
      );
    }
  }

  interface CustomObj {
    [key: string]: string;
  }

  interface QueryPathObj {
    [key: string]: CustomObj;
  }

  function transformPath({
    pathVariables = {},
    query = {},
  }: QueryPathObj = {}) {
    const queryValues = { ...staticQuery, ...query };
    let finalPath = path;

    // Replace wildcards
    if (Array.isArray(pathWildcards)) {
      pathWildcards.forEach((wildcard) => {
        const pathVariableObj = wildcard.replace(/({|})/g, "");
        const variable = pathVariables[pathVariableObj] || "";
        finalPath = finalPath.replace(wildcard, variable);
      });
    }

    if (Object.keys(queryValues).length) {
      finalPath = `${finalPath}?${qs.stringify(queryValues)}`;
    }

    return safeUrl(`/${scope}/${finalPath}`);
  }

  transformPath.path = path;

  return transformPath;
}

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
  DRIVER_CPF: apiEndpoint("/drivers/{cpf}"),
  DRIVERS: apiEndpoint("/driver-cars/car/{id}"),
  INSPECTIONS: apiEndpoint("/inspections/car/{id}"),
  INCOMES: apiEndpoint("/incomes/car/{id}"),
  REMINDERS: apiEndpoint("/reminders/car/{id}"),
  CAR_EXPENSES: apiEndpoint("/car-expenses/car/{id}"),
  CAR_EXPENSES_ALL: apiEndpoint("/car-expenses/car-all"),
  MAINTENANCES: apiEndpoint("/maintenances/car/{id}"),
  DRIVER_PENDENCIES: apiEndpoint("/pendencies/car-driver/{id}"),
  DRIVERS_EDIT: apiEndpoint("/driver-cars/{id}"),
  INSPECTIONS_EDIT: apiEndpoint("/inspections/{id}"),
  INCOMES_EDIT: apiEndpoint("/incomes/{id}"),
  REMINDERS_EDIT: apiEndpoint("/reminders/{id}"),
  CAR_EXPENSES_EDIT: apiEndpoint("/car-expenses/{id}"),
  MAINTENANCES_EDIT: apiEndpoint("/maintenances/{id}"),
  BODY_DAMAGE_EDIT: apiEndpoint("/car-body-damages/{id}"),
  DRIVER_PENDENCIES_EDIT: apiEndpoint("/pendencies/{id}"),
  REPORTS: apiEndpoint("/reports"),
  REPORTS_HISTORY: apiEndpoint("/reports-history"),
  REPORTS_MAINTENANCE: apiEndpoint("/reports/maintenance"),
};
export default endpoints;
