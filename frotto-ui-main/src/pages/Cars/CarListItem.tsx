// src/pages/Cars/CarListItem.tsx
import React from "react";
import { IonCard, IonIcon } from "@ionic/react";
import { createOutline, trashOutline } from "ionicons/icons";
import { useHistory } from "react-router";
import CarBrandMark from "../../components/Car/CarBrandMark";
import {
  normalizeCarBrand,
  resolveCarBrandDisplayName,
} from "../../components/Car/carBrandAssets";
import { CarAdminStatus, CarModel } from "../../constants/CarModels";
import endpoints from "../../constants/endpoints";
import { TEXT } from "../../constants/texts";
import api from "../../services/axios/axios";
import { useAlert } from "../../services/hooks/useAlert";

interface CarListItemProps extends CarModel {
  onDeleted?: (id: number) => void;
}

type CarVisualTone =
  | "active"
  | "rented"
  | "sale"
  | "retired"
  | "maintenance"
  | "blocked";

type CarVisualStatusMeta = {
  label: string;
  tone: CarVisualTone;
  detailLabel: string;
  detailValue: string;
};

const CarListItem: React.FC<CarListItemProps> = ({
  id,
  name,
  brand,
  marca,
  model,
  plate,
  group,
  driverName,
  odometer,
  adminStatus,
  onDeleted,
}) => {
  const history = useHistory();
  const { showErrorAlert } = useAlert();

  const brandLabel = resolveCarBrandDisplayName({ brand, marca });
  const titleParts = [name, model].filter(
    (value): value is string => Boolean(value?.trim())
  );
  const title = Array.from(new Set(titleParts)).join(" - ") || brandLabel || "Veiculo";
  const showBrandLabel = Boolean(
    brandLabel &&
      !normalizeCarBrand(title).includes(normalizeCarBrand(brandLabel))
  );
  const plateLabel = plate?.trim() || "--";
  const ownerLabel = group?.trim() || "--";
  const isRented = Boolean(driverName);
  const effectiveAdminStatus: CarAdminStatus = adminStatus || "ATIVO";
  const statusMeta = resolveCarVisualStatusMeta(
    effectiveAdminStatus,
    isRented,
    driverName
  );
  const kmValue =
    typeof odometer === "number"
      ? odometer.toLocaleString("pt-BR")
      : "--";

  const handleOpen = () => {
    if (id != null) {
      history.push(`/menu/carros/${id}`);
    }
  };

  const handleEdit = (event: React.MouseEvent<HTMLButtonElement>) => {
    event.stopPropagation();
    handleOpen();
  };

  const handleDelete = async (event: React.MouseEvent<HTMLButtonElement>) => {
    event.stopPropagation();
    if (id == null) return;

    const carLabel = title || plateLabel || "este veiculo";
    const confirmDelete = window.confirm(`${TEXT.deleteDefault} ${carLabel}?`);
    if (!confirmDelete) return;

    try {
      await api.delete(endpoints.CAR({ pathVariables: { id } }));

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
        <div className="car-card__topline">
          <div className="car-card__headline">
            <CarBrandMark
              brand={brand}
              marca={marca}
              size="sm"
              className="car-card__brand-mark"
            />

            <div className="car-card__headline-copy">
              {showBrandLabel && (
                <span className="car-card__brand-name">{brandLabel}</span>
              )}
              <div className="car-card__title">{title}</div>
            </div>
          </div>

          <span className={`car-card__badge car-card__badge--${statusMeta.tone}`}>
            {statusMeta.label}
          </span>
        </div>

        <div className="car-card__meta">
          <div className="car-card__meta-item car-card__meta-item--plate">
            <span className="car-card__meta-label">Placa</span>
            <span className="car-card__meta-value car-card__meta-value--plate">
              {plateLabel}
            </span>
          </div>

          <div className="car-card__meta-item">
            <span className="car-card__meta-label">Grupo</span>
            <span className="car-card__meta-value">{ownerLabel}</span>
          </div>
        </div>

        <div className="car-card__footer">
          <div className="car-card__footer-main">
            <div className="car-card__km">
              <span className="car-card__km-label">Odometro</span>
              <span className="car-card__km-value">
                {kmValue}
                <small>km</small>
              </span>
            </div>

            <div className="car-card__detail">
              <span className="car-card__detail-label">{statusMeta.detailLabel}</span>
              <span
                className={`car-card__detail-value car-card__detail-value--${statusMeta.tone}`}
              >
                {statusMeta.detailValue}
              </span>
            </div>
          </div>

          <div className="car-card__actions">
            <button
              type="button"
              className="car-card__action car-card__action--edit"
              onClick={handleEdit}
              aria-label={`Editar ${title}`}
            >
              <IonIcon icon={createOutline} className="car-card__icon" />
            </button>

            <button
              type="button"
              className="car-card__action car-card__action--danger"
              onClick={handleDelete}
              aria-label={`Excluir ${title}`}
            >
              <IonIcon icon={trashOutline} className="car-card__icon" />
            </button>
          </div>
        </div>
      </div>
    </IonCard>
  );
};

function resolveCarVisualStatusMeta(
  status: CarAdminStatus,
  isRented: boolean,
  driverName?: string
): CarVisualStatusMeta {
  if (status === "RETIRADO") {
    return {
      label: "Retirado",
      tone: "retired",
      detailLabel: "Disponibilidade",
      detailValue: "Fora de operacao",
    };
  }

  if (status === "A_VENDA") {
    return {
      label: "A venda",
      tone: "sale",
      detailLabel: "Disponibilidade",
      detailValue: "Em processo de venda",
    };
  }

  if (status === "MANUTENCAO") {
    return {
      label: "Manutencao",
      tone: "maintenance",
      detailLabel: "Disponibilidade",
      detailValue: "Em manutencao",
    };
  }

  if (status === "BLOQUEADO") {
    return {
      label: "Bloqueado",
      tone: "blocked",
      detailLabel: "Disponibilidade",
      detailValue: "Uso bloqueado",
    };
  }

  if (isRented) {
    return {
      label: "Alugado",
      tone: "rented",
      detailLabel: "Motorista",
      detailValue: driverName?.trim() || "--",
    };
  }

  return {
    label: "Ativo",
    tone: "active",
    detailLabel: "Disponibilidade",
    detailValue: "Disponivel",
  };
}

export default CarListItem;
