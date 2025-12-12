import { stringDateToDB } from "../services/dateFormat";

export const LABEL_TYPE = "stacked";
export const SELECT_TYPE = "action-sheet";

const dateNow = (): Date => {
  var local = new Date();
  local.setMinutes(local.getMinutes() - local.getTimezoneOffset());
  return local;
};

export const DATE_TODAY = stringDateToDB(dateNow().toISOString());

export const YEAR_NOW = dateNow().getFullYear();