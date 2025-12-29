import axios from "axios";

// ✅ baseURL RELATIVO (same-origin)
const api = axios.create({
  baseURL: "",
  withCredentials: false, // ✅ JWT: não precisa cookie
});

export default api;