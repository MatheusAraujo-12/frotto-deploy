// src/pages/Cars/CarListItem.tsx
import React from "react";
import { IonIcon, IonItem, IonNote, IonText } from "@ionic/react";
import { trashOutline } from "ionicons/icons";
import CarBrandMark from "../../components/Car/CarBrandMark";
import FrottoBadge, { FrottoBadgeVariant } from "../../components/UI/FrottoBadge";
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

// Rótulos preservados como estavam (sem acentuação) — divergência conhecida
// em relação a Car.tsx/selectOptions.ts, documentada em DESIGN_SYSTEM.md.
// Não normalizar nesta migração visual.
const ADMIN_STATUS_LABEL: Record<CarAdminStatus, string> = {
  ATIVO: "Ativo",
  RETIRADO: "Retirado",
  A_VENDA: "A venda",
  MANUTENCAO: "Manutencao",
  BLOQUEADO: "Bloqueado",
};

// Decisão oficial (DESIGN_SYSTEM.md, seção "Decisões de status"): A_VENDA usa
// variant="info" — é um estado informativo, não um warning. Os tokens legados
// --app-car-status-sale-* (família âmbar) ficam como candidatos a consolidação
// futura, não devem ser usados aqui.
const ADMIN_STATUS_VARIANT: Record<CarAdminStatus, FrottoBadgeVariant> = {
  ATIVO: "neutral",
  RETIRADO: "dark",
  A_VENDA: "info",
  MANUTENCAO: "warning",
  BLOQUEADO: "danger",
};

const CarListItem: React.FC<CarListItemProps> = (car) => {
  const { showErrorAlert, showSuccessAlert } = useAlert();
  const normalizedCar = normalizeCarRecord(car);
  const carIdentity = resolveCarIdentity(normalizedCar);
  const adminStatus = (normalizedCar.adminStatus || "ATIVO") as CarAdminStatus;
  const hasAdminOverride = adminStatus !== "ATIVO";

  const statusLabel = hasAdminOverride
    ? ADMIN_STATUS_LABEL[adminStatus]
    : normalizedCar?.driverName
    ? "Alugado"
    : "Disponivel";

  const statusVariant: FrottoBadgeVariant = hasAdminOverride
    ? ADMIN_STATUS_VARIANT[adminStatus]
    : normalizedCar?.driverName
    ? "success"
    : "warning";

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
      const { fleetBillingFeedback } = await import("../../services/fleetBillingFeedback");
      showSuccessAlert?.(await fleetBillingFeedback("deleted"));
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
              <FrottoBadge variant={statusVariant} className="car-list-item__status">
                {statusLabel}
              </FrottoBadge>
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
