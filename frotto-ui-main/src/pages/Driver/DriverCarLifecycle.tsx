import { IonButton } from "@ionic/react";
import { MouseEvent, useRef, useState } from "react";
import { CarDriverModel } from "../../constants/CarModels";
import { TEXT } from "../../constants/texts";
import FrottoBadge from "../../components/UI/FrottoBadge";
import { getApiErrorMessage } from "../../services/apiErrorMessage";
import {
  DRIVER_CAR_ERROR_MESSAGES,
  DRIVER_CAR_STATUS_LABEL,
  DRIVER_CAR_STATUS_VARIANT,
  RESTORE_CONFLICT_MESSAGE,
  RESTORE_SUCCESS_MESSAGE,
  canRestorePrimary,
  canReturnReserve,
  createSingleFlight,
  describeReserveReturn,
  driverCarStatus,
  isRestoreConflict,
  restorePrimaryContract,
  returnReserveCar,
} from "../../services/driverAssignmentService";
import { useAlert } from "../../services/hooks/useAlert";
import "./DriverCarLifecycle.css";

/** Status of a contract as the backend reports it, plus whether it is a reserve car or a suspended primary. */
export const DriverCarBadges: React.FC<{ driverCar?: CarDriverModel }> = ({ driverCar }) => {
  const status = driverCarStatus(driverCar);
  return (
    <span className="driver-car-badges">
      <FrottoBadge variant={DRIVER_CAR_STATUS_VARIANT[status]}>{DRIVER_CAR_STATUS_LABEL[status]}</FrottoBadge>
      {driverCar?.reserve && <FrottoBadge variant="info">Carro reserva</FrottoBadge>}
      {!driverCar?.reserve && status === "SUSPENDED" && <FrottoBadge variant="neutral">Vínculo principal</FrottoBadge>}
    </span>
  );
};

interface DriverCarLifecycleActionsProps {
  driverCar?: CarDriverModel;
  /** Called after the backend changed the contract (the caller reloads its data). */
  onChanged: (updated?: CarDriverModel) => void;
}

/**
 * "Devolver carro reserva" for an active reserve and "Reativar vínculo" for a suspended primary. Each calls its own
 * backend endpoint once (repeated clicks while it runs are ignored) and reports the outcome the backend returned.
 */
export const DriverCarLifecycleActions: React.FC<DriverCarLifecycleActionsProps> = ({ driverCar, onChanged }) => {
  const { showErrorAlert, showSuccessAlert, showWarningAlert } = useAlert();
  const singleFlight = useRef(createSingleFlight());
  const [busy, setBusy] = useState(false);

  if (!canReturnReserve(driverCar) && !canRestorePrimary(driverCar)) {
    return null;
  }
  const driverName = driverCar?.driver?.name || undefined;

  const run = (action: () => Promise<void>) =>
    singleFlight.current(async () => {
      setBusy(true);
      try {
        await action();
      } finally {
        setBusy(false);
      }
    });

  const handleReturn = (event: MouseEvent) => {
    event.stopPropagation();
    if (!window.confirm("Devolver o carro reserva? O vínculo temporário será finalizado.")) {
      return;
    }
    void run(async () => {
      try {
        const result = await returnReserveCar(driverCar!.id!);
        const feedback = describeReserveReturn(result, driverName);
        if (feedback.tone === "success") {
          showSuccessAlert(feedback.message);
        } else {
          showWarningAlert(feedback.message);
        }
        onChanged();
      } catch (error) {
        showErrorAlert(getApiErrorMessage(error, TEXT.saveFailed, DRIVER_CAR_ERROR_MESSAGES));
      }
    });
  };

  const handleRestore = (event: MouseEvent) => {
    event.stopPropagation();
    void run(async () => {
      try {
        const restored = await restorePrimaryContract(driverCar!.id!);
        showSuccessAlert(RESTORE_SUCCESS_MESSAGE);
        onChanged(restored);
      } catch (error) {
        showErrorAlert(
          isRestoreConflict(error) ? RESTORE_CONFLICT_MESSAGE : getApiErrorMessage(error, TEXT.saveFailed, DRIVER_CAR_ERROR_MESSAGES)
        );
      }
    });
  };

  return canReturnReserve(driverCar) ? (
    <IonButton size="small" fill="outline" className="app-semantic-btn app-semantic--warning" disabled={busy} onClick={handleReturn}>
      Devolver carro reserva
    </IonButton>
  ) : (
    <IonButton size="small" fill="outline" className="app-semantic-btn app-semantic--edit" disabled={busy} onClick={handleRestore}>
      Reativar vínculo
    </IonButton>
  );
};
