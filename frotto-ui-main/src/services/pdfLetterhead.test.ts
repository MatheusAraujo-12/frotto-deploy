import api from "./axios/axios";
import { loadPdfLetterheadData } from "./pdfLetterhead";
import profileService from "./profileService";

jest.mock("./axios/axios", () => ({
  __esModule: true,
  default: { get: jest.fn(), defaults: { baseURL: "https://api.frotto.test" } },
}));
jest.mock("./profileService");

const mockedApi = api as unknown as { get: jest.Mock };
const mockedProfileService = profileService as jest.Mocked<typeof profileService>;

const PNG = new Uint8Array([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]);
// Distinct file per test: the in-memory image cache of the module lives for the whole test file.
const signed = (name: string) => `https://arquivos-staging.frotto.test/files/logos/2026/10/${name}.png`;

const profile = (overrides: Record<string, unknown>) => ({ logoUrl: null, ...overrides }) as any;

describe("loadPdfLetterheadData - logomarca", () => {
  let fetchMock: jest.Mock;

  beforeEach(() => {
    jest.clearAllMocks();
    localStorage.clear();
    fetchMock = jest.fn(async () => ({ ok: true, status: 200, blob: async () => new Blob([PNG], { type: "image/png" }) }));
    (global as any).fetch = fetchMock;
  });

  it("loads the signed logo URL with a plain fetch, never through the JWT axios instance", async () => {
    mockedProfileService.getMe.mockResolvedValue(
      profile({ logoUrl: "logos/2026/10/plain-fetch.png", logoAccessUrl: `${signed("plain-fetch")}?exp=100&sig=aaa` })
    );

    const { logoDataUrl } = await loadPdfLetterheadData();

    expect(logoDataUrl).toMatch(/^data:image\/png;base64,/);
    expect(fetchMock).toHaveBeenCalledWith(`${signed("plain-fetch")}?exp=100&sig=aaa`);
    expect(mockedApi.get).not.toHaveBeenCalled();
  });

  it("never sends the JWT to /files even when the files domain shares the API origin", async () => {
    const sameOrigin = "https://api.frotto.test/files/logos/2026/10/same-origin.png?exp=100&sig=aaa";
    mockedProfileService.getMe.mockResolvedValue(profile({ logoAccessUrl: sameOrigin }));

    const { logoDataUrl } = await loadPdfLetterheadData();

    expect(logoDataUrl).toMatch(/^data:image\/png;base64,/);
    expect(fetchMock).toHaveBeenCalledWith(sameOrigin);
    expect(mockedApi.get).not.toHaveBeenCalled();
  });

  it("re-signed URLs of the same file reuse the cache instead of keeping expired URLs", async () => {
    mockedProfileService.getMe.mockResolvedValue(profile({ logoAccessUrl: `${signed("re-signed")}?exp=100&sig=aaa` }));
    await loadPdfLetterheadData();
    mockedProfileService.getMe.mockResolvedValue(profile({ logoAccessUrl: `${signed("re-signed")}?exp=200&sig=bbb` }));

    const { logoDataUrl } = await loadPdfLetterheadData();

    expect(logoDataUrl).toMatch(/^data:image\/png;base64,/);
    expect(fetchMock).toHaveBeenCalledTimes(1);
    const cacheKeys = Object.keys(localStorage).filter((key) => key.startsWith("frotto:pdf:image:data-url:"));
    expect(cacheKeys).toHaveLength(1);
    expect(cacheKeys[0]).not.toMatch(/sig%3D|exp%3D/);
  });

  it("produces a PDF without logo when the backend resolves no logo, even if a key is stored", async () => {
    mockedProfileService.getMe.mockResolvedValue(profile({ logoUrl: "1771248962905_LOGO_4.png", logoAccessUrl: null }));

    const { logoDataUrl } = await loadPdfLetterheadData();

    expect(logoDataUrl).toBe("");
    expect(fetchMock).not.toHaveBeenCalled();
    expect(mockedApi.get).not.toHaveBeenCalled();
  });

  it("uses the legacy public bucket URL resolved by the backend with a plain fetch", async () => {
    const bucketUrl = "https://localuz-locamais.s3.us-east-1.amazonaws.com/1771248962905_LOGO_4.png";
    mockedProfileService.getMe.mockResolvedValue(profile({ logoUrl: "1771248962905_LOGO_4.png", logoAccessUrl: bucketUrl }));

    const { logoDataUrl } = await loadPdfLetterheadData();

    expect(logoDataUrl).toMatch(/^data:image\/png;base64,/);
    expect(fetchMock).toHaveBeenCalledWith(bucketUrl);
    expect(mockedApi.get).not.toHaveBeenCalled();
  });

  it("an image the files domain no longer serves leaves the PDF without logo", async () => {
    fetchMock.mockResolvedValue({ ok: false, status: 403, blob: async () => new Blob([]) });
    jest.spyOn(console, "warn").mockImplementation(() => undefined);
    mockedProfileService.getMe.mockResolvedValue(profile({ logoAccessUrl: `${signed("no-longer-served")}?exp=1&sig=expired` }));

    const { logoDataUrl } = await loadPdfLetterheadData();

    expect(logoDataUrl).toBe("");
  });
});
