import endpoints from "../constants/endpoints";
import api from "./axios/axios";

export type TaxPersonType = "CPF" | "CNPJ";

export interface MeResponseDTO {
  login: string | null;
  firstName: string | null;
  lastName: string | null;
  imageUrl: string | null;
  langKey: string | null;
  personalName: string | null;
  personalCpf: string | null;
  personalBirthDate: string | null;
  personalEmail: string | null;
  personalPhone: string | null;
  taxPersonType: TaxPersonType | null;
  taxLandlordName: string | null;
  taxCpf: string | null;
  taxEmail: string | null;
  taxPhone: string | null;
  taxCompanyName: string | null;
  taxCnpj: string | null;
  taxIe: string | null;
  taxContactPhone: string | null;
  taxAddress: string | null;
}

export interface UpdatePersonalPayload {
  firstName: string | null;
  lastName: string | null;
  imageUrl: string | null;
  langKey: string | null;
  personalName: string | null;
  personalCpf: string | null;
  personalBirthDate: string | null;
  personalEmail: string | null;
  personalPhone: string | null;
}

export interface UpdateTaxDataPayload {
  taxPersonType: TaxPersonType;
  taxLandlordName: string | null;
  taxCpf: string | null;
  taxEmail: string | null;
  taxPhone: string | null;
  taxCompanyName: string | null;
  taxCnpj: string | null;
  taxIe: string | null;
  taxContactPhone: string | null;
  taxAddress: string | null;
}

export interface ChangePasswordPayload {
  oldPassword: string;
  newPassword: string;
}

const profileService = {
  async getMe(): Promise<MeResponseDTO> {
    const { data } = await api.get<MeResponseDTO>(endpoints.ME());
    return data;
  },

  async updatePersonal(payload: UpdatePersonalPayload): Promise<MeResponseDTO> {
    const { data } = await api.patch<MeResponseDTO>(endpoints.ME(), payload);
    return data;
  },

  async updateTaxData(payload: UpdateTaxDataPayload): Promise<MeResponseDTO> {
    const { data } = await api.patch<MeResponseDTO>(
      endpoints.ME_TAX_DATA(),
      payload
    );
    return data;
  },

  async changePassword(payload: ChangePasswordPayload): Promise<void> {
    await api.post(endpoints.ME_CHANGE_PASSWORD(), payload);
  },
};

export default profileService;
