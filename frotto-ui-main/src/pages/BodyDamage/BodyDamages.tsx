import {
  IonBackButton,
  IonButton,
  IonButtons,
  IonContent,
  IonHeader,
  IonIcon,
  IonItem,
  IonLabel,
  IonList,
  IonModal,
  IonPage,
  IonProgressBar,
  IonSearchbar,
  IonTitle,
  IonToolbar,
  useIonRouter,
} from "@ionic/react";
import api from "../../services/axios/axios";
import endpoints from "../../constants/endpoints";
import { TEXT } from "../../constants/texts";
import { add } from "ionicons/icons";
import { useCallback, useEffect, useMemo, useState } from "react";
import { useAlert } from "../../services/hooks/useAlert";
import { CarBodyDamageModel } from "../../constants/CarModels";
import { filterListObj } from "../../services/filterList";
import ItemNotFound from "../../components/List/ItemNotFound";
import { RouteComponentProps, useHistory, useLocation } from "react-router";
import BodyDamageAdd from "./BodyDamageAddModal/BodyDamageAdd";
import BodyDamage from "./BodyDamage";

interface BodyDamageDetail
  extends RouteComponentProps<{
    id: string;
  }> {}

const BodyDamages: React.FC<BodyDamageDetail> = ({ match }) => {
  const location = useLocation();
  const nav = useHistory();
  const history = useIonRouter();
  const { showErrorAlert } = useAlert();
  const [isLoading, setisLoading] = useState(false);
  const [modalCarDamage, setModalCarDamage] = useState<CarBodyDamageModel>({});
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [searchValue, setSearchValue] = useState<string | undefined>(undefined);
  const [carDamageList, setCarDamageList] = useState<CarBodyDamageModel[]>([]);

  useEffect(() => {
    if (!location.search.includes("modalOpened=true")) {
      setIsModalOpen(false);
    }
  }, [location]);

  const loadBodyDamages = async () => {
    setisLoading(true);
    try {
      const { data } = await api.get(
        endpoints.BODY_DAMAGE({
          pathVariables: {
            id: match.params.id,
          },
        })
      );
      setisLoading(false);
      if (data) {
        setCarDamageList(data);
      } else {
        history.push("/menu", "none", "replace");
      }
    } catch (error) {
      setisLoading(false);
      showErrorAlert(TEXT.loadCarDamagesFailed);
    }
  };

  useEffect(() => {
    loadBodyDamages();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const filteredList = useMemo(() => {
    return filterListObj(carDamageList, searchValue);
  }, [carDamageList, searchValue]);

  const activeDoneList = useMemo(() => {
    const activeList: CarBodyDamageModel[] = [];
    const doneList: CarBodyDamageModel[] = [];
    filteredList.forEach((item: CarBodyDamageModel) => {
      if (item.resolved) {
        doneList.push(item);
      } else {
        activeList.push(item);
      }
    });
    return { active: activeList, done: doneList };
  }, [filteredList]);

  const closeModal = useCallback((response?: CarBodyDamageModel) => {
    setIsModalOpen(false);
    nav.goBack();

    if (!response) return;

    setCarDamageList((prev) => {
      const exists = prev.some((item) => item.id === response.id);
      if (exists) {
        return prev.map((item) => (item.id === response.id ? response : item));
      }
      return [response, ...prev];
    });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  return (
    <IonPage id="car-damages-page">
      <IonHeader>
        <IonToolbar>
          <IonButtons slot="start">
            <IonBackButton defaultHref="/menu" />
          </IonButtons>
          <IonButtons slot="primary">
            <IonButton
              disabled={isLoading}
              onClick={(e) => {
                e.preventDefault();
                setModalCarDamage({});
                setIsModalOpen(true);
                nav.push(nav.location.pathname + "?modalOpened=true");
              }}
            >
              <IonIcon slot="icon-only" icon={add}></IonIcon>
            </IonButton>
          </IonButtons>
          <IonTitle>{TEXT.carDamages}</IonTitle>
        </IonToolbar>
        <IonToolbar>
          <IonSearchbar
            debounce={500}
            placeholder={TEXT.search}
            onIonChange={(e) => setSearchValue(e.detail.value)}
          ></IonSearchbar>
          {isLoading && <IonProgressBar type="indeterminate"></IonProgressBar>}
        </IonToolbar>
      </IonHeader>
      <IonContent>
        <IonList>
          <IonItem>
            <IonLabel>
              <h1>{TEXT.carDamagesActive}</h1>
            </IonLabel>
          </IonItem>
          {activeDoneList.active.map((carDamage: CarBodyDamageModel, index) => {
            return (
              <IonItem
                key={index}
                button
                onClick={() => {
                  setModalCarDamage(carDamage);
                  setIsModalOpen(true);
                  nav.push(nav.location.pathname + "?modalOpened=true");
                }}
              >
                <BodyDamage carDamage={carDamage} />
              </IonItem>
            );
          })}
          {!isLoading && activeDoneList.active.length === 0 && <ItemNotFound />}
        </IonList>
        <IonList>
          <IonItem>
            <IonLabel>
              <h1>{TEXT.carDamagesDone}</h1>
            </IonLabel>
          </IonItem>

          {activeDoneList.done.map((carDamage: CarBodyDamageModel, index) => {
            return (
              <IonItem
                key={index}
                button
                onClick={() => {
                  setModalCarDamage(carDamage);
                  setIsModalOpen(true);
                  nav.push(nav.location.pathname + "?modalOpened=true");
                }}
              >
                <BodyDamage carDamage={carDamage} />
              </IonItem>
            );
          })}
          {!isLoading && activeDoneList.done.length === 0 && <ItemNotFound />}
        </IonList>
      </IonContent>
      <IonModal isOpen={isModalOpen} backdropDismiss={false}>
        <BodyDamageAdd
          closeModal={closeModal}
          carId={match.params.id}
          initialValues={modalCarDamage}
        />
      </IonModal>
    </IonPage>
  );
};

export default BodyDamages;
