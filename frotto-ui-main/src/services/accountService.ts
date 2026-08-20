import { AccountDTO } from "../constants/AccountModels";
import endpoints from "../constants/endpoints";
import api from "./axios/axios";

const accountService = {
  async getAccount(): Promise<AccountDTO> {
    const { data } = await api.get<AccountDTO>(endpoints.ACCOUNT());
    return data;
  },
};

export default accountService;
