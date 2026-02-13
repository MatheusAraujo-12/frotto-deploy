import uniq from "lodash/uniq";
import qs from "qs";

type Primitive = string | number | boolean | null | undefined;

function safePath(path: string): string {
  return `${path}`.replace(/\/{2,}/g, "/");
}

/**
 * Normaliza URL:
 * - mantém http/https se existir
 * - remove barras duplicadas
 * - remove barra final
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

/**
 * Remove "/api" do fim do baseURL se alguém colocar isso no env,
 * para evitar montar "https://.../api" + "/api/..." => "/api/api/..."
 */
export function normalizeBaseUrl(baseURL: string): string {
  const clean = (baseURL || "").trim().replace(/\/$/, "");
  if (!clean) return clean;
  // remove apenas um "/api" no final, se existir
  return clean.replace(/\/api$/, "");
}

type PathVariables = Record<string, Primitive>;
type QueryValues = Record<string, Primitive>;

type TransformArgs = {
  pathVariables?: PathVariables;
  query?: QueryValues;
};

function toStr(v: Primitive): string {
  if (v === null || v === undefined) return "";
  return String(v);
}

/**
 * Cria um endpoint builder.
 * Por padrão scope="api" -> retorna "/api/...."
 * Se você quiser endpoints SEM "/api", passe scope="".
 */
function apiEndpoint(path: string, scope: string = "api", staticQuery: QueryValues = {}) {
  const pathWildcards = `${path}`.match(/{(\w+)}/g);

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

  function transformPath({ pathVariables = {}, query = {} }: TransformArgs = {}) {
    const queryValues: QueryValues = { ...staticQuery, ...query };
    let finalPath = path;

    // Replace wildcards like {id}
    if (Array.isArray(pathWildcards)) {
      pathWildcards.forEach((wildcard) => {
        const key = wildcard.replace(/({|})/g, "");
        finalPath = finalPath.replace(wildcard, toStr(pathVariables[key]));
      });
    }

    // Querystring
    const cleanedQuery: Record<string, string> = {};
    Object.keys(queryValues).forEach((k) => {
      const v = queryValues[k];
      if (v !== null && v !== undefined && `${v}`.length > 0) {
        cleanedQuery[k] = String(v);
      }
    });

    if (Object.keys(cleanedQuery).length) {
      finalPath = `${finalPath}?${qs.stringify(cleanedQuery)}`;
    }

    // Monta /{scope}/{path} com segurança
    const prefix = scope ? `/${scope}` : "";
    return safeUrl(`${prefix}/${finalPath}`);
  }

  // útil pra debug
  (transformPath as any).path = path;
  (transformPath as any).scope = scope;

  return transformPath;
}

/**
 * IMPORTANTÍSSIMO:
 * Se o seu backend já está atrás do NGINX do frontend em /api,
 * e você quer evitar CORS, você pode usar baseURL="" e manter scope="api"
 * => chamadas ficam relativas: "/api/authenticate".
 */
const endpoints = {
  REGISTER: apiEndpoint("/register"),
  AUTH: apiEndpoint("/authenticate"),

  USER: apiEndpoint("/user/{id}"),
  ME: apiEndpoint("/me"),
  ME_TAX_DATA: apiEndpoint("/me/tax-data"),
  ME_CHANGE_PASSWORD: apiEndpoint("/me/change-password"),
  ME_AVATAR: apiEndpoint("/me/avatar"),

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
  DRIVER_DEBTS: apiEndpoint("/drivers/{id}/debts"),
  DRIVER_DEBT_SUMMARY: apiEndpoint("/drivers/{id}/debt-summary"),

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
  DRIVER_PENDENCIES_PAY: apiEndpoint("/pendencies/{id}/pay"),

  REPORTS: apiEndpoint("/reports"),
  REPORTS_HISTORY: apiEndpoint("/reports-history"),
  REPORTS_MAINTENANCE: apiEndpoint("/reports/maintenance"),
};

export default endpoints;
