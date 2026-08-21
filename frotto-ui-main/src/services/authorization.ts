import { AccountDTO } from "../constants/AccountModels";

export const ROLE_ADMIN = "ROLE_ADMIN";

export const accountIsAdmin = (account?: AccountDTO | null): boolean => {
  const authorities = account?.authorities;
  return Array.isArray(authorities) && authorities.includes(ROLE_ADMIN);
};
