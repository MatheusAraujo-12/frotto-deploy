import {
  IonButton,
  IonButtons,
  IonContent,
  IonHeader,
  IonIcon,
  IonTitle,
  IonToolbar,
} from "@ionic/react";
import { carOutline, swapHorizontalOutline } from "ionicons/icons";
import { DriverAssignmentType } from "../../constants/CarModels";
import "./DriverAssignmentChoice.css";

interface DriverAssignmentChoiceProps {
  /** Plate of the car of the driver's current open contract, as returned by the backend (may be absent). */
  conflictingCarPlate?: string;
  onChoose: (assignment: DriverAssignmentType) => void;
  onCancel: () => void;
}

/**
 * Asked only when the backend answers that the driver already has an open contract in another car. Shows the two
 * possible movements; the user always decides - nothing is inferred and Cancelar changes nothing.
 */
const DriverAssignmentChoice: React.FC<DriverAssignmentChoiceProps> = ({ conflictingCarPlate, onChoose, onCancel }) => {
  const currentCar = conflictingCarPlate ? `o veículo ${conflictingCarPlate}` : "outro veículo";

  return (
    <>
      <IonHeader className="ion-no-border">
        <IonToolbar className="app-toolbar-clean">
          <IonTitle>Motorista já vinculado a outro veículo</IonTitle>
          <IonButtons slot="end">
            <IonButton className="app-cancel-btn" fill="clear" onClick={onCancel}>
              Cancelar
            </IonButton>
          </IonButtons>
        </IonToolbar>
      </IonHeader>
      <IonContent>
        <div className="app-shell app-shell--compact driver-assignment-choice">
          <p className="driver-assignment-choice__intro">
            Este motorista possui um vínculo com {currentCar}. Como deseja realizar a movimentação?
          </p>
          <button type="button" className="driver-assignment-choice__option" onClick={() => onChoose("PERMANENT")}>
            <span className="app-soft-icon">
              <IonIcon icon={swapHorizontalOutline} />
            </span>
            <span className="driver-assignment-choice__text">
              <strong>Transferência definitiva</strong>
              <span>Encerra o vínculo atual e transfere o motorista definitivamente para este veículo.</span>
            </span>
          </button>
          <button type="button" className="driver-assignment-choice__option" onClick={() => onChoose("RESERVE")}>
            <span className="app-soft-icon app-soft-icon--info">
              <IonIcon icon={carOutline} />
            </span>
            <span className="driver-assignment-choice__text">
              <strong>Carro reserva</strong>
              <span>Mantém o veículo original como vínculo principal e utiliza este veículo temporariamente.</span>
            </span>
          </button>
          <IonButton expand="block" fill="outline" className="app-outline-btn" onClick={onCancel}>
            Cancelar
          </IonButton>
        </div>
      </IonContent>
    </>
  );
};

export default DriverAssignmentChoice;
