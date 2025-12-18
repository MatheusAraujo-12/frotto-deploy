import axios from "axios";

// Allow pointing the frontend to an external API host when needed.
// Falls back to relative URLs (same origin) if the env var is not set.
const apiBaseUrl = (process.env.REACT_APP_SERVER_URL || "").replace(/\/+$/, "");

const api = axios.create({
  baseURL: apiBaseUrl || undefined,
  timeout: 20000,
  headers: {
    "Content-Type": "application/json",
  },
});

export default api;
