import { ChecklistType } from "./checklistUtils";

/**
 * Opening the existing Entrega/Devolução wizard of Documentos from another screen (Inspeções): the operation, the car
 * and where to go back when the wizard closes travel in the URL, so the user never navigates through Documentos.
 */
export interface ChecklistLaunch {
  checklistType: ChecklistType;
  carId: number;
  /** Internal path to return to when the wizard closes (only paths of the app menu). */
  returnTo: string | null;
}

export function checklistLaunchUrl(checklistType: ChecklistType, carId: number | string, returnTo?: string): string {
  const params = new URLSearchParams({ checklist: checklistType, carId: `${carId}` });
  if (returnTo) {
    params.set("from", returnTo);
  }
  return `/documents?${params.toString()}`;
}

/** The launch request of a /documents URL, or null when there is none (or it is not valid). */
export function parseChecklistLaunch(search: string): ChecklistLaunch | null {
  const params = new URLSearchParams(search || "");
  const checklistType = params.get("checklist");
  const carId = Number(params.get("carId"));
  if ((checklistType !== "ENTREGA" && checklistType !== "DEVOLUCAO") || !Number.isInteger(carId) || carId <= 0) {
    return null;
  }
  const from = params.get("from");
  // Never an external or arbitrary target: only a screen of the app menu.
  const returnTo = from && /^\/menu\/[A-Za-z0-9/_-]*$/.test(from) ? from : null;
  return { checklistType, carId, returnTo };
}
