import {
  IonBackButton,
  IonButton,
  IonButtons,
  IonCard,
  IonCardContent,
  IonCardHeader,
  IonCardSubtitle,
  IonCardTitle,
  IonContent,
  IonHeader,
  IonIcon,
  IonItem,
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
import { add, checkmarkDoneOutline, warningOutline } from "ionicons/icons";
import { useCallback, useEffect, useMemo, useState } from "react";
import { useAlert } from "../../services/hooks/useAlert";
import { CarBodyDamageModel } from "../../constants/CarModels";
import { filterListObj } from "../../services/filterList";
import { RouteComponentProps, useHistory, useLocation } from "react-router";
import BodyDamageAdd from "./BodyDamageAddModal/BodyDamageAdd";
import BodyDamage from "./BodyDamage";
import "./BodyDamages.css";

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

  const openDamageModal = (carDamage: CarBodyDamageModel) => {
    setModalCarDamage(carDamage);
    setIsModalOpen(true);
    nav.push(nav.location.pathname + "?modalOpened=true");
  };

  return (
    <IonPage id="car-damages-page">
      <IonHeader className="ion-no-border">
        <IonToolbar className="app-toolbar-clean">
          <IonButtons slot="start">
            <IonBackButton defaultHref="/menu" />
          </IonButtons>
          <IonTitle>{TEXT.carDamages}</IonTitle>
          <IonButtons slot="end">
            <IonButton
              className="app-primary-btn body-damages-add-btn"
              disabled={isLoading}
              onClick={(event) => {
                event.preventDefault();
                openDamageModal({});
              }}
            >
              <IonIcon slot="icon-only" icon={add}></IonIcon>
            </IonButton>
          </IonButtons>
        </IonToolbar>
        <IonToolbar className="app-subtoolbar">
          <IonSearchbar
            debounce={500}
            placeholder={TEXT.search}
            value={searchValue}
            onIonChange={(e) => setSearchValue(e.detail.value ?? undefined)}
          ></IonSearchbar>
          {isLoading && <IonProgressBar type="indeterminate"></IonProgressBar>}
        </IonToolbar>
      </IonHeader>
      <IonContent>
        <div className="app-shell app-shell--compact">
          <section className="app-section">
            <div className="body-damages-section-head">
              <h2 className="app-section-title">{TEXT.carDamages}</h2>
              <p className="app-section-subtitle">
                Acompanhe danos pendentes e o histórico já finalizado deste
                veículo.
              </p>
            </div>

            <IonCard className="app-panel-card">
              <IonCardHeader className="app-panel-header">
                <div className="app-soft-icon app-soft-icon--warning">
                  <IonIcon icon={warningOutline} />
                </div>
                <div className="app-panel-header__content">
                  <IonCardTitle className="app-panel-title">
                    {TEXT.carDamagesActive}
                  </IonCardTitle>
                  <IonCardSubtitle className="app-panel-subtitle">
                    Danos que ainda precisam de resolução.
                  </IonCardSubtitle>
                </div>
              </IonCardHeader>
              <IonCardContent>
                <div className="body-damages-list">
                  {activeDoneList.active.map((carDamage: CarBodyDamageModel, index) => {
                    return (
                      <IonItem
                        key={carDamage.id ?? `body-damage-active-${index}`}
                        button
                        detail={false}
                        lines="none"
                        className="body-damage-list-item"
                        onClick={() => {
                          openDamageModal(carDamage);
                        }}
                      >
                        <BodyDamage carDamage={carDamage} />
                      </IonItem>
                    );
                  })}
                </div>

                {!isLoading && activeDoneList.active.length === 0 && (
                  <div className="app-empty-state">
                    <strong>{TEXT.carDamagesActive}</strong>
                    <span>Nenhum dano pendente encontrado.</span>
                  </div>
                )}
              </IonCardContent>
            </IonCard>

            <IonCard className="app-panel-card">
              <IonCardHeader className="app-panel-header">
                <div className="app-soft-icon app-soft-icon--success">
                  <IonIcon icon={checkmarkDoneOutline} />
                </div>
                <div className="app-panel-header__content">
                  <IonCardTitle className="app-panel-title">
                    {TEXT.carDamagesDone}
                  </IonCardTitle>
                  <IonCardSubtitle className="app-panel-subtitle">
                    Danos já encerrados para consulta rápida.
                  </IonCardSubtitle>
                </div>
              </IonCardHeader>
              <IonCardContent>
                <div className="body-damages-list">
                  {activeDoneList.done.map((carDamage: CarBodyDamageModel, index) => {
                    return (
                      <IonItem
                        key={carDamage.id ?? `body-damage-done-${index}`}
                        button
                        detail={false}
                        lines="none"
                        className="body-damage-list-item"
                        onClick={() => {
                          openDamageModal(carDamage);
                        }}
                      >
                        <BodyDamage carDamage={carDamage} />
                      </IonItem>
                    );
                  })}
                </div>

                {!isLoading && activeDoneList.done.length === 0 && (
                  <div className="app-empty-state">
                    <strong>{TEXT.carDamagesDone}</strong>
                    <span>Nenhum dano finalizado encontrado.</span>
                  </div>
                )}
              </IonCardContent>
            </IonCard>
          </section>
        </div>
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
