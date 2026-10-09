import { IonButton, useIonViewWillEnter } from "@ionic/react";
import { useCallback, useEffect, useState } from "react";
import { useHistory } from "react-router";
import FrottoBadge from "../../components/UI/FrottoBadge";
import { DocumentModel } from "../../constants/DocumentModels";
import { IN_APP_LAUNCH_STATE, checklistLaunchUrl } from "../Documents/checklistLaunch";
import { checklistDraftDetails, checklistDraftLabel, loadChecklistDrafts } from "./checklistDraftData";
import "./ChecklistDrafts.css";

interface ChecklistDraftsProps {
  carId: string;
  /** This screen: where the checklist returns when it closes. */
  returnTo: string;
}

/**
 * Checklist drafts of this car (Entrega, Devolução and the older form), each continued in the existing wizard; a
 * finalized checklist is not listed here (its inspection offers Emitir 2ª via). Nothing is shown when there is none.
 */
const ChecklistDrafts: React.FC<ChecklistDraftsProps> = ({ carId, returnTo }) => {
  const history = useHistory();
  const [drafts, setDrafts] = useState<DocumentModel[]>([]);

  const load = useCallback(async () => {
    try {
      setDrafts(await loadChecklistDrafts(carId));
    } catch (_error) {
      setDrafts([]);
    }
  }, [carId]);

  useEffect(() => {
    void load();
  }, [load]);
  // Back from the wizard (a draft saved, continued or finalized): the list is read again.
  useIonViewWillEnter(() => {
    void load();
  }, [load]);

  if (!drafts.length) {
    return null;
  }
  return (
    <section className="checklist-drafts" aria-label="Checklists em rascunho" data-testid="checklist-drafts">
      <p className="checklist-drafts__title">Checklists em rascunho</p>
      {drafts.map((draft) => (
        <div key={draft.id} className="checklist-drafts__item" data-testid="checklist-draft">
          <div className="checklist-drafts__text">
            <strong>{checklistDraftLabel(draft)}</strong>
            <span>{checklistDraftDetails(draft)}</span>
          </div>
          <FrottoBadge variant="warning">Rascunho</FrottoBadge>
          <IonButton
            size="small"
            fill="outline"
            className="app-outline-btn"
            aria-label={`Continuar preenchimento do ${checklistDraftLabel(draft).toLowerCase()} #${draft.id}`}
            onClick={() => history.push(checklistLaunchUrl(null, carId, returnTo, draft.id), IN_APP_LAUNCH_STATE)}
          >
            Continuar preenchimento
          </IonButton>
        </div>
      ))}
    </section>
  );
};

export default ChecklistDrafts;
