import { IonButton } from "@ionic/react";
import { useState } from "react";
import { useHistory } from "react-router";
import { TEXT } from "../../constants/texts";
import { checklistLaunchUrl } from "../Documents/checklistLaunch";

interface InspectionNewMenuProps {
  carId: string;
  /** Inspeção Avulsa: the current inspection form. */
  onSingleInspection: () => void;
}

/**
 * "+ Novo" of the inspections: Inspeção Avulsa (as before) or a Checklist de Entrega / Devolução, filled in the
 * existing wizard of Documentos opened for this car (and back here when it closes).
 */
const InspectionNewMenu: React.FC<InspectionNewMenuProps> = ({ carId, onSingleInspection }) => {
  const [isOpen, setIsOpen] = useState(false);
  const history = useHistory();
  const here = `/menu/carros/${carId}`;

  const choose = (action: () => void) => {
    setIsOpen(false);
    action();
  };

  return (
    <>
      <IonButton
        className="app-semantic-btn app-semantic--neutral"
        size="small"
        fill="outline"
        aria-expanded={isOpen}
        aria-controls="inspection-new-options"
        onClick={() => setIsOpen((open) => !open)}
      >
        {TEXT.new}
      </IonButton>
      {isOpen && (
        <div id="inspection-new-options" className="app-actions-row" role="group" aria-label="Nova inspeção">
          <IonButton size="small" fill="outline" className="app-outline-btn" onClick={() => choose(onSingleInspection)}>
            Inspeção Avulsa
          </IonButton>
          <IonButton
            size="small"
            fill="outline"
            className="app-outline-btn"
            onClick={() => choose(() => history.push(checklistLaunchUrl("ENTREGA", carId, here)))}
          >
            Checklist de Entrega
          </IonButton>
          <IonButton
            size="small"
            fill="outline"
            className="app-outline-btn"
            onClick={() => choose(() => history.push(checklistLaunchUrl("DEVOLUCAO", carId, here)))}
          >
            Checklist de Devolução
          </IonButton>
        </div>
      )}
    </>
  );
};

export default InspectionNewMenu;
