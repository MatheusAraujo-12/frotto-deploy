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
  IonListHeader,
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
import { DriverPendencyModel } from "../../constants/CarModels";
import { filterListObj } from "../../services/filterList";
import ItemNotFound from "../../components/List/ItemNotFound";
import { RouteComponentProps, useHistory, useLocation } from "react-router";
import {
  IonLabelLeft,
  IonLabekRight,
} from "../../components/List/IonLabekRight";
import DriverPendencyAdd from "./DriverPendencyAddModal/DriverPendencyAdd";
import { currencyFormat } from "../../services/currencyFormat";
import { add } from "ionicons/icons";
import { formatDateView } from "../../services/dateFormat";
interface DriverPendencyDetail
  extends RouteComponentProps<{
    id: string;
  }> {}

const DriverPendencies: React.FC<DriverPendencyDetail> = ({ match }) => {
  const location = useLocation();
  const nav = useHistory();
  const history = useIonRouter();
  const { showErrorAlert } = useAlert();
  const [isLoading, setisLoading] = useState(false);
  const [modalDriverPendencyValue, setModalDriverPendencyValue] =
    useState<DriverPendencyModel>({});
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [searchValue, setSearchValue] = useState<string | undefined>(undefined);
  const [driverPendencyList, setDriverPendencyList] = useState<
    DriverPendencyModel[]
  >([]);

  useEffect(() => {
    if (!location.search.includes("modalOpened=true")) {
      setIsModalOpen(false);
    }
  }, [location]);

  const loadDriverPendencys = async () => {
    setisLoading(true);
    try {
      const { data } = await api.get(
        endpoints.DRIVER_PENDENCIES({
          pathVariables: {
            id: match.params.id,
          },
        })
      );
      setisLoading(false);
      if (data) {
        setDriverPendencyList(data);
      } else {
        history.push("/menu", "none", "replace");
      }
    } catch (error) {
      setisLoading(false);
      showErrorAlert(TEXT.loadPendenciesFailed);
    }
  };

  useEffect(() => {
    loadDriverPendencys();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const filteredList = useMemo(() => {
    return filterListObj(driverPendencyList, searchValue);
  }, [driverPendencyList, searchValue]);

  const totalCost = useMemo(() => {
    let cost = 0;
    driverPendencyList.forEach((driverPendency) => {
      if (driverPendency.cost) {
        cost += driverPendency.cost;
      }
    });
    return cost;
  }, [driverPendencyList]);

  const closeModal = useCallback((response?: DriverPendencyModel) => {
    setIsModalOpen(false);
    nav.goBack();

    if (!response) return;

    setDriverPendencyList((prev) => {
      if (response.delete && response.id !== undefined) {
        return prev.filter((item) => item.id !== response.id);
      }
      const exists = prev.some((item) => item.id === response.id);
      if (exists) {
        return prev.map((item) => (item.id === response.id ? response : item));
      }
      return [response, ...prev];
    });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  return (
    <IonPage id="driver-pendencies-page">
      <IonHeader>
        <IonToolbar>
          <IonButtons slot="start">
            <IonBackButton defaultHref="/menu" />
          </IonButtons>
          <IonTitle>{TEXT.driverPendencies}</IonTitle>
          <IonButtons slot="end">
            <IonButton
              strong={true}
              onClick={() => {
                setModalDriverPendencyValue({});
                setIsModalOpen(true);
                nav.push(nav.location.pathname + "?modalOpened=true");
              }}
            >
              <IonIcon slot="icon-only" icon={add}></IonIcon>
            </IonButton>
          </IonButtons>
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
          <IonListHeader>
            <IonLabel>
              <p>
                <strong>
                  {TEXT.total} {currencyFormat(totalCost)}
                </strong>
              </p>
            </IonLabel>
          </IonListHeader>
          {filteredList.map((driverPendency: DriverPendencyModel, index) => {
            return (
              <IonItem
                key={index}
                button
                onClick={() => {
                  setModalDriverPendencyValue(driverPendency);
                  setIsModalOpen(true);
                  nav.push(nav.location.pathname + "?modalOpened=true");
                }}
              >
                <IonLabelLeft class="ion-text-wrap">
                  <h2>{formatDateView(driverPendency.date)}</h2>
                  <p>{driverPendency.name}</p>
                </IonLabelLeft>
                <IonLabekRight>
                  <p>{currencyFormat(driverPendency.cost)}</p>
                </IonLabekRight>
              </IonItem>
            );
          })}
          {!isLoading && filteredList.length === 0 && <ItemNotFound />}
        </IonList>
      </IonContent>
      <IonModal isOpen={isModalOpen} backdropDismiss={false}>
        <DriverPendencyAdd
          driverCarId={match.params.id}
          closeModal={closeModal}
          initialValues={modalDriverPendencyValue}
        />
      </IonModal>
    </IonPage>
  );
};

export default DriverPendencies;
