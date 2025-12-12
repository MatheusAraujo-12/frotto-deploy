import { IonBadge, IonText } from "@ionic/react";
import {
  IonLabelLeft,
  IonLabekRight,
} from "../../components/List/IonLabekRight";
import { CarBodyDamageModel } from "../../constants/CarModels";
import { TEXT } from "../../constants/texts";
import { currencyFormat } from "../../services/currencyFormat";
import { formatDateView } from "../../services/dateFormat";

interface BodyDamageProps {
  carDamage: CarBodyDamageModel;
}

const BodyDamage: React.FC<BodyDamageProps> = ({ carDamage }) => {
  return (
    <>
      <IonLabelLeft class="ion-text-wrap">
        <h2>
          <IonText color="primary">{`${carDamage.part}`}</IonText>
        </h2>
        <p>{`${formatDateView(carDamage.date)}`}</p>
        <p>{carDamage.responsible}</p>
      </IonLabelLeft>
      <IonLabekRight>
        <h2>
          <IonText color="dark">{currencyFormat(carDamage.cost)}</IonText>
        </h2>
        {!carDamage.resolved && (
          <IonBadge slot="start" color="warning">
            {TEXT.notResolved}
          </IonBadge>
        )}
        {carDamage.resolved && (
          <IonBadge slot="start" color="success">
            {TEXT.resolved}
          </IonBadge>
        )}
      </IonLabekRight>
    </>
  );
};

export default BodyDamage;
