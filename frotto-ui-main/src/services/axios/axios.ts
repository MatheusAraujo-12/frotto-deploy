import axios from "axios";
import { normalizeBaseUrl } from "../../constants/endpoints";

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

export default api;
