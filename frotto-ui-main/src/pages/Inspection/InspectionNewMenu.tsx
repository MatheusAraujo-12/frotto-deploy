import { IonButton } from "@ionic/react";
import { useState } from "react";
import { useHistory } from "react-router";
import { TEXT } from "../../constants/texts";
import { DocumentModel } from "../../constants/DocumentModels";
import { IN_APP_LAUNCH_STATE, checklistLaunchUrl } from "../Documents/checklistLaunch";
import { ChecklistType, checklistTypeLabel } from "../Documents/checklistUtils";
import { checklistDraftDetails, loadChecklistDrafts } from "./checklistDraftData";
import "./ChecklistDrafts.css";

interface InspectionNewMenuProps {
  carId: string;
  /** Inspeção Avulsa: the current inspection form. */
  onSingleInspection: () => void;
  /** This screen: where the checklist returns when it closes (default: the car page). */
  returnTo?: string;
}

/**
 * "+ Novo" of the inspections: Inspeção Avulsa (as before) or a Checklist de Entrega / Devolução, filled in the
 * existing wizard of Documentos opened for this car (and back here when it closes). A draft of the same operation
 * already started for the car is offered first: continued, or a new checklist on purpose - never overwritten.
 */
const InspectionNewMenu: React.FC<InspectionNewMenuProps> = ({ carId, onSingleInspection, returnTo }) => {
  const [isOpen, setIsOpen] = useState(false);
  const [pending, setPending] = useState<{ type: ChecklistType; drafts: DocumentModel[] } | null>(null);
  const history = useHistory();
  const here = returnTo || `/menu/carros/${carId}`;

  const choose = (action: () => void) => {
    setIsOpen(false);
    setPending(null);
    action();
  };
  const launch = (type: ChecklistType | null, documentId?: number) =>
    history.push(checklistLaunchUrl(type, carId, here, documentId), IN_APP_LAUNCH_STATE);

  const startChecklist = async (type: ChecklistType) => {
    let drafts: DocumentModel[] = [];
    try {
      drafts = (await loadChecklistDrafts(carId)).filter((draft) => draft.checklistType === type);
    } catch (_error) {
      drafts = []; // the list is only a convenience: a new checklist can always be started
    }
    if (drafts.length) {
      setPending({ type, drafts });
      return;
    }
    choose(() => launch(type));
  };

  return (
    <>
      <IonButton
        className="app-semantic-btn app-semantic--neutral"
        size="small"
        fill="outline"
        aria-expanded={isOpen}
        aria-controls="inspection-new-options"
        onClick={() => {
          setIsOpen((open) => !open);
          setPending(null);
        }}
      >
        {TEXT.new}
      </IonButton>
      {isOpen && !pending && (
        <div id="inspection-new-options" className="app-actions-row" role="group" aria-label="Nova inspeção">
          <IonButton size="small" fill="outline" className="app-outline-btn" onClick={() => choose(onSingleInspection)}>
            Inspeção Avulsa
          </IonButton>
          <IonButton size="small" fill="outline" className="app-outline-btn" onClick={() => void startChecklist("ENTREGA")}>
            Checklist de Entrega
          </IonButton>
          <IonButton size="small" fill="outline" className="app-outline-btn" onClick={() => void startChecklist("DEVOLUCAO")}>
            Checklist de Devolução
          </IonButton>
        </div>
      )}
      {isOpen && pending && (
        <div id="inspection-new-options" className="checklist-drafts" role="group" aria-label="Rascunho existente" data-testid="checklist-draft-choice">
          <p className="checklist-drafts__title">
            Já existe rascunho de Checklist de {checklistTypeLabel(pending.type)} para este veículo:
          </p>
          {pending.drafts.map((draft) => (
            <div key={draft.id} className="checklist-drafts__item">
              <span className="checklist-drafts__text">{checklistDraftDetails(draft)}</span>
              <IonButton size="small" fill="outline" className="app-outline-btn" onClick={() => choose(() => launch(null, draft.id))}>
                Continuar rascunho #{draft.id}
              </IonButton>
            </div>
          ))}
          <div className="app-actions-row">
            <IonButton size="small" fill="outline" className="app-outline-btn" onClick={() => choose(() => launch(pending.type))}>
              Criar novo checklist
            </IonButton>
            <IonButton size="small" fill="clear" className="app-cancel-btn" onClick={() => setPending(null)}>
              Voltar
            </IonButton>
          </div>
        </div>
      )}
    </>
  );
};

export default InspectionNewMenu;
