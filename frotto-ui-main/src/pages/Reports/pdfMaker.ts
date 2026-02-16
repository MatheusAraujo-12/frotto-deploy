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
const reportPageMargins: [number, number, number, number] = [40, 120, 40, 60];

const AVATAR_CACHE_KEY_PREFIX = "frotto:reports:avatar:dataurl:";
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
          width: 64,
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
    const avatarUrl = resolveAvatarUrl(profile);
    const avatarDataUrl = await loadAvatarDataUrl(avatarUrl);
    return { profile, avatarDataUrl: avatarDataUrl || null };
  } catch (_error) {
    return { profile: null, avatarDataUrl: null };
  }
}

function resolveAvatarUrl(profile: MeResponseDTO | null): string {
  if (!profile) {
    return "";
  }

  const rawUrl = `${profile.avatarUrl ?? profile.imageUrl ?? ""}`.trim();
  if (!rawUrl) {
    return "";
  }

  return resolveApiUrl(rawUrl);
}

async function loadAvatarDataUrl(avatarUrl: string): Promise<string> {
  const normalizedUrl = `${avatarUrl || ""}`.trim();
  if (!normalizedUrl) {
    return "";
  }

  if (normalizedUrl.startsWith("data:")) {
    return normalizedUrl;
  }

  const memoryCached = avatarMemoryCache.get(normalizedUrl);
  if (memoryCached) {
    return memoryCached;
  }

  const cacheKey = `${AVATAR_CACHE_KEY_PREFIX}${encodeURIComponent(normalizedUrl)}`;
  const localCached = readAvatarCache(cacheKey);
  if (localCached) {
    avatarMemoryCache.set(normalizedUrl, localCached);
    return localCached;
  }

  const downloadedDataUrl = await downloadAvatarDataUrl(normalizedUrl);
  if (!downloadedDataUrl) {
    return "";
  }

  avatarMemoryCache.set(normalizedUrl, downloadedDataUrl);
  writeAvatarCache(cacheKey, downloadedDataUrl);
  return downloadedDataUrl;
}

async function downloadAvatarDataUrl(url: string): Promise<string | null> {
  try {
    const response = await api.get<Blob>(url, { responseType: "blob" });
    return await blobToDataUrl(response.data);
  } catch (_error) {
    return null;
  }
}

function blobToDataUrl(blob: Blob): Promise<string> {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => resolve(`${reader.result || ""}`);
    reader.onerror = () => reject(reader.error || new Error("Avatar read failed"));
    reader.readAsDataURL(blob);
  });
}

function readAvatarCache(cacheKey: string): string | null {
  try {
    const data = localStorage.getItem(cacheKey);
    return data || null;
  } catch (_error) {
    return null;
  }
}

function writeAvatarCache(cacheKey: string, dataUrl: string) {
  try {
    localStorage.setItem(cacheKey, dataUrl);
  } catch (_error) {
    // ignore cache write failures (quota/private mode)
  }
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
