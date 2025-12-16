import axios from "axios";

const api = axios.create({
  // Endpoints already generate paths that start with `/api`.
  // Keep baseURL empty so requests use the full path returned by `endpoints`.
  baseURL: "",
  timeout: 20000,
  headers: {
    "Content-Type": "application/json",
  },
});

export default api;