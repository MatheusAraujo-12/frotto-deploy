import { IonBadge, IonIcon } from "@ionic/react";
import { alertCircleOutline, checkmarkDoneOutline } from "ionicons/icons";
import { CarBodyDamageModel } from "../../constants/CarModels";
import { TEXT } from "../../constants/texts";
import { currencyFormat } from "../../services/currencyFormat";
import { formatDateView } from "../../services/dateFormat";

interface BodyDamageProps {
  carDamage: CarBodyDamageModel;
}

const BodyDamage: React.FC<BodyDamageProps> = ({ carDamage }) => {
  return (
    <div className="body-damage-list-item__wrap">
      <div
        className={`app-soft-icon ${
          carDamage.resolved ? "app-soft-icon--success" : "app-soft-icon--warning"
        }`.trim()}
      >
        <IonIcon
          icon={carDamage.resolved ? checkmarkDoneOutline : alertCircleOutline}
        />
      </div>

      <div className="body-damage-list-item__content">
        <div className="body-damage-list-item__main ion-text-wrap">
          <div className="body-damage-list-item__top">
            <h3 className="body-damage-list-item__title">{carDamage.part || "-"}</h3>
            <IonBadge color={carDamage.resolved ? "success" : "warning"}>
              {carDamage.resolved ? TEXT.resolved : TEXT.notResolved}
            </IonBadge>
          </div>
          <p className="body-damage-list-item__meta">
            {formatDateView(carDamage.date)}
          </p>
          <p className="body-damage-list-item__responsible">
            {carDamage.responsible || "-"}
          </p>
        </div>

        <div className="body-damage-list-item__aside ion-text-wrap">
          <p className="body-damage-list-item__value">
            {currencyFormat(carDamage.cost)}
          </p>
          <p className="body-damage-list-item__hint">{TEXT.cost}</p>
        </div>
      </div>
    </div>
  );
};

export default BodyDamage;
