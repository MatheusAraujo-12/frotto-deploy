import * as pdfMake from "pdfmake/build/pdfmake";
import * as pdfFonts from "pdfmake/build/vfs_fonts";
import {
  DocumentModel,
  DocumentType,
} from "../../constants/DocumentModels";
import {
  buildPdfLetterhead,
  loadPdfLetterheadData,
  PDF_LETTERHEAD_PAGE_MARGINS,
  resolveFiscalIdentity,
} from "../../services/pdfLetterhead";
import { formatCurrencyPtBr, parseDecimal } from "../../services/decimalPtBr";

const vfsFonts =
  (pdfFonts as any).pdfMake?.vfs || (pdfFonts as any).default || (pdfFonts as any);
(pdfMake as any).vfs = vfsFonts;

export async function generateDocumentPdf(document: DocumentModel): Promise<void> {
  const blob = await createDocumentPdfBlob(document);
  downloadBlob(blob, buildPdfFileName(document));
}

export async function createDocumentPdfBlob(document: DocumentModel): Promise<Blob> {
  const letterheadData = await loadPdfLetterheadData();
  const fiscal = resolveFiscalIdentity(letterheadData.profile);
  const payload = document.payload || {};
  const content = buildDocumentContent(document.type, document, payload, fiscal);

  const docDefinition: any = {
    pageMargins: PDF_LETTERHEAD_PAGE_MARGINS,
    header: (_currentPage: number, _pageCount: number, pageSize: any) =>
      buildPdfLetterhead(
        letterheadData.profile,
        letterheadData.avatarDataUrl,
        pageSize,
        PDF_LETTERHEAD_PAGE_MARGINS
      ) as any,
    content: content as any,
    styles: {
      title: { fontSize: 14, bold: true, alignment: "center", margin: [0, 0, 0, 12] },
      section: { fontSize: 10, margin: [0, 0, 0, 8], lineHeight: 1.3 },
      label: { bold: true },
      signatureLine: { margin: [0, 34, 0, 0], fontSize: 10, alignment: "center" },
      small: { fontSize: 8, color: "#607D8B" },
      tableHeader: { bold: true, fillColor: "#F5F7FA", fontSize: 9 },
    },
    defaultStyle: {
      fontSize: 10,
      color: "#263238",
    },
  };

  return new Promise((resolve, reject) => {
    try {
      pdfMake.createPdf(docDefinition).getBlob((blob: Blob) => resolve(blob));
    } catch (error) {
      reject(error);
    }
  });
}

function buildDocumentContent(
  type: DocumentType,
  document: DocumentModel,
  payload: Record<string, any>,
  fiscal: ReturnType<typeof resolveFiscalIdentity>
) {
  if (type === "RECIBO_ALUGUEL") {
    return buildReciboAluguelContent(document, payload, fiscal);
  }
  if (type === "MULTA") {
    return buildMultaContent(document, payload, fiscal);
  }
  if (type === "MANUTENCAO_COMPARTILHADA") {
    return buildManutencaoCompartilhadaContent(document, payload, fiscal);
  }
  if (type === "CONFISSAO_DIVIDA") {
    return buildConfissaoDividaContent(document, payload, fiscal);
  }
  return buildEntregaDevolucaoChecklistContent(document, payload, fiscal);
}

function buildReciboAluguelContent(
  document: DocumentModel,
  payload: Record<string, any>,
  fiscal: ReturnType<typeof resolveFiscalIdentity>
) {
  const driverName = getText(payload, "driverName", document.driverName || "Motorista");
  const driverCpf = getText(payload, "driverCpf", document.driverCpf || "");
  const vehicle = `${document.carModel || getText(payload, "carModel", "Veículo")} - ${document.carPlate || getText(payload, "carPlate", "-")}`;
  const valorFinal = getNumber(payload, "valorFinal", 0);

  return [
    { text: "RECIBO DE PAGAMENTO DE ALUGUEL DE VEÍCULO", style: "title" },
    {
      text:
        `Recebi de ${driverName}${driverCpf ? ` (CPF ${driverCpf})` : ""}, a quantia de ${formatCurrency(valorFinal)}, ` +
        `referente à locação do veículo ${vehicle}, período de ${formatDate(getText(payload, "periodoInicio"))} ` +
        `a ${formatDate(getText(payload, "periodoFim"))}. Declaro plena quitação do valor ora recebido, ` +
        `sem prejuízo das demais obrigações contratuais eventualmente vigentes.`,
      style: "section",
    },
    {
      text:
        `Recebedor: ${fiscal.fiscalName} - ${fiscal.documentLabel}: ${fiscal.documentValue || "-"}${
          fiscal.address ? ` - Endereço: ${fiscal.address}` : ""
        }`,
      style: "section",
    },
    {
      text:
        `Detalhamento financeiro: Aluguel ${formatCurrency(getNumber(payload, "valorAluguel"))}; ` +
        `Descontos ${formatCurrency(getNumber(payload, "descontos"))}; ` +
        `Acréscimos ${formatCurrency(getNumber(payload, "acrescimos"))}; ` +
        `Valor final ${formatCurrency(valorFinal)}.`,
      style: "section",
    },
    {
      text:
        `Forma de pagamento: ${getText(payload, "formaPagamento", "-")}. ` +
        `Data do pagamento: ${formatDate(getText(payload, "dataPagamento"))}.`,
      style: "section",
    },
    { text: `Observações: ${getText(payload, "observacoes", "-")}`, style: "section" },
    ...buildSignatureBlock(fiscal.fiscalName, driverName),
  ];
}

function buildMultaContent(
  document: DocumentModel,
  payload: Record<string, any>,
  fiscal: ReturnType<typeof resolveFiscalIdentity>
) {
  const driverName = getText(payload, "driverName", document.driverName || "Motorista");
  const driverCpf = getText(payload, "driverCpf", document.driverCpf || "");

  return [
    { text: "NOTIFICAÇÃO E TERMO DE CIÊNCIA E RESPONSABILIDADE POR INFRAÇÃO DE TRÂNSITO", style: "title" },
    {
      text:
        `${fiscal.fiscalName} (${fiscal.documentLabel} ${fiscal.documentValue || "-"}) notifica ${driverName}${
          driverCpf ? ` (CPF ${driverCpf})` : ""
        }, ` +
        `quanto à infração de trânsito vinculada ao veículo ${document.carPlate || getText(payload, "carPlate", "-")}, ` +
        `ocorrida em ${formatDateTime(getText(payload, "dataHora"))}, no local ${getText(payload, "local", "-")}.`,
      style: "section",
    },
    {
      text:
        `Dados da infração: AIT ${getText(payload, "ait", "-")}; Órgão autuador ${getText(payload, "orgao", "-")}; ` +
        `Enquadramento ${getText(payload, "enquadramento", "-")}; Valor ${formatCurrency(getNumber(payload, "valor"))}; ` +
        `Vencimento ${formatDate(getText(payload, "vencimento"))}.`,
      style: "section",
    },
    {
      text:
        "Cláusula de responsabilidade: o condutor declara ciência e assume integral responsabilidade pelo pagamento da infração, " +
        "incluindo encargos legais. Caso o pagamento inicial seja realizado pela empresa, fica o motorista obrigado ao reembolso " +
        "integral no prazo estipulado, sob pena de registro de débito e demais medidas cabíveis.",
      style: "section",
    },
    { text: `Responsável pelo pagamento: ${getText(payload, "responsavelPagamento", "-")}.`, style: "section" },
    { text: `Observações: ${getText(payload, "observacoes", "-")}`, style: "section" },
    ...buildAttachmentsSection(payload.attachments || document.attachments || []),
    ...buildSignatureBlock(fiscal.fiscalName, driverName),
  ];
}

function buildManutencaoCompartilhadaContent(
  document: DocumentModel,
  payload: Record<string, any>,
  fiscal: ReturnType<typeof resolveFiscalIdentity>
) {
  const driverName = getText(payload, "driverName", document.driverName || "Motorista");
  const valorTotal = getNumber(payload, "valorTotal");
  const parteMotorista = getNumber(payload, "parteMotoristaValor");

  return [
    { text: "ACORDO DE RATEIO DE DESPESAS DE MANUTENÇÃO", style: "title" },
    {
      text:
        `${fiscal.fiscalName}, ${fiscal.documentLabel} ${fiscal.documentValue || "-"}, e ${driverName}, ` +
        "ajustam o rateio da manutenção do veículo nos seguintes termos.",
      style: "section",
    },
    {
      text:
        `Data: ${formatDate(getText(payload, "data"))}; Oficina: ${getText(payload, "oficina", "-")}; ` +
        `Descrição: ${getText(payload, "descricao", "-")}.`,
      style: "section",
    },
    {
      text:
        `Valor total: ${formatCurrency(valorTotal)}. Forma de divisão: ${getText(payload, "formaDivisao", "-")}. ` +
        `Parcela do motorista: ${formatCurrency(parteMotorista)}.`,
      style: "section",
    },
    {
      text:
        "O valor devido pelo motorista deverá ser pago no prazo ajustado entre as partes. Em caso de inadimplemento, " +
        "incidirão multa contratual de 2% e juros simples de 1% ao mês, sem prejuízo de atualização monetária e cobrança judicial.",
      style: "section",
    },
    { text: `Observações: ${getText(payload, "observacoes", "-")}`, style: "section" },
    ...buildAttachmentsSection(payload.attachments || document.attachments || []),
    ...buildSignatureBlock(fiscal.fiscalName, driverName),
  ];
}

function buildConfissaoDividaContent(
  document: DocumentModel,
  payload: Record<string, any>,
  fiscal: ReturnType<typeof resolveFiscalIdentity>
) {
  const driverName = getText(payload, "driverName", document.driverName || "Motorista");
  const driverCpf = getText(payload, "driverCpf", document.driverCpf || "");
  const formaPagamento = getText(payload, "formaPagamento", "A_VISTA");
  const qtdParcelas = getNumber(payload, "parcelasQtd");
  const valorParcela = getNumber(payload, "valorParcela");

  return [
    { text: "INSTRUMENTO PARTICULAR DE CONFISSÃO E RECONHECIMENTO DE DÍVIDA", style: "title" },
    {
      text:
        `Credor: ${fiscal.fiscalName}, ${fiscal.documentLabel} ${fiscal.documentValue || "-"}${
          fiscal.address ? `, endereço ${fiscal.address}` : ""
        }.`,
      style: "section",
    },
    {
      text:
        `Devedor: ${driverName}${driverCpf ? `, CPF ${driverCpf}` : ""}. ` +
        `Origem da dívida: ${getText(payload, "origemDaDivida", "-")}.`,
      style: "section",
    },
    {
      text:
        `Valor total reconhecido: ${formatCurrency(getNumber(payload, "valorTotal"))}. ` +
        `Forma de pagamento: ${formaPagamento}.`,
      style: "section",
    },
    formaPagamento === "PARCELADO"
      ? {
          text:
            `Parcelamento: ${qtdParcelas} parcelas de ${formatCurrency(valorParcela)}, com início em ${formatDate(
              getText(payload, "vencimentoInicial")
            )}.`,
          style: "section",
        }
      : {
          text: `Pagamento à vista com vencimento em ${formatDate(getText(payload, "vencimentoInicial"))}.`,
          style: "section",
        },
    {
      text:
        "Inadimplemento: o não pagamento de qualquer parcela implicará vencimento antecipado das demais, " +
        "multa de 2%, juros de 1% ao mês, correção monetária e demais encargos legais.",
      style: "section",
    },
    {
      text:
        "Fica eleito o foro do domicílio do credor para dirimir eventuais controvérsias oriundas deste instrumento, " +
        "com renúncia de qualquer outro, por mais privilegiado que seja.",
      style: "section",
    },
    { text: `Observações: ${getText(payload, "observacoes", "-")}`, style: "section" },
    ...buildWitnessSection(payload),
    ...buildSignatureBlock(fiscal.fiscalName, driverName),
  ];
}

function buildEntregaDevolucaoChecklistContent(
  document: DocumentModel,
  payload: Record<string, any>,
  fiscal: ReturnType<typeof resolveFiscalIdentity>
) {
  const driverName = getText(payload, "driverName", document.driverName || "Motorista");
  const checklistItens = Array.isArray(payload.checklistItens) ? payload.checklistItens : [];

  return [
    { text: "TERMO DE ENTREGA/DEVOLUÇÃO DE VEÍCULO COM CHECKLIST E VISTORIA", style: "title" },
    {
      text:
        `Pelo presente termo, ${fiscal.fiscalName} (${fiscal.documentLabel} ${fiscal.documentValue || "-"}) e ${driverName} ` +
        `registram a ${getText(payload, "tipo", "ENTREGA")} do veículo ${document.carPlate || getText(payload, "carPlate", "-")} ` +
        `em ${formatDateTime(getText(payload, "dataHora"))}.`,
      style: "section",
    },
    {
      text:
        `Quilometragem: ${getText(payload, "km", "-")} km. Combustível: ${getText(payload, "combustivel", "-")}.`,
      style: "section",
    },
    {
      table: {
        headerRows: 1,
        widths: ["*", 60, "*"],
        body: [
          [
            { text: "Item", style: "tableHeader" },
            { text: "OK", style: "tableHeader" },
            { text: "Observação", style: "tableHeader" },
          ],
          ...checklistItens.map((item: any) => [
            getText(item, "item", "-"),
            item?.ok ? "Sim" : "Não",
            getText(item, "observacao", "-"),
          ]),
        ],
      },
      layout: "lightHorizontalLines",
      margin: [0, 0, 0, 10],
    },
    { text: `Avarias registradas: ${getText(payload, "avariasTexto", "-")}`, style: "section" },
    ...buildAttachmentsSection(payload.fotos || payload.attachments || document.attachments || []),
    {
      text:
        "As partes declaram ciência quanto ao estado do veículo e à responsabilidade por danos não registrados neste ato, " +
        "salvo vícios ocultos e hipóteses legalmente excepcionadas.",
      style: "section",
    },
    ...buildSignatureBlock(fiscal.fiscalName, driverName),
  ];
}

function buildSignatureBlock(fiscalName: string, driverName: string) {
  const today = new Date();
  return [
    {
      text: `Local e data: ________________________, ${today.toLocaleDateString("pt-BR")}.`,
      style: "section",
      margin: [0, 18, 0, 0],
    },
    {
      columns: [
        {
          width: "*",
          stack: [
            { text: "__________________________________________", style: "signatureLine" },
            { text: fiscalName, alignment: "center", fontSize: 9 },
            { text: "Credor/Locador", alignment: "center", fontSize: 8, color: "#607D8B" },
          ],
        },
        {
          width: "*",
          stack: [
            { text: "__________________________________________", style: "signatureLine" },
            { text: driverName, alignment: "center", fontSize: 9 },
            { text: "Devedor/Motorista", alignment: "center", fontSize: 8, color: "#607D8B" },
          ],
        },
      ],
      columnGap: 28,
      margin: [0, 22, 0, 0],
    },
  ];
}

function buildWitnessSection(payload: Record<string, any>) {
  const witnessOneName = getText(payload, "testemunha1Nome", "");
  const witnessOneCpf = getText(payload, "testemunha1Cpf", "");
  const witnessTwoName = getText(payload, "testemunha2Nome", "");
  const witnessTwoCpf = getText(payload, "testemunha2Cpf", "");

  if (!witnessOneName && !witnessTwoName) {
    return [];
  }

  return [
    {
      text: "Testemunhas:",
      style: "section",
      margin: [0, 10, 0, 4],
    },
    {
      text: `1) ${witnessOneName || "____________________"} ${witnessOneCpf ? `- CPF ${witnessOneCpf}` : ""}`,
      style: "section",
    },
    {
      text: `2) ${witnessTwoName || "____________________"} ${witnessTwoCpf ? `- CPF ${witnessTwoCpf}` : ""}`,
      style: "section",
    },
  ];
}

function buildAttachmentsSection(attachments: string[]) {
  if (!attachments || !attachments.length) {
    return [];
  }
  return [
    {
      text: "Anexos registrados:",
      style: "section",
      margin: [0, 8, 0, 4],
    },
    {
      ul: attachments.map((item) => `${item}`),
      margin: [0, 0, 0, 10],
      fontSize: 9,
    },
  ];
}

function buildPdfFileName(document: DocumentModel): string {
  const date = new Date().toISOString().slice(0, 10);
  return `documento_${document.type.toLowerCase()}_${document.id || "novo"}_${date}.pdf`;
}

function downloadBlob(blob: Blob, fileName: string) {
  const url = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = url;
  link.download = fileName;
  document.body.appendChild(link);
  link.click();
  link.remove();
  URL.revokeObjectURL(url);
}

function getText(source: Record<string, any>, key: string, fallback = ""): string {
  const value = source?.[key];
  if (value === null || value === undefined) {
    return fallback;
  }
  const normalized = `${value}`.trim();
  return normalized || fallback;
}

function getNumber(source: Record<string, any>, key: string, fallback = 0): number {
  const value = source?.[key];
  if (value === null || value === undefined || value === "") {
    return fallback;
  }
  const parsed = parseDecimal(value);
  return parsed === null ? fallback : parsed;
}

function formatCurrency(value: number): string {
  return formatCurrencyPtBr(Number.isFinite(value) ? value : 0);
}

function formatDate(value?: string): string {
  if (!value) {
    return "-";
  }
  const parsed = new Date(value);
  if (Number.isNaN(parsed.getTime())) {
    return value;
  }
  return parsed.toLocaleDateString("pt-BR");
}

function formatDateTime(value?: string): string {
  if (!value) {
    return "-";
  }
  const parsed = new Date(value);
  if (Number.isNaN(parsed.getTime())) {
    return value;
  }
  return parsed.toLocaleString("pt-BR");
}
