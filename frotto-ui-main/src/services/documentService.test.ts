import api from "./axios/axios";
import documentService from "./documentService";

jest.mock("./axios/axios", () => ({
  __esModule: true,
  default: { get: jest.fn(), post: jest.fn(), patch: jest.fn(), delete: jest.fn(), defaults: { baseURL: "" } },
}));

const mockedApi = api as unknown as { post: jest.Mock };
const pdf = () => new File(["%PDF-1.7"], "a.pdf", { type: "application/pdf" });
const httpError = (status: number, message?: string) => ({ response: { status, data: message ? { message } : {} } });

describe("documentService.uploadDocumentAttachments", () => {
  beforeEach(() => jest.resetAllMocks());

  it("keeps the real error when the file is rejected (no retry with the legacy field)", async () => {
    mockedApi.post.mockRejectedValue(httpError(400, "error.upload.invalidtype"));

    await expect(documentService.uploadDocumentAttachments(20, [pdf()])).rejects.toEqual(httpError(400, "error.upload.invalidtype"));
    expect(mockedApi.post).toHaveBeenCalledTimes(1);
  });

  it("keeps the real error when the storage is unavailable", async () => {
    mockedApi.post.mockRejectedValue(httpError(503, "error.upload.storageunavailable"));

    await expect(documentService.uploadDocumentAttachments(20, [pdf()])).rejects.toBeTruthy();
    expect(mockedApi.post).toHaveBeenCalledTimes(1);
  });

  it("still retries with the legacy field name for other client errors", async () => {
    mockedApi.post.mockRejectedValueOnce(httpError(404)).mockResolvedValueOnce({ data: { id: 20, attachments: ["k"] } });

    await expect(documentService.uploadDocumentAttachments(20, [pdf()])).resolves.toEqual({ id: 20, attachments: ["k"] });
    expect(mockedApi.post).toHaveBeenCalledTimes(2);
  });
});

describe("documentService.finalizeDocument", () => {
  beforeEach(() => jest.resetAllMocks());

  it("posts the finalization; the assignment goes in the query only when the user chose one", async () => {
    mockedApi.post.mockResolvedValue({ data: { id: 20, status: "FINAL" } });

    await documentService.finalizeDocument(20);
    await documentService.finalizeDocument(20, "RESERVE");

    expect(mockedApi.post.mock.calls[0][0]).toBe("/api/documents/20/finalize");
    expect(mockedApi.post.mock.calls[1][0]).toBe("/api/documents/20/finalize?assignment=RESERVE");
  });
});
