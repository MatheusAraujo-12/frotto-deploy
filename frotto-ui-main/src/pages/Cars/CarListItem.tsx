// src/pages/Cars/CarListItem.tsx
import React from "react";
import { IonCard, IonIcon } from "@ionic/react";
import { CarModel } from "../../constants/CarModels";
import { createOutline, trashOutline } from "ionicons/icons";
import { useHistory } from "react-router";
import api from "../../services/axios/axios";
import endpoints from "../../constants/endpoints";
import { useAlert } from "../../services/hooks/useAlert";
import { TEXT } from "../../constants/texts";

interface CarListItemProps extends CarModel {
  onDeleted?: (id: number) => void; // 👈 agora o TS conhece essa prop
}

const CarListItem: React.FC<CarListItemProps> = ({
  id,
  name,
  model,
  plate,
  group,        // Proprietário
  driverName,
  odometer,
  onDeleted,
}) => {
  const history = useHistory();
  const { showErrorAlert } = useAlert();

  // Título estilo "ONIX - CHEVROLET" (ajuste conforme sua API)
  const title = [name || model].filter(Boolean).join(" - ") || "Veículo";

  const isRented = !!driverName;
  const kmValue =
    typeof odometer === "number"
      ? odometer.toLocaleString("pt-BR")
      : "--";

  const handleOpen = () => {
    if (id != null) {
      history.push(`/menu/carros/${id}`);
    }
  };

  const handleEdit = (e: React.MouseEvent) => {
    e.stopPropagation();
    handleOpen();
  };

  const handleDelete = async (e: React.MouseEvent) => {
    e.stopPropagation();
    if (id == null) return;

    const carLabel = title || plate || "este veículo";
    const confirmDelete = window.confirm(
      `${TEXT.deleteDefault} ${carLabel}?`
    );
    if (!confirmDelete) return;

    try {
      await api.delete(
        endpoints.CAR({ pathVariables: { id } })
      );

      // se o pai passou onDeleted, avisa para remover da lista
      if (onDeleted) {
        onDeleted(id);
      }
    } catch (err) {
      showErrorAlert(TEXT.deleteFailed);
    }
  };

  return (
    <IonCard className="car-card" onClick={handleOpen}>
      <div className="car-card__main">
        {/* Coluna esquerda – infos */}
        <div className="car-card__info-block">
          <div className="car-card__title">{title}</div>

          <div className="car-card__line">
            Placa: <span>{plate || "--"}</span>
          </div>

          <div className="car-card__line">
            Proprietário: <span>{group || "—"}</span>
          </div>

          <div className="car-card__line">
            Motorista:{" "}
            {isRented ? (
              <span className="car-card__driver car-card__driver--rented">
                {driverName}
              </span>
            ) : (
              <span className="car-card__badge car-card__badge--available">
                Disponível
              </span>
            )}
          </div>

          <div className="car-card__line car-card__line--km">
            KM: <span>{kmValue}</span>
          </div>
        </div>

        {/* Coluna direita – texto + ações */}
        <div className="car-card__aside">
          <span className="car-card__hint">Pressione para abrir</span>

          <div className="car-card__actions">
            <IonIcon
              icon={createOutline}
              className="car-card__icon car-card__icon--edit"
              onClick={handleEdit}
            />
            <IonIcon
              icon={trashOutline}
              className="car-card__icon car-card__icon--delete"
              onClick={handleDelete}
            />
          </div>
        </div>
      </div>
    </IonCard>
  );
};

export default CarListItem;
