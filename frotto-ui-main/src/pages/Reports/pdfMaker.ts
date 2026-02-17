import * as pdfMake from "pdfmake/build/pdfmake";
import * as pdfFonts from "pdfmake/build/vfs_fonts";
import {
  buildPdfLetterhead,
  loadPdfLetterheadData,
  PDF_LETTERHEAD_PAGE_MARGINS,
} from "../../services/pdfLetterhead";
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
  const headerData = await loadPdfLetterheadData();

  const docRef: any = {
    content,
    styles: getStyles(),
    pageMargins: PDF_LETTERHEAD_PAGE_MARGINS,
    header: (_currentPage: number, _pageCount: number, pageSize: any) =>
      buildPdfLetterhead(
        headerData.profile,
        headerData.avatarDataUrl,
        pageSize,
        PDF_LETTERHEAD_PAGE_MARGINS
      ),
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
