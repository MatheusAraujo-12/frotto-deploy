import axios from "axios";

const baseURL =
  (process.env.REACT_APP_API_URL || "").replace(/\/$/, "") ||
  "https://api.frotto.com.br";

const api = axios.create({
  baseURL,
  withCredentials: false,
});

export default api;
