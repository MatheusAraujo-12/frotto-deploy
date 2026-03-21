import React from "react";
import { IonIcon } from "@ionic/react";
import { searchOutline } from "ionicons/icons";

export type ItemNotFoundProps = {
  message?: string;
};

const ItemNotFound: React.FC<ItemNotFoundProps> = ({
  message = "Nenhum item encontrado",
}) => {
  return (
    <div className="app-item-not-found">
      <IonIcon icon={searchOutline} className="app-item-not-found__icon" />
      <div className="app-item-not-found__text">{message}</div>
    </div>
  );
};

export default ItemNotFound;
