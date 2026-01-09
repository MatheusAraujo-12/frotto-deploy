import * as pdfMake from "pdfmake/build/pdfmake";
import * as pdfFonts from "pdfmake/build/vfs_fonts";
import { Filesystem, Directory } from "@capacitor/filesystem";
import { FileOpener } from "@capacitor-community/file-opener";
// import { FileOpener as fileOpener } from "@ionic-native/file-opener";
import { isPlatform } from "@ionic/react";
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

(pdfMake as any).vfs = pdfFonts.pdfMake.vfs;

export const createMonthlyReport = (reportsMonthly: ReportsMonthly) => {
  const docRef = {
    content: createContentReportMonthly(reportsMonthly),
    styles: {
      header: {
        fontSize: 18,
        bold: true,
      },
      subheader: {
        fontSize: 15,
        bold: true,
      },
      mt: {
        margin: marginTop,
      },
      mb: {
        margin: marginBottom,
      },
      mb30: {
        margin: marginBottom30,
      },
      tableHeader: {
        bold: true,
        fontSize: 13,
        color: "black",
      },
    },
  };

  const pdfObj = pdfMake.createPdf(docRef);
  openFile(pdfObj);
};

export const createHistoryReport = (
  reportsHistory: ReportsHistory[],
  group: string
) => {
  const docRef = {
    content: createContentReportHistory(reportsHistory, group),
    styles: {
      header: {
        fontSize: 18,
        bold: true,
      },
      subheader: {
        fontSize: 15,
        bold: true,
      },
      mt: {
        margin: marginTop,
      },
      mb: {
        margin: marginBottom,
      },
      mb30: {
        margin: marginBottom30,
      },
      tableHeader: {
        bold: true,
        fontSize: 13,
        color: "black",
      },
    },
  };

  const pdfObj = pdfMake.createPdf(docRef);
  openFile(pdfObj);
};

export const createMaintenanceReport = (
  reportsMaintenance: ReportDTO[],
  group: string,
  year: string
) => {
  const docRef = {
    content: createContentReportMaintenance(reportsMaintenance, group, year),
    styles: {
      header: {
        fontSize: 18,
        bold: true,
      },
      subheader: {
        fontSize: 15,
        bold: true,
      },
      mt: {
        margin: marginTop,
      },
      mb: {
        margin: marginBottom,
      },
      mb30: {
        margin: marginBottom30,
      },
      tableHeader: {
        bold: true,
        fontSize: 13,
        color: "black",
      },
    },
  };

  const pdfObj = pdfMake.createPdf(docRef);
  openFile(pdfObj);
};

const openFile = (pdfObj: pdfMake.TCreatedPdf) => {
  const directory = Directory.Cache;
  const fileName = "frotto_report.pdf";
  if (isPlatform("cordova")) {
    try {
      pdfObj.getBase64(async (base64) => {
        console.log("base64" + base64);
        await Filesystem.writeFile({
          path: fileName,
          data: base64,
          directory: directory,
        });
        const result = await Filesystem.getUri({
          directory: directory,
          path: fileName,
        });
        FileOpener.open({
          filePath: result.uri,
          contentType: "application/pdf",
        });
      });
    } catch (e) {
      console.log("não foi possivel salvar arquivo");
    }
  } else {
    // pdfObj.download(fileName);
    pdfObj.open();
  }
};
