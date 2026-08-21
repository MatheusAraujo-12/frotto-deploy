import axios from "axios";
import { normalizeBaseUrl } from "../../constants/endpoints";
import { getToken } from "../localStorage/localstorage";

const envBaseUrl =
  process.env.NODE_ENV === "development"
    ? process.env.REACT_APP_SERVER_URL || process.env.REACT_APP_API_URL
    : process.env.REACT_APP_API_URL || process.env.REACT_APP_SERVER_URL;

const baseURL = normalizeBaseUrl(envBaseUrl || "");

// Empty baseURL keeps same-origin (e.g. reverse proxy with /api).
const api = axios.create({
  baseURL,
  withCredentials: false,
});

// Read the current token for every request. This keeps every service that imports this
// shared client authenticated even when it runs before AppSetup's mount effect or after the
// token changes during login/logout.
api.interceptors.request.use((config) => {
  const token = getToken();

  if (token) {
    config.headers.set("Authorization", token);
  } else {
    config.headers.delete("Authorization");
  }

  return config;
});

export default api;
