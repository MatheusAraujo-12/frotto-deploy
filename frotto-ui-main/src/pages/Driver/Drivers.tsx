import {
  IonBackButton,
  IonButtons,
  IonContent,
  IonHeader,
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
import { useCallback, useEffect, useMemo, useState } from "react";
import { useAlert } from "../../services/hooks/useAlert";
import { CarDriverModel } from "../../constants/CarModels";
import { filterListObj } from "../../services/filterList";
import ItemNotFound from "../../components/List/ItemNotFound";
import { RouteComponentProps, useHistory, useLocation } from "react-router";
import DriverAdd from "./DriverAddModal/DriverAdd";
import { formatDateView } from "../../services/dateFormat";
import {
  IonLabelLeft,
  IonLabekRight,
} from "../../components/List/IonLabekRight";
import { currencyFormat } from "../../services/currencyFormat";
import { formatCPF, formatTel } from "../../services/iMaskFormat";

interface DriverDetail
  extends RouteComponentProps<{
    id: string;
  }> {}

const Drivers: React.FC<DriverDetail> = ({ match }) => {
  const location = useLocation();
  const nav = useHistory();
  const history = useIonRouter();
  const { showErrorAlert } = useAlert();
  const [isLoading, setisLoading] = useState(false);
  const [modalDriver, setModalDriver] = useState<CarDriverModel>({});
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [searchValue, setSearchValue] = useState<string | undefined>(undefined);
  const [driverList, setDriversList] = useState<CarDriverModel[]>([]);

  useEffect(() => {
    if (!location.search.includes("modalOpened=true")) {
      setIsModalOpen(false);
    }
  }, [location]);

  const loadDrivers = async () => {
    setisLoading(true);
    try {
      const { data } = await api.get(
        endpoints.DRIVERS({
          pathVariables: {
            id: match.params.id,
          },
        })
      );
      setisLoading(false);
      if (data) {
        setDriversList(data);
      } else {
        history.push("/menu", "none", "replace");
      }
    } catch (error) {
      setisLoading(false);
      showErrorAlert(TEXT.loadDriversFailed);
    }
  };

  useEffect(() => {
    loadDrivers();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const filteredList = useMemo(() => {
    return filterListObj(driverList, searchValue);
  }, [driverList, searchValue]);

  const activeDoneList = useMemo(() => {
    let activeItem: CarDriverModel | undefined;
    const doneList: CarDriverModel[] = [];
    filteredList.forEach((item: CarDriverModel) => {
      if (item.concluded) {
        doneList.push(item);
      } else {
        activeItem = item;
      }
    });
    return { active: activeItem, done: doneList };
  }, [filteredList]);

  const closeModal = useCallback((response?: CarDriverModel) => {
    setIsModalOpen(false);
    nav.goBack();

    if (!response) return;

    setDriversList((prev) => {
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
          <IonTitle>{TEXT.drivers}</IonTitle>
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
        <div className="section-shell">
          <IonList>
            <IonItem>
              <IonLabel>
                <h1>{TEXT.active}</h1>
              </IonLabel>
            </IonItem>

            {activeDoneList.active && (
              <IonItem
                button
                onClick={() => {
                  setModalDriver(activeDoneList.active!);
                  setIsModalOpen(true);
                  nav.push(nav.location.pathname + "?modalOpened=true");
                }}
              >
                <IonLabelLeft class="ion-text-wrap">
                  <h2>{activeDoneList.active?.driver?.name}</h2>
                  <p>{formatCPF(activeDoneList.active?.driver?.cpf)}</p>
                  <p>{formatTel(activeDoneList.active?.driver?.contact)}</p>
                </IonLabelLeft>
                <IonLabekRight>
                  <p>{`${formatDateView(activeDoneList.active?.startDate)}`}</p>
                  <p>{currencyFormat(activeDoneList.active?.warranty)}</p>
                  <p>{`${activeDoneList.active?.driver?.email}`}</p>
                </IonLabekRight>
              </IonItem>
            )}
            {!isLoading && !activeDoneList.active && <ItemNotFound />}
          </IonList>
          <IonList>
            <IonItem>
              <IonLabel>
                <h1>{TEXT.carDamagesDone}</h1>
              </IonLabel>
            </IonItem>

            {activeDoneList.done.map((carDriver: CarDriverModel, index) => {
              return (
                <IonItem
                  key={index}
                  button
                  onClick={() => {
                    setModalDriver(carDriver);
                    setIsModalOpen(true);
                    nav.push(nav.location.pathname + "?modalOpened=true");
                  }}
                >
                  <IonLabelLeft class="ion-text-wrap">
                    <h2>{carDriver?.driver?.name}</h2>
                    <p>{formatCPF(carDriver?.driver?.cpf)}</p>
                    <p>{formatTel(carDriver?.driver?.contact)}</p>
                  </IonLabelLeft>
                  <IonLabekRight>
                    <p>{`${formatDateView(
                      carDriver?.startDate
                    )} - ${formatDateView(carDriver?.endDate)}`}</p>
                    <p>{currencyFormat(carDriver?.debt)}</p>
                    <p>{`${carDriver?.driver?.email}`}</p>
                  </IonLabekRight>
                </IonItem>
              );
            })}
            {!isLoading && activeDoneList.done.length === 0 && <ItemNotFound />}
          </IonList>
        </div>
      </IonContent>
      <IonModal isOpen={isModalOpen} backdropDismiss={false}>
        <DriverAdd
          carId={match.params.id}
          closeModal={closeModal}
          initialValues={modalDriver}
        />
      </IonModal>
    </IonPage>
  );
};

export default Drivers;
