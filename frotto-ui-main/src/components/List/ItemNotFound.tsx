import { IonItem, IonLabel, IonText } from "@ionic/react";
import { TEXT } from "../../constants/texts";

interface ItemNotFoundProps {
  text?: string;
}

const ItemNotFound: React.FC<ItemNotFoundProps> = ({ text }) => {
  return (
    <IonItem>
      <IonLabel class="ion-text-wrap">
        <IonText color="secondary">{text ? text : TEXT.itensNotFound}</IonText>
      </IonLabel>
    </IonItem>
  );
};

export default ItemNotFound;
