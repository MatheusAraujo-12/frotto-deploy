import { DocumentModel } from "../../constants/DocumentModels";
import documentService from "../../services/documentService";
import { formatDateView } from "../../services/dateFormat";
import { checklistTypeLabel } from "../Documents/checklistUtils";

/** Checklist drafts of a car (this account, as the backend lists them), most recently updated first. */
export async function loadChecklistDrafts(carId: number | string): Promise<DocumentModel[]> {
  const documents = await documentService.listDocuments({
    carId: Number(carId),
    type: "ENTREGA_DEVOLUCAO_CHECKLIST",
    status: "DRAFT",
    limit: 50,
  });
  return (Array.isArray(documents) ? documents : [])
    .filter((document) => document.status === "DRAFT" && document.type === "ENTREGA_DEVOLUCAO_CHECKLIST")
    .sort((a, b) => `${b.updatedAt || ""}`.localeCompare(`${a.updatedAt || ""}`));
}

/** "Checklist de Entrega", "Checklist de Devolução" or the older form (no type). */
export function checklistDraftLabel(document: DocumentModel): string {
  return document.checklistType ? `Checklist de ${checklistTypeLabel(document.checklistType)}` : "Checklist (formato anterior)";
}

/** "#12 · João Silva · atualizado em 08/10/2026". */
export function checklistDraftDetails(document: DocumentModel): string {
  return [
    `#${document.id}`,
    document.driverName || "",
    document.updatedAt ? `atualizado em ${formatDateView(document.updatedAt)}` : "",
  ]
    .filter(Boolean)
    .join(" · ");
}
