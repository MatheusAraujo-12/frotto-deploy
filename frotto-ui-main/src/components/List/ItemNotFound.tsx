import React from "react";
import { IonButton, IonIcon } from "@ionic/react";
import { searchOutline } from "ionicons/icons";

export type ItemNotFoundProps = {
  message?: string;
  title?: string;
  description?: string;
  actionLabel?: string;
  onAction?: () => void;
};

const ItemNotFound: React.FC<ItemNotFoundProps> = ({
  message = "Nenhum item encontrado",
  title,
  description,
  actionLabel,
  onAction,
}) => {
  return (
    <div className="app-item-not-found">
      <IonIcon icon={searchOutline} className="app-item-not-found__icon" />
      {title ? (
        <>
          <div className="app-item-not-found__title">{title}</div>
          {description && (
            <div className="app-item-not-found__description">{description}</div>
          )}
        </>
      ) : (
        <div className="app-item-not-found__text">{message}</div>
      )}
      {actionLabel && onAction && (
        <IonButton className="app-item-not-found__action" onClick={onAction}>
          {actionLabel}
        </IonButton>
      )}
    </div>
  );
};

export default ItemNotFound;
