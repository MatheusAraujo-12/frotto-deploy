const TOKEN_KEY = "TOKEN";
const TOKEN_CHANGED_EVENT = "frotto:token-changed";
export const BODY_DAMAGE_KEY = "BODY_DAMAGE_KEY";
export const EXPENSES_KEY = "EXPENSES_KEY";
export const SERVICES_KEY = "SERVICES_KEY";
export const INCOME_KEY = "INCOME_KEY";
export const DRIVER_PENDENCIES_KEY = "DRIVER_PENDENCIES_KEY";
export const TIRE_BRANDS_KEY = "TIRE_BRANDS_KEY";

export const getToken = (): string | null => {
  return localStorage.getItem(TOKEN_KEY);
};

export const setToken = (value: string) => {
  localStorage.setItem(TOKEN_KEY, value);
  window.dispatchEvent(new Event(TOKEN_CHANGED_EVENT));
};

export const removeToken = (): void => {
  localStorage.removeItem(TOKEN_KEY);
  window.dispatchEvent(new Event(TOKEN_CHANGED_EVENT));
};

export const subscribeToTokenChanges = (listener: () => void): (() => void) => {
  window.addEventListener(TOKEN_CHANGED_EVENT, listener);
  return () => window.removeEventListener(TOKEN_CHANGED_EVENT, listener);
};

export const getArrays = (key: string): string[] => {
  const stringItens = localStorage.getItem(key);
  return stringItens ? JSON.parse(stringItens) : [];
};

export const addOneToArray = (newItem: string, key: string) => {
  const currentArray = getArrays(key);
  currentArray.push(newItem);
  localStorage.setItem(key, JSON.stringify(currentArray));
};
