import axios from "axios";

const api = axios.create({
  baseURL: "https://api.frotto.com.br/api",
  timeout: 20000,
  headers: { "Content-Type": "application/json" },
});

export default api;
