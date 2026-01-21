import * as pdfMake from "pdfmake/build/pdfmake";
import * as pdfFonts from "pdfmake/build/vfs_fonts";
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
  const docRef = {
    content: createContentReportMonthly(reportsMonthly),
    styles: getStyles(),
  };

  const pdfObj = pdfMake.createPdf(docRef);
  openFileWeb(pdfObj, "frotto_report.pdf");
};

export const createHistoryReport = (reportsHistory: ReportsHistory[], group: string) => {
  const docRef = {
    content: createContentReportHistory(reportsHistory, group),
    styles: getStyles(),
  };

  const pdfObj = pdfMake.createPdf(docRef);
  openFileWeb(pdfObj, "frotto_report.pdf");
};

export const createMaintenanceReport = (
  reportsMaintenance: ReportDTO[],
  group: string,
  year: string
) => {
  const docRef = {
    content: createContentReportMaintenance(reportsMaintenance, group, year),
    styles: getStyles(),
  };

  const pdfObj = pdfMake.createPdf(docRef);
  openFileWeb(pdfObj, "frotto_report.pdf");
};

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
 * WEB: baixa o PDF (recomendado)
 * Você também pode trocar por pdfObj.open() se preferir abrir em nova aba.
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

    // libera memória
    URL.revokeObjectURL(url);
  });
}
