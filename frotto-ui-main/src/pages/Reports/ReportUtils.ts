import { currencyFormat } from "../../services/currencyFormat";
import {
  formatDateView,
  formatDateViewMMYYYY,
  stringDateToMonthYear,
} from "../../services/dateFormat";

export interface ReportsMonthly {
  group: string;
  date: string;
  groupEarnings: number;
  groupExpenses: number;
  groupAdminFee: number;
  groupNetEarnings: number;
  groupPercentageProfit: number;
  groupInvested: number;

  reportsDTO: ReportDTO[];
}

export interface ReportsHistory {
  carId: number;
  carName: string;
  initialValue: number;
  color: string;
  year: number;

  totalEarnings: number;
  totalExpenses: number;
  totalAdminFee: number;
  totalNetEarnings: number;

  totalPercentageProfit: number;
  averagePercentageProfit: number;
  carHistory: CarHistory[];
}

interface CarHistory {
  carId: number;
  date: string;
  earnings: number;
  expenses: number;
  adminFee: number;
  netEarnings: number;
  odometer: number;
}

export interface ReportDTO {
  carId: number;
  carName: string;
  color: string;
  odometer: number;
  year: number;
  date: string;
  earnings: number;
  expenses: number;
  adminFee: number;
  netEarnings: number;
  initialValue: number;
  percentageProfit: number;
  incomes: ReportItem[];
  carExpenses: ReportItem[];
  inspections: ReportItem[];
  maintenances: ReportItem[];
}

interface ReportItem {
  name: string;
  date: string;
  ammount: number;
}

export const createContentReportMonthly = (reportsMonthly: ReportsMonthly) => {
  const content: any[] = [];

  content.push({ text: "Relatório mensal", style: "header" });
  content.push({
    text:
      reportsMonthly.group + " , " + stringDateToMonthYear(reportsMonthly.date),
    style: ["subheader", "mb"],
  });
  content.push("Somatório de todos os carros");
  content.push({
    style: ["mb"],
    table: {
      widths: ["*", "*", "*", "*", "*"],
      body: [
        [
          { text: "Receitas", style: "tableHeader" },
          { text: "Despesas", style: "tableHeader" },
          { text: "Saldo", style: "tableHeader" },
          { text: "Taxa Admin.", style: "tableHeader" },
          { text: "Lucro", style: "tableHeader" },
        ],
        [
          currencyFormat(reportsMonthly.groupEarnings),
          currencyFormat(reportsMonthly.groupExpenses),
          currencyFormat(
            reportsMonthly.groupEarnings - reportsMonthly.groupExpenses
          ),
          currencyFormat(reportsMonthly.groupAdminFee),
          currencyFormat(reportsMonthly.groupNetEarnings),
        ],
      ],
    },
  });
  content.push({
    style: "mb30",
    table: {
      widths: ["*", "*"],
      body: [
        [
          { text: "Total Investido", style: "tableHeader" },
          { text: "Lucro Percentual", style: "tableHeader" },
        ],
        [
          currencyFormat(reportsMonthly.groupInvested),
          reportsMonthly.groupPercentageProfit + "%",
        ],
      ],
    },
  });

  reportsMonthly.reportsDTO.forEach((reportDto) => {
    content.push({
      stack: [
        {
          text: reportDto.carName,
          style: ["header"],
        },
        {
          text:
            "Ano " +
            reportDto.year +
            ", " +
            reportDto.color +
            ", " +
            reportDto.odometer +
            "Km",
        },
        {
          style: ["mb"],
          table: {
            widths: ["*", "*", "*", "*", "*"],
            body: [
              [
                { text: "Receitas", style: "tableHeader" },
                { text: "Despesas", style: "tableHeader" },
                { text: "Saldo", style: "tableHeader" },
                { text: "Taxa Admin.", style: "tableHeader" },
                { text: "Lucro", style: "tableHeader" },
              ],
              [
                currencyFormat(reportDto.earnings),
                currencyFormat(reportDto.expenses),
                currencyFormat(reportDto.earnings - reportDto.expenses),
                currencyFormat(reportDto.adminFee),
                currencyFormat(reportDto.netEarnings),
              ],
            ],
          },
        },
        {
          style: ["mb"],
          table: {
            widths: ["*", "*"],
            body: [
              [
                { text: "Valor Investido no Carro", style: "tableHeader" },
                { text: "Lucro Percentual", style: "tableHeader" },
              ],
              [
                currencyFormat(reportDto.initialValue),
                reportDto.percentageProfit + "%",
              ],
            ],
          },
        },
      ],
      unbreakable: true,
    });

    content.push([
      {
        table: {
          headerRows: 1,
          widths: ["*", 85, 100],
          body: [
            [{ text: "Receitas", style: "tableHeader", colSpan: 3 }, {}, {}],
            ...createItensV2(reportDto.incomes),
          ],
        },
        layout: "headerLineOnly",
      },
      {
        table: {
          headerRows: 1,
          widths: ["*", 85, 100],
          body: [
            [
              {
                text: "Despesas - Manutenções",
                style: "tableHeader",
                colSpan: 3,
              },
              {},
              {},
            ],
            ...createItensV2(reportDto.maintenances),
          ],
        },
        layout: "headerLineOnly",
      },
      {
        table: {
          headerRows: 1,
          widths: ["*", 85, 100],
          body: [
            [
              {
                text: "Despesas - Inspeções",
                style: "tableHeader",
                colSpan: 3,
              },
              {},
              {},
            ],
            ...createItensV2(reportDto.inspections),
          ],
        },
        layout: "headerLineOnly",
      },
      {
        style: ["mb30"],
        table: {
          headerRows: 1,
          widths: ["*", 85, 100],
          body: [
            [
              { text: "Despesas - Outras", style: "tableHeader", colSpan: 3 },
              {},
              {},
            ],
            ...createItensV2(reportDto.carExpenses),
          ],
        },
        layout: "headerLineOnly",
      },
    ]);
  });

  return content;
};

const createItensV2 = (listItens: ReportItem[]) => {
  const items: any[] = [];
  listItens.forEach((item) => {
    items.push([
      { text: item.name, fontSize: 10 },
      {
        text: formatDateView(item.date),
        fontSize: 10,
      },
      currencyFormat(item.ammount),
    ]);
  });
  return items;
};

export const createContentReportHistory = (
  reportsHistory: ReportsHistory[],
  group: string
) => {
  const content: any[] = [];
  content.push({ text: "Relatório Histórico financeiro", style: "header" });
  content.push({
    text: group,
    style: ["subheader", "mb"],
  });
  reportsHistory.forEach((reportHistory) => {
    content.push(
      {
        text: reportHistory.carName,
        style: ["header"],
      },
      {
        text: "Ano " + reportHistory.year + ", " + reportHistory.color,
      }
    );
    content.push({
      style: ["mb"],
      table: {
        widths: ["*", "*", "*", "*", "*"],
        body: [
          [
            { text: "Receitas", style: "tableHeader" },
            { text: "Despesas", style: "tableHeader" },
            { text: "Saldo", style: "tableHeader" },
            { text: "Taxa Admin.", style: "tableHeader" },
            { text: "Lucro", style: "tableHeader" },
          ],
          [
            currencyFormat(reportHistory.totalEarnings),
            currencyFormat(reportHistory.totalExpenses),
            currencyFormat(
              reportHistory.totalEarnings - reportHistory.totalExpenses
            ),
            currencyFormat(reportHistory.totalAdminFee),
            currencyFormat(reportHistory.totalNetEarnings),
          ],
        ],
      },
    });
    content.push({
      style: ["mb"],
      table: {
        widths: ["*", "*", "*"],
        body: [
          [
            { text: "Valor Investido", style: "tableHeader" },
            { text: "Lucro %", style: "tableHeader" },
            { text: "Lucro Mensal Médio ", style: "tableHeader" },
          ],
          [
            currencyFormat(reportHistory.initialValue),
            reportHistory.totalPercentageProfit + "%",
            reportHistory.averagePercentageProfit + "%",
          ],
        ],
      },
    });
    content.push({
      style: ["mb30"],
      table: {
        widths: ["*", "*", "*", "*", "*"],
        body: [
          [
            { text: "Data", style: "tableHeader" },
            { text: "Receita", style: "tableHeader" },
            { text: "Despesa", style: "tableHeader" },
            { text: "Taxa Adm.", style: "tableHeader" },
            { text: "Lucro", style: "tableHeader" },
          ],
          ...historyItens(reportHistory.carHistory),
        ],
      },
      layout: "headerLineOnly",
    });
  });

  return content;
};

const historyItens = (carHistory: CarHistory[]) => {
  const items: any[] = [];

  carHistory.forEach((item) => {
    items.push([
      { text: formatDateViewMMYYYY(item.date), fontSize: 10 },
      { text: item.earnings, fontSize: 10 },
      { text: item.expenses, fontSize: 10 },
      { text: item.adminFee, fontSize: 10 },
      { text: item.netEarnings, fontSize: 10 },
    ]);
  });
  return items;
};

export const createContentReportMaintenance = (
  reportDtos: ReportDTO[],
  group: string,
  year: string
) => {
  const content: any[] = [];
  content.push({ text: "Relatório de Manutenções - " + year, style: "header" });
  content.push({
    text: group,
    style: ["subheader", "mb"],
  });

  reportDtos.forEach((reportDto) => {
    content.push(
      {
        text: reportDto.carName,
        style: ["header"],
      },
      {
        text: "Ano " + reportDto.year + ", " + reportDto.color,
      }
    );

    content.push({
      style: ["mb30"],
      table: {
        widths: ["*", "*", "*"],
        body: [
          [
            { text: "Manutenção", style: "tableHeader" },
            { text: "Data", style: "tableHeader" },
            { text: "Valor", style: "tableHeader" },
          ],
          ...createItensV2(reportDto.maintenances),
        ],
      },
      layout: "headerLineOnly",
    });
  });

  return content;
};
