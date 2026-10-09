import { ChecklistType } from "./checklistUtils";

/**
 * Opening the existing Entrega/Devolução wizard of Documentos from another screen (Inspeções): the operation (or the
 * draft being continued), the car and where to go back travel in the URL. The URL stays while the wizard is open
 * (a refresh reopens the same checklist), so the user never navigates through Documentos.
 */
export interface ChecklistLaunch {
  /** The operation of a new checklist (absent when continuing a draft: its own type is used). */
  checklistType: ChecklistType | null;
  carId: number;
  /** A draft being continued (also set after the first save of a new one, so a refresh reopens it). */
  documentId: number | null;
  /** Internal path to return to when the wizard closes (only paths of the app menu). */
  returnTo: string | null;
}

/** State of the history entry pushed by the app (a launch inside the app returns with history.goBack()). */
export const IN_APP_LAUNCH_STATE = { checklistLaunch: true };

export function checklistLaunchUrl(
  checklistType: ChecklistType | null,
  carId: number | string,
  returnTo?: string | null,
  documentId?: number | null
): string {
  const params = new URLSearchParams();
  if (checklistType) {
    params.set("checklist", checklistType);
  }
  params.set("carId", `${carId}`);
  if (documentId) {
    params.set("documentId", `${documentId}`);
  }
  if (returnTo) {
    params.set("from", returnTo);
  }
  return `/documents?${params.toString()}`;
}

/** The launch request of a /documents URL, or null when there is none (or it is not valid). */
export function parseChecklistLaunch(search: string): ChecklistLaunch | null {
  const params = new URLSearchParams(search || "");
  const rawType = params.get("checklist");
  const checklistType = rawType === "ENTREGA" || rawType === "DEVOLUCAO" ? rawType : null;
  const carId = Number(params.get("carId"));
  const rawDocument = params.get("documentId");
  const documentId = rawDocument === null ? null : Number(rawDocument);
  if (rawType !== null && !checklistType) {
    return null;
  }
  if (!Number.isInteger(carId) || carId <= 0) {
    return null;
  }
  if (documentId !== null && (!Number.isInteger(documentId) || documentId <= 0)) {
    return null;
  }
  if (!checklistType && !documentId) {
    return null;
  }
  const from = params.get("from");
  // Never an external or arbitrary target: only a screen of the app menu.
  const returnTo = from && /^\/menu\/[A-Za-z0-9/_-]*$/.test(from) ? from : null;
  return { checklistType, carId, documentId, returnTo };
}

/** The URL of the same launch with the draft saved meanwhile (a refresh reopens it). */
export function withLaunchDocument(launch: ChecklistLaunch, documentId: number): string {
  return checklistLaunchUrl(launch.checklistType, launch.carId, launch.returnTo, documentId);
}
