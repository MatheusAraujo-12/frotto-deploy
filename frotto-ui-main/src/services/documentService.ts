import endpoints from "../constants/endpoints";
import {
  CarSearchModel,
  DocumentModel,
  DocumentSavePayload,
  DocumentStatus,
  DocumentType,
  DriverSearchModel,
} from "../constants/DocumentModels";
import api from "./axios/axios";

type DocumentFilters = {
  driverId?: number;
  carId?: number;
  type?: DocumentType;
  status?: DocumentStatus;
  limit?: number;
  page?: number;
};

const normalizeDigits = (value: string): string => `${value || ""}`.replace(/\D+/g, "");
const normalizePlate = (value: string): string => `${value || ""}`.toUpperCase().replace(/[^A-Z0-9]/g, "");

const shouldRetryLegacyAttachmentField = (error: unknown): boolean => {
  const status = (error as any)?.response?.status;
  return status === 400 || status === 404 || status === 415 || status === 422;
};

const uploadWithFieldName = async (
  id: number,
  files: File[],
  fieldName: "file" | "files"
): Promise<DocumentModel> => {
  const formData = new FormData();
  files.forEach((file) => formData.append(fieldName, file));
  const { data } = await api.post<DocumentModel>(
    endpoints.DOCUMENT_ATTACHMENTS({ pathVariables: { id } }),
    formData
  );
  return data;
};

const documentService = {
  async searchDrivers(query: string): Promise<DriverSearchModel[]> {
    const normalized = `${query || ""}`.trim();
    if (!normalized) {
      return [];
    }
    const digits = normalizeDigits(normalized);
    const q = digits.length >= 3 ? digits : normalized;
    const { data } = await api.get<DriverSearchModel[]>(endpoints.DRIVERS_SEARCH({ query: { q } }));
    return Array.isArray(data) ? data : [];
  },

  async searchCars(plate: string): Promise<CarSearchModel[]> {
    const normalized = normalizePlate(plate);
    if (!normalized) {
      return [];
    }
    const { data } = await api.get<CarSearchModel[]>(
      endpoints.CARS_SEARCH({ query: { plate: normalized } })
    );
    return Array.isArray(data) ? data : [];
  },

  async listDocuments(filters: DocumentFilters = {}): Promise<DocumentModel[]> {
    const { data } = await api.get<DocumentModel[]>(
      endpoints.DOCUMENTS({
        query: {
          driverId: filters.driverId,
          carId: filters.carId,
          type: filters.type,
          status: filters.status,
          limit: filters.limit ?? 30,
          page: filters.page ?? 0,
        },
      })
    );
    return Array.isArray(data) ? data : [];
  },

  async getDocument(id: number): Promise<DocumentModel> {
    const { data } = await api.get<DocumentModel>(
      endpoints.DOCUMENT({ pathVariables: { id } })
    );
    return data;
  },

  async deleteDocument(id: number): Promise<void> {
    await api.delete(endpoints.DOCUMENT({ pathVariables: { id } }));
  },

  async createDocument(payload: DocumentSavePayload): Promise<DocumentModel> {
    const { data } = await api.post<DocumentModel>(endpoints.DOCUMENTS(), payload);
    return data;
  },

  async updateDocument(id: number, payload: Partial<DocumentSavePayload>): Promise<DocumentModel> {
    const { data } = await api.patch<DocumentModel>(
      endpoints.DOCUMENT({ pathVariables: { id } }),
      payload
    );
    return data;
  },

  async finalizeDocument(id: number): Promise<DocumentModel> {
    const { data } = await api.post<DocumentModel>(
      endpoints.DOCUMENT_FINALIZE({ pathVariables: { id } }),
      {}
    );
    return data;
  },

  async markDocumentSent(id: number): Promise<DocumentModel> {
    const { data } = await api.post<DocumentModel>(
      endpoints.DOCUMENT_MARK_SENT({ pathVariables: { id } }),
      {}
    );
    return data;
  },

  async generateDocumentPdf(id: number, pdfUrl?: string): Promise<DocumentModel> {
    const { data } = await api.post<DocumentModel>(
      endpoints.DOCUMENT_GENERATE_PDF({ pathVariables: { id } }),
      pdfUrl ? { pdfUrl } : {}
    );
    return data;
  },

  async uploadDocumentAttachments(id: number, files: File[]): Promise<DocumentModel> {
    if (!files.length) {
      return this.getDocument(id);
    }

    try {
      return await uploadWithFieldName(id, files, "file");
    } catch (error) {
      if (!shouldRetryLegacyAttachmentField(error)) {
        throw error;
      }
      return uploadWithFieldName(id, files, "files");
    }
  },
};

export default documentService;
