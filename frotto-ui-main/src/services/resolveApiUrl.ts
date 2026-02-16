import api from "./axios/axios";

const HTTP_URL_REGEX = /^https?:\/\//i;

export const resolveApiUrl = (path: string): string => {
  const value = `${path || ""}`.trim();
  if (!value) {
    return "";
  }

  if (HTTP_URL_REGEX.test(value) || value.startsWith("blob:") || value.startsWith("data:")) {
    return value;
  }

  const normalizedPath = value.startsWith("/") ? value : `/${value}`;
  const baseURL = `${api.defaults.baseURL || ""}`.trim().replace(/\/$/, "");

  if (!baseURL) {
    return normalizedPath;
  }

  return `${baseURL}${normalizedPath}`;
};
