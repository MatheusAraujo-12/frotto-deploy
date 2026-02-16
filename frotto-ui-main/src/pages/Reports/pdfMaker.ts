import * as pdfMake from "pdfmake/build/pdfmake";
import * as pdfFonts from "pdfmake/build/vfs_fonts";
import { maskCNPJ, maskCPF } from "../../services/profileFormat";
import api from "../../services/axios/axios";
import profileService, { MeResponseDTO } from "../../services/profileService";
import { resolveApiUrl } from "../../services/resolveApiUrl";
import {
  createContentReportHistory,
  createContentReportMaintenance,
  createContentReportMonthly,
  ReportDTO,
  ReportsHistory,
  ReportsMonthly,
} from "./ReportUtils";

const marginTop: [number, number, number, number] = [0, 15, 0, 0];
const marginBottom: [number, number, number, number] = [0, 0, 0, 15];
const marginBottom30: [number, number, number, number] = [0, 0, 0, 45];
const reportPageMargins: [number, number, number, number] = [40, 115, 40, 60];
const avatarMemoryCache = new Map<string, string>();

const vfsFonts =
  (pdfFonts as any).pdfMake?.vfs || (pdfFonts as any).default || (pdfFonts as any);
(pdfMake as any).vfs = vfsFonts;

export const createMonthlyReport = (reportsMonthly: ReportsMonthly) => {
  const content = createContentReportMonthly(reportsMonthly);
  void createReportPdf(content);
};

export const createHistoryReport = (reportsHistory: ReportsHistory[], group: string) => {
  const content = createContentReportHistory(reportsHistory, group);
  void createReportPdf(content);
};

export const createMaintenanceReport = (
  reportsMaintenance: ReportDTO[],
  group: string,
  year: string
) => {
  const content = createContentReportMaintenance(reportsMaintenance, group, year);
  void createReportPdf(content);
};

async function createReportPdf(content: any[]) {
  const headerData = await loadHeaderData();

  const docRef: any = {
    content,
    styles: getStyles(),
    pageMargins: reportPageMargins,
    header: (_currentPage: number, _pageCount: number, pageSize: any) =>
      buildReportHeader(headerData.profile, headerData.avatarDataUrl, pageSize),
  };

  const pdfObj = pdfMake.createPdf(docRef);
  openFileWeb(pdfObj, "frotto_report.pdf");
}

function getStyles() {
  return {
    header: { fontSize: 18, bold: true },
    subheader: { fontSize: 15, bold: true },
    mt: { margin: marginTop },
    mb: { margin: marginBottom },
    mb30: { margin: marginBottom30 },
    tableHeader: { bold: true, fontSize: 13, color: "black" },
  };
}

function buildReportHeader(
  profile: MeResponseDTO | null,
  avatarDataUrlOrNull: string | null,
  pageSize: any
) {
  const taxPersonType = `${profile?.taxPersonType || ""}`.toUpperCase();
  const isCnpj = taxPersonType === "CNPJ" || (!taxPersonType && Boolean(`${profile?.taxCnpj || ""}`.trim()));

  const displayName = (
    isCnpj
      ? `${profile?.taxCompanyName || ""}`.trim() || `${profile?.taxLandlordName || ""}`.trim()
      : `${profile?.taxLandlordName || ""}`.trim() || `${profile?.taxCompanyName || ""}`.trim()
  ) || "Perfil";

  const documentValue = isCnpj
    ? maskCNPJ(`${profile?.taxCnpj || ""}`)
    : maskCPF(`${profile?.taxCpf || ""}`);
  const documentLabel = isCnpj ? "CNPJ" : "CPF";

  const address = `${profile?.taxAddress || ""}`.trim();

  const textStack: any[] = [
    {
      text: displayName,
      fontSize: 12,
      bold: true,
      color: "#263238",
      margin: [0, 0, 0, 3],
    },
    {
      text: `${documentLabel}: ${documentValue || "-"}`,
      fontSize: 10,
      color: "#455A64",
    },
  ];

  if (address) {
    textStack.push({
      text: address,
      fontSize: 9,
      color: "#546E7A",
      margin: [0, 3, 0, 0],
    });
  }

  const headerLineWidth = Math.max(
    0,
    (Number(pageSize?.width) || 595) - reportPageMargins[0] - reportPageMargins[2]
  );

  const headerColumns = avatarDataUrlOrNull
    ? [
        {
          width: 70,
          image: avatarDataUrlOrNull,
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
    margin: [40, 18, 40, 0],
    stack: [
      {
        columns: headerColumns,
      },
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

async function loadHeaderData(): Promise<{ profile: MeResponseDTO | null; avatarDataUrl: string | null }> {
  try {
    const profile = await profileService.getMe();
    console.log("[PDF] avatarUrl", profile?.avatarUrl || null);
    const avatarCandidates = resolveAvatarCandidates(profile);
    const avatarDataUrl = await resolveAvatarDataUrl(avatarCandidates);
    console.log("[PDF] avatarDataUrl length", avatarDataUrl?.length || 0);
    return { profile, avatarDataUrl: avatarDataUrl || null };
  } catch (_error) {
    return { profile: null, avatarDataUrl: null };
  }
}

function resolveAvatarCandidates(profile: MeResponseDTO | null): string[] {
  if (!profile) {
    return [];
  }

  const rawCandidates = [`${profile.avatarUrl || ""}`.trim(), `${profile.imageUrl || ""}`.trim()]
    .filter((item, index, arr) => Boolean(item) && arr.indexOf(item) === index);
  if (!rawCandidates.length) {
    return [];
  }

  const candidates = rawCandidates.flatMap((rawUrl) => [resolveApiUrl(rawUrl), resolveS3AvatarUrl(rawUrl)]);
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

async function resolveAvatarDataUrl(avatarCandidates: string[]): Promise<string> {
  for (const candidate of avatarCandidates) {
    const dataUrl = await loadImageAsDataUrl(candidate);
    if (dataUrl) {
      return dataUrl;
    }
  }

  return "";
}

async function loadImageAsDataUrl(url: string): Promise<string> {
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

  try {
    const response = await api.get<Blob>(normalizedUrl, { responseType: "blob" });
    const fromApi = await blobToImageDataUrl(response.data);
    if (fromApi) {
      avatarMemoryCache.set(normalizedUrl, fromApi);
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
        return fromFetch;
      }

      throw new Error("invalid avatar image data from fetch");
    } catch (fetchError) {
      console.warn("[PDF] avatar load failed", fetchError || apiError);
    }
  }

  return "";
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

/**
 * WEB: downloads the generated PDF.
 * You can swap to pdfObj.open() to open in a new tab.
 */
function openFileWeb(pdfObj: pdfMake.TCreatedPdf, fileName: string) {
  pdfObj.getBlob((blob: Blob) => {
    const url = URL.createObjectURL(blob);

    const a = document.createElement("a");
    a.href = url;
    a.download = fileName;
    document.body.appendChild(a);
    a.click();
    a.remove();

    URL.revokeObjectURL(url);
  });
}
