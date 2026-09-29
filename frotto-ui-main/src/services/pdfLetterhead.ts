import { maskCNPJ, maskCPF } from "./profileFormat";
import api from "./axios/axios";
import profileService, { MeResponseDTO } from "./profileService";
import { resolveApiUrl } from "./resolveApiUrl";

export const PDF_LETTERHEAD_PAGE_MARGINS: [number, number, number, number] = [40, 115, 40, 60];

const IMAGE_DATA_URL_CACHE_PREFIX = "frotto:pdf:image:data-url:";
const imageDataUrlMemoryCache = new Map<string, string>();

type FiscalIdentity = {
  fiscalName: string;
  documentLabel: "CPF" | "CNPJ";
  documentValue: string;
  address: string;
  stateRegistration: string;
};

/**
 * Identidade documental: a logomarca (não o avatar) é a única imagem elegível
 * para o cabeçalho de PDFs de Documentos/Relatórios. Ver DESIGN_SYSTEM.md
 * seção 26 — avatar nunca é usado como fallback quando não há logomarca.
 */
export async function loadPdfLetterheadData(): Promise<{ profile: MeResponseDTO | null; logoDataUrl: string }> {
  try {
    const profile = await profileService.getMe();
    const rawLogoUrl = `${profile.logoUrl || ""}`.trim();
    const logoDataUrl = await loadLogoDataUrl(rawLogoUrl);
    return { profile, logoDataUrl };
  } catch (_error) {
    return { profile: null, logoDataUrl: "" };
  }
}

export function buildPdfLetterhead(
  profile: MeResponseDTO | null,
  logoDataUrl: string,
  pageSize: any,
  pageMargins: [number, number, number, number] = PDF_LETTERHEAD_PAGE_MARGINS
) {
  const fiscalIdentity = resolveFiscalIdentity(profile);
  const headerLineWidth = Math.max(0, (Number(pageSize?.width) || 595) - pageMargins[0] - pageMargins[2]);

  const textStack: any[] = [
    {
      text: fiscalIdentity.fiscalName,
      fontSize: 12,
      bold: true,
      color: "#263238",
      margin: [0, 0, 0, 3],
    },
    {
      text: `${fiscalIdentity.documentLabel}: ${fiscalIdentity.documentValue || "-"}`,
      fontSize: 10,
      color: "#455A64",
    },
  ];

  if (fiscalIdentity.address) {
    textStack.push({
      text: fiscalIdentity.address,
      fontSize: 9,
      color: "#546E7A",
      margin: [0, 3, 0, 0],
    });
  }

  if (fiscalIdentity.stateRegistration) {
    textStack.push({
      text: `IE: ${fiscalIdentity.stateRegistration}`,
      fontSize: 8,
      color: "#607D8B",
      margin: [0, 2, 0, 0],
    });
  }

  const headerColumns = logoDataUrl
    ? [
        {
          width: 70,
          image: logoDataUrl,
          fit: [60, 60],
        },
        {
          width: "*",
          stack: textStack,
          margin: [10, 5, 0, 0],
        },
      ]
    : [
        {
          width: "*",
          stack: textStack,
          margin: [0, 5, 0, 0],
        },
      ];

  return {
    margin: [pageMargins[0], 18, pageMargins[2], 0],
    stack: [
      { columns: headerColumns },
      {
        margin: [0, 10, 0, 0],
        canvas: [
          {
            type: "line",
            x1: 0,
            y1: 0,
            x2: headerLineWidth,
            y2: 0,
            lineWidth: 0.7,
            lineColor: "#D0D7DE",
          },
        ],
      },
    ],
  };
}

export function resolveFiscalIdentity(profile: MeResponseDTO | null): FiscalIdentity {
  const taxPersonType = `${profile?.taxPersonType || ""}`.toUpperCase();
  const isCnpj = taxPersonType === "CNPJ" || (!taxPersonType && Boolean(`${profile?.taxCnpj || ""}`.trim()));

  const fiscalName = (
    isCnpj
      ? `${profile?.taxCompanyName || ""}`.trim() || `${profile?.taxLandlordName || ""}`.trim()
      : `${profile?.taxLandlordName || ""}`.trim() || `${profile?.taxCompanyName || ""}`.trim()
  ) || "Perfil";

  const documentLabel: "CPF" | "CNPJ" = isCnpj ? "CNPJ" : "CPF";
  const documentValue = isCnpj ? maskCNPJ(`${profile?.taxCnpj || ""}`) : maskCPF(`${profile?.taxCpf || ""}`);

  return {
    fiscalName,
    documentLabel,
    documentValue,
    address: `${profile?.taxAddress || ""}`.trim(),
    stateRegistration: `${profile?.taxIe || ""}`.trim(),
  };
}

async function loadLogoDataUrl(rawLogoUrl: string): Promise<string> {
  const candidates = resolveLogoCandidates(rawLogoUrl);
  for (const candidate of candidates) {
    const dataUrl = await loadImageAsDataUrl(candidate);
    if (dataUrl) {
      return dataUrl;
    }
  }
  return "";
}

function resolveLogoCandidates(rawLogoUrl: string): string[] {
  const value = `${rawLogoUrl || ""}`.trim();
  if (!value) {
    return [];
  }

  const candidates = [resolveApiUrl(value), resolveS3LogoUrl(value)];
  return candidates.filter((item, index) => Boolean(item) && candidates.indexOf(item) === index);
}

function resolveS3LogoUrl(path: string): string {
  const value = `${path || ""}`.trim();
  if (!value) {
    return "";
  }

  if (
    value.startsWith("http://") ||
    value.startsWith("https://") ||
    value.startsWith("blob:") ||
    value.startsWith("data:")
  ) {
    return value;
  }

  const s3Base = `${process.env.REACT_APP_S3_URL || ""}`.trim().replace(/\/$/, "");
  if (!s3Base) {
    return "";
  }

  const normalizedPath = value.startsWith("/") ? value : `/${value}`;
  return `${s3Base}${normalizedPath}`;
}

export async function loadImageAsDataUrl(url: string): Promise<string> {
  const normalizedUrl = `${url || ""}`.trim();
  if (!normalizedUrl) {
    return "";
  }

  if (isImageDataUrl(normalizedUrl)) {
    return normalizedUrl;
  }

  if (normalizedUrl.startsWith("data:")) {
    return "";
  }

  const memoryCached = imageDataUrlMemoryCache.get(normalizedUrl);
  if (memoryCached) {
    return memoryCached;
  }

  const localCacheKey = `${IMAGE_DATA_URL_CACHE_PREFIX}${encodeURIComponent(normalizedUrl)}`;
  const localCached = readLocalImageCache(localCacheKey);
  if (localCached) {
    imageDataUrlMemoryCache.set(normalizedUrl, localCached);
    return localCached;
  }

  try {
    const response = await api.get<Blob>(normalizedUrl, { responseType: "blob" });
    const fromApi = await blobToImageDataUrl(response.data);
    if (fromApi) {
      imageDataUrlMemoryCache.set(normalizedUrl, fromApi);
      writeLocalImageCache(localCacheKey, fromApi);
      return fromApi;
    }
    throw new Error("invalid image data from api");
  } catch (apiError) {
    try {
      const response = await fetch(normalizedUrl);
      if (!response.ok) {
        throw new Error(`status ${response.status}`);
      }
      const blob = await response.blob();
      const fromFetch = await blobToImageDataUrl(blob);
      if (fromFetch) {
        imageDataUrlMemoryCache.set(normalizedUrl, fromFetch);
        writeLocalImageCache(localCacheKey, fromFetch);
        return fromFetch;
      }
      throw new Error("invalid image data from fetch");
    } catch (fetchError) {
      console.warn("[PDF] image load failed", fetchError || apiError);
    }
  }

  return "";
}

function readLocalImageCache(key: string): string {
  try {
    return `${localStorage.getItem(key) || ""}`;
  } catch (_error) {
    return "";
  }
}

function writeLocalImageCache(key: string, dataUrl: string) {
  try {
    localStorage.setItem(key, dataUrl);
  } catch (_error) {
    // ignore localStorage failures
  }
}

async function blobToImageDataUrl(blob: Blob): Promise<string> {
  const blobType = `${blob?.type || ""}`.toLowerCase();
  if (!blobType.startsWith("image/")) {
    return "";
  }
  const dataUrl = await blobToDataUrl(blob);
  return isImageDataUrl(dataUrl) ? dataUrl : "";
}

function blobToDataUrl(blob: Blob): Promise<string> {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => resolve(`${reader.result || ""}`);
    reader.onerror = () => reject(reader.error || new Error("Image read failed"));
    reader.readAsDataURL(blob);
  });
}

function isImageDataUrl(value: string): boolean {
  return /^data:image\//i.test(`${value || ""}`);
}
