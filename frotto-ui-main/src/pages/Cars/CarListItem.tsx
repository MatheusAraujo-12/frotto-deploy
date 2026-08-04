// src/pages/Cars/CarListItem.tsx
import React from "react";
import { IonIcon, IonItem, IonNote, IonText } from "@ionic/react";
import { trashOutline } from "ionicons/icons";
import CarBrandMark from "../../components/Car/CarBrandMark";
import {
  normalizeCarRecord,
  resolveCarIdentity,
} from "../../components/Car/carIdentity";
import { CarAdminStatus, CarModel } from "../../constants/CarModels";
import { TEXT } from "../../constants/texts";
import endpoints from "../../constants/endpoints";
import api from "../../services/axios/axios";
import { useAlert } from "../../services/hooks/useAlert";
import "./CarListItem.css";

interface CarListItemProps extends CarModel {
  onDeleted?: (deletedCarId: number) => void;
}

const ADMIN_STATUS_LABEL: Record<CarAdminStatus, string> = {
  ATIVO: "Ativo",
  RETIRADO: "Retirado",
  A_VENDA: "A venda",
  MANUTENCAO: "Manutencao",
  BLOQUEADO: "Bloqueado",
};

const ADMIN_STATUS_CLASS: Record<CarAdminStatus, string> = {
  ATIVO: "car-status-neutral",
  RETIRADO: "car-status-dark",
  A_VENDA: "car-status-info",
  MANUTENCAO: "car-status-warning",
  BLOQUEADO: "car-status-danger",
};

const CarListItem: React.FC<CarListItemProps> = (car) => {
  const { showErrorAlert } = useAlert();
  const normalizedCar = normalizeCarRecord(car);
  const carIdentity = resolveCarIdentity(normalizedCar);
  const adminStatus = (normalizedCar.adminStatus || "ATIVO") as CarAdminStatus;
  const hasAdminOverride = adminStatus !== "ATIVO";

  const statusLabel = hasAdminOverride
    ? ADMIN_STATUS_LABEL[adminStatus]
    : normalizedCar?.driverName
    ? "Alugado"
    : "Disponivel";

  const statusClass = hasAdminOverride
    ? ADMIN_STATUS_CLASS[adminStatus]
    : normalizedCar?.driverName
    ? "car-status-success"
    : "car-status-warning";

  const handleDelete = async (event: React.MouseEvent<HTMLButtonElement>) => {
    event.preventDefault();
    event.stopPropagation();
    const id = normalizedCar.id;
    if (id == null) return;

    const carLabel = carIdentity.displayName || normalizedCar.plate || "este veículo";
    const confirmDelete = window.confirm(`${TEXT.deleteDefault} ${carLabel}?`);
    if (!confirmDelete) return;

    try {
      await api.delete(endpoints.CAR({ pathVariables: { id } }));
      car.onDeleted?.(id);
    } catch (err) {
      showErrorAlert(TEXT.deleteFailed);
    }
  };

  return (
    <IonItem
      button
      detail={false}
      lines="none"
      routerLink={`/menu/carros/${normalizedCar.id}`}
      routerOptions={{ unmount: true }}
      routerDirection="forward"
      className="car-list-item"
    >
      <div className="car-list-item__wrap">
        <div className="car-list-item__left">
          <CarBrandMark
            name={normalizedCar.name}
            brand={normalizedCar.brand}
            marca={normalizedCar.marca}
            model={normalizedCar.model}
            size="sm"
            className="car-list-item__mark"
          />

          <div className="car-list-item__content">
            <div className="car-list-item__header">
              <h2 className="car-list-item__title">
                <IonText color="primary">{carIdentity.displayName}</IonText>
              </h2>
              <div className={`car-list-item__status ${statusClass}`}>
                {statusLabel}
              </div>
            </div>

            <p className="car-list-item__meta">
              {[normalizedCar?.year, normalizedCar.color, normalizedCar?.plate]
                .filter(Boolean)
                .join(" - ") || "-"}
            </p>

            <p className="car-list-item__meta">
              Quilometragem: {normalizedCar?.odometer || 0} {TEXT.km}
            </p>

            <p className="car-list-item__meta">
              Dono/Grupo: {normalizedCar?.group || "-"}
            </p>

            {normalizedCar?.driverName && (
              <IonNote className="car-list-item__driver">
                Motorista: {normalizedCar.driverName}
              </IonNote>
            )}
          </div>
        </div>

        <div className="car-list-item__actions">
          <button
            type="button"
            className="car-list-item__action car-list-item__action--danger"
            onClick={handleDelete}
            aria-label={`Excluir ${carIdentity.displayName}`}
          >
            <IonIcon icon={trashOutline} />
          </button>
        </div>
      </div>
    </IonItem>
  );
};

export default CarListItem;
