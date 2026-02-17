import { maskCNPJ, maskCPF } from "./profileFormat";
import api from "./axios/axios";
import profileService, { MeResponseDTO } from "./profileService";
import { resolveApiUrl } from "./resolveApiUrl";

export const PDF_LETTERHEAD_PAGE_MARGINS: [number, number, number, number] = [40, 115, 40, 60];

const AVATAR_CACHE_PREFIX = "frotto:pdf:avatar:data-url:";
const avatarMemoryCache = new Map<string, string>();

type FiscalIdentity = {
  fiscalName: string;
  documentLabel: "CPF" | "CNPJ";
  documentValue: string;
  address: string;
  stateRegistration: string;
};

export async function loadPdfLetterheadData(): Promise<{ profile: MeResponseDTO | null; avatarDataUrl: string }> {
  try {
    const profile = await profileService.getMe();
    const rawAvatarUrl = `${profile.avatarUrl ?? profile.imageUrl ?? ""}`.trim();
    console.log("[PDF] avatarUrl", rawAvatarUrl || null);
    const avatarDataUrl = await loadAvatarDataUrl(rawAvatarUrl);
    console.log("[PDF] avatarDataUrl length", avatarDataUrl?.length || 0);
    return { profile, avatarDataUrl };
  } catch (_error) {
    return { profile: null, avatarDataUrl: "" };
  }
}

export function buildPdfLetterhead(
  profile: MeResponseDTO | null,
  avatarDataUrl: string,
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

  const headerColumns = avatarDataUrl
    ? [
        {
          width: 70,
          image: avatarDataUrl,
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

async function loadAvatarDataUrl(rawAvatarUrl: string): Promise<string> {
  const candidates = resolveAvatarCandidates(rawAvatarUrl);
  for (const candidate of candidates) {
    const dataUrl = await loadImageAsDataUrl(candidate);
    if (dataUrl) {
      return dataUrl;
    }
  }
  return "";
}

function resolveAvatarCandidates(rawAvatarUrl: string): string[] {
  const value = `${rawAvatarUrl || ""}`.trim();
  if (!value) {
    return [];
  }

  const candidates = [resolveApiUrl(value), resolveS3AvatarUrl(value)];
  return candidates.filter((item, index) => Boolean(item) && candidates.indexOf(item) === index);
}

function resolveS3AvatarUrl(path: string): string {
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

  const memoryCached = avatarMemoryCache.get(normalizedUrl);
  if (memoryCached) {
    return memoryCached;
  }

  const localCacheKey = `${AVATAR_CACHE_PREFIX}${encodeURIComponent(normalizedUrl)}`;
  const localCached = readLocalAvatarCache(localCacheKey);
  if (localCached) {
    avatarMemoryCache.set(normalizedUrl, localCached);
    return localCached;
  }

  try {
    const response = await api.get<Blob>(normalizedUrl, { responseType: "blob" });
    const fromApi = await blobToImageDataUrl(response.data);
    if (fromApi) {
      avatarMemoryCache.set(normalizedUrl, fromApi);
      writeLocalAvatarCache(localCacheKey, fromApi);
      return fromApi;
    }
    throw new Error("invalid avatar image data from api");
  } catch (apiError) {
    try {
      const response = await fetch(normalizedUrl);
      if (!response.ok) {
        throw new Error(`status ${response.status}`);
      }
      const blob = await response.blob();
      const fromFetch = await blobToImageDataUrl(blob);
      if (fromFetch) {
        avatarMemoryCache.set(normalizedUrl, fromFetch);
        writeLocalAvatarCache(localCacheKey, fromFetch);
        return fromFetch;
      }
      throw new Error("invalid avatar image data from fetch");
    } catch (fetchError) {
      console.warn("[PDF] avatar load failed", fetchError || apiError);
    }
  }

  return "";
}

function readLocalAvatarCache(key: string): string {
  try {
    return `${localStorage.getItem(key) || ""}`;
  } catch (_error) {
    return "";
  }
}

function writeLocalAvatarCache(key: string, dataUrl: string) {
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
    reader.onerror = () => reject(reader.error || new Error("Avatar read failed"));
    reader.readAsDataURL(blob);
  });
}

function isImageDataUrl(value: string): boolean {
  return /^data:image\//i.test(`${value || ""}`);
}
