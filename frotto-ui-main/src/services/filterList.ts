import { formatDateView } from "./dateFormat";

const objToString = (obj: Object) => {
  return Object.entries(obj).reduce((str, [key, val]) => {
    if (key === "date" || key === "startDate" || key === "endDate") {
      return `${str}${formatDateView(val)} `;
    }
    if (Array.isArray(val)) {
      let stgArray = "";
      val.forEach((arrayItem) => {
        if (typeof arrayItem === "object") {
          if (arrayItem.name !== undefined) {
            stgArray = stgArray + arrayItem.name;
          }
        }
      });
      return `${str}${stgArray} `;
    }
    return `${str}${val} `;
  }, "");
};

export const filterListObj = (
  objList: Object[],
  searchValue: string | undefined
) => {
  if (searchValue && searchValue !== "" && objList && objList.length > 0) {
    const lowerCaseSearch = searchValue.toLowerCase();

    return objList.filter((obj: Object) => {
      const stringObj = objToString(obj).toLowerCase();
      if (stringObj.search(lowerCaseSearch) !== -1) {
        return true;
      } else {
        return false;
      }
    });
  }
  return objList;
};

export const filterListString = (
  stringList: string[],
  searchValue: string | undefined
) => {
  if (
    searchValue &&
    searchValue !== "" &&
    stringList &&
    stringList.length > 0
  ) {
    const lowerCaseSearch = searchValue.toLowerCase();
    return stringList.filter((stringItem: string) => {
      const stringItemLower = stringItem.toLowerCase();
      return stringItemLower.includes(lowerCaseSearch);
    });
  }
  return stringList;
};

type Entry<T> = {
  [K in keyof T]: [K, T[K]];
}[keyof T];

export function filterObject<T extends object>(
  obj: T,
  fn: (entry: Entry<T>, i: number, arr: Entry<T>[]) => boolean
) {
  return Object.fromEntries(
    (Object.entries(obj) as Entry<T>[]).filter(fn)
  ) as Partial<T>;
}
