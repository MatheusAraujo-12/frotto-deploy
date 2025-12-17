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
    <div style={{ textAlign: "center", padding: 16, opacity: 0.85 }}>
      <IonIcon icon={searchOutline} style={{ fontSize: 34, marginBottom: 8 }} />
      <div style={{ fontWeight: 600 }}>{message}</div>
    </div>
  );
};

export default ItemNotFound;
