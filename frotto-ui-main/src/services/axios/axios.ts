import axios from "axios";

const baseURL =
  (process.env.REACT_APP_API_URL || "").replace(/\/$/, "") ||
  "https://api.frotto.com.br/api";

const api = axios.create({
  baseURL,
  withCredentials: true, // só se você usa cookie/sessão
});

export default api;
